package com.ivomens.mcheli.core.hud.expr;

/**
 * How formula values compare and convert, as Aviator defined it and HUD scripts rely on:
 * <ul>
 *     <li>two integers compare as longs; an integer and a double, or two doubles, with {@link Double#compare},
 *     so {@code -0.0 < 0.0} and NaN is equal to itself and above every number;</li>
 *     <li>booleans compare with booleans, {@code false} first;</li>
 *     <li>nil is below every value and equal only to nil;</li>
 *     <li>a boolean and a number cannot be ordered, and are never equal.</li>
 * </ul>
 */
final class Semantics {

    private Semantics() {
    }

    /** Whether values of these types can be ordered; values that cannot are never equal. */
    static boolean comparable(ExprType a, ExprType b) {
        return a == ExprType.NIL || b == ExprType.NIL || a.isNumeric() == b.isNumeric();
    }

    /**
     * Compares the values of {@code left} and {@code right} in this frame, evaluating both, left first.
     *
     * @throws IllegalStateException when they cannot be compared
     */
    static int compare(CompiledExpr left, CompiledExpr right, Variables variables) {
        ExprType a = left.typeIn(variables);
        ExprType b = right.typeIn(variables);
        if (a == ExprType.INT && b == ExprType.INT) {
            return Long.compare(left.asLong(variables), right.asLong(variables));
        }
        if (a.isNumeric() && b.isNumeric()) {
            return Double.compare(left.asDouble(variables), right.asDouble(variables));
        }
        if (a == ExprType.BOOL && b == ExprType.BOOL) {
            return Boolean.compare(left.asBool(variables), right.asBool(variables));
        }
        if (a == ExprType.NIL || b == ExprType.NIL) {
            evaluate(left, variables);
            evaluate(right, variables);
            return a == b ? 0 : a == ExprType.NIL ? -1 : 1;
        }
        throw new IllegalStateException("cannot compare " + a.description() + " with " + b.description());
    }

    /** Evaluates a value only for the failure it may raise, as an operation that ignores it still would. */
    static void evaluate(CompiledExpr expr, Variables variables) {
        switch (expr.typeIn(variables)) {
            case INT -> expr.asLong(variables);
            case DOUBLE -> expr.asDouble(variables);
            case BOOL -> expr.asBool(variables);
            case NIL -> {
            }
        }
    }

    /** The boolean value of {@code expr}, which must be a boolean in this frame. */
    static boolean bool(CompiledExpr expr, Variables variables, String context) {
        ExprType type = expr.typeIn(variables);
        if (type != ExprType.BOOL) {
            throw new IllegalStateException(context + " needs a boolean, not " + type.description());
        }
        return expr.asBool(variables);
    }

    /** The message of an operation that cannot take values of these types. */
    static String typeError(String verb, ExprType a, ExprType b) {
        return "cannot " + verb + " " + a.description() + " and " + b.description();
    }

    static String typeError(String verb, ExprType type) {
        return "cannot " + verb + " " + type.description();
    }
}
