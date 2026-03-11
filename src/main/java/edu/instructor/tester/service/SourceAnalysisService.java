package edu.instructor.tester.service;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import com.sun.source.tree.CompilationUnitTree;
import edu.instructor.tester.model.CheckResult;
import org.springframework.stereotype.Service;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Performs static analysis on student Java source using AST traversal.
 *
 * This avoids false positives from comments/string literals that can happen
 * when checks rely on raw regex matches alone.
 */
@Service
public class SourceAnalysisService {

    private static final List<String> PARSER_OPTIONS = List.of(
            "--enable-preview", "--release", "25", "-proc:none"
    );

    public List<CheckResult> analyze(String source) {
        var results = new ArrayList<CheckResult>();
        ParseContext parseContext = parse(source);

        if (parseContext == null || parseContext.compilationUnit() == null) {
            results.add(CheckResult.warn(CheckResult.Category.SOURCE_ANALYSIS,
                    "Source parser status",
                    "AST parsing was unavailable, so source-analysis checks were skipped. No text fallback is used."));
            return results;
        }

        SourceFacts facts = collectFacts(parseContext.compilationUnit());

        results.add(buildMeanSignatureCheck(facts.meanMethod()));
        results.add(buildMedianSignatureCheck(facts.medianMethod()));

        results.add(facts.usesArrayListInteger()
                ? CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses ArrayList<Integer>",
                "ArrayList<Integer> declaration found in parsed source.")
                : CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses ArrayList<Integer>",
                "No ArrayList<Integer> declaration found. The assignment requires ArrayList<Integer>."));

        results.add(facts.mainCallsMean()
                ? CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                "main() calls getMean()",
                "A call to getMean() is present inside main().")
                : CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS,
                "main() calls getMean()",
                "No call to getMean() found inside main()."));

        results.add(facts.mainCallsMedian()
                ? CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                "main() calls getMedian()",
                "A call to getMedian() is present inside main().")
                : CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS,
                "main() calls getMedian()",
                "No call to getMedian() found inside main()."));

        results.add(facts.usesRandom()
                ? CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses random number generation",
                "Random number generation detected (Random / nextInt / Math.random / ThreadLocalRandom).")
                : CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses random number generation",
                "No recognizable random number generation found."));

        if (facts.usesRandom()) {
            results.add(buildRandomRangeCheck(facts.rangeObservations()));
        }

        results.add(facts.usesAdd()
                ? CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses ArrayList add()",
                "A call to .add() was detected.")
                : CheckResult.warn(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses ArrayList add()",
                "Could not find a .add() call."));

        results.add(facts.usesSort()
                ? CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses ArrayList sort()",
                "A call to .sort() was detected.")
                : CheckResult.warn(CheckResult.Category.SOURCE_ANALYSIS,
                "Uses ArrayList sort()",
                "Could not find a .sort() call."));

        return results;
    }

    /**
     * Extracts the full method text (signature + body) for the given method name,
     * or {@code null} if not found.
     */
    public String extractFullMethod(String source, String methodName) {
        ParseContext parseContext = parse(source);
        if (parseContext == null || parseContext.compilationUnit() == null) {
            return null;
        }

        MethodTree target = findMethod(parseContext.compilationUnit(), methodName);
        if (target == null) {
            return null;
        }

        try {
            SourcePositions sourcePositions = parseContext.trees().getSourcePositions();
            long start = sourcePositions.getStartPosition(parseContext.compilationUnit(), target);
            long end = sourcePositions.getEndPosition(parseContext.compilationUnit(), target);
            if (start >= 0 && end > start && end <= source.length()) {
                return source.substring((int) start, (int) end);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private CheckResult buildMeanSignatureCheck(MethodTree method) {
        return buildSignatureCheck("getMean", method,
                "mean calculation", "double or float");
    }

    private CheckResult buildMedianSignatureCheck(MethodTree method) {
        return buildSignatureCheck("getMedian", method,
                "median calculation", "double or float");
    }

    private CheckResult buildSignatureCheck(String methodName, MethodTree method, String context, String expectedReturn) {
        String checkName = methodName + "() declared correctly";
        if (method == null) {
            return CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS, checkName,
                    "Could not find a method named " + methodName + " that accepts ArrayList<Integer>.");
        }

        if (!hasArrayListIntegerParam(method)) {
            return CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS, checkName,
                    "Found " + methodName + "(), but its parameter should be exactly ArrayList<Integer>.");
        }

        String returnType = method.getReturnType() == null ? "void" : method.getReturnType().toString().trim();
        if ("double".equals(returnType) || "float".equals(returnType)) {
            return CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS, checkName,
                    "Found: " + returnType + " " + methodName + "(ArrayList<Integer> ...) — valid for " + context + ".");
        }

        if ("int".equals(returnType)) {
            return CheckResult.warn(CheckResult.Category.SOURCE_ANALYSIS, checkName,
                    "Found: int " + methodName + "(ArrayList<Integer> ...) — method exists, but return type should be " +
                            expectedReturn + " to avoid truncation.");
        }

        return CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS, checkName,
                "Found " + methodName + "(ArrayList<Integer> ...), but return type '" + returnType +
                        "' is not appropriate. Use " + expectedReturn + ".");
    }

    private CheckResult buildRandomRangeCheck(List<RangeObservation> observations) {
        if (observations.isEmpty()) {
            return CheckResult.warn(CheckResult.Category.SOURCE_ANALYSIS,
                    "Random range is [0, 100]",
                    "Random generation was found, but the range arguments could not be verified.");
        }

        boolean hasPass = observations.stream().anyMatch(o -> o.verdict() == RangeVerdict.PASS);
        boolean hasFail = observations.stream().anyMatch(o -> o.verdict() == RangeVerdict.FAIL);

        if (hasPass && !hasFail) {
            String detail = observations.stream()
                    .filter(o -> o.verdict() == RangeVerdict.PASS)
                    .findFirst()
                    .map(RangeObservation::detail)
                    .orElse("Detected a random range expression that produces values in [0, 100].");
            return CheckResult.pass(CheckResult.Category.SOURCE_ANALYSIS, "Random range is [0, 100]", detail);
        }

        if (hasFail && !hasPass) {
            String detail = observations.stream()
                    .filter(o -> o.verdict() == RangeVerdict.FAIL)
                    .findFirst()
                    .map(RangeObservation::detail)
                    .orElse("Detected random range expressions that do not match [0, 100].");
            return CheckResult.fail(CheckResult.Category.SOURCE_ANALYSIS, "Random range is [0, 100]", detail);
        }

        return CheckResult.warn(CheckResult.Category.SOURCE_ANALYSIS,
                "Random range is [0, 100]",
                "Both correct and questionable random-range patterns were detected. Review the generator call manually.");
    }

    private SourceFacts collectFacts(CompilationUnitTree compilationUnit) {
        FactsScanner scanner = new FactsScanner();
        scanner.scan(compilationUnit, null);
        return scanner.toFacts();
    }

    private ParseContext parse(String source) {
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                return null;
            }

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
                JavaFileObject sourceObject = new StringSourceFileObject("Submission.java", source);
                JavacTask task = (JavacTask) compiler.getTask(
                        null, fileManager, diagnostics, PARSER_OPTIONS, null, List.of(sourceObject)
                );

                Iterable<? extends CompilationUnitTree> parsedUnits = task.parse();
                CompilationUnitTree compilationUnit = parsedUnits.iterator().hasNext()
                        ? parsedUnits.iterator().next()
                        : null;
                Trees trees = Trees.instance(task);
                return new ParseContext(compilationUnit, trees);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean hasArrayListIntegerParam(MethodTree method) {
        if (method.getParameters().size() != 1) {
            return false;
        }
        VariableTree param = method.getParameters().get(0);
        return isArrayListIntegerType(param.getType());
    }

    private boolean isArrayListIntegerType(Tree tree) {
        if (!(tree instanceof ParameterizedTypeTree parameterized)) {
            return false;
        }

        String raw = simpleName(parameterized.getType().toString());
        if (!"ArrayList".equals(raw)) {
            return false;
        }

        if (parameterized.getTypeArguments().size() != 1) {
            return false;
        }

        String arg = simpleName(parameterized.getTypeArguments().get(0).toString());
        return "Integer".equals(arg);
    }

    private MethodTree findMethod(CompilationUnitTree compilationUnit, String methodName) {
        MethodFinder finder = new MethodFinder(methodName);
        finder.scan(compilationUnit, null);
        return finder.result();
    }

    private static String simpleName(String rawType) {
        int dot = rawType.lastIndexOf('.');
        return dot >= 0 ? rawType.substring(dot + 1) : rawType;
    }

    private static String invokedMethodName(ExpressionTree select) {
        if (select instanceof MemberSelectTree memberSelectTree) {
            return memberSelectTree.getIdentifier().toString();
        }
        if (select instanceof IdentifierTree identifierTree) {
            return identifierTree.getName().toString();
        }
        return null;
    }

    private static ExpressionTree unwrap(ExpressionTree tree) {
        ExpressionTree current = tree;
        while (current instanceof ParenthesizedTree parenthesizedTree) {
            current = parenthesizedTree.getExpression();
        }
        if (current instanceof TypeCastTree typeCastTree) {
            return unwrap(typeCastTree.getExpression());
        }
        return current;
    }

    private static Integer integerLiteral(ExpressionTree tree) {
        ExpressionTree unwrapped = unwrap(tree);
        if (unwrapped instanceof LiteralTree literalTree) {
            Object value = literalTree.getValue();
            if (value instanceof Integer i) {
                return i;
            }
            if (value instanceof Long l && l <= Integer.MAX_VALUE && l >= Integer.MIN_VALUE) {
                return (int) l.longValue();
            }
        }
        if (unwrapped instanceof UnaryTree unaryTree && unaryTree.getKind() == Tree.Kind.UNARY_MINUS) {
            Integer nested = integerLiteral(unaryTree.getExpression());
            if (nested != null) {
                return -nested;
            }
        }
        return null;
    }

    private static boolean isMathRandomCall(ExpressionTree tree) {
        ExpressionTree unwrapped = unwrap(tree);
        if (!(unwrapped instanceof MethodInvocationTree invocationTree)) {
            return false;
        }
        String methodName = invokedMethodName(invocationTree.getMethodSelect());
        return "random".equals(methodName) && invocationTree.getMethodSelect().toString().contains("Math.random");
    }

    private record ParseContext(CompilationUnitTree compilationUnit, Trees trees) {}

    private record SourceFacts(
            MethodTree meanMethod,
            MethodTree medianMethod,
            boolean usesArrayListInteger,
            boolean mainCallsMean,
            boolean mainCallsMedian,
            boolean usesRandom,
            List<RangeObservation> rangeObservations,
            boolean usesAdd,
            boolean usesSort
    ) {}

    private enum RangeVerdict { PASS, FAIL, UNKNOWN }

    private record RangeObservation(RangeVerdict verdict, String detail) {}

    private static class FactsScanner extends TreeScanner<Void, Void> {
        private MethodTree meanMethod;
        private MethodTree medianMethod;
        private boolean usesArrayListInteger;
        private boolean mainCallsMean;
        private boolean mainCallsMedian;
        private boolean usesRandom;
        private boolean usesAdd;
        private boolean usesSort;
        private boolean inMainMethod;
        private final List<RangeObservation> rangeObservations = new ArrayList<>();

        @Override
        public Void visitMethod(MethodTree methodTree, Void unused) {
            String name = methodTree.getName().toString();
            if ("getMean".equals(name)) {
                meanMethod = chooseBetterSignatureMethod(meanMethod, methodTree);
            }
            if ("getMedian".equals(name)) {
                medianMethod = chooseBetterSignatureMethod(medianMethod, methodTree);
            }

            boolean wasInMain = inMainMethod;
            if ("main".equals(name)) {
                inMainMethod = true;
            }
            super.visitMethod(methodTree, unused);
            inMainMethod = wasInMain;
            return null;
        }

        @Override
        public Void visitParameterizedType(ParameterizedTypeTree parameterizedTypeTree, Void unused) {
            if (isArrayListInteger(parameterizedTypeTree)) {
                usesArrayListInteger = true;
            }
            return super.visitParameterizedType(parameterizedTypeTree, unused);
        }

        @Override
        public Void visitNewClass(NewClassTree newClassTree, Void unused) {
            if ("Random".equals(simpleName(newClassTree.getIdentifier().toString()))) {
                usesRandom = true;
            }
            return super.visitNewClass(newClassTree, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree invocationTree, Void unused) {
            String methodName = invokedMethodName(invocationTree.getMethodSelect());
            String selectText = invocationTree.getMethodSelect().toString();

            if ("add".equals(methodName)) {
                usesAdd = true;
            }
            if ("sort".equals(methodName)) {
                usesSort = true;
            }
            if (inMainMethod && "getMean".equals(methodName)) {
                mainCallsMean = true;
            }
            if (inMainMethod && "getMedian".equals(methodName)) {
                mainCallsMedian = true;
            }

            if ("nextInt".equals(methodName)) {
                usesRandom = true;
                observeNextInt(invocationTree);
            }
            if ("random".equals(methodName) && selectText.contains("Math.random")) {
                usesRandom = true;
            }
            if (selectText.contains("ThreadLocalRandom")) {
                usesRandom = true;
            }

            return super.visitMethodInvocation(invocationTree, unused);
        }

        @Override
        public Void visitBinary(BinaryTree binaryTree, Void unused) {
            if (binaryTree.getKind() == Tree.Kind.MULTIPLY) {
                Integer right = integerLiteral(binaryTree.getRightOperand());
                Integer left = integerLiteral(binaryTree.getLeftOperand());
                if (isMathRandomCall(binaryTree.getLeftOperand()) && right != null) {
                    usesRandom = true;
                    observeMathRandomMultiply(right);
                } else if (isMathRandomCall(binaryTree.getRightOperand()) && left != null) {
                    usesRandom = true;
                    observeMathRandomMultiply(left);
                }
            }
            return super.visitBinary(binaryTree, unused);
        }

        SourceFacts toFacts() {
            return new SourceFacts(
                    meanMethod,
                    medianMethod,
                    usesArrayListInteger,
                    mainCallsMean,
                    mainCallsMedian,
                    usesRandom,
                    List.copyOf(rangeObservations),
                    usesAdd,
                    usesSort
            );
        }

        private void observeNextInt(MethodInvocationTree invocationTree) {
            List<? extends ExpressionTree> args = invocationTree.getArguments();
            if (args.size() == 1) {
                Integer bound = integerLiteral(args.get(0));
                if (bound == null) {
                    rangeObservations.add(new RangeObservation(
                            RangeVerdict.UNKNOWN,
                            "nextInt(...) detected, but the bound is not a numeric literal."
                    ));
                    return;
                }
                if (bound == 101) {
                    rangeObservations.add(new RangeObservation(
                            RangeVerdict.PASS,
                            "nextInt(101) produces values from 0 to 100 inclusive."
                    ));
                } else {
                    rangeObservations.add(new RangeObservation(
                            RangeVerdict.FAIL,
                            "nextInt(" + bound + ") produces values from 0 to " + (bound - 1) +
                                    ". Use nextInt(101) for [0, 100]."
                    ));
                }
                return;
            }

            if (args.size() == 2) {
                Integer origin = integerLiteral(args.get(0));
                Integer bound = integerLiteral(args.get(1));
                if (origin == null || bound == null) {
                    rangeObservations.add(new RangeObservation(
                            RangeVerdict.UNKNOWN,
                            "nextInt(origin, bound) detected, but one or both arguments are not numeric literals."
                    ));
                    return;
                }
                if (origin == 0 && bound == 101) {
                    rangeObservations.add(new RangeObservation(
                            RangeVerdict.PASS,
                            "nextInt(0, 101) produces values from 0 to 100 inclusive."
                    ));
                } else {
                    rangeObservations.add(new RangeObservation(
                            RangeVerdict.FAIL,
                            "nextInt(" + origin + ", " + bound + ") produces values from " +
                                    origin + " to " + (bound - 1) + ". Use nextInt(0, 101) for [0, 100]."
                    ));
                }
                return;
            }

            rangeObservations.add(new RangeObservation(
                    RangeVerdict.UNKNOWN,
                    "nextInt(...) detected, but argument pattern is not recognized."
            ));
        }

        private void observeMathRandomMultiply(int multiplier) {
            if (multiplier == 101) {
                rangeObservations.add(new RangeObservation(
                        RangeVerdict.PASS,
                        "Math.random() * 101 (cast to int) produces values from 0 to 100 inclusive."
                ));
            } else {
                rangeObservations.add(new RangeObservation(
                        RangeVerdict.FAIL,
                        "Math.random() * " + multiplier + " (cast to int) produces values from 0 to " +
                                (multiplier - 1) + ". Use 101 for [0, 100]."
                ));
            }
        }

        private boolean isArrayListInteger(ParameterizedTypeTree parameterizedTypeTree) {
            String raw = simpleName(parameterizedTypeTree.getType().toString());
            if (!"ArrayList".equals(raw)) {
                return false;
            }
            if (parameterizedTypeTree.getTypeArguments().size() != 1) {
                return false;
            }
            String arg = simpleName(parameterizedTypeTree.getTypeArguments().get(0).toString());
            return "Integer".equals(arg);
        }

        private MethodTree chooseBetterSignatureMethod(MethodTree current, MethodTree candidate) {
            if (current == null) {
                return candidate;
            }

            boolean currentMatches = hasExpectedParam(current);
            boolean candidateMatches = hasExpectedParam(candidate);

            if (!currentMatches && candidateMatches) {
                return candidate;
            }
            return current;
        }

        private boolean hasExpectedParam(MethodTree methodTree) {
            if (methodTree.getParameters().size() != 1) {
                return false;
            }
            VariableTree param = methodTree.getParameters().get(0);
            return param.getType() instanceof ParameterizedTypeTree p && isArrayListInteger(p);
        }
    }

    private static class MethodFinder extends TreeScanner<Void, Void> {
        private final String targetName;
        private MethodTree result;

        MethodFinder(String targetName) {
            this.targetName = targetName;
        }

        @Override
        public Void visitMethod(MethodTree methodTree, Void unused) {
            if (result == null && targetName.equals(methodTree.getName().toString())) {
                result = methodTree;
            }
            return super.visitMethod(methodTree, unused);
        }

        MethodTree result() {
            return result;
        }
    }

    private static class StringSourceFileObject extends SimpleJavaFileObject {
        private final String source;

        StringSourceFileObject(String fileName, String source) {
            super(URI.create("string:///" + fileName), JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
