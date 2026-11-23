package com.timelapse.backend.service.git;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiFileEvidenceTest {

    @TempDir
    Path directory;

    @Test
    void aiFileReadsAreBoundedAndBinaryAware() throws Exception {
        String sha;
        try (var git = Git.init().setDirectory(directory.toFile()).call()) {
            Files.writeString(directory.resolve("large.txt"), "x".repeat(100000));
            Files.write(directory.resolve("image.bin"), new byte[]{0, 1, 2, 3});
            git.add().addFilepattern(".").call();
            sha = git.commit().setMessage("Fixture").setAuthor("Test", "test@example.com").call().getName();
        }
        var service = new GitMiningService(new GitRepositoryService(), new GitCommitService(), null, new GitFileService(), null, null);
        var text = service.getFileContent(directory.resolve(".git"), sha, "large.txt", 48000);
        assertEquals(100000, text.size());
        assertFalse(text.binary());
        assertTrue(text.content().length() < 48100);
        assertTrue(text.content().endsWith("[File truncated]"));
        var full = service.getFileContent(directory.resolve(".git"), sha, "large.txt");
        assertEquals(100000, full.content().length());
        assertEquals(text.size(), full.size());
        var binary = service.getFileContent(directory.resolve(".git"), sha, "image.bin", 48000);
        assertTrue(binary.binary());
        assertNull(binary.content());
        var fullBinary = service.getFileContent(directory.resolve(".git"), sha, "image.bin");
        assertTrue(fullBinary.binary());
        assertNull(fullBinary.content());
        assertThrows(IllegalArgumentException.class, () -> service.getFileContent(directory.resolve(".git"), sha, "missing", 48000));
    }
}
