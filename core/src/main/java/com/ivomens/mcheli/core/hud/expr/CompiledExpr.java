package com.ivomens.mcheli.core.hud.expr;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A node of a compiled formula. Each node knows when it is compiled which {@link #types() types} its value
 * can have. Almost always that is exactly one type, every operand below is just as fixed, and the node is
 * <em>settled</em>: it is read through the accessor of its type with no check at all.
 * <p>
 * A node that is not settled (a {@code ?:} whose branches differ, a {@code max} of mixed values, anything
 * above them) works its type out per frame in {@link #typeIn}, checking its operands on the way, and is read
 * only after that. A node that fails, such as an addition of a boolean that hides behind a {@code ?:},
 * throws {@link IllegalStateException} with the reason when the frame reaches it.
 * <p>
 * Evaluating never allocates or boxes.
 */
public abstract class CompiledExpr {

    private final Set<ExprType> types;
    /** The type when there is only one, or null. */
    private final ExprType fixedType;
    /** The type is fixed and nothing below can fail on a type, so {@link #typeIn} needs no frame. */
    private final boolean settled;

    CompiledExpr(Set<ExprType> types, CompiledExpr... operands) {
        this.types = Collections.unmodifiableSet(types.isEmpty() ? EnumSet.noneOf(ExprType.class) : EnumSet.copyOf(types));
        this.fixedType = types.size() == 1 ? types.iterator().next() : null;
        boolean operandsSettled = true;
        for (CompiledExpr operand : operands) {
            operandsSettled &= operand.settled;
        }
        this.settled = this.fixedType != null && operandsSettled;
    }

    /** The types the value can have; empty for a node that always fails. */
    public final Set<ExprType> types() {
        return this.types;
    }

    /**
     * The type of the value in this frame, one of {@link #types()}. For a node that is not settled this checks
     * the operands the frame reaches, and fails as the operation would; it must be asked before reading such a
     * node.
     */
    public final ExprType typeIn(Variables variables) {
        return this.settled ? this.fixedType : varyingType(variables);
    }

    /**
     * The type in this frame of a node that is not settled. Nodes that check their operands when they are read
     * (comparisons, {@code &&}) keep this default, their one type.
     */
    ExprType varyingType(Variables variables) {
        if (this.fixedType == null) {
            throw new IllegalStateException("a formula node of type " + this.types + " has no varying type");
        }
        return this.fixedType;
    }

    /** The value, when {@link #typeIn} is {@link ExprType#INT}. */
    public long asLong(Variables variables) {
        throw misread("an integer");
    }

    /** The value, when {@link #typeIn} is {@link ExprType#INT} (widened) or {@link ExprType#DOUBLE}. */
    public double asDouble(Variables variables) {
        throw misread("a number");
    }

    /** The value, when {@link #typeIn} is {@link ExprType#BOOL}. */
    public boolean asBool(Variables variables) {
        throw misread("a boolean");
    }

    private IllegalStateException misread(String what) {
        return new IllegalStateException("a formula node of type " + this.types + " was read as " + what);
    }

    static Set<ExprType> only(ExprType type) {
        return EnumSet.of(type);
    }

    /** An integer constant or an integer variable. */
    static final class IntValue extends CompiledExpr {

        private final long constant;
        private final int slot;

        private IntValue(long constant, int slot) {
            super(only(ExprType.INT));
            this.constant = constant;
            this.slot = slot;
        }

        static IntValue constant(long value) {
            return new IntValue(value, -1);
        }

        static IntValue variable(int slot) {
            return new IntValue(0, slot);
        }

        @Override
        public long asLong(Variables variables) {
            return this.slot < 0 ? this.constant : variables.intValue(this.slot);
        }

        @Override
        public double asDouble(Variables variables) {
            return asLong(variables);
        }
    }

    /** A double constant or a double variable. */
    static final class DoubleValue extends CompiledExpr {

        private final double constant;
        private final int slot;

        private DoubleValue(double constant, int slot) {
            super(only(ExprType.DOUBLE));
            this.constant = constant;
            this.slot = slot;
        }

        static DoubleValue constant(double value) {
            return new DoubleValue(value, -1);
        }

        static DoubleValue variable(int slot) {
            return new DoubleValue(0, slot);
        }

        @Override
        public double asDouble(Variables variables) {
            return this.slot < 0 ? this.constant : variables.doubleValue(this.slot);
        }
    }

    /** A boolean constant or a boolean variable. */
    static final class BoolValue extends CompiledExpr {

        private final boolean constant;
        private final int slot;

        private BoolValue(boolean constant, int slot) {
            super(only(ExprType.BOOL));
            this.constant = constant;
            this.slot = slot;
        }

        static BoolValue constant(boolean value) {
            return new BoolValue(value, -1);
        }

        static BoolValue variable(int slot) {
            return new BoolValue(false, slot);
        }

        @Override
        public boolean asBool(Variables variables) {
            return this.slot < 0 ? this.constant : variables.boolValue(this.slot);
        }
    }

    /** {@code nil}, or a name that is no variable. It has no value to read. */
    static final class Nil extends CompiledExpr {

        static final Nil INSTANCE = new Nil();

        private Nil() {
            super(only(ExprType.NIL));
        }
    }

    /** A part of a formula that cannot work, reported when it was compiled. It fails only when it is reached. */
    static final class Failure extends CompiledExpr {

        private final String reason;

        Failure(String reason) {
            super(EnumSet.noneOf(ExprType.class));
            this.reason = reason;
        }

        String reason() {
            return this.reason;
        }

        @Override
        ExprType varyingType(Variables variables) {
            throw new IllegalStateException(this.reason);
        }
    }
}
