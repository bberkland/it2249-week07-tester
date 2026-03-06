# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build (requires Java 25 and Maven)
mvn clean package

# Run
java --enable-preview -jar target/week07-tester-1.0.0.jar
# or:
mvn spring-boot:run

# App serves on http://localhost:8080
```

Java 25 with `--enable-preview` is required. The pom.xml configures this for Maven compilation automatically.

## What This Project Does

An instructor-facing Spring Boot 4.0.2 web app that automatically grades student Java submissions for a Week 07 ArrayList assignment. An instructor uploads a student's `.java` file via the web UI, and the app runs a three-phase evaluation pipeline:

1. **Source Analysis** (`SourceAnalysisService`) — Regex-based static analysis checks that `getMean(ArrayList<Integer>)` returning double/float and `getMedian(ArrayList<Integer>)` returning int exist with correct signatures, are called from `main()`, that `ArrayList<Integer>` is used, that random number generation is present with the correct range (`nextInt(101)` for [0,100] inclusive), and that `.add()` and `.sort()` are used.

2. **Execution** (`ExecutionService`) — Writes the source to a temp directory and runs it via `java FileName.java` (single-file source launch, no separate javac step). Pipes the user-specified input size as stdin. 15-second timeout. Cleans up temp files after.

3. **Output Analysis** (`OutputAnalysisService`) — Parses stdout to collect printed integers, verifies count matches expected input size, checks all values are in [0,100], and confirms the reported mean and median values match the actual mean/median of the printed list.

Results are aggregated in `TestResult` (containing `CheckResult` items with PASS/FAIL/WARN status) and rendered via Thymeleaf templates.

## Key Architecture Details

- **No database** — stateless; each upload is evaluated independently.
- **No compilation step** — relies on Java 25 single-file source launch (`java File.java`).
- **No external AI calls** — all analysis is regex/pattern-matching based. Student code never leaves the server.
- `Week07.java` in the project root is a **sample student submission** for testing, not part of the app itself.
- `CheckResult.Category` enum (`SOURCE_ANALYSIS`, `OUTPUT_CHECKS`) groups checks in the results UI.
- The `EvaluationService` is the orchestrator that wires the three phases together.
