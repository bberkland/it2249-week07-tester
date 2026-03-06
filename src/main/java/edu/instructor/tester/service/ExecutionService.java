package edu.instructor.tester.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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

    public record Result(boolean success, String stdout, String stderr) {}

    /**
     * Writes the source to a temp file and runs it with {@code java FileName.java}.
     *
     * @param sourceContent  raw Java source text
     * @param fileName       original file name, e.g. "Week07.java"
     * @param stdinInput     text to pipe as standard input (e.g. "10\n")
     */
    public Result run(String sourceContent, String fileName, String stdinInput)
            throws IOException, InterruptedException {

        // Write source to a temp directory — java needs a real path to the file
        Path tempDir = Files.createTempDirectory("week07-tester-");
        Path sourceFile = tempDir.resolve(fileName);
        Files.writeString(sourceFile, sourceContent);

        ProcessBuilder pb = new ProcessBuilder("java", sourceFile.toString());
        pb.directory(tempDir.toFile());

        Process proc = pb.start();

        // Feed the simulated user input then close stdin immediately so the program
        // doesn't hang waiting for more after the last Scanner read.
        try (var stdin = proc.getOutputStream()) {
            stdin.write(stdinInput.getBytes());
            stdin.flush();
        }

        boolean finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            cleanUp(tempDir);
            return new Result(false, "",
                    "Program timed out after " + TIMEOUT_SECONDS + " seconds. " +
                    "This usually means an infinite loop or the program is waiting " +
                    "for more input than was provided.");
        }

        String stdout = new String(proc.getInputStream().readAllBytes()).trim();
        String stderr = new String(proc.getErrorStream().readAllBytes()).trim();
        boolean success = (proc.exitValue() == 0);

        cleanUp(tempDir);
        return new Result(success, stdout, stderr);
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
