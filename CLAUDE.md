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

Java 25 with `--enable-preview` is required. The `pom.xml` configures this for Maven compilation automatically.

## Project Purpose

Instructor-facing Spring Boot 4.0.2 web app that evaluates student Java submissions for the Week 07 ArrayList assignment (mean and median). It is an instructor aid for fast consistency checks, not a full auto-grader.

## Evaluation Pipeline

1. **Source Analysis** (`SourceAnalysisService`)
   - Uses Java AST parsing (`javac` tree API) rather than raw regex matching.
   - Verifies `getMean(ArrayList<Integer>)`, `getMedian(ArrayList<Integer>)`, `main()` invocation of both methods, `ArrayList<Integer>` usage, random generation usage/range, and `.add()` / `.sort()` usage.
   - AST-driven checks avoid false positives from comments and string literals.
   - No sanitized-text fallback is used; source checks depend on the Java 25 AST path.

2. **Execution** (`ExecutionService`)
   - Writes source to a temp directory and runs with single-file launch: `java FileName.java`.
   - Drains stdout and stderr concurrently to prevent child-process blocking on full output pipes.
   - Captures bounded output (`MAX_CAPTURE_BYTES`) and marks truncation when needed.
   - Enforces 15-second timeout and returns captured partial output on timeout.
   - Always cleans temporary files in `finally`.

3. **Output Analysis** (`OutputAnalysisService`)
   - Parses output values via multiple strategies:
     - explicit line values (pure integer lines and recognized labeled value lines)
     - bracket lists like `[1, 2, 3]`
   - Chooses the most plausible value set using confidence + expected-count scoring.
   - Flags ambiguous parsing situations with a WARN check.
   - Extracts mean/median from lines containing those keywords using the **last numeric token** on the line.
   - Validates count, value range, mean correctness, and median correctness against parsed values.

4. **AI Fallback (optional)** (`AiAnalysisService`)
   - If no printable values are found, extracted `getMean` / `getMedian` methods can be evaluated by Claude when `ANTHROPIC_API_KEY` is configured.
   - If key is missing or API fails, AI checks return WARN (non-fatal).

## Key Architecture Notes

- Stateless app, no database.
- Single-file source launch is used instead of a separate `javac` compilation phase.
- `Week07.java` in the repository root is sample student code for local testing, not part of the Spring app runtime.
- Results are aggregated in `TestResult` as `CheckResult` entries (`PASS`, `FAIL`, `WARN`) grouped by category in the UI.
- `EvaluationService` orchestrates the full pipeline.
