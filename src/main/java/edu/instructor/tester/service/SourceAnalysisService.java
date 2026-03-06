package edu.instructor.tester.service;

import edu.instructor.tester.model.CheckResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Performs static analysis on the student's Java source text.
 *
 * Checks:
 *   1. getMean(ArrayList<Integer>) returning double/float is present
 *   2. getMedian(ArrayList<Integer>) returning double/float is present
 *   3. Uses ArrayList<Integer>
 *   4. main() calls getMean()
 *   5. main() calls getMedian()
 *   6. Random number generation is present
 *  6b. Random range is [0, 100] inclusive
 *   7. Uses ArrayList add()
 *   8. Uses ArrayList sort(null)
 */
@Service
public class SourceAnalysisService {

    // getMean signature — accepts ArrayList<Integer>, captures return type
    private static final Pattern SIG_GET_MEAN = Pattern.compile(
            "(?:static\\s+)?(int|double|float)\\s+getMean\\s*\\(\\s*ArrayList\\s*<\\s*Integer\\s*>\\s*\\w+\\s*\\)");

    // getMedian signature — accepts ArrayList<Integer>, captures return type
    private static final Pattern SIG_GET_MEDIAN = Pattern.compile(
            "(?:static\\s+)?(int|double|float)\\s+getMedian\\s*\\(\\s*ArrayList\\s*<\\s*Integer\\s*>\\s*\\w+\\s*\\)");

    // ArrayList<Integer> usage
    private static final Pattern ARRAYLIST_INTEGER = Pattern.compile(
            "ArrayList\\s*<\\s*Integer\\s*>");

    // Random usage — covers Random.nextInt, Math.random, ThreadLocalRandom, etc.
    private static final Pattern RANDOM_USAGE = Pattern.compile(
            "\\bnew\\s+Random\\b|\\bnextInt\\s*\\(|\\bMath\\.random\\s*\\(|ThreadLocalRandom");

    // nextInt with a single literal argument: nextInt(101), nextInt( 100 ), etc.
    private static final Pattern NEXT_INT_ONE_ARG = Pattern.compile(
            "\\bnextInt\\s*\\(\\s*(\\d+)\\s*\\)");

    // nextInt with two literal arguments: nextInt(0, 101), etc.
    private static final Pattern NEXT_INT_TWO_ARG = Pattern.compile(
            "\\bnextInt\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");

    // Math.random() multiplied by a literal: (int)(Math.random() * 101)
    private static final Pattern MATH_RANDOM_MULT = Pattern.compile(
            "Math\\.random\\s*\\(\\s*\\)\\s*\\*\\s*(\\d+)");

    // ─────────────────────────────────────────────────────────────────────────

