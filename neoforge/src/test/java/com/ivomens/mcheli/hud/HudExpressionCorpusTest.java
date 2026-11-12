package com.ivomens.mcheli.hud;

import com.ivomens.mcheli.core.hud.HudVariable;
import com.ivomens.mcheli.golden.Golden;
import com.ivomens.mcheli.core.hud.expr.Formula;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HUD expression differential corpus (docs/ARCHITECTURE.md Part 2, §5.6): every formula of the bundled HUDs
 * and the HUD test scripts, evaluated by the formula compiler over a fixed grid of variable values. The golden
 * was recorded with Aviator, the engine the formulas were written for, and the compiler must give the same
 * result for every formula and frame, failures included.
 * <p>
 * Each entry is the text Aviator was given, where the legacy boolean comparisons were already rewritten as
 * text; the compiler reads the formula as the file wrote it ({@code is_uav==0}) and must agree.
 * <p>
 * A text that is no formula at all is the one case the two describe differently: the compiler reports it
 * when the HUD loads, Aviator when it compiled it on first use. For those the test checks that Aviator
 * refused it too, and writes Aviator's words, which the golden holds.
 * <p>
 * The frames are listed in {@code hud/expression-frames.txt}; {@code hud/expressions.txt} has one entry per
 * formula with its result in each frame.
 */
class HudExpressionCorpusTest {

    static final int FRAMES = 32;

    @Test
    void everyFormulaEvaluatesAsRecorded() {
        Map<String, ? extends Set<String>> formulas = HudFormulas.collect(HudFormulas.loadHuds().values());
        List<Map<String, Object>> frames = HudVariables.frames(FRAMES);

        Golden.check("hud/expression-frames.txt", header("variable values of each frame"),
                () -> HudVariables.describe(frames));
        Golden.check("hud/expressions.txt", header(formulas.size() + " formulas over " + FRAMES + " frames"),
                () -> evaluateAll(formulas, frames));
    }

    private static String evaluateAll(Map<String, ? extends Set<String>> formulas, List<Map<String, Object>> frames) {
        StringBuilder text = new StringBuilder();
        formulas.forEach((aviatorText, compilerTexts) -> {
            String results = null;
            for (String source : compilerTexts) {
                String these = results(source, frames);
                assertTrue(results == null || results.equals(these),
                        () -> "the spellings of '" + aviatorText + "' disagree: " + compilerTexts);
                results = these;
            }
            text.append(aviatorText).append('\n').append("  ").append(results).append('\n');
        });
        return text.toString();
    }

    /** The result in every frame, or a single value when every frame gives the same. */
    private static String results(String source, List<Map<String, Object>> frames) {
        Formula formula = Formula.compile(source, HudVariable.TABLE, problem -> {
        });
        List<String> results = new ArrayList<>();
        for (Map<String, Object> frame : frames) {
            results.add(evaluate(formula, frame));
        }
        if (results.stream().distinct().count() == 1) {
            return "every frame: " + results.getFirst();
        }
        return String.join(" | ", results);
    }

    /** The compiler's result, or for a text that is no formula, Aviator's refusal. */
    static String evaluate(Formula formula, Map<String, Object> frame) {
        if (!formula.readable()) {
            assertTrue(AviatorOracle.rejects(formula.source()),
                    () -> "the compiler cannot read '" + formula.source() + "', which Aviator accepts");
            return AviatorOracle.evaluate(formula.source(), frame);
        }
        try {
            return Double.toString(formula.evaluate(HudFrames.of(frame)));
        } catch (RuntimeException e) {
            return AviatorOracle.failure(e);
        }
    }

    @Test
    void theVariablesHaveTheTypesTheGridGivesThem() {
        Map<String, Object> frame = HudVariables.frames(1).getFirst();
        assertEquals(HudVariable.values().length, frame.size());
        HudFrames.of(frame);
    }

    private static String header(String what) {
        return "{\"schema\":1,\"what\":\"" + what + "\",\"seed\":" + HudVariables.SEED + ",\"commit\":\""
                + Golden.commit() + "\"}";
    }
}
