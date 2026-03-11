package edu.instructor.tester.service;

import edu.instructor.tester.model.CheckResult;
import edu.instructor.tester.model.TestResult;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the full evaluation pipeline for one student submission:
 *
 *   1. Source analysis  — AST-based checks on the source text
 *   2. Execution        — runs "java FileName.java" with piped stdin
 *   3. Output analysis  — parses stdout, verifies numbers, mean, and median
 *   4. AI fallback      — reviews extracted methods when the output lacks values
 *
 * There is no separate compilation step. Java 25 single-file source launch
 * ("java Week07.java") handles compilation internally; any syntax errors surface
 * as stderr output with a non-zero exit code and are shown to the instructor.
 */
@Service
public class EvaluationService {

    private final ExecutionService      executionService;
    private final SourceAnalysisService sourceAnalysisService;
    private final OutputAnalysisService outputAnalysisService;
    private final AiAnalysisService     aiAnalysisService;

    public EvaluationService(ExecutionService executionService,
                             SourceAnalysisService sourceAnalysisService,
                             OutputAnalysisService outputAnalysisService,
                             AiAnalysisService aiAnalysisService) {
        this.executionService      = executionService;
        this.sourceAnalysisService = sourceAnalysisService;
        this.outputAnalysisService = outputAnalysisService;
        this.aiAnalysisService     = aiAnalysisService;
    }

    /**
     * Runs the full evaluation and returns a populated TestResult.
     *
     * @param sourceContent  raw Java source text
     * @param fileName       original file name (e.g. "Week07.java")
     * @param inputSize      integer to send as stdin (e.g. 10)
     */
    public TestResult evaluate(String sourceContent, String fileName, int inputSize) {
        TestResult result = new TestResult();
        result.setFileName(fileName);
        result.setInputSize(inputSize);

        // ── Step 1: Source analysis ────────────────────────────────────────────
        // Always runs — useful feedback even when the program can't execute.
        sourceAnalysisService.analyze(sourceContent).forEach(result::addCheck);

        // ── Step 2: Run "java FileName.java" ──────────────────────────────────
        ExecutionService.Result execResult;
        try {
            execResult = executionService.run(sourceContent, fileName, inputSize + "\n");
        } catch (Exception e) {
            result.addCheck(CheckResult.fail(CheckResult.Category.OUTPUT_CHECKS,
                    "Program launched",
                    "Internal error launching java: " + e.getMessage()));
            return result;
        }

        result.setProgramOutput(execResult.stdout());
        result.setProgramStderr(execResult.stderr());
        result.setExecutionSucceeded(execResult.success());

        if (!execResult.success()) {
            // stderr may contain compile errors or a runtime exception stack trace
            String detail = execResult.stderr().isBlank()
                    ? "Program exited with a non-zero exit code and no stderr output."
                    : "See stderr output below for compile or runtime errors.";
            result.addCheck(CheckResult.fail(CheckResult.Category.OUTPUT_CHECKS,
                    "Program ran successfully",
                    detail));
            return result;
        }

        result.addCheck(CheckResult.pass(CheckResult.Category.OUTPUT_CHECKS,
                "Program ran successfully",
                "Exited cleanly with no errors or exceptions."));

        // ── Step 3: Output analysis ────────────────────────────────────────────
        var outputResult = outputAnalysisService.analyze(execResult.stdout(), inputSize);
        outputResult.checks().forEach(result::addCheck);

        // ── Step 4: AI fallback when output has no printable values ──────────
        if (!outputResult.valuesFound()) {
            String meanMethod = sourceAnalysisService.extractFullMethod(sourceContent, "getMean");
            if (meanMethod != null) {
                aiAnalysisService.analyzeMethod(meanMethod, "getMean").forEach(result::addCheck);
            }

            String medianMethod = sourceAnalysisService.extractFullMethod(sourceContent, "getMedian");
            if (medianMethod != null) {
                aiAnalysisService.analyzeMethod(medianMethod, "getMedian").forEach(result::addCheck);
            }
        }

        return result;
    }
}