    public List<CheckResult> analyze(String source) {
        var results = new ArrayList<CheckResult>();

        // ── 1. getMean() signature ─────────────────────────────────────────────
        Matcher meanMatcher = SIG_GET_MEAN.matcher(source);
        boolean hasMean = meanMatcher.find();
        if (!hasMean) {
            results.add(CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS,
                "getMean() declared correctly",
                "Could not find a method named getMean that accepts ArrayList<Integer> and returns a numeric type. " +
                "Check that the name is spelled exactly as required and that it accepts ArrayList<Integer>."));
        } else {
            String meanReturnType = meanMatcher.group(1);
            if (meanReturnType.equals("double") || meanReturnType.equals("float")) {
                results.add(CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                    "getMean() declared correctly",
                    "Found: " + meanReturnType + " getMean(ArrayList<Integer> ...) — correct name, parameter type, and return type."));
            } else {
                results.add(CheckResult.warn(CheckResult.Category.SOURCE_ANALYSIS,
                    "getMean() declared correctly",
                    "Found: " + meanReturnType + " getMean(ArrayList<Integer> ...) — method exists, but the return type should be " +
                    "double or float for a mean calculation (integer division truncates the result)."));
            }
        }

        // ── 2. getMedian() signature ──────────────────────────────────────────
        Matcher medianMatcher = SIG_GET_MEDIAN.matcher(source);
        boolean hasMedian = medianMatcher.find();
        if (!hasMedian) {
            results.add(CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS,
                "getMedian() declared correctly",
                "Could not find a method named getMedian that accepts ArrayList<Integer> and returns a numeric type. " +
                "Check that the name is spelled exactly as required and that it accepts ArrayList<Integer>."));
        } else {
            String medianReturnType = medianMatcher.group(1);
            if (medianReturnType.equals("double") || medianReturnType.equals("float")) {
                results.add(CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                    "getMedian() declared correctly",
                    "Found: " + medianReturnType + " getMedian(ArrayList<Integer> ...) — correct name, parameter type, and return type."));
            } else {
                results.add(CheckResult.warn(CheckResult.Category.SOURCE_ANALYSIS,
                    "getMedian() declared correctly",
                    "Found: " + medianReturnType + " getMedian(ArrayList<Integer> ...) — method exists, but the return type should be " +
                    "double or float so the median is correct when there is an even number of values."));
            }
        }

        // ── 3. Uses ArrayList<Integer> ────────────────────────────────────────
        boolean usesArrayList = ARRAYLIST_INTEGER.matcher(source).find();
        results.add(usesArrayList
            ? CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses ArrayList<Integer>",
                "ArrayList<Integer> declaration found in source.")
            : CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses ArrayList<Integer>",
                "No ArrayList<Integer> declaration found. The assignment requires using ArrayList<Integer>, not arrays or other collection types.")
        );

        // ── 4. main() calls getMean() ─────────────────────────────────────────
        String mainBody = extractMethodBody(source, "main");

        boolean mainCallsMean = mainBody != null && mainBody.contains("getMean(");
        results.add(mainCallsMean
            ? CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                "main() calls getMean()",
                "A call to getMean() is present inside main().")
            : CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS,
                "main() calls getMean()",
                "No call to getMean() found inside main(). The mean must be " +
                "obtained by calling the method — not computed inline in main().")
        );

        // ── 5. main() calls getMedian() ──────────────────────────────────────
        boolean mainCallsMedian = mainBody != null && mainBody.contains("getMedian(");
        results.add(mainCallsMedian
            ? CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                "main() calls getMedian()",
                "A call to getMedian() is present inside main().")
            : CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS,
                "main() calls getMedian()",
                "No call to getMedian() found inside main(). The median must be " +
                "obtained by calling the method — not computed inline in main().")
        );

        // ── 6. Random number generation ───────────────────────────────────────
        boolean usesRandom = RANDOM_USAGE.matcher(source).find();
        results.add(usesRandom
            ? CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses random number generation",
                "Random number generation detected (Random / nextInt / Math.random).")
            : CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses random number generation",
                "No recognizable random number generation found. " +
                "The ArrayList should be filled with values from Random.nextInt() or similar.")
        );

        // ── 6b. Random range produces [0, 100] inclusive ─────────────────────
        if (usesRandom) {
            results.add(checkRandomRange(source));
        }

        // ── 7. Uses ArrayList add() ──────────────────────────────────────────
        boolean usesAdd = source.contains(".add(");
        results.add(usesAdd
            ? CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses ArrayList add()",
                "A call to .add() detected — confirms use of ArrayList's add method.")
            : CheckResult.warn(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses ArrayList add()",
                "Could not find a .add() call. The assignment requires using ArrayList's add() method to populate the list.")
        );

        // ── 8. Uses ArrayList sort(null) ─────────────────────────────────────
        boolean usesSort = source.contains(".sort(");
        results.add(usesSort
            ? CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses ArrayList sort()",
                "A call to .sort() detected — confirms sorting the ArrayList.")
            : CheckResult.warn(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses ArrayList sort()",
                "Could not find a .sort() call. The assignment requires using .sort(null) to sort the ArrayList for median calculation.")
        );

        return results;
    }

    // ── range-check helper ─────────────────────────────────────────────────

    /**
     * Inspects the source for nextInt() or Math.random() calls and verifies that
     * the generated range is [0, 100] inclusive.
     */
    private CheckResult checkRandomRange(String source) {
        // Check two-arg nextInt first (more specific)
        Matcher twoArg = NEXT_INT_TWO_ARG.matcher(source);
        if (twoArg.find()) {
            int origin = Integer.parseInt(twoArg.group(1));
            int bound  = Integer.parseInt(twoArg.group(2));
            if (origin == 0 && bound == 101) {
                return CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                    "Random range is [0, 100]",
                    "nextInt(" + origin + ", " + bound + ") produces values from 0 to 100 inclusive.");
            } else {
                return CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS,
                    "Random range is [0, 100]",
                    "nextInt(" + origin + ", " + bound + ") produces values from " +
                    origin + " to " + (bound - 1) + ". " +
                    "Use nextInt(0, 101) to get the correct range of 0 to 100 inclusive.");
            }
        }

        Matcher oneArg = NEXT_INT_ONE_ARG.matcher(source);
        if (oneArg.find()) {
            int bound = Integer.parseInt(oneArg.group(1));
            if (bound == 101) {
                return CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                    "Random range is [0, 100]",
                    "nextInt(101) produces values from 0 to 100 inclusive.");
            } else {
                return CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS,
                    "Random range is [0, 100]",
                    "nextInt(" + bound + ") produces values from 0 to " + (bound - 1) + ". " +
                    "Use nextInt(101) to get the correct range of 0 to 100 inclusive.");
            }
        }

        Matcher mathRand = MATH_RANDOM_MULT.matcher(source);
        if (mathRand.find()) {
            int multiplier = Integer.parseInt(mathRand.group(1));
            if (multiplier == 101) {
                return CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                    "Random range is [0, 100]",
                    "Math.random() * 101 (cast to int) produces values from 0 to 100 inclusive.");
            } else {
                return CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS,
                    "Random range is [0, 100]",
                    "Math.random() * " + multiplier + " (cast to int) produces values from 0 to " +
                    (multiplier - 1) + ". " +
                    "Use Math.random() * 101 to get the correct range of 0 to 100 inclusive.");
            }
        }

        // Random is used but we couldn't parse the arguments
        return CheckResult.warn(CheckResult.Category.SOURCE_ANALYSIS,
            "Random range is [0, 100]",
            "Random number generation detected, but could not verify the range from source code. " +
            "Ensure the code uses nextInt(101) or nextInt(0, 101) for values in [0, 100].");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts the full method text (signature + body) for the given method name,
     * or {@code null} if not found. Useful for passing method code to external analysis.
     */
    public String extractFullMethod(String source, String methodName) {
        Pattern declPattern = Pattern.compile(
                "(?m)^[ \\t]*(?:(?:public|protected|private|static|final|synchronized)\\s+)*" +
                "\\w[\\w<>\\[\\]]*"                    +   // return type
                "\\s+" + Pattern.quote(methodName) + "\\s*\\("
        );

        Matcher m = declPattern.matcher(source);
        if (!m.find()) return null;

        int braceStart = source.indexOf('{', m.end());
        if (braceStart < 0) return null;

        int depth = 0;
        int i = braceStart;
        while (i < source.length()) {
            char c = source.charAt(i);
            if      (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(m.start(), i + 1);
            }
            i++;
        }
        return null;
    }

    /**
     * Extracts the body of the method DECLARATION with the given name by brace-counting.
     */
    private String extractMethodBody(String source, String methodName) {
        Pattern declPattern = Pattern.compile(
                "(?m)^[ \\t]*(?:(?:public|protected|private|static|final|synchronized)\\s+)*" +
                "\\w[\\w<>\\[\\]]*"                    +   // return type
                "\\s+" + Pattern.quote(methodName) + "\\s*\\("
        );

        Matcher m = declPattern.matcher(source);
        if (!m.find()) return null;

        int braceStart = source.indexOf('{', m.end());
        if (braceStart < 0) return null;

        int depth = 0;
        int i = braceStart;
        while (i < source.length()) {
            char c = source.charAt(i);
            if      (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(braceStart, i + 1);
            }
            i++;
        }
        return null;
    }
}
