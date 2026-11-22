package com.timelapse.backend.service.refactoring;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

/** Runs without Spring or database access, inside a reusable supervised JVM. */
public final class RefactoringWorker {
    private RefactoringWorker() {}

    public static void run(String[] args) throws Exception {
        if (args.length == 2) {
            // Parent death closes stdin, preventing orphaned idle workers.
            Thread parentWatch = new Thread(() -> {
                try { while (System.in.read() != -1) {} } catch (Exception ignored) {}
                System.exit(0);
            }, "parent-watch");
            parentWatch.setDaemon(true);
            parentWatch.start();
            Path directory = Path.of(args[1]);
            ObjectMapper protocol = new ObjectMapper();
            while (true) {
                Path request = directory.resolve("request.json");
                if (!java.nio.file.Files.exists(request)) { Thread.sleep(10); continue; }
                String[] job = protocol.readValue(request.toFile(), String[].class);
                java.nio.file.Files.delete(request);
                run(job);
            }
        }
        if (args.length != 4) throw new IllegalArgumentException("Expected worker, git directory, SHA and result path");
        String sha = args[2];
        if (!sha.matches("[a-fA-F0-9]{40}")) throw new IllegalArgumentException("Invalid commit SHA");
        ObjectMapper mapper = new ObjectMapper();
        List<RefactoringResult.Detection> detections = new ArrayList<>();
        String parent = null;
        try (var repository = new FileRepositoryBuilder().setGitDir(Path.of(args[1]).toFile()).setMustExist(true).build();
             var walk = new RevWalk(repository)) {
            var commit = walk.parseCommit(repository.resolve(sha));
            if (commit.getParentCount() > 0) {
                parent = commit.getParent(0).getName();
                int[] callbacks = {0};
                Exception[] failure = {null};
                // Use the explicit first parent, matching the parent SHA stored with the evidence.
                // The parent process owns the deadline and can kill even non-interruptible code.
                new GitHistoryRefactoringMinerImpl().detectAtMergeCommit(repository, sha, 0, new RefactoringHandler() {
                    @Override public void handle(String commitId, List<Refactoring> refactorings) {
                        callbacks[0]++;
                        if (!sha.equals(commitId) || refactorings == null) {
                            failure[0] = new IllegalStateException("Invalid detector callback");
                            return;
                        }
                        for (var r : refactorings) detections.add(new RefactoringResult.Detection(
                            r.getRefactoringType().toString(), r.toString(),
                            mapper.valueToTree(r.leftSide()), mapper.valueToTree(r.rightSide())));
                    }
                    @Override public void handleException(String commitId, Exception error) { failure[0] = error; }
                });
                if (failure[0] != null) throw new IllegalStateException("Detector failed", failure[0]);
                if (callbacks[0] != 1) throw new IllegalStateException("Detector did not return exactly one successful result");
            }
        }
        Path output = Path.of(args[3]);
        Path temporary = output.resolveSibling("result.tmp");
        mapper.writeValue(temporary.toFile(), new RefactoringResult(parent, detections));
        java.nio.file.Files.move(temporary, output, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
}
