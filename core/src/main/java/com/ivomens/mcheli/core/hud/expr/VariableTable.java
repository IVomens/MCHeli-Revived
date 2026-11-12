package com.ivomens.mcheli.core.hud.expr;

import java.util.Optional;

/** The variables formulas may read, looked up by name while a formula compiles. */
@FunctionalInterface
public interface VariableTable {

    Optional<Variable> find(String name);
}
