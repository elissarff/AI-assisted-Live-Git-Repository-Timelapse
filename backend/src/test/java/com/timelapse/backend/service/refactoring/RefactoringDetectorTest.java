package com.timelapse.backend.service.refactoring;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefactoringDetectorTest {
    @TempDir Path directory;

    @Test
    void realIsolatedWorkerDetectsClassRenameAndPreservesSourceLocations() throws Exception {
        String parent;
        String sha;
        try (var git = Git.init().setDirectory(directory.toFile()).call()) {
            Files.writeString(directory.resolve("Before.java"), "public class Before { public int value() { return 42; } }\n");
            git.add().addFilepattern(".").call();
            parent = git.commit().setMessage("Initial").setAuthor("Test", "test@example.com").call().getName();
            Files.delete(directory.resolve("Before.java"));
            Files.writeString(directory.resolve("After.java"), "public class After { public int value() { return 42; } }\n");
            git.rm().addFilepattern("Before.java").call();
            git.add().addFilepattern(".").call();
            sha = git.commit().setMessage("Rename class").setAuthor("Test", "test@example.com").call().getName();
        }
        var detector = spy(new RefactoringDetector(new ObjectMapper(), 30, 512));
        RefactoringResult result;
        try {
            result = detector.detect(directory.resolve(".git"), sha);
            assertEquals(result, detector.detect(directory.resolve(".git"), sha));
            verify(detector, times(1)).start(anyList(), any());
        } finally { detector.close(); }
        assertEquals(parent, result.parentSha());
        var rename = result.detections().stream().filter(d -> d.type().equals("RENAME_CLASS")).findFirst()
            .orElseThrow(() -> new AssertionError("Expected class rename, received " + result.detections()));
        assertTrue(rename.beforeLocations().toString().contains("Before.java"));
        assertTrue(rename.afterLocations().toString().contains("After.java"));
    }

    @Test
    void hardDeadlineKillsWorkerProcessBeforeReturning() throws Exception {
        var detector = spy(new RefactoringDetector(new ObjectMapper(), 1, 512));
        doReturn(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", System.getProperty("surefire.test.class.path"), HangingWorker.class.getName()))
            .when(detector).command(any(), anyString(), any());
        var child = new AtomicReference<Process>();
        doAnswer(invocation -> {
            Process process = (Process)invocation.callRealMethod();
            child.set(process);
            return process;
        }).when(detector).start(anyList(), any());
        assertThrows(TimeoutException.class, () -> detector.detect(directory, "a".repeat(40)));
        assertNotNull(child.get());
        assertFalse(child.get().isAlive(), "Timed out workers must be dead before the next job starts");
    }

    public static class HangingWorker {
        public static void main(String[] args) {
            while (true) { try { Thread.sleep(1000); } catch (InterruptedException ignored) {} }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "gitlapse.test.repository", matches = ".+")
    void realAssetviewerTimeoutDoesNotPreventNextCommitAnalysis() throws Exception {
        Path repository = Path.of(System.getProperty("gitlapse.test.repository"));
        var detector = new RefactoringDetector(new ObjectMapper(), 30, 768);
        assertThrows(TimeoutException.class,
            () -> detector.detect(repository, "7c62f79182650dc6b605c453df0d529b7dd3e370", 2));
        var result = detector.detect(repository, "acf74dbe0bfc62142ebece2b9cda0d0f7e4ee5f9");
        assertNotNull(result.detections());
        assertTrue(result.detections().isEmpty());
    }
}
