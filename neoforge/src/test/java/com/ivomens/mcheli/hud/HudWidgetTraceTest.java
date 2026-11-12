package com.ivomens.mcheli.hud;

import com.ivomens.mcheli.api.client.HudAnchor;
import com.ivomens.mcheli.api.client.HudWidget;
import com.ivomens.mcheli.api.client.HudWidgetContext;
import com.ivomens.mcheli.api.event.Subscription;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.golden.Golden;
import com.ivomens.mcheli.golden.LogCapture;
import com.ivomens.mcheli.hud.client.widget.HudLayout;
import com.ivomens.mcheli.hud.client.widget.HudWidgetLayer;
import com.ivomens.mcheli.hud.client.widget.HudWidgetRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HUD trace of the addon widget layer: a set of sample widgets, drawn through {@link RecordingCanvas} in each HUD
 * layout, against a golden file. Covers every anchor, stacking up and down, a widget too large for its slot, one
 * hidden by {@code isShown}, one that throws (logged once, then skipped), and every pen of the context.
 */
class HudWidgetTraceTest {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int GUI_SCALE = 2;
    private static final int COLOR = 0xFF7FFFD4;

    @Test
    void sampleWidgetsDrawAsRecorded() {
        Golden.check("hud/widget/sample.trace", "{\"schema\":1,\"widgets\":\"sample\",\"canvas\":\"" + WIDTH + "x"
                + HEIGHT + " at gui scale " + GUI_SCALE + "\",\"commit\":\"" + Golden.commit() + "\"}",
                HudWidgetTraceTest::trace);
    }

    /** With nothing registered the layer draws nothing at all, not even a transform. */
    @Test
    void noWidgetsDrawNothing() {
        RecordingCanvas canvas = new RecordingCanvas(WIDTH, HEIGHT, GUI_SCALE);
        new HudWidgetLayer(new HudWidgetRegistry()).draw(canvas, helicopter(), null, 0, 0.5F, HudLayout.FLIGHT,
                COLOR, 1.0F);
        assertEquals(List.of(), canvas.calls());
    }

    @Test
    void unsubscribedWidgetsAreGoneAndIdsAreUnique() {
        HudWidgetRegistry registry = new HudWidgetRegistry();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "one");
        Subscription subscription = registry.register(id, new Sample(HudAnchor.TOP_LEFT, 40, 12, 0, "ONE"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(id, new Sample(HudAnchor.TOP_LEFT, 40, 12, 0, "TWO")));
        subscription.unsubscribe();
        subscription.unsubscribe();
        assertTrue(registry.isEmpty());
        registry.register(id, new Sample(HudAnchor.TOP_LEFT, 40, 12, 0, "THREE"));
        assertTrue(!registry.isEmpty());
    }

    private static String trace() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.ROOT);
        try {
            HudWidgetRegistry registry = new HudWidgetRegistry();
            register(registry);
            HudWidgetLayer layer = new HudWidgetLayer(registry);
            StringBuilder text = new StringBuilder();
            for (HudLayout layout : HudLayout.values()) {
                for (float scale : new float[]{1.0F, 1.5F}) {
                    RecordingCanvas canvas = new RecordingCanvas(WIDTH, HEIGHT, GUI_SCALE);
                    canvas.note("-- layout " + layout + " scale " + scale);
                    try (LogCapture log = LogCapture.start()) {
                        layer.draw(canvas, helicopter(), null, 0, 0.5F, layout, COLOR, scale);
                        log.lines().forEach(line -> canvas.note("log: " + line.lines().findFirst().orElse("")));
                    }
                    canvas.calls().forEach(call -> text.append(call).append('\n'));
                }
            }
            return text.toString();
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static void register(HudWidgetRegistry registry) {
        List<HudWidget> widgets = new ArrayList<>();
        for (HudAnchor anchor : HudAnchor.values()) {
            widgets.add(new Sample(anchor, 60, HudWidgetContext.READOUT_HEIGHT, 0, anchor.name()));
            widgets.add(new Sample(anchor, 40, HudWidgetContext.LINE_HEIGHT, 1, "2ND"));
        }
        widgets.add(new Sample(HudAnchor.TOP_LEFT, 400, 10, 2, "TOO WIDE"));
        widgets.add(new Sample(HudAnchor.LEFT, 30, 10, -1, "TANK ONLY") {
            @Override
            public boolean isShown(HudWidgetContext context) {
                return context.kind() == VehicleKind.TANK;
            }
        });
        widgets.add(new Sample(HudAnchor.RIGHT, 30, 10, -1, "BROKEN") {
            @Override
            public void render(HudWidgetContext context) {
                context.text("BEFORE", 0, 0, context.color());
                throw new IllegalStateException("broken on purpose");
            }
        });
        for (int i = 0; i < widgets.size(); i++) {
            registry.register(ResourceLocation.fromNamespaceAndPath("test", "widget_" + (char) ('a' + i)),
                    widgets.get(i));
        }
    }

    private static Vehicle helicopter() {
        return (Vehicle) Proxy.newProxyInstance(Vehicle.class.getClassLoader(), new Class<?>[]{Vehicle.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "kind" -> VehicleKind.HELICOPTER;
                    case "throttle" -> 0.75;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** Uses every pen: a readout when it is tall enough, otherwise a line of text, a gauge and a box. */
    private static class Sample implements HudWidget {

        private final HudAnchor anchor;
        private final int width;
        private final int height;
        private final int order;
        private final String name;

        Sample(HudAnchor anchor, int width, int height, int order, String name) {
            this.anchor = anchor;
            this.width = width;
            this.height = height;
            this.order = order;
            this.name = name;
        }

        @Override
        public HudAnchor anchor() {
            return this.anchor;
        }

        @Override
        public int width() {
            return this.width;
        }

        @Override
        public int height() {
            return this.height;
        }

        @Override
        public int order() {
            return this.order;
        }

        @Override
        public void render(HudWidgetContext hud) {
            if (hud.height() >= HudWidgetContext.READOUT_HEIGHT) {
                hud.readout(0, 0, hud.width(), this.name, String.format("%.0f%%",
                        hud.vehicle().throttle() * 100), hud.color());
                return;
            }
            hud.label(this.name, 0, 0, hud.withAlpha(hud.color(), 0.5F));
            hud.textRight(Float.toString(hud.textWidth("ab") + hud.labelWidth("abc")), hud.width(), 0,
                    hud.cautionColor());
            hud.textCentered(hud.seat() + "/" + hud.partialTick() + "/" + hud.modernHud(), hud.width() / 2F, 2,
                    hud.warningColor());
            hud.bar(0, hud.height() - 3, hud.width(), 3, 0.4F, hud.color());
            hud.line(0, 0, hud.width(), 0, hud.color());
            hud.box(0, 0, hud.width(), hud.height(), hud.color());
            hud.fill(1, 1, 2, 2, hud.withAlpha(hud.color(), hud.opacity() / 4));
        }
    }
}
