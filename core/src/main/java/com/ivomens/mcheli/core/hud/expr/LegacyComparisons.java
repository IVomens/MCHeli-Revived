package com.ivomens.mcheli.core.hud.expr;

import com.ivomens.mcheli.core.hud.expr.Token.Kind;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the comparisons older scripts make with variables that were 0/1 integers and are booleans now
 * ({@link Variable#legacyComparisons}) as the boolean tests they meant:
 * <pre>
 * reloading == 0   →  !reloading        reloading != 0   →  reloading
 * reloading == 1   →  reloading         reloading != 1   →  !reloading
 * </pre>
 * The rewrite works on tokens, where the variable is written directly before the operator and the number is
 * exactly {@code 0} or {@code 1}, which is where the regular expression of earlier versions applied it. So
 * precedence is that of the rewritten text: {@code a+reloading==0} reads {@code a+!reloading}.
 */
final class LegacyComparisons {

    private LegacyComparisons() {
    }

    static List<Token> rewrite(List<Token> tokens, VariableTable variables) {
        List<Token> result = new ArrayList<>(tokens.size());
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            Boolean negated = i + 2 < tokens.size() && isLegacyVariable(token, variables)
                    ? negation(tokens.get(i + 1), tokens.get(i + 2)) : null;
            if (negated == null) {
                result.add(token);
                continue;
            }
            if (negated) {
                result.add(new Token(Kind.SYMBOL, "!", token.position()));
            }
            result.add(token);
            i += 2;
        }
        return result;
    }

    private static boolean isLegacyVariable(Token token, VariableTable variables) {
        return token.kind() == Kind.NAME
                && variables.find(token.text()).map(Variable::legacyComparisons).orElse(false);
    }

    /** Whether {@code variable <operator> <number>} reads as {@code !variable}, or null when it is no such test. */
    private static Boolean negation(Token operator, Token number) {
        if (number.kind() != Kind.NUMBER || !number.text().equals("0") && !number.text().equals("1")) {
            return null;
        }
        boolean comparedWithZero = number.text().equals("0");
        if (operator.isSymbol("==")) {
            return comparedWithZero;
        }
        if (operator.isSymbol("!=")) {
            return !comparedWithZero;
        }
        return null;
    }
}
