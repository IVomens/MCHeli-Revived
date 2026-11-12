package com.ivomens.mcheli.core.hud.expr;

/** A formula that cannot be read: a character, a number or an arrangement of tokens the grammar does not know. */
public final class ExprException extends RuntimeException {

    ExprException(String message) {
        super(message);
    }
}
