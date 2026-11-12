package com.ivomens.mcheli.core.hud.expr;

/** The prefix operators. */
enum UnaryOperator {

    /** {@code -x}: integer or double negation. */
    NEGATE("-", "negate"),
    /** {@code !b}: boolean not. */
    NOT("!", "apply '!' to"),
    /** {@code ~i}: bitwise not of an integer. */
    BIT_NOT("~", "apply '~' to");

    final String symbol;
    /** The verb of a type error: "cannot negate a boolean". */
    final String verb;

    UnaryOperator(String symbol, String verb) {
        this.symbol = symbol;
        this.verb = verb;
    }

    static UnaryOperator of(Token token) {
        for (UnaryOperator operator : values()) {
            if (token.isSymbol(operator.symbol)) {
                return operator;
            }
        }
        return null;
    }
}
