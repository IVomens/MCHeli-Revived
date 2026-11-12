package com.ivomens.mcheli.core.hud.expr;

import com.ivomens.mcheli.core.hud.expr.CompiledExpr.Failure;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * A compiled HUD formula: an expression over the HUD variables, read as a double by the command that uses
 * it. Formulas are compiled once, when their HUD loads, against a {@link VariableTable}; evaluating one reads
 * the variables by slot, with no lookup, boxing or allocation.
 * <p>
 * The language is the part of Aviator that HUD scripts use, with Aviator's meaning:
 * <ul>
 *     <li>integers ({@code 12}, {@code 0xff28d448}) are 64-bit and stay integers through {@code + - * / %},
 *     so {@code 7/2} is 3; a double anywhere makes the operation a double one;</li>
 *     <li>{@code & | ^ ~ << >> >>>} on integers, {@code && || !} on booleans, {@code ?:};</li>
 *     <li>comparisons and equality as {@link Semantics} describes them;</li>
 *     <li>the functions of {@link Functions}.</li>
 * </ul>
 * Strings, assignments, lambdas and the other Aviator statements are not part of it.
 */
public final class Formula {

    private final String source;
    private final CompiledExpr root;
    private final boolean readable;

    private Formula(String source, CompiledExpr root, boolean readable) {
        this.source = source;
        this.root = root;
        this.readable = readable;
    }

    /**
     * A formula as HUD files have always been read: in lower case, {@code #} for {@code 0x}, without spaces
     * or tabs, and without a leading {@code +}. A formula the file leaves out is {@code 0}.
     */
    public static String normalise(String text) {
        if (text == null) {
            return "0";
        }
        String formula = text.toLowerCase(Locale.ROOT).replace("#", "0x").replace("\t", "").replace(" ", "");
        return formula.startsWith("+") ? formula.substring(1) : formula;
    }

    /**
     * Compiles a normalised formula. Problems are reported to {@code problems}, each once, with the formula
     * named; a formula that cannot be read still compiles, to one that fails when it is evaluated.
     */
    public static Formula compile(String source, VariableTable variables, Consumer<String> problems) {
        Consumer<String> reported = message -> problems.accept("'" + source + "': " + message);
        try {
            Ast ast = Parser.parse(LegacyComparisons.rewrite(Lexer.tokens(source), variables));
            return new Formula(source, new ExprCompiler(variables, reported).compile(ast), true);
        } catch (ExprException e) {
            reported.accept(e.getMessage());
            return new Formula(source, new Failure("HUD expression failed to compile: " + source + ": "
                    + e.getMessage()), false);
        }
    }

    public String source() {
        return this.source;
    }

    /** Whether the text is a formula at all; one that is not fails whenever it is evaluated. */
    public boolean readable() {
        return this.readable;
    }

    /**
     * The value in this frame as a double: an integer widened, a boolean as 1 or 0.
     *
     * @throws IllegalStateException when the formula fails in this frame, or has no value (it names something
     *                               that is not a variable)
     * @throws ArithmeticException   on an integer division by zero
     */
    public double evaluate(Variables variables) {
        return switch (this.root.typeIn(variables)) {
            case INT -> this.root.asLong(variables);
            case DOUBLE -> this.root.asDouble(variables);
            case BOOL -> this.root.asBool(variables) ? 1.0 : 0.0;
            case NIL -> throw new IllegalStateException("HUD expression returned null: " + this.source);
        };
    }

    @Override
    public String toString() {
        return this.source;
    }
}
