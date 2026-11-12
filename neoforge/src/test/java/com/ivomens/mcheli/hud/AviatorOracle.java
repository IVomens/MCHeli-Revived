package com.ivomens.mcheli.hud;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.exception.CompileExpressionErrorException;
import com.googlecode.aviator.exception.ExpressionSyntaxErrorException;

import java.util.HashMap;
import java.util.Map;

/**
 * Aviator, the expression engine HUD formulas were written for, kept as a test dependency to check the
 * formula compiler against. It evaluates a formula the way the game did before the compiler: with the
 * variables boxed into a map, and the result read as a double.
 */
final class AviatorOracle {

    private static final AviatorEvaluatorInstance AVIATOR = AviatorEvaluator.newInstance();
    /** Longest error message kept; Aviator appends parser context that adds nothing. */
    private static final int MAX_MESSAGE = 160;

    private AviatorOracle() {
    }

    /** The result as the expression golden writes it: the double, or the failure in brackets. */
    static String evaluate(String formula, Map<String, Object> variables) {
        Expression compiled;
        try {
            compiled = AVIATOR.compile(formula, true);
        } catch (RuntimeException e) {
            return failure(new IllegalStateException("HUD expression failed to compile: " + formula, e));
        }
        try {
            Object result = compiled.execute(new HashMap<>(variables));
            if (result instanceof Number number) return Double.toString(number.doubleValue());
            if (result instanceof Boolean bool) return Double.toString(bool ? 1 : 0);
            return failure(new IllegalStateException("HUD expression returned " + result + ": " + formula));
        } catch (RuntimeException e) {
            return failure(e);
        }
    }

    /** Whether Aviator refuses to compile the formula at all, as opposed to failing while it runs. */
    static boolean rejects(String formula) {
        try {
            AVIATOR.compile(formula, true);
            return false;
        } catch (CompileExpressionErrorException | ExpressionSyntaxErrorException e) {
            return true;
        }
    }

    /** A failure as the expression golden writes it: {@code [SimpleClassName: first line of the message]}. */
    static String failure(Throwable e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String message = String.valueOf(cause.getMessage()).lines().findFirst().orElse("");
        if (message.length() > MAX_MESSAGE) {
            message = message.substring(0, MAX_MESSAGE) + "...";
        }
        return "[" + cause.getClass().getSimpleName() + ": " + message + "]";
    }
}
