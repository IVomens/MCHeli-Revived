package com.ivomens.mcheli.core.hud.expr;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * The functions formulas may call, with Aviator's names and results: {@code math.abs} keeps an integer an
 * integer, {@code math.round} gives an integer, the other {@code math.*} functions give doubles, and
 * {@code max} and {@code min} give back the largest or smallest argument itself, the first one of equals.
 * The bundled HUDs call none of them; they are here for addon packs.
 */
final class Functions {

    /** The one-argument functions that work on a double and give one. */
    private static final Map<String, DoubleUnaryOperator> MATH = Map.ofEntries(
            Map.entry("math.sqrt", Math::sqrt),
            Map.entry("math.sin", Math::sin),
            Map.entry("math.cos", Math::cos),
            Map.entry("math.tan", Math::tan),
            Map.entry("math.asin", Math::asin),
            Map.entry("math.acos", Math::acos),
            Map.entry("math.atan", Math::atan),
            Map.entry("math.log", Math::log),
            Map.entry("math.log10", Math::log10),
            Map.entry("math.floor", Math::floor),
            Map.entry("math.ceil", Math::ceil));

    /** Every function name, sorted, for messages. */
    static final Set<String> NAMES = names();

    private Functions() {
    }

    /**
     * The node for a call. A call that cannot work (an unknown function, the wrong number of arguments, an
     * argument that is never a number) gives a {@link CompiledExpr.Failure}, which fails only if a frame
     * reaches it, as the call failed in Aviator.
     */
    static CompiledExpr call(String function, List<CompiledExpr> arguments) {
        DoubleUnaryOperator math = MATH.get(function);
        if (math != null) {
            return numeric(function, arguments, 1, ExprType.DOUBLE, (x, unused) -> math.applyAsDouble(x));
        }
        return switch (function) {
            case "math.abs" -> numeric(function, arguments, 1, null, null);
            case "math.round" -> numeric(function, arguments, 1, ExprType.INT, (x, unused) -> Math.round(x));
            case "math.pow" -> numeric(function, arguments, 2, ExprType.DOUBLE, Math::pow);
            case "max", "min" -> arguments.isEmpty()
                    ? new CompiledExpr.Failure(function + " needs at least one argument")
                    : new Extremum(function.equals("max"), arguments);
            default -> new CompiledExpr.Failure("there is no function '" + function + "'; formulas can call "
                    + String.join(", ", NAMES));
        };
    }

    /**
     * A function of {@code count} numbers, or the failure of a call that does not fit it.
     *
     * @param result the type of the result, or null for {@code math.abs}, whose result has its argument's type
     */
    private static CompiledExpr numeric(String function, List<CompiledExpr> arguments, int count, ExprType result,
                                        DoubleBinaryOperator compute) {
        if (arguments.size() != count) {
            return new CompiledExpr.Failure(function + " takes " + count + (count == 1 ? " argument" : " arguments")
                    + ", not " + arguments.size());
        }
        for (CompiledExpr argument : arguments) {
            if (!argument.types().isEmpty() && argument.types().stream().noneMatch(ExprType::isNumeric)) {
                return new CompiledExpr.Failure(function + " needs a number, not "
                        + argument.types().iterator().next().description());
            }
        }
        return result == null ? new Abs(arguments.getFirst()) : new Numeric(function, arguments, result, compute);
    }

    private static Set<String> names() {
        Set<String> names = new TreeSet<>(MATH.keySet());
        names.addAll(List.of("math.abs", "math.round", "math.pow", "max", "min"));
        return names;
    }

    private static void requireNumber(String function, CompiledExpr argument, Variables variables) {
        ExprType type = argument.typeIn(variables);
        if (!type.isNumeric()) {
            throw new IllegalStateException(function + " needs a number, not " + type.description());
        }
    }

    /**
     * A function of one or two numbers, each read as a double. For {@code math.round} the double holds the
     * rounded long exactly: a double too large for its fraction to matter is already a whole number.
     */
    private static final class Numeric extends CompiledExpr {

        private final String name;
        private final CompiledExpr first;
        /** The second argument, or null for a function of one. */
        private final CompiledExpr second;
        private final DoubleBinaryOperator compute;

        Numeric(String name, List<CompiledExpr> arguments, ExprType result, DoubleBinaryOperator compute) {
            super(EnumSet.of(result), arguments.toArray(CompiledExpr[]::new));
            this.name = name;
            this.first = arguments.getFirst();
            this.second = arguments.size() > 1 ? arguments.get(1) : null;
            this.compute = compute;
        }

        @Override
        ExprType varyingType(Variables variables) {
            requireNumber(this.name, this.first, variables);
            if (this.second != null) {
                requireNumber(this.name, this.second, variables);
            }
            return super.varyingType(variables);
        }

        @Override
        public long asLong(Variables variables) {
            return (long) asDouble(variables);
        }

        @Override
        public double asDouble(Variables variables) {
            double x = this.first.asDouble(variables);
            return this.compute.applyAsDouble(x, this.second == null ? 0.0 : this.second.asDouble(variables));
        }
    }

    /** {@code math.abs}: of an integer, an integer; of a double, a double. */
    private static final class Abs extends CompiledExpr {

        private final CompiledExpr argument;

        Abs(CompiledExpr argument) {
            super(numericTypes(argument.types()), argument);
            this.argument = argument;
        }

        @Override
        ExprType varyingType(Variables variables) {
            requireNumber("math.abs", this.argument, variables);
            return this.argument.typeIn(variables);
        }

        @Override
        public long asLong(Variables variables) {
            return Math.abs(this.argument.asLong(variables));
        }

        @Override
        public double asDouble(Variables variables) {
            return typeIn(variables) == ExprType.INT ? asLong(variables) : Math.abs(this.argument.asDouble(variables));
        }
    }

    /** {@code max} and {@code min}: the chosen argument itself, so its type is the node's type in the frame. */
    private static final class Extremum extends CompiledExpr {

        private final boolean largest;
        private final CompiledExpr[] arguments;

        Extremum(boolean largest, List<CompiledExpr> arguments) {
            super(allTypes(arguments), arguments.toArray(CompiledExpr[]::new));
            this.largest = largest;
            this.arguments = arguments.toArray(CompiledExpr[]::new);
        }

        private CompiledExpr chosen(Variables variables) {
            CompiledExpr chosen = this.arguments[0];
            for (int i = 1; i < this.arguments.length; i++) {
                int order = Semantics.compare(this.arguments[i], chosen, variables);
                if (this.largest ? order > 0 : order < 0) {
                    chosen = this.arguments[i];
                }
            }
            return chosen;
        }

        @Override
        ExprType varyingType(Variables variables) {
            return chosen(variables).typeIn(variables);
        }

        @Override
        public long asLong(Variables variables) {
            return chosen(variables).asLong(variables);
        }

        @Override
        public double asDouble(Variables variables) {
            return chosen(variables).asDouble(variables);
        }

        @Override
        public boolean asBool(Variables variables) {
            return chosen(variables).asBool(variables);
        }
    }

    private static Set<ExprType> numericTypes(Set<ExprType> types) {
        Set<ExprType> numeric = EnumSet.noneOf(ExprType.class);
        types.stream().filter(ExprType::isNumeric).forEach(numeric::add);
        return numeric;
    }

    private static Set<ExprType> allTypes(List<CompiledExpr> arguments) {
        Set<ExprType> types = EnumSet.noneOf(ExprType.class);
        arguments.forEach(argument -> types.addAll(argument.types()));
        return types;
    }
}
