package com.ivomens.mcheli.core.hud.expr;

import java.util.EnumSet;
import java.util.Set;

/**
 * The operator nodes. Each one states its typing rule once, as a static method the compiler uses to work out
 * a node's types at load time and the node uses for a frame when an operand's type varies.
 */
final class Operations {

    private Operations() {
    }

    /** The types {@code rule} gives over every combination of operand types; empty when none is allowed. */
    static Set<ExprType> resultTypes(Set<ExprType> left, Set<ExprType> right, BinaryRule rule) {
        Set<ExprType> types = EnumSet.noneOf(ExprType.class);
        for (ExprType a : left) {
            for (ExprType b : right) {
                ExprType result = rule.resultType(a, b);
                if (result != null) {
                    types.add(result);
                }
            }
        }
        return types;
    }

    /** The type an operation gives for two operand types, or null when it cannot take them. */
    @FunctionalInterface
    interface BinaryRule {
        ExprType resultType(ExprType a, ExprType b);
    }

    /** {@code -x}: an integer or a double. */
    static final class Negate extends CompiledExpr {

        private final CompiledExpr operand;

        Negate(CompiledExpr operand, Set<ExprType> types) {
            super(types, operand);
            this.operand = operand;
        }

        static ExprType resultType(ExprType type) {
            return type.isNumeric() ? type : null;
        }

        @Override
        ExprType varyingType(Variables variables) {
            ExprType type = this.operand.typeIn(variables);
            if (resultType(type) == null) {
                throw new IllegalStateException(Semantics.typeError(UnaryOperator.NEGATE.verb, type));
            }
            return type;
        }

        @Override
        public long asLong(Variables variables) {
            return -this.operand.asLong(variables);
        }

        @Override
        public double asDouble(Variables variables) {
            return typeIn(variables) == ExprType.INT ? -this.operand.asLong(variables) : -this.operand.asDouble(variables);
        }
    }

    /** {@code !b} and {@code ~i}: one type in, the same type out. */
    static final class Complement extends CompiledExpr {

        private final UnaryOperator operator;
        private final CompiledExpr operand;

        Complement(UnaryOperator operator, CompiledExpr operand, Set<ExprType> types) {
            super(types, operand);
            this.operator = operator;
            this.operand = operand;
        }

        static ExprType resultType(UnaryOperator operator, ExprType type) {
            ExprType wanted = operator == UnaryOperator.NOT ? ExprType.BOOL : ExprType.INT;
            return type == wanted ? type : null;
        }

        @Override
        ExprType varyingType(Variables variables) {
            ExprType type = this.operand.typeIn(variables);
            if (resultType(this.operator, type) == null) {
                throw new IllegalStateException(Semantics.typeError(this.operator.verb, type));
            }
            return type;
        }

        @Override
        public long asLong(Variables variables) {
            return ~this.operand.asLong(variables);
        }

        @Override
        public double asDouble(Variables variables) {
            return asLong(variables);
        }

        @Override
        public boolean asBool(Variables variables) {
            return !this.operand.asBool(variables);
        }
    }

    /**
     * The arithmetic and integer operators. Two integers give integer arithmetic, which wraps around and fails
     * on a division by zero; the arithmetic operators give double arithmetic for any other two numbers.
     */
    static final class Arithmetic extends CompiledExpr {

        private final BinaryOperator operator;
        private final CompiledExpr left;
        private final CompiledExpr right;

        Arithmetic(BinaryOperator operator, CompiledExpr left, CompiledExpr right, Set<ExprType> types) {
            super(types, left, right);
            this.operator = operator;
            this.left = left;
            this.right = right;
        }

        static ExprType resultType(BinaryOperator operator, ExprType a, ExprType b) {
            if (a == ExprType.INT && b == ExprType.INT) {
                return ExprType.INT;
            }
            boolean doubles = operator.category == BinaryOperator.Category.ARITHMETIC;
            return doubles && a.isNumeric() && b.isNumeric() ? ExprType.DOUBLE : null;
        }

