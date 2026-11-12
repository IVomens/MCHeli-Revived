package com.ivomens.mcheli.architecture;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Size limits from docs/ARCHITECTURE.md §7, measured on the main sources.
 *
 * <p>A class is measured from its declaration (annotations included, the javadoc above it not) to its
 * closing brace; nested classes count towards the class around them. A method or constructor is
 * measured the same way. Methods of anonymous classes and lambdas are part of the method that contains
 * them and are not listed on their own.
 */
class SourceSizeRulesTest {

    private static final int MAX_CLASS_LINES = 500;
    private static final int MAX_METHOD_LINES = 50;

    private static List<CompilationUnit> sources;

    @BeforeAll
    static void parseSources() {
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setAttributeComments(false));
        List<String> problems = new ArrayList<>();
        sources = javaFiles().stream()
                .map(file -> parse(parser, file, problems))
                .flatMap(Optional::stream)
                .toList();
        if (!problems.isEmpty()) {
            fail("Could not measure these sources:\n" + String.join("\n", problems));
        }
    }

    @Test
    void classesStayWithinTheLineLimit() {
        SortedMap<String, Integer> tooLong = new TreeMap<>();
        for (CompilationUnit source : sources) {
            for (TypeDeclaration<?> type : source.getTypes()) {
                int lines = lineCount(type);
                if (lines > MAX_CLASS_LINES) {
                    tooLong.put(type.getFullyQualifiedName().orElseThrow(), lines);
                }
            }
        }
        RuleCheck.withinLimit("Classes are at most " + MAX_CLASS_LINES + " lines long", tooLong, MAX_CLASS_LINES,
                "lines");
    }

    @Test
    void methodsStayWithinTheLineLimit() {
        SortedMap<String, Integer> tooLong = new TreeMap<>();
        for (CompilationUnit source : sources) {
            for (CallableDeclaration<?> callable : source.findAll(CallableDeclaration.class)) {
                measureMethod(callable, tooLong);
            }
            for (CompactConstructorDeclaration constructor : source.findAll(CompactConstructorDeclaration.class)) {
                measureMethod(constructor, tooLong);
            }
        }
        RuleCheck.withinLimit("Methods and constructors are at most " + MAX_METHOD_LINES + " lines long", tooLong,
                MAX_METHOD_LINES, "lines");
    }

    private static void measureMethod(BodyDeclaration<?> method, SortedMap<String, Integer> tooLong) {
        int lines = lineCount(method);
        if (lines <= MAX_METHOD_LINES || isInsideAnonymousClass(method)) {
            return;
        }
        tooLong.put(ownerName(method) + "#" + signature(method), lines);
    }

    /** {@code name(Type,Type...)}, enough to tell overloads apart and stable under reformatting. */
    private static String signature(BodyDeclaration<?> method) {
        if (method instanceof CallableDeclaration<?> callable) {
            String parameters = callable.getParameters().stream()
                    .map(SourceSizeRulesTest::parameterType)
                    .collect(Collectors.joining(","));
            return callable.getNameAsString() + "(" + parameters + ")";
        }
        // A compact record constructor has no parameter list of its own.
        return ((CompactConstructorDeclaration) method).getNameAsString() + "()";
    }

    private static String parameterType(Parameter parameter) {
        String type = parameter.getType().asString().replaceAll("\\s+", "");
        return parameter.isVarArgs() ? type + "..." : type;
    }

    /** The dotted name of the class or enum constant that declares the method, e.g. {@code a.b.Outer.Inner}. */
    private static String ownerName(Node method) {
        Node parent = method.getParentNode().orElseThrow();
        if (parent instanceof TypeDeclaration<?> type) {
            return type.getFullyQualifiedName().orElseGet(type::getNameAsString);
        }
        if (parent instanceof EnumConstantDeclaration constant) {
            return ownerName(constant) + "." + constant.getNameAsString();
        }
        throw new IllegalStateException("Unexpected method owner: " + parent.getClass().getSimpleName());
    }

    private static boolean isInsideAnonymousClass(Node method) {
        return method.getParentNode().orElseThrow() instanceof ObjectCreationExpr;
    }

    private static int lineCount(Node node) {
        return node.getRange().map(range -> range.end.line - range.begin.line + 1).orElseThrow();
    }

    private static List<Path> javaFiles() {
        List<Path> found = new ArrayList<>();
        for (Path root : ArchitecturePaths.mainSources()) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(file -> file.toString().endsWith(".java")).forEach(found::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return found.stream().sorted().toList();
    }

    private static Optional<CompilationUnit> parse(JavaParser parser, Path file, List<String> problems) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(file);
            if (!result.isSuccessful()) {
                problems.add(file + ": " + result.getProblems());
            }
            return result.getResult();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
