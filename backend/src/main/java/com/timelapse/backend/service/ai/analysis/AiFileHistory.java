package com.timelapse.backend.service.ai.analysis;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.springframework.stereotype.Component;

import com.timelapse.backend.service.git.GitRepositoryService;

@Component
public class AiFileHistory {

    private final GitRepositoryService repositories;

    public AiFileHistory(GitRepositoryService repositories) {
        this.repositories = repositories;
    }

    public String evidence(Path directory, String sha, String path, boolean evolution) throws Exception {
        var text = new StringBuilder("Selected revision: " + sha + "\nSelected file: " + path + "\nCompare against first parent. Rename links are Git similarity matches, not confirmed semantic identity.\n");
        String tracked = path;
        int visited = 0, changes = 0;
        try (var repository = repositories.openRepository(directory); var walk = new RevWalk(repository)) {
            var current = walk.parseCommit(repository.resolve(sha));
            while (current != null && visited < 500 && changes < 20 && text.length() < 15000) {
                visited++;
                var parent = current.getParentCount() == 0 ? null : walk.parseCommit(current.getParent(0));
                var bytes = new ByteArrayOutputStream();
                int limit = evolution ? 2000 : 12000;
                var bounded = new OutputStream() {
                    @Override
                    public void write(int value) {
                        if (bytes.size() < limit) {
                            bytes.write(value);
                    
                        }}

                    @Override
                    public void write(byte[] value, int offset, int length) {
                        bytes.write(value, offset, Math.min(length, Math.max(0, limit - bytes.size())));
                    }
                };
                try (var formatter = new DiffFormatter(bounded)) {
                    formatter.setRepository(repository);
                    formatter.setDetectRenames(true);
                    for (var entry : formatter.scan(parent == null ? (RevTree) null : parent.getTree(), current.getTree())) {
                        if (!tracked.equals(entry.getNewPath()) && !tracked.equals(entry.getOldPath())) {
                            continue;
                        }
                        formatter.format(entry);
                        formatter.flush();
                        String patch = bytes.toString(StandardCharsets.UTF_8);
                        if (patch.contains("Binary files differ")) {
                            throw new IllegalArgumentException("Binary file changes cannot be explained");
                        }
                        text.append("\nCommit: ").append(current.getName()).append(" Parent: ").append(parent == null ? "none" : parent.getName())
                                .append("\nMessage: ").append(ExplicitAiService.bounded(current.getFullMessage(), 300)).append("\n")
                                .append(patch).append(bytes.size() >= limit ? "\n[Patch truncated]\n" : "\n");
                        changes++;
                        if (entry.getChangeType() == DiffEntry.ChangeType.RENAME) {
                            tracked = entry.getOldPath();
                        }
                        if (entry.getChangeType() == DiffEntry.ChangeType.ADD) {
                            parent = null;
                        }
                        break;
                    }
                }
                if (!evolution) {
                    break;
                }
                current = parent;
            }
        }
        if (changes == 0) {
            throw new IllegalArgumentException(evolution ? "No file history found" : "This file is unchanged in this commit");
        }
        if (evolution) {
            text.append("\nEvidence: ").append(changes).append(" changes across ").append(visited)
                    .append(" first-parent commits. Bounded to 20 changes / 500 commits; history may be partial.\n");
        }
        return ExplicitAiService.bounded(text.toString(), 16000);
    }
}
