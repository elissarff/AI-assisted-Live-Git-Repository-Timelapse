package com.timelapse.backend.service.refactoring;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.JarFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelapse.backend.BackendApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RefactoringDetector {
    private final ObjectMapper mapper;
    private final int timeoutSeconds;
    private final int heapMb;

    public RefactoringDetector(ObjectMapper mapper,
            @Value("${app.refactoring.timeout-seconds:60}") int timeoutSeconds,
            @Value("${app.refactoring.max-heap-mb:768}") int heapMb) {
        if (timeoutSeconds < 1 || timeoutSeconds > 300 || heapMb < 128) throw new IllegalArgumentException("Invalid refactoring worker limits");
        this.mapper = mapper;
        this.timeoutSeconds = timeoutSeconds;
        this.heapMb = heapMb;
    }

    public RefactoringResult detect(Path gitDirectory, String sha) throws Exception {
        return detect(gitDirectory, sha, timeoutSeconds);
    }

    public RefactoringResult detect(Path gitDirectory, String sha, int limitSeconds) throws Exception {
        if (limitSeconds < 1 || limitSeconds > 300) throw new IllegalArgumentException("Analysis limit must be between 1 and 300 seconds");
        Session session = sessions.get();
        if (session == null || !session.process.isAlive()) {
            if (session != null) discard(session);
            Path directory = Files.createTempDirectory("gitlapse-refactoring-");
            try {
                session = new Session(directory, start(command(gitDirectory, sha, directory.resolve("result.json")), directory.resolve("worker.log")));
                sessions.set(session);
                live.add(session);
            } catch (Exception e) {
                Files.deleteIfExists(directory.resolve("worker.log"));
                Files.deleteIfExists(directory);
                throw e;
            }
        }
        Path output = session.directory.resolve("result.json");
        try {
            Path pending = session.directory.resolve("request.tmp");
            mapper.writeValue(pending.toFile(), new String[]{"--refactoring-worker", gitDirectory.toAbsolutePath().toString(), sha, output.toString()});
            Files.move(pending, session.directory.resolve("request.json"), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(limitSeconds);
            while (!Files.exists(output)) {
                if (!session.process.isAlive()) throw new IllegalStateException("Detector process failed. " + logTail(session.directory.resolve("worker.log")));
                if (System.nanoTime() >= deadline) throw new TimeoutException("Analysis exceeded " + limitSeconds + " seconds. No complete result was saved.");
                Thread.sleep(10);
            }
            RefactoringResult result = mapper.readValue(output.toFile(), RefactoringResult.class);
            if (result.detections() == null) throw new IllegalStateException("Detector returned an invalid result");
            Files.delete(output);
            return result;
        } catch (Exception e) {
            discard(session);
            sessions.remove();
            throw e;
        }
    }

    private record Session(Path directory, Process process) {}
    private final ThreadLocal<Session> sessions = new ThreadLocal<>();
    private final java.util.Set<Session> live = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void discard(Session session) throws Exception {
        if (!live.remove(session)) return;
        session.process.destroyForcibly();
        session.process.onExit().join();
        try (var files = Files.list(session.directory)) {
            for (Path file : files.toList()) Files.deleteIfExists(file);
        }
        Files.deleteIfExists(session.directory);
    }

    @jakarta.annotation.PreDestroy
    public void close() throws Exception {
        for (Session session : List.copyOf(live)) discard(session);
        sessions.remove();
    }

    Process start(List<String> command, Path log) throws Exception {
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        // The detector only needs local Git evidence, not the backend's credentials or JVM agents.
        builder.environment().keySet().removeIf(key -> key.matches("(?i).*(KEY|TOKEN|SECRET|PASSWORD).*"));
        for (String key : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) builder.environment().remove(key);
        return builder.start();
    }

    List<String> command(Path gitDirectory, String sha, Path output) throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xmx" + heapMb + "m");
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        // Support both Maven/IDE classpaths and the executable Spring Boot jar used by Docker.
        boolean bootJar = false;
        if (!classPath.contains(File.pathSeparator) && classPath.endsWith(".jar")) {
            try (var jar = new JarFile(classPath)) {
                bootJar = jar.getEntry("BOOT-INF/classes/com/timelapse/backend/BackendApplication.class") != null;
            }
        }
        if (bootJar) { command.add("-jar"); command.add(classPath); }
        else { command.add("-cp"); command.add(classPath); command.add(BackendApplication.class.getName()); }
        command.addAll(List.of("--refactoring-worker", output.getParent().toString()));
        return command;
    }

    private String logTail(Path log) throws Exception {
        try (var file = new java.io.RandomAccessFile(log.toFile(), "r")) {
            file.seek(Math.max(0, file.length() - 1200));
            byte[] bytes = new byte[(int)(file.length() - file.getFilePointer())];
            file.readFully(bytes);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
