package com.timelapse.backend.service.ai.analysis;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.timelapse.backend.service.git.GitRepositoryService;

class AiFileHistoryTest {

    @TempDir
    Path directory;

    @Test
    void tracksRenamesStopsAtCreationAndRejectsUnchangedDiff() throws Exception {
        String root, modified, renamed, head;
        try (var git = Git.init().setDirectory(directory.toFile()).call()) {
            Files.writeString(directory.resolve("Before.java"), "class Before {}\n");
            git.add().addFilepattern(".").call();
            root = git.commit().setMessage("Create file").setAuthor("T", "t@example.com").call().getName();
            Files.writeString(directory.resolve("Before.java"), "class Before { int number; }\n");
            git.add().addFilepattern(".").call();
            modified = git.commit().setMessage("Add number").setAuthor("T", "t@example.com").call().getName();
            Files.move(directory.resolve("Before.java"), directory.resolve("After.java"));
            git.rm().addFilepattern("Before.java").call();
            git.add().addFilepattern(".").call();
            renamed = git.commit().setMessage("Rename file").setAuthor("T", "t@example.com").call().getName();
            Files.writeString(directory.resolve("Other.txt"), "Unrelated content");
            git.add().addFilepattern(".").call();
            head = git.commit().setMessage("Unrelated change").setAuthor("T", "t@example.com").call().getName();
        }
        var history = new AiFileHistory(new GitRepositoryService());
        Path repo = directory.resolve(".git");
        String evolution = history.evidence(repo, head, "After.java", true);
        assertTrue(evolution.contains(root));
        assertTrue(evolution.contains(modified));
        assertTrue(evolution.contains(renamed));
        assertTrue(evolution.contains("Before.java"));
        assertFalse(evolution.contains("Unrelated content"));
        assertThrows(IllegalArgumentException.class, () -> history.evidence(repo, head, "After.java", false));
        String diff = history.evidence(repo, modified, "Before.java", false);
        assertTrue(diff.contains("+class Before { int number; }"));
        assertFalse(diff.contains(renamed));
        assertTrue(history.evidence(repo, root, "Before.java", false).contains("Parent: none"));
    }
}
