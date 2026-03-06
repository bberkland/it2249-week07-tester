package edu.instructor.tester.service;

import edu.instructor.tester.model.CheckResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyses the captured stdout of a student program.
 *
 * Checks performed:
 *   1. Program produced output
 *   2. Correct number of values listed (matches expected input size)
 *   3. All values in [0, 100]
 *   4. Mean reported correctly (compared to actual mean of listed numbers)
 *   5. Median reported correctly (compared to calculated median of listed numbers)
 */
@Service
public class OutputAnalysisService {

    /** Bundles the check results with a flag indicating whether integer values were found in the output. */
    public record AnalysisResult(List<CheckResult> checks, boolean valuesFound) {}


    // Matches a line containing "mean" (case-insensitive) with a number (int or decimal)
    private static final Pattern MEAN_LINE = Pattern.compile(
            "(?i).*mean.*?([\\d]+\\.?\\d*).*");

    // Matches a line containing "median" (case-insensitive) with a number (int or decimal)
    private static final Pattern MEDIAN_LINE = Pattern.compile(
            "(?i).*median.*?([\\d]+\\.?\\d*).*");

    // A line that is purely an integer (possibly with surrounding whitespace)
    private static final Pattern INTEGER_LINE = Pattern.compile("^\\s*-?\\d+\\s*$");

    // A labelled line such as "Number 1: 42" — captures the LAST integer on the line.
    private static final Pattern LABELLED_INTEGER_LINE = Pattern.compile(".*\\b(\\d+)\\s*$");

    // A bracketed list like [53, 36, 87, 71, 88, 66, 22, 47, 69, 45]
    private static final Pattern BRACKET_LIST = Pattern.compile("\\[\\s*(-?\\d+(?:\\s*,\\s*-?\\d+)*)\\s*]");

    // ─────────────────────────────────────────────────────────────────────────

