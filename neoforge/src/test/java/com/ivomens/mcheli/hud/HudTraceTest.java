package com.ivomens.mcheli.hud;

import com.ivomens.mcheli.core.content.def.hud.HudProgram;
import com.ivomens.mcheli.content.registry.LoadedContent;
import com.ivomens.mcheli.golden.Golden;
import com.ivomens.mcheli.golden.LogCapture;
import com.ivomens.mcheli.golden.content.ContentPipeline;
import com.ivomens.mcheli.hud.client.HudRenderer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HUD trace snapshot (docs/ARCHITECTURE.md Part 2, §5.7): every draw call a scripted HUD makes for a
 * few fixed frames, recorded through {@link RecordingCanvas}. Each frame is drawn twice in a row by the
 * same renderer, because the colour carries over from one frame to the next.
 * <p>
 * Covers the bundled HUDs, the TXT corpus HUD and the test scripts in {@code content/hud}
 * (gates, exit, call, auto-scale switching, a formula that breaks while drawing, and a text whose format
 * does not fit its value).
 */
class HudTraceTest {

    /** A 1280x720 window at GUI scale 2. */
    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int GUI_SCALE = 2;
    /** Draws per frame: the second shows the colour carried over from the first. */
    private static final int REPEATS = 2;

    @TestFactory
    List<DynamicTest> everyHudDrawsAsRecorded() {
        Map<String, LoadedContent<HudProgram, Hud>> huds = HudFormulas.loadHuds();
        List<DynamicTest> tests = new ArrayList<>();
        huds.forEach((name, hud) -> tests.add(DynamicTest.dynamicTest(name, () -> Golden.check(
                "hud/trace/" + name + ".trace", header(name), () -> trace(hud.value())))));
        return tests;
    }

    /** The HUDs of format 2, whose blocks nest; kept apart from the expression corpus, which predates them. */
    @TestFactory
    List<DynamicTest> nestedBlocksDrawAsRecorded() {
        Path pack = ContentPipeline.testPack("hud_v2");
        Map<String, LoadedContent<?, ?>> files = ContentPipeline.loadEach(ContentPipeline.bundledPack(), pack).get(pack);
        List<DynamicTest> tests = new ArrayList<>();
        files.forEach((file, info) -> {
            String name = "hud_v2/" + file.substring(file.lastIndexOf('/') + 1);
            tests.add(DynamicTest.dynamicTest(name, () -> Golden.check("hud/trace/" + name + ".trace", header(name),
                    () -> trace((Hud) info.value()))));
        });
        return tests;
    }

    /** All frames of one HUD, each with a new renderer, so a HUD's trace does not depend on the others. */
    static String trace(Hud hud) {
        Locale previous = Locale.getDefault();
        // String.format follows the player's locale in game; the golden is written with a fixed one
        Locale.setDefault(Locale.ROOT);
        try {
            StringBuilder text = new StringBuilder();
            for (HudTraceFrame frame : HudTraceFrame.all()) {
                RecordingCanvas canvas = new RecordingCanvas(WIDTH, HEIGHT, GUI_SCALE);
                HudRenderer renderer = new HudRenderer(name -> HudManager.get(name).orElse(null));
                for (int repeat = 1; repeat <= REPEATS; repeat++) {
                    canvas.note("-- frame " + frame.name() + " #" + repeat + " (colour at start "
                            + String.format("#%08X", renderer.session().color()) + ")");
                    try (LogCapture log = LogCapture.start()) {
                        renderer.render(hud, frame.toFrame(), canvas, frame.autoScaleRequested());
                        log.lines().forEach(line -> canvas.note("log: " + line));
                    }
                }
                canvas.calls().forEach(call -> text.append(call).append('\n'));
            }
            return text.toString();
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static String header(String name) {
        return "{\"schema\":1,\"hud\":\"" + name + "\",\"canvas\":\"" + WIDTH + "x" + HEIGHT + " at gui scale "
                + GUI_SCALE + "\",\"locale\":\"ROOT\",\"commit\":\"" + Golden.commit() + "\"}";
    }
}
