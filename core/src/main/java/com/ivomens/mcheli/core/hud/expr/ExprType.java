package com.ivomens.mcheli.core.hud.expr;

/**
 * The type of a formula value. HUD scripts were written against Aviator, which keeps 64-bit integers
 * apart from doubles ({@code 7/2} is 3, {@code 7/2.0} is 3.5), so the split is kept. {@code NIL} is the
 * value of a name that is not a variable: Aviator read such names as nil instead of failing.
 */
public enum ExprType {

    INT("an integer"),
    DOUBLE("a number"),
    BOOL("a boolean"),
    NIL("nil");

    private final String description;

    ExprType(String description) {
        this.description = description;
    }

    public boolean isNumeric() {
        return this == INT || this == DOUBLE;
    }

    /** The type with an article, for messages: "a boolean". */
    public String description() {
        return this.description;
    }
}