    public AnalysisResult analyze(String stdout, int expectedCount) {
        var results = new ArrayList<CheckResult>();

        if (stdout == null || stdout.isBlank()) {
            results.add(CheckResult.fail(CheckResult.Category.OUTPUT_CHECKS,
                "Program produced output",
                "The program produced no output at all. Check that System.out / IO is used in main()."));
            return new AnalysisResult(results, false);
        }

        results.add(CheckResult.pass(CheckResult.Category.OUTPUT_CHECKS,
            "Program produced output",
            "stdout is not empty — the program ran and printed something."));

        String[] lines = stdout.split("\\r?\\n");

        // ── 1. Collect the integer lines ──────────────────────────────────────
        // Skip lines that look like mean/median summary lines.
        List<Integer> listedNumbers = new ArrayList<>();
        for (String line : lines) {
            // Skip mean/median summary lines
            if (MEAN_LINE.matcher(line).matches() || MEDIAN_LINE.matcher(line).matches()) {
                continue;
            }

            if (INTEGER_LINE.matcher(line).matches()) {
                try {
                    listedNumbers.add(Integer.parseInt(line.trim()));
                } catch (NumberFormatException ignored) {}
            } else {
                Matcher lm = LABELLED_INTEGER_LINE.matcher(line);
                if (lm.matches()) {
                    try {
                        listedNumbers.add(Integer.parseInt(lm.group(1)));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // ── 1b. Fallback: look for bracket-delimited list like [53, 36, 87] ─
        if (listedNumbers.isEmpty()) {
            for (String line : lines) {
                if (MEAN_LINE.matcher(line).matches() || MEDIAN_LINE.matcher(line).matches()) {
                    continue;
                }
                Matcher bm = BRACKET_LIST.matcher(line);
                if (bm.find()) {
                    for (String token : bm.group(1).split("\\s*,\\s*")) {
                        try {
                            listedNumbers.add(Integer.parseInt(token.trim()));
                        } catch (NumberFormatException ignored) {}
                    }
                    break; // use the first bracket list found
                }
            }
        }

        // ── 2. Count check ────────────────────────────────────────────────────
        boolean countMatch = listedNumbers.size() == expectedCount;
        results.add(countMatch
            ? CheckResult.pass(CheckResult.Category.OUTPUT_CHECKS,
                "Correct number of values listed (" + expectedCount + ")",
                "Found exactly " + listedNumbers.size() + " integer line(s) in the output.")
            : CheckResult.fail(CheckResult.Category.OUTPUT_CHECKS,
                "Correct number of values listed (" + expectedCount + ")",
                "Expected " + expectedCount + " integer line(s) but found " + listedNumbers.size() + ". " +
                "Make sure each random number is printed on its own line inside the loop.")
        );

        if (listedNumbers.isEmpty()) {
            return new AnalysisResult(results, false);
        }

        // ── 3. Range check ────────────────────────────────────────────────────
        List<Integer> outOfRange = listedNumbers.stream()
                .filter(n -> n < 0 || n > 100)
                .toList();

        results.add(outOfRange.isEmpty()
            ? CheckResult.pass(CheckResult.Category.OUTPUT_CHECKS,
                "All listed values in range [0, 100]",
                "All " + listedNumbers.size() + " printed integers are within [0, 100].")
            : CheckResult.fail(CheckResult.Category.OUTPUT_CHECKS,
                "All listed values in range [0, 100]",
                "Out-of-range values found: " + outOfRange + ". " +
                "Use rand.nextInt(101) to generate numbers from 0 to 100 inclusive.")
        );

        // Compute ground-truth mean and median from the program's own listed numbers.
        double trueMean = listedNumbers.stream().mapToInt(Integer::intValue).average().orElse(0.0);

        List<Integer> sorted = new ArrayList<>(listedNumbers);
        Collections.sort(sorted);
        int size = sorted.size();

        // ── 4. Mean reported correctly ────────────────────────────────────────
        OptionalDouble reportedMean = findMeanValue(lines);

        if (reportedMean.isEmpty()) {
            results.add(CheckResult.fail(CheckResult.Category.OUTPUT_CHECKS,
                "Mean reported correctly",
                "Could not find a line containing the word 'mean' with a number. " +
                "Make sure getMean() result is printed with a label like \"Mean: N.NN\"."));
        } else if (Math.abs(reportedMean.getAsDouble() - trueMean) < 0.01) {
            results.add(CheckResult.pass(CheckResult.Category.OUTPUT_CHECKS,
                "Mean reported correctly",
                String.format("Reported mean = %.2f — matches the actual mean of the printed list (%.2f).",
                    reportedMean.getAsDouble(), trueMean)));
        } else {
            results.add(CheckResult.fail(CheckResult.Category.OUTPUT_CHECKS,
                "Mean reported correctly",
                String.format("Reported mean = %.2f but the actual mean of the printed list is %.2f. " +
                    "Check the getMean() logic — sum all values and divide by count as a floating-point operation.",
                    reportedMean.getAsDouble(), trueMean)));
        }

        // ── 5. Median reported correctly ──────────────────────────────────────
        OptionalDouble reportedMedian = findMedianValue(lines);

        // Calculate expected medians — accept both integer and floating-point results.
        // Integer median: what you get with int division (assignment spec).
        // Float median: what you get with double division (common student variation).
        double expectedMedianExact;
        int expectedMedianInt;
        if (size % 2 == 1) {
            expectedMedianExact = sorted.get(size / 2);
            expectedMedianInt = sorted.get(size / 2);
        } else {
            expectedMedianExact = (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
            expectedMedianInt = (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2;
        }

        // Build a set of acceptable values: exact float average, int average, and both middle values
        Set<Double> acceptableMedians = new HashSet<>();
        acceptableMedians.add(expectedMedianExact);
        acceptableMedians.add((double) expectedMedianInt);
        if (size % 2 == 0) {
            acceptableMedians.add((double) sorted.get(size / 2 - 1));
            acceptableMedians.add((double) sorted.get(size / 2));
        }

        if (reportedMedian.isEmpty()) {
            results.add(CheckResult.fail(CheckResult.Category.OUTPUT_CHECKS,
                "Median reported correctly",
                "Could not find a line containing the word 'median' with a number. " +
                "Make sure getMedian() result is printed with a label like \"Median: N\"."));
        } else {
            double reported = reportedMedian.getAsDouble();
            boolean matches = acceptableMedians.stream()
                    .anyMatch(expected -> Math.abs(reported - expected) < 0.01);
            if (matches) {
                results.add(CheckResult.pass(CheckResult.Category.OUTPUT_CHECKS,
                    "Median reported correctly",
                    String.format("Reported median = %s — matches the expected median of the printed list.",
                        formatNumber(reported))));
            } else {
                results.add(CheckResult.fail(CheckResult.Category.OUTPUT_CHECKS,
                    "Median reported correctly",
                    String.format("Reported median = %s but the expected median of the printed list is %s. " +
                        "Check the getMedian() logic — sort the list, then take the middle element " +
                        "(or the average of the two middle elements for even counts).",
                        formatNumber(reported), formatNumber(expectedMedianExact))));
            }
        }

        return new AnalysisResult(results, true);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Scans all output lines for the first one matching the mean pattern
     * and extracts the associated number (int or decimal).
     */
    private OptionalDouble findMeanValue(String[] lines) {
        for (String line : lines) {
            Matcher m = MEAN_LINE.matcher(line);
            if (m.matches()) {
                String raw = m.group(1);
                if (raw != null) {
                    try {
                        return OptionalDouble.of(Double.parseDouble(raw.trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return OptionalDouble.empty();
    }

    /**
     * Scans all output lines for the first one matching the median pattern
     * and extracts the associated number (int or decimal).
     */
    private OptionalDouble findMedianValue(String[] lines) {
        for (String line : lines) {
            Matcher m = MEDIAN_LINE.matcher(line);
            if (m.matches()) {
                String raw = m.group(1);
                if (raw != null) {
                    try {
                        return OptionalDouble.of(Double.parseDouble(raw.trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return OptionalDouble.empty();
    }

    /** Formats a double as an integer string if it has no fractional part, otherwise as decimal. */
    private String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((int) value);
        }
        return String.format("%.2f", value);
    }
}
