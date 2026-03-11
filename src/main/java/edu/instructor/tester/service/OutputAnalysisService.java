package edu.instructor.tester.service;

import edu.instructor.tester.model.CheckResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyses captured stdout and verifies value list, mean, and median checks.
 */
@Service
public class OutputAnalysisService {

    /** Bundles check results with a flag indicating whether numeric value output was found. */
    public record AnalysisResult(List<CheckResult> checks, boolean valuesFound) {}

    private record ValueCandidate(String source, List<Integer> values, int confidence) {}

    private static final Pattern MEAN_KEYWORD = Pattern.compile("(?i)\\bmean\\b");
    private static final Pattern MEDIAN_KEYWORD = Pattern.compile("(?i)\\bmedian\\b");
    private static final Pattern NUMBER_TOKEN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private static final Pattern INTEGER_LINE = Pattern.compile("^\\s*-?\\d+\\s*$");
    private static final Pattern LABELLED_VALUE_LINE = Pattern.compile(
            "(?i)^\\s*(?:number|num|value|item|random\\s*number|generated\\s*number)\\s*\\d*\\s*[:=\\-]\\s*(-?\\d+)\\s*$"
    );
    private static final Pattern BRACKET_LIST = Pattern.compile("\\[\\s*(-?\\d+(?:\\s*,\\s*-?\\d+)*)\\s*]");

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
        List<ValueCandidate> candidates = collectValueCandidates(lines);
        CandidateSelection selection = selectBestCandidate(candidates, expectedCount);
        List<Integer> listedNumbers = selection.best() == null ? List.of() : selection.best().values();

        if (selection.ambiguous() && selection.best() != null) {
            results.add(CheckResult.warn(CheckResult.Category.OUTPUT_CHECKS,
                    "Output value parsing was ambiguous",
                    "Multiple plausible numeric output formats were detected. " +
                            "Using values from " + selection.best().source() + "."));
        }

        boolean countMatch = listedNumbers.size() == expectedCount;
        results.add(countMatch
                ? CheckResult.pass(CheckResult.Category.OUTPUT_CHECKS,
                "Correct number of values listed (" + expectedCount + ")",
                "Found exactly " + listedNumbers.size() + " values using " + describeSource(selection.best()) + ".")
                : CheckResult.fail(CheckResult.Category.OUTPUT_CHECKS,
                "Correct number of values listed (" + expectedCount + ")",
                "Expected " + expectedCount + " values but found " + listedNumbers.size() +
                        " using " + describeSource(selection.best()) + "."));

        if (listedNumbers.isEmpty()) {
            return new AnalysisResult(results, false);
        }

        List<Integer> outOfRange = listedNumbers.stream()
                .filter(n -> n < 0 || n > 100)
                .toList();

        results.add(outOfRange.isEmpty()
                ? CheckResult.pass(CheckResult.Category.OUTPUT_CHECKS,
                "All listed values in range [0, 100]",
                "All " + listedNumbers.size() + " parsed values are within [0, 100].")
                : CheckResult.fail(CheckResult.Category.OUTPUT_CHECKS,
                "All listed values in range [0, 100]",
                "Out-of-range values found: " + outOfRange + "."));

        double trueMean = listedNumbers.stream().mapToInt(Integer::intValue).average().orElse(0.0);

        List<Integer> sorted = new ArrayList<>(listedNumbers);
        Collections.sort(sorted);
        int size = sorted.size();

        OptionalDouble reportedMean = findLabelValue(lines, MEAN_KEYWORD);
        if (reportedMean.isEmpty()) {
            results.add(CheckResult.fail(CheckResult.Category.OUTPUT_CHECKS,
                    "Mean reported correctly",
                    "Could not find a line containing the word 'mean' with a numeric value."));
        } else if (Math.abs(reportedMean.getAsDouble() - trueMean) < 0.01) {
            results.add(CheckResult.pass(CheckResult.Category.OUTPUT_CHECKS,
                    "Mean reported correctly",
                    String.format("Reported mean = %.2f — matches the actual mean of the parsed values (%.2f).",
                            reportedMean.getAsDouble(), trueMean)));
        } else {
            results.add(CheckResult.fail(CheckResult.Category.OUTPUT_CHECKS,
                    "Mean reported correctly",
                    String.format("Reported mean = %.2f but the actual mean of the parsed values is %.2f.",
                            reportedMean.getAsDouble(), trueMean)));
        }

