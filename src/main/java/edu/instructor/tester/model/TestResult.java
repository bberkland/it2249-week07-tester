package edu.instructor.tester.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates everything produced while evaluating one student submission.
 *
 * There is no separate compilation output — Java 25 single-file launch compiles
 * and runs in one step, so compile errors simply appear in programStderr.
 */
public class TestResult {

    private String  fileName;
    private int     inputSize;
    private String  programOutput    = "";
    private String  programStderr    = "";
    private boolean executionSucceeded = false;

    private final List<CheckResult> checks = new ArrayList<>();

    // ── convenience ──────────────────────────────────────────────────────────

    public void addCheck(CheckResult check) { checks.add(check); }

    public boolean isOverallPassed() {
        return checks.stream().noneMatch(CheckResult::isFailed);
    }

    public long getPassedCount()  { return checks.stream().filter(CheckResult::isPassed).count(); }
    public long getWarningCount() { return checks.stream().filter(CheckResult::isWarning).count(); }
    public long getFailedCount()  { return checks.stream().filter(CheckResult::isFailed).count(); }
    public long getTotalCount()   { return checks.size(); }

    public List<CheckResult> getChecksForCategory(CheckResult.Category cat) {
        return checks.stream().filter(c -> c.getCategory() == cat).toList();
    }

    public CheckResult.Category[] getCategories() { return CheckResult.Category.values(); }

    // ── getters / setters ─────────────────────────────────────────────────────

    public String  getFileName()                    { return fileName; }
    public void    setFileName(String v)            { fileName = v; }

    public int     getInputSize()                   { return inputSize; }
    public void    setInputSize(int v)              { inputSize = v; }

    public String  getProgramOutput()               { return programOutput; }
    public void    setProgramOutput(String v)       { programOutput = v; }

    public String  getProgramStderr()               { return programStderr; }
    public void    setProgramStderr(String v)       { programStderr = v; }

    public boolean isExecutionSucceeded()           { return executionSucceeded; }
    public void    setExecutionSucceeded(boolean v) { executionSucceeded = v; }

    public List<CheckResult> getChecks()            { return checks; }
}
