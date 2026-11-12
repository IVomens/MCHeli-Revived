package com.ivomens.mcheli.core.hud.expr;

/**
 * The variable values a compiled formula reads, by the slot its {@link Variable} was given when the
 * formula was compiled. A formula only asks a slot for the type its variable declares.
 */
public interface Variables {

    long intValue(int slot);

    double doubleValue(int slot);

    boolean boolValue(int slot);
}
