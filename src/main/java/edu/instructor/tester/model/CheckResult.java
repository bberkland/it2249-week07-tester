package edu.instructor.tester.model;

/**
 * One named test check produced during evaluation of a student submission.
 */
public class CheckResult {

    public enum Status { PASS, FAIL, WARN }

    public enum Category {
        SOURCE_ANALYSIS("Source Code Analysis"),
        OUTPUT_CHECKS  ("Output Verification"),
        AI_ANALYSIS    ("AI Code Review");

        private final String label;
        Category(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final Category category;
    private final String   checkName;
    private final Status   status;
    private final String   details;

    private CheckResult(Category category, String checkName, Status status, String details) {
        this.category  = category;
        this.checkName = checkName;
        this.status    = status;
        this.details   = details;
    }

    // ── factory helpers ───────────────────────────────────────────────────────

    public static CheckResult pass(Category cat, String name, String details) {
        return new CheckResult(cat, name, Status.PASS, details);
    }
    public static CheckResult fail(Category cat, String name, String details) {
        return new CheckResult(cat, name, Status.FAIL, details);
    }
    public static CheckResult warn(Category cat, String name, String details) {
        return new CheckResult(cat, name, Status.WARN, details);
    }

    // ── getters ───────────────────────────────────────────────────────────────

    public Category getCategory()  { return category; }
    public String   getCheckName() { return checkName; }
    public Status   getStatus()    { return status; }
    public String   getDetails()   { return details; }

    public boolean isPassed()  { return status == Status.PASS; }
    public boolean isFailed()  { return status == Status.FAIL; }
    public boolean isWarning() { return status == Status.WARN; }
}
