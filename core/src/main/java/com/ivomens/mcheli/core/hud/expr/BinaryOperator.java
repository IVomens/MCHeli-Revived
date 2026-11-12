package com.ivomens.mcheli.core.hud.expr;

import java.util.function.DoubleBinaryOperator;
import java.util.function.IntPredicate;
import java.util.function.LongBinaryOperator;

/**
 * The infix operators with Aviator's precedence, which is Java's: a higher level binds tighter, and every
 * level groups from the left. Each operator carries what it computes, so the compiled nodes only choose
 * between integer and double arithmetic.
 */
enum BinaryOperator {

    OR("||", 1, Category.LOGICAL),
    AND("&&", 2, Category.LOGICAL),
    BIT_OR("|", 3, "combine with '|'", (a, b) -> a | b),
    BIT_XOR("^", 4, "combine with '^'", (a, b) -> a ^ b),
    BIT_AND("&", 5, "combine with '&'", (a, b) -> a & b),
    EQUAL("==", 6, Category.EQUALITY, order -> order == 0),
    NOT_EQUAL("!=", 6, Category.EQUALITY, order -> order != 0),
    LESS("<", 7, Category.ORDERING, order -> order < 0),
    LESS_OR_EQUAL("<=", 7, Category.ORDERING, order -> order <= 0),
    GREATER(">", 7, Category.ORDERING, order -> order > 0),
    GREATER_OR_EQUAL(">=", 7, Category.ORDERING, order -> order >= 0),
    SHIFT_LEFT("<<", 8, "shift", (a, b) -> a << b),
    SHIFT_RIGHT(">>", 8, "shift", (a, b) -> a >> b),
    UNSIGNED_SHIFT_RIGHT(">>>", 8, "shift", (a, b) -> a >>> b),
    ADD("+", 9, "add", (a, b) -> a + b, (a, b) -> a + b),
    SUBTRACT("-", 9, "subtract", (a, b) -> a - b, (a, b) -> a - b),
    MULTIPLY("*", 10, "multiply", (a, b) -> a * b, (a, b) -> a * b),
    /** Integer division truncates and fails on zero; double division gives an infinity or NaN. */
    DIVIDE("/", 10, "divide", (a, b) -> a / b, (a, b) -> a / b),
    REMAINDER("%", 10, "take the remainder of", (a, b) -> a % b, (a, b) -> a % b);

    enum Category {
        /** Integers or doubles: integer arithmetic when both sides are integers, double otherwise. */
        ARITHMETIC,
        /** Integers only: the bit operators and shifts. */
        INTEGER,
        /** Any two values {@link Semantics#comparable comparable}; anything else fails. */
        ORDERING,
        /** Any two values; values that cannot be compared are not equal. */
        EQUALITY,
        /** Booleans, evaluated left to right with short-circuit. */
        LOGICAL
    }

    static final int LOWEST_PRECEDENCE = 1;
    static final int HIGHEST_PRECEDENCE = 10;

    final String symbol;
    final int precedence;
    final Category category;
    /** The verb of a type error: "cannot add a boolean and an integer". */
    final String verb;
    final LongBinaryOperator longs;
    final DoubleBinaryOperator doubles;
    /** For comparisons: whether the result of {@link Semantics#compare} makes the comparison true. */
    final IntPredicate ordering;

    BinaryOperator(String symbol, int precedence, Category category) {
        this(symbol, precedence, category, "combine", null, null, null);
    }

    BinaryOperator(String symbol, int precedence, Category category, IntPredicate ordering) {
        this(symbol, precedence, category, "compare", null, null, ordering);
    }

    BinaryOperator(String symbol, int precedence, String verb, LongBinaryOperator longs) {
        this(symbol, precedence, Category.INTEGER, verb, longs, null, null);
    }

    BinaryOperator(String symbol, int precedence, String verb, LongBinaryOperator longs, DoubleBinaryOperator doubles) {
        this(symbol, precedence, Category.ARITHMETIC, verb, longs, doubles, null);
    }

    BinaryOperator(String symbol, int precedence, Category category, String verb, LongBinaryOperator longs,
                   DoubleBinaryOperator doubles, IntPredicate ordering) {
        this.symbol = symbol;
        this.precedence = precedence;
        this.category = category;
        this.verb = verb;
        this.longs = longs;
        this.doubles = doubles;
        this.ordering = ordering;
    }

    /** The operator {@code token} spells at {@code precedence}, or null. */
    static BinaryOperator of(Token token, int precedence) {
        for (BinaryOperator operator : values()) {
            if (operator.precedence == precedence && token.isSymbol(operator.symbol)) {
                return operator;
            }
        }
        return null;
    }
}