        OptionalDouble reportedMedian = findLabelValue(lines, MEDIAN_KEYWORD);
        double expectedMedianExact;
        int expectedMedianInt;
        if (size % 2 == 1) {
            expectedMedianExact = sorted.get(size / 2);
            expectedMedianInt = sorted.get(size / 2);
        } else {
            expectedMedianExact = (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
            expectedMedianInt = (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2;
        }

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
                    "Could not find a line containing the word 'median' with a numeric value."));
        } else {
            double reported = reportedMedian.getAsDouble();
            boolean matches = acceptableMedians.stream()
                    .anyMatch(expected -> Math.abs(reported - expected) < 0.01);
            if (matches) {
                results.add(CheckResult.pass(CheckResult.Category.OUTPUT_CHECKS,
                        "Median reported correctly",
                        "Reported median = " + formatNumber(reported) + " — matches the expected median."));
            } else {
                results.add(CheckResult.fail(CheckResult.Category.OUTPUT_CHECKS,
                        "Median reported correctly",
                        "Reported median = " + formatNumber(reported) +
                                " but expected " + formatNumber(expectedMedianExact) + "."));
            }
        }

        return new AnalysisResult(results, true);
    }

    private List<ValueCandidate> collectValueCandidates(String[] lines) {
        List<ValueCandidate> candidates = new ArrayList<>();

        List<Integer> lineValues = collectLineValues(lines);
        if (!lineValues.isEmpty()) {
            candidates.add(new ValueCandidate("line-based value output", lineValues, 2));
        }

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (containsKeyword(line, MEAN_KEYWORD) || containsKeyword(line, MEDIAN_KEYWORD)) {
                continue;
            }
            Matcher matcher = BRACKET_LIST.matcher(line);
            while (matcher.find()) {
                List<Integer> values = parseBracketTokens(matcher.group(1));
                if (!values.isEmpty()) {
                    candidates.add(new ValueCandidate("bracket list on line " + (i + 1), values, 3));
                }
            }
        }

        return candidates;
    }

    private List<Integer> collectLineValues(String[] lines) {
        List<Integer> values = new ArrayList<>();
        for (String line : lines) {
            if (containsKeyword(line, MEAN_KEYWORD) || containsKeyword(line, MEDIAN_KEYWORD)) {
                continue;
            }

            if (INTEGER_LINE.matcher(line).matches()) {
                try {
                    values.add(Integer.parseInt(line.trim()));
                } catch (NumberFormatException ignored) {
                    // ignore malformed numeric token
                }
                continue;
            }

            Matcher labelledMatcher = LABELLED_VALUE_LINE.matcher(line);
            if (labelledMatcher.matches()) {
                try {
                    values.add(Integer.parseInt(labelledMatcher.group(1)));
                } catch (NumberFormatException ignored) {
                    // ignore malformed numeric token
                }
            }
        }
        return values;
    }

    private CandidateSelection selectBestCandidate(List<ValueCandidate> candidates, int expectedCount) {
        if (candidates.isEmpty()) {
            return new CandidateSelection(null, false);
        }

        int bestScore = Integer.MIN_VALUE;
        ValueCandidate best = null;
        boolean ambiguous = false;

        for (ValueCandidate candidate : candidates) {
            int score = candidateScore(candidate, expectedCount);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
                ambiguous = false;
            } else if (score == bestScore && best != null && !best.values().equals(candidate.values())) {
                ambiguous = true;
            }
        }

        return new CandidateSelection(best, ambiguous);
    }

    private int candidateScore(ValueCandidate candidate, int expectedCount) {
        int sizeDelta = Math.abs(candidate.values().size() - expectedCount);
        int expectedBonus = candidate.values().size() == expectedCount ? 1000 : 0;
        return expectedBonus + candidate.confidence() * 100 - sizeDelta * 10;
    }

    private OptionalDouble findLabelValue(String[] lines, Pattern keyword) {
        for (String line : lines) {
            if (!containsKeyword(line, keyword)) {
                continue;
            }
            OptionalDouble lastNumber = lastNumberOnLine(line);
            if (lastNumber.isPresent()) {
                return lastNumber;
            }
        }
        return OptionalDouble.empty();
    }

    private OptionalDouble lastNumberOnLine(String line) {
        Matcher matcher = NUMBER_TOKEN.matcher(line);
        String lastToken = null;
        while (matcher.find()) {
            lastToken = matcher.group();
        }
        if (lastToken == null) {
            return OptionalDouble.empty();
        }
        try {
            return OptionalDouble.of(Double.parseDouble(lastToken));
        } catch (NumberFormatException ignored) {
            return OptionalDouble.empty();
        }
    }

    private boolean containsKeyword(String line, Pattern keyword) {
        return keyword.matcher(line).find();
    }

    private List<Integer> parseBracketTokens(String tokenList) {
        List<Integer> values = new ArrayList<>();
        for (String token : tokenList.split("\\s*,\\s*")) {
            try {
                values.add(Integer.parseInt(token.trim()));
            } catch (NumberFormatException ignored) {
                // ignore malformed numeric token
            }
        }
        return values;
    }

    private String describeSource(ValueCandidate candidate) {
        return candidate == null ? "no parsed value source" : candidate.source();
    }

    private String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((int) value);
        }
        return String.format("%.2f", value);
    }

    private record CandidateSelection(ValueCandidate best, boolean ambiguous) {}
}
