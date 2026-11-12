package com.ivomens.mcheli.core.hud.expr;

import com.ivomens.mcheli.core.hud.expr.Token.Kind;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a formula into tokens. Numbers are decimal ({@code 12}, {@code 0.5}, {@code .5}, {@code 5.},
 * {@code 1e3}, {@code 1.5e-3}) or hexadecimal ({@code 0xff28d448}); names may contain dots ({@code math.sin}); symbols are
 * matched longest first, so {@code >>>} is one token. Whitespace separates tokens and is otherwise ignored.
 */
final class Lexer {

    /** Every symbol, longest first. */
    private static final List<String> SYMBOLS = List.of(">>>", "<<", ">>", "<=", ">=", "==", "!=", "&&", "||",
            "+", "-", "*", "/", "%", "!", "~", "<", ">", "&", "|", "^", "?", ":", "(", ")", ",");

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int position;

    private Lexer(String source) {
        this.source = source;
    }

    /** The tokens of {@code source}, ending with an {@link Kind#END} token. */
    static List<Token> tokens(String source) {
        Lexer lexer = new Lexer(source);
        lexer.run();
        return lexer.tokens;
    }

    private void run() {
        while (this.position < this.source.length()) {
            char c = this.source.charAt(this.position);
            if (Character.isWhitespace(c)) {
                this.position++;
            } else if (isDigit(c) || c == '.' && isDigit(peek(1))) {
                number();
            } else if (isNameStart(c)) {
                name();
            } else {
                symbol();
            }
        }
        this.tokens.add(new Token(Kind.END, "", this.position));
    }

    private void number() {
        int start = this.position;
        if (this.source.startsWith("0x", start) || this.source.startsWith("0X", start)) {
            this.position += 2;
            skipWhile(Lexer::isHexDigit);
            if (this.position == start + 2) {
                throw error("'" + this.source.substring(start, this.position) + "' needs hexadecimal digits", start);
            }
        } else {
            skipWhile(Lexer::isDigit);
            if (peek(0) == '.') {
                this.position++;
                skipWhile(Lexer::isDigit);
            }
            if (peek(0) == 'e' || peek(0) == 'E') {
                this.position++;
                // Aviator reads a minus here but not a plus: 1e-3 is 0.001, and 1e+2 was 1 + 2
                if (peek(0) == '-') {
                    this.position++;
                }
                if (!isDigit(peek(0))) {
                    throw error("the exponent of '" + this.source.substring(start, this.position)
                            + "' needs digits", start);
                }
                skipWhile(Lexer::isDigit);
            }
        }
        this.tokens.add(new Token(Kind.NUMBER, this.source.substring(start, this.position), start));
    }

    /** A name, with dots between its parts: {@code altitude}, {@code math.sin}. */
    private void name() {
        int start = this.position;
        skipWhile(Lexer::isNamePart);
        while (peek(0) == '.' && isNameStart(peek(1))) {
            this.position++;
            skipWhile(Lexer::isNamePart);
        }
        this.tokens.add(new Token(Kind.NAME, this.source.substring(start, this.position), start));
    }

    private void symbol() {
        for (String symbol : SYMBOLS) {
            if (this.source.startsWith(symbol, this.position)) {
                this.tokens.add(new Token(Kind.SYMBOL, symbol, this.position));
                this.position += symbol.length();
                return;
            }
        }
        throw error("unexpected character '" + this.source.charAt(this.position) + "'", this.position);
    }

    private void skipWhile(CharTest test) {
        while (this.position < this.source.length() && test.matches(this.source.charAt(this.position))) {
            this.position++;
        }
    }

    /** The character {@code offset} after the current one, or 0 past the end. */
    private char peek(int offset) {
        int index = this.position + offset;
        return index < this.source.length() ? this.source.charAt(index) : 0;
    }

    private static ExprException error(String message, int position) {
        return new ExprException(message + " at position " + (position + 1));
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHexDigit(char c) {
        return isDigit(c) || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }

    private static boolean isNameStart(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_';
    }

    private static boolean isNamePart(char c) {
        return isNameStart(c) || isDigit(c);
    }

    @FunctionalInterface
    private interface CharTest {
        boolean matches(char c);
    }
}
