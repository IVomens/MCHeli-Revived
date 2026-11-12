package com.ivomens.mcheli.hud;

import com.ivomens.mcheli.core.hud.HudVariable;
import com.ivomens.mcheli.golden.content.LegacyHudView;
import com.ivomens.mcheli.core.hud.expr.ExprType;
import com.ivomens.mcheli.core.hud.expr.Formula;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The formula compiler against Aviator on inputs no golden fixes: the HUD corpus over random frames, and
 * random formulas over the whole grammar. Both must give the same value, or both fail. Failure messages are
 * not compared: a HUD only cares that the formula stops it. Aviator gets each formula with the legacy boolean
 * comparisons rewritten as text, the way the game handed formulas to it.
 * <p>
 * A random formula Aviator refuses to compile is skipped: no pack can rely on it, and its parser refuses some
 * arrangements the grammar allows, such as a shift right after a comparison ({@code a>b>>>1}).
 */
class FormulaDifferentialTest {

    private static final long SEED = 20261107L;
    private static final int CORPUS_FRAMES = 400;
    private static final int RANDOM_FORMULAS = 4000;
    private static final int FRAMES_PER_RANDOM_FORMULA = 4;

    private static final long[] INTS = {0, 1, -1, 2, 3, 7, -7, 10, 90, 255, 13000, Long.MAX_VALUE, Long.MIN_VALUE};
    private static final double[] DOUBLES = {0.0, -0.0, 0.5, -0.5, 1.0, 2.5, -45.25, 179.9, 1e300, -1e-300,
            0.05000000074505806, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
    private static final List<String> SYMBOLS = List.of("+", "-", "*", "/", "%", "<<", ">>", ">>>", "&", "|", "^",
            "<", "<=", ">", ">=", "==", "!=", "&&", "||");
    private static final List<String> FUNCTIONS = List.of("math.abs", "math.sqrt", "math.sin", "math.cos",
            "math.tan", "math.asin", "math.acos", "math.atan", "math.log", "math.log10", "math.floor", "math.ceil",
            "math.round");

    @Test
    void corpusOverRandomFrames() {
        Set<String> formulas = new TreeSet<>();
        HudFormulas.collect(HudFormulas.loadHuds().values()).values().forEach(formulas::addAll);
        Random random = new Random(SEED);
        List<Map<String, Object>> frames = new ArrayList<>();
        for (int i = 0; i < CORPUS_FRAMES; i++) {
            frames.add(randomFrame(random));
        }
        for (String source : formulas) {
            Formula formula = compile(source);
            for (Map<String, Object> frame : frames) {
                compare(source, formula, frame);
            }
        }
    }

    @Test
    void randomFormulasOverRandomFrames() {
        Random random = new Random(SEED + 1);
        int compared = 0;
        for (int i = 0; i < RANDOM_FORMULAS; i++) {
            String source = new Generator(random).expression(4);
            Formula formula = compile(source);
            if (!formula.readable()) {
                fail("the compiler cannot read a formula of its own grammar: " + source);
            }
            for (int frame = 0; frame < FRAMES_PER_RANDOM_FORMULA; frame++) {
                compared += compare(source, formula, randomFrame(random)) ? 1 : 0;
            }
        }
        assertTrue(compared > RANDOM_FORMULAS * FRAMES_PER_RANDOM_FORMULA / 2,
                "Aviator compiled too few of the random formulas to compare: " + compared);
    }

    private static Formula compile(String source) {
        return Formula.compile(source, HudVariable.TABLE, problem -> {
        });
    }

    /** Whether Aviator compiled the formula, so the two results could be compared. */
    private static boolean compare(String source, Formula formula, Map<String, Object> frame) {
        String aviatorText = LegacyHudView.aviatorText(source);
        if (AviatorOracle.rejects(aviatorText)) {
            return false;
        }
        String expected = AviatorOracle.evaluate(aviatorText, frame);
        String actual = HudExpressionCorpusTest.evaluate(formula, frame);
        if (!expected.startsWith("[") || !actual.startsWith("[")) {
            assertEquals(expected, actual, () -> "'" + source + "' in frame " + frame);
        }
        return true;
    }

    /** A value for every variable, of its type, drawn from values at the edges of each type. */
    private static Map<String, Object> randomFrame(Random random) {
        Map<String, Object> frame = new LinkedHashMap<>();
        for (HudVariable variable : HudVariable.values()) {
            frame.put(variable.scriptName(), switch (variable.type()) {
                case INT -> INTS[random.nextInt(INTS.length)];
                case DOUBLE -> random.nextInt(4) == 0 ? random.nextDouble() * 400 - 200
                        : DOUBLES[random.nextInt(DOUBLES.length)];
                case BOOL -> random.nextBoolean();
                case NIL -> throw new IllegalStateException();
            });
        }
        return frame;
    }

    /** Random formulas in the compiler's grammar, mostly parenthesised, sometimes relying on precedence. */
    private record Generator(Random random) {

        String expression(int depth) {
            if (depth == 0 || this.random.nextInt(5) == 0) {
                return leaf();
            }
            return switch (this.random.nextInt(10)) {
                case 0 -> pick(List.of("-", "!", "~")) + operand(depth - 1);
                case 1 -> operand(depth - 1) + "?" + operand(depth - 1) + ":" + operand(depth - 1);
                case 2 -> pick(FUNCTIONS) + "(" + expression(depth - 1) + ")";
                case 3 -> "math.pow(" + expression(depth - 1) + "," + expression(depth - 1) + ")";
                case 4 -> pick(List.of("max", "min")) + "(" + expression(depth - 1) + "," + expression(depth - 1)
                        + (this.random.nextBoolean() ? "," + expression(depth - 1) : "") + ")";
                case 5 -> leaf() + pick(SYMBOLS) + leaf() + pick(SYMBOLS) + leaf();
                default -> operand(depth - 1) + pick(SYMBOLS) + operand(depth - 1);
            };
        }

        private String operand(int depth) {
            String expression = expression(depth);
            return expression.matches("[a-z_0-9.]+") ? expression : "(" + expression + ")";
        }

        private String leaf() {
            return switch (this.random.nextInt(9)) {
                case 0 -> Long.toString(INTS[this.random.nextInt(INTS.length - 2)]).replace("-", "");
                case 1 -> pick(List.of("0", "1", "2", "10", "0x1f", "0xff28d448", "007"));
                case 2 -> pick(List.of("0.5", "2.25", ".5", "5.", "1e3", "0.0", "1.5e-3"));
                case 3 -> pick(List.of("true", "false", "nil", "no_such_variable"));
                default -> variable(ExprType.values()[this.random.nextInt(3)]);
            };
        }

        private String variable(ExprType type) {
            List<HudVariable> ofType = Arrays.stream(HudVariable.values())
                    .filter(variable -> variable.type() == type).toList();
            HudVariable variable = ofType.get(this.random.nextInt(ofType.size()));
            // the variables older scripts compared with 0 and 1, the way they did
            if (variable.name().equals("RELOADING") && this.random.nextBoolean()) {
                return "reloading" + pick(List.of("==0", "!=0", "==1", "!=1"));
            }
            return variable.scriptName();
        }

        private String pick(List<String> choices) {
            return choices.get(this.random.nextInt(choices.size()));
        }
    }
}
