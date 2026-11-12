package com.ivomens.mcheli.core.hud.expr;

import java.util.List;

/** The syntax tree of a formula, as {@link Parser} reads it and before any name or type is checked. */
sealed interface Ast {

    record IntLiteral(long value) implements Ast {
    }

    record DoubleLiteral(double value) implements Ast {
    }

    record BoolLiteral(boolean value) implements Ast {
    }

    record NilLiteral() implements Ast {
    }

    /** A variable. */
    record Name(String name) implements Ast {
    }

    record Unary(UnaryOperator operator, Ast operand) implements Ast {
    }

    record Binary(BinaryOperator operator, Ast left, Ast right) implements Ast {
    }

    /** {@code condition ? then : otherwise}. */
    record Conditional(Ast condition, Ast then, Ast otherwise) implements Ast {
    }

    record Call(String function, List<Ast> arguments) implements Ast {

        public Call {
            arguments = List.copyOf(arguments);
        }
    }
}
