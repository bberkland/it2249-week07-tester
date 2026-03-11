package edu.instructor.tester.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs a student .java source file using the single-file source-code launch feature
 * that has been standard since Java 11 and is fully supported in Java 25:
 *
 *     java Week07.java
 *
 * No javac step, no --enable-preview flag, no classpath setup needed.
 * The JVM compiles and runs the source in one pass; syntax and compile errors
 * appear on stderr with a non-zero exit code, just like a normal failure.
 */
@Service
public class ExecutionService {

    private static final int TIMEOUT_SECONDS = 15;
    private static final int STREAM_READ_TIMEOUT_SECONDS = 2;
    private static final int MAX_CAPTURE_BYTES = 1_000_000;

    public record Result(boolean success, String stdout, String stderr) {}
    private record CapturedOutput(String text) {}

    /**
     * Writes the source to a temp file and runs it with {@code java FileName.java}.
     *
     * @param sourceContent  raw Java source text
     * @param fileName       original file name, e.g. "Week07.java"
     * @param stdinInput     text to pipe as standard input (e.g. "10\n")
     */
    public Result run(String sourceContent, String fileName, String stdinInput)
            throws IOException, InterruptedException {

        // Write source to a temp directory — java needs a real path to the file.
        Path tempDir = Files.createTempDirectory("week07-tester-");
        ExecutorService ioReaders = Executors.newFixedThreadPool(2);
        try {
            Path sourceFile = tempDir.resolve(fileName);
            Files.writeString(sourceFile, sourceContent);

            ProcessBuilder pb = new ProcessBuilder("java", sourceFile.toString());
            pb.directory(tempDir.toFile());

            Process proc = pb.start();

            // Read both streams concurrently so the child process cannot block on full pipes.
            Future<CapturedOutput> stdoutFuture = ioReaders.submit(() -> readStreamCapped(proc.getInputStream()));
            Future<CapturedOutput> stderrFuture = ioReaders.submit(() -> readStreamCapped(proc.getErrorStream()));

            // Feed simulated user input then close stdin immediately.
            try (OutputStream stdin = proc.getOutputStream()) {
                stdin.write(stdinInput.getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            }

            boolean finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                proc.waitFor(STREAM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }

            CapturedOutput stdout = getCaptured(stdoutFuture, "stdout");
            CapturedOutput stderr = getCaptured(stderrFuture, "stderr");

            if (!finished) {
                String timeoutMessage = "Program timed out after " + TIMEOUT_SECONDS + " seconds. " +
                        "This usually means an infinite loop, blocking I/O, or waiting for more input.";
                String mergedStderr = mergeMessages(stderr.text(), timeoutMessage);
                return new Result(false, stdout.text(), mergedStderr);
            }

            boolean success = (proc.exitValue() == 0);
            return new Result(success, stdout.text(), stderr.text());
        } finally {
            ioReaders.shutdownNow();
            cleanUp(tempDir);
        }
    }

    private CapturedOutput getCaptured(Future<CapturedOutput> streamFuture, String streamName) {
        try {
            return streamFuture.get(STREAM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return new CapturedOutput("[" + streamName + " capture timed out]");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CapturedOutput("[" + streamName + " capture interrupted]");
        } catch (ExecutionException e) {
            return new CapturedOutput("[" + streamName + " capture failed: " + e.getCause().getMessage() + "]");
        }
    }

    private CapturedOutput readStreamCapped(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        boolean truncated = false;

        int read;
        while ((read = stream.read(chunk)) != -1) {
            if (buffer.size() < MAX_CAPTURE_BYTES) {
                int toWrite = Math.min(read, MAX_CAPTURE_BYTES - buffer.size());
                buffer.write(chunk, 0, toWrite);
                if (toWrite < read) {
                    truncated = true;
                }
            } else {
                truncated = true;
            }
        }

        String text = buffer.toString(StandardCharsets.UTF_8).trim();
        if (truncated) {
            text = mergeMessages(text, "[output truncated after " + MAX_CAPTURE_BYTES + " bytes]");
        }
        return new CapturedOutput(text);
    }

    private String mergeMessages(String existing, String next) {
        if (existing == null || existing.isBlank()) return next;
        if (next == null || next.isBlank()) return existing;
        return existing + System.lineSeparator() + next;
    }

    private void cleanUp(Path dir) {
        try {
            if (dir != null && Files.exists(dir)) {
                try (var walk = Files.walk(dir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                        .map(java.nio.file.Path::toFile)
                        .forEach(java.io.File::delete);
                }
            }
        } catch (IOException ignored) {}
    }
}
