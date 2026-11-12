package com.ivomens.mcheli.core.hud.expr;

/**
 * One token of a formula.
 *
 * @param kind     what the token is
 * @param text     the token as written; for a symbol, the symbol itself
 * @param position the index of its first character, for messages
 */
record Token(Kind kind, String text, int position) {

    enum Kind {
        /** A decimal or {@code 0x} hexadecimal number. */
        NUMBER,
        /** A variable or function name, or {@code true}, {@code false} and {@code nil}. */
        NAME,
        /** An operator, a parenthesis or a comma. */
        SYMBOL,
        /** The end of the formula. */
        END
    }

    boolean isSymbol(String symbol) {
        return this.kind == Kind.SYMBOL && this.text.equals(symbol);
    }

    /** The token for messages: {@code '+'}, or "the end of the formula". */
    String describe() {
        return this.kind == Kind.END ? "the end of the formula" : "'" + this.text + "'";
    }
}