        @Override
        ExprType varyingType(Variables variables) {
            ExprType a = this.left.typeIn(variables);
            ExprType b = this.right.typeIn(variables);
            ExprType result = resultType(this.operator, a, b);
            if (result == null) {
                throw new IllegalStateException(Semantics.typeError(this.operator.verb, a, b));
            }
            return result;
        }

        @Override
        public long asLong(Variables variables) {
            return this.operator.longs.applyAsLong(this.left.asLong(variables), this.right.asLong(variables));
        }

        @Override
        public double asDouble(Variables variables) {
            if (typeIn(variables) == ExprType.INT) {
                return asLong(variables);
            }
            return this.operator.doubles.applyAsDouble(this.left.asDouble(variables), this.right.asDouble(variables));
        }
    }

    /** The ordering and equality operators, by {@link Semantics#compare}. */
    static final class Comparison extends CompiledExpr {

        private final BinaryOperator operator;
        private final CompiledExpr left;
        private final CompiledExpr right;

        Comparison(BinaryOperator operator, CompiledExpr left, CompiledExpr right) {
            super(only(ExprType.BOOL), left, right);
            this.operator = operator;
            this.left = left;
            this.right = right;
        }

        /** Ordering takes two comparable values; equality takes any two, and incomparable ones are not equal. */
        static ExprType resultType(BinaryOperator operator, ExprType a, ExprType b) {
            boolean equality = operator.category == BinaryOperator.Category.EQUALITY;
            return equality || Semantics.comparable(a, b) ? ExprType.BOOL : null;
        }

        @Override
        public boolean asBool(Variables variables) {
            if (this.operator.category == BinaryOperator.Category.EQUALITY
                    && !Semantics.comparable(this.left.typeIn(variables), this.right.typeIn(variables))) {
                Semantics.evaluate(this.left, variables);
                Semantics.evaluate(this.right, variables);
                return this.operator == BinaryOperator.NOT_EQUAL;
            }
            return this.operator.ordering.test(Semantics.compare(this.left, this.right, variables));
        }
    }

    /** {@code &&} and {@code ||}: booleans, left to right, the right side only when it decides the result. */
    static final class Logical extends CompiledExpr {

        private final BinaryOperator operator;
        private final CompiledExpr left;
        private final CompiledExpr right;

        Logical(BinaryOperator operator, CompiledExpr left, CompiledExpr right) {
            super(only(ExprType.BOOL), left, right);
            this.operator = operator;
            this.left = left;
            this.right = right;
        }

        static ExprType resultType(ExprType a, ExprType b) {
            return a == ExprType.BOOL && b == ExprType.BOOL ? ExprType.BOOL : null;
        }

        @Override
        public boolean asBool(Variables variables) {
            boolean first = Semantics.bool(this.left, variables, this.operator.symbol);
            if (this.operator == BinaryOperator.AND ? !first : first) {
                return first;
            }
            return Semantics.bool(this.right, variables, this.operator.symbol);
        }
    }

    /** {@code condition ? then : otherwise}; when the branches differ in type, so does the node. */
    static final class Conditional extends CompiledExpr {

        private final CompiledExpr condition;
        private final CompiledExpr then;
        private final CompiledExpr otherwise;

        Conditional(CompiledExpr condition, CompiledExpr then, CompiledExpr otherwise) {
            super(union(then.types(), otherwise.types()), condition, then, otherwise);
            this.condition = condition;
            this.then = then;
            this.otherwise = otherwise;
        }

        private CompiledExpr branch(Variables variables) {
            return Semantics.bool(this.condition, variables, "?:") ? this.then : this.otherwise;
        }

        @Override
        ExprType varyingType(Variables variables) {
            return branch(variables).typeIn(variables);
        }

        @Override
        public long asLong(Variables variables) {
            return branch(variables).asLong(variables);
        }

        @Override
        public double asDouble(Variables variables) {
            return branch(variables).asDouble(variables);
        }

        @Override
        public boolean asBool(Variables variables) {
            return branch(variables).asBool(variables);
        }
    }

    static Set<ExprType> union(Set<ExprType> a, Set<ExprType> b) {
        Set<ExprType> types = EnumSet.noneOf(ExprType.class);
        types.addAll(a);
        types.addAll(b);
        return types;
    }
}
