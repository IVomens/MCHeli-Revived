package com.ivomens.mcheli.core.hud.expr;

import java.util.Objects;

/**
 * A name formulas can read.
 *
 * @param name               the name as formulas write it, in lower case
 * @param type               the type of every value of the variable; never {@link ExprType#NIL}
 * @param slot               where {@link Variables} keeps the value
 * @param legacyComparisons  whether the variable was a 0/1 integer in older scripts, which still compare it
 *                           with {@code == 0} or {@code != 1}; those comparisons are read as boolean tests
 */
public record Variable(String name, ExprType type, int slot, boolean legacyComparisons) {

    public Variable {
        Objects.requireNonNull(name);
        if (type == ExprType.NIL) {
            throw new IllegalArgumentException("a variable always has a value: " + name);
        }
    }
}
