package com.ivomens.mcheli.core.hud.expr;

import com.ivomens.mcheli.core.hud.expr.CompiledExpr.BoolValue;
import com.ivomens.mcheli.core.hud.expr.CompiledExpr.DoubleValue;
import com.ivomens.mcheli.core.hud.expr.CompiledExpr.Failure;
import com.ivomens.mcheli.core.hud.expr.CompiledExpr.IntValue;
import com.ivomens.mcheli.core.hud.expr.CompiledExpr.Nil;
import com.ivomens.mcheli.core.hud.expr.Operations.Arithmetic;
import com.ivomens.mcheli.core.hud.expr.Operations.Comparison;
import com.ivomens.mcheli.core.hud.expr.Operations.Complement;
import com.ivomens.mcheli.core.hud.expr.Operations.Conditional;
import com.ivomens.mcheli.core.hud.expr.Operations.Logical;
import com.ivomens.mcheli.core.hud.expr.Operations.Negate;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Turns a syntax tree into compiled nodes: names become variable slots, and every operation gets its types.
 * <p>
 * What can never work is reported here, at load time: a name that is no variable (it reads as nil, as it did
 * in Aviator, so the formula still gives what it always gave) and an operation no type of its operands allows,
 * such as adding a boolean. The operation becomes a {@link Failure}, which only fails if a frame reaches it:
 * Aviator failed when it evaluated the operation, and an untaken {@code ?:} branch never did.
 */
final class ExprCompiler {

    private final VariableTable variables;
    private final Consumer<String> problems;

    ExprCompiler(VariableTable variables, Consumer<String> problems) {
        this.variables = variables;
        this.problems = problems;
    }

    CompiledExpr compile(Ast ast) {
        return switch (ast) {
            case Ast.IntLiteral literal -> IntValue.constant(literal.value());
            case Ast.DoubleLiteral literal -> DoubleValue.constant(literal.value());
            case Ast.BoolLiteral literal -> BoolValue.constant(literal.value());
            case Ast.NilLiteral nil -> Nil.INSTANCE;
            case Ast.Name name -> variable(name.name());
            case Ast.Unary unary -> unary(unary.operator(), compile(unary.operand()));
            case Ast.Binary binary -> binary(binary.operator(), compile(binary.left()), compile(binary.right()));
            case Ast.Conditional conditional -> conditional(compile(conditional.condition()),
                    compile(conditional.then()), compile(conditional.otherwise()));
            case Ast.Call call -> reported(Functions.call(call.function(),
                    call.arguments().stream().map(this::compile).toList()));
        };
    }

    private CompiledExpr variable(String name) {
        Variable variable = this.variables.find(name).orElse(null);
        if (variable == null) {
            this.problems.accept("'" + name + "' is not a HUD variable, so it has no value");
            return Nil.INSTANCE;
        }
        return switch (variable.type()) {
            case INT -> IntValue.variable(variable.slot());
            case DOUBLE -> DoubleValue.variable(variable.slot());
            case BOOL -> BoolValue.variable(variable.slot());
            case NIL -> throw new IllegalStateException("a variable has no value: " + name);
        };
    }

    private CompiledExpr unary(UnaryOperator operator, CompiledExpr operand) {
        Set<ExprType> types = EnumSet.noneOf(ExprType.class);
        for (ExprType type : operand.types()) {
            ExprType result = operator == UnaryOperator.NEGATE ? Negate.resultType(type)
                    : Complement.resultType(operator, type);
            if (result != null) {
                types.add(result);
            }
        }
        if (types.isEmpty() && !operand.types().isEmpty()) {
            return failure(Semantics.typeError(operator.verb, first(operand)));
        }
        return operator == UnaryOperator.NEGATE ? new Negate(operand, types)
                : new Complement(operator, operand, types);
    }

    private CompiledExpr binary(BinaryOperator operator, CompiledExpr left, CompiledExpr right) {
        if (operator.category == BinaryOperator.Category.LOGICAL) {
            return logical(operator, left, right);
        }
        boolean arithmetic = operator.category == BinaryOperator.Category.ARITHMETIC
                || operator.category == BinaryOperator.Category.INTEGER;
        Set<ExprType> types = Operations.resultTypes(left.types(), right.types(), arithmetic
                ? (a, b) -> Arithmetic.resultType(operator, a, b)
                : (a, b) -> Comparison.resultType(operator, a, b));
        if (types.isEmpty() && !left.types().isEmpty() && !right.types().isEmpty()) {
            return failure(Semantics.typeError(operator.verb, first(left), first(right)));
        }
        return arithmetic ? new Arithmetic(operator, left, right, types) : new Comparison(operator, left, right);
    }

    /**
     * {@code &&} and {@code ||}. Only the left side must be able to be a boolean: the right one is checked
     * when it is reached, since {@code false && 1} was false in Aviator, not a failure.
     */
    private CompiledExpr logical(BinaryOperator operator, CompiledExpr left, CompiledExpr right) {
        if (neverBoolean(left)) {
            return failure("'" + operator.symbol + "' needs a boolean, not " + first(left).description());
        }
        if (neverBoolean(right)) {
            this.problems.accept("the right side of '" + operator.symbol + "' is " + first(right).description()
                    + ", which fails whenever the left side does not decide the result");
        }
        return new Logical(operator, left, right);
    }

    private CompiledExpr conditional(CompiledExpr condition, CompiledExpr then, CompiledExpr otherwise) {
        if (neverBoolean(condition)) {
            return failure("the condition of '?:' needs a boolean, not " + first(condition).description());
        }
        return new Conditional(condition, then, otherwise);
    }

    private CompiledExpr reported(CompiledExpr expr) {
        if (expr instanceof Failure failure) {
            this.problems.accept(failure.reason());
        }
        return expr;
    }

    private Failure failure(String reason) {
        this.problems.accept(reason);
        return new Failure(reason);
    }

    private static boolean neverBoolean(CompiledExpr expr) {
        return !expr.types().isEmpty() && !expr.types().contains(ExprType.BOOL);
    }

    /** One of the types of {@code expr}, to name in a message about an operation none of them allows. */
    private static ExprType first(CompiledExpr expr) {
        return expr.types().iterator().next();
    }
}
