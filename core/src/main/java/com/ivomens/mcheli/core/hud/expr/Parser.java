package com.ivomens.mcheli.core.hud.expr;

import com.ivomens.mcheli.core.hud.expr.Token.Kind;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads tokens into an {@link Ast} by precedence climbing:
 * <pre>
 * formula     := conditional END
 * conditional := binary(1) [ '?' conditional ':' conditional ]
 * binary(n)   := binary(n + 1) { operator of level n  binary(n + 1) }    levels in {@link BinaryOperator}
 * unary       := ( '-' | '!' | '~' ) unary | primary
 * primary     := NUMBER | true | false | nil | NAME [ '(' [ conditional { ',' conditional } ] ')' ]
 *              | '(' conditional ')'
 * </pre>
 */
final class Parser {

    private final List<Token> tokens;
    private int next;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    static Ast parse(List<Token> tokens) {
        Parser parser = new Parser(tokens);
        if (parser.peek().kind() == Kind.END) {
            throw new ExprException("the formula is empty");
        }
        Ast formula = parser.conditional();
        Token rest = parser.peek();
        if (rest.kind() != Kind.END) {
            throw unexpected(rest);
        }
        return formula;
    }

    private Ast conditional() {
        Ast condition = binary(BinaryOperator.LOWEST_PRECEDENCE);
        if (!peek().isSymbol("?")) {
            return condition;
        }
        advance();
        Ast then = conditional();
        expect(":");
        return new Ast.Conditional(condition, then, conditional());
    }

    private Ast binary(int precedence) {
        if (precedence > BinaryOperator.HIGHEST_PRECEDENCE) {
            return unary();
        }
        Ast left = binary(precedence + 1);
        for (BinaryOperator operator; (operator = BinaryOperator.of(peek(), precedence)) != null; ) {
            advance();
            left = new Ast.Binary(operator, left, binary(precedence + 1));
        }
        return left;
    }

    private Ast unary() {
        UnaryOperator operator = UnaryOperator.of(peek());
        if (operator == null) {
            return primary();
        }
        advance();
        return new Ast.Unary(operator, unary());
    }

    private Ast primary() {
        Token token = advance();
        return switch (token.kind()) {
            case NUMBER -> number(token);
            case NAME -> name(token);
            case SYMBOL -> {
                if (!token.isSymbol("(")) {
                    throw unexpected(token);
                }
                Ast inner = conditional();
                expect(")");
                yield inner;
            }
            case END -> throw unexpected(token);
        };
    }

    private Ast name(Token token) {
        if (peek().isSymbol("(")) {
            advance();
            return new Ast.Call(token.text(), arguments());
        }
        return switch (token.text()) {
            case "true" -> new Ast.BoolLiteral(true);
            case "false" -> new Ast.BoolLiteral(false);
            case "nil" -> new Ast.NilLiteral();
            default -> new Ast.Name(token.text());
        };
    }

    private List<Ast> arguments() {
        List<Ast> arguments = new ArrayList<>();
        if (peek().isSymbol(")")) {
            advance();
            return arguments;
        }
        arguments.add(conditional());
        while (peek().isSymbol(",")) {
            advance();
            arguments.add(conditional());
        }
        expect(")");
        return arguments;
    }

    /** An integer is a 64-bit long, as in Aviator; a number with a point or an exponent is a double. */
    private static Ast number(Token token) {
        String text = token.text();
        try {
            if (text.startsWith("0x") || text.startsWith("0X")) {
                return new Ast.IntLiteral(Long.parseLong(text.substring(2), 16));
            }
            if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0) {
                return new Ast.DoubleLiteral(Double.parseDouble(text));
            }
            return new Ast.IntLiteral(Long.parseLong(text));
        } catch (NumberFormatException e) {
            throw new ExprException("'" + text + "' does not fit in a 64-bit integer");
        }
    }

    private void expect(String symbol) {
        Token token = advance();
        if (!token.isSymbol(symbol)) {
            throw new ExprException("expected '" + symbol + "' but found " + token.describe() + at(token));
        }
    }

    private Token peek() {
        return this.tokens.get(this.next);
    }

    private Token advance() {
        Token token = peek();
        if (token.kind() != Kind.END) {
            this.next++;
        }
        return token;
    }

    private static ExprException unexpected(Token token) {
        return new ExprException("unexpected " + token.describe() + at(token));
    }

    private static String at(Token token) {
        return " at position " + (token.position() + 1);
    }
}
