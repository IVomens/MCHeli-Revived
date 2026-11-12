package com.ivomens.mcheli.example;

import com.ivomens.mcheli.api.client.HudAnchor;
import com.ivomens.mcheli.api.client.HudWidget;
import com.ivomens.mcheli.api.client.HudWidgetContext;

import java.util.Locale;

/**
 * A HUD widget: the pilot's clock. It shows the world's time of day as a boxed readout in the top right corner of
 * the vehicle HUD, in the HUD's colour, and below it how far through the day the world is as a gauge. Only the
 * pilot (seat 0) sees it. {@link ExampleAddonClient} registers it at client set-up.
 */
public final class ExampleClockWidget implements HudWidget {

    /** Ticks in a Minecraft day; tick 0 is 06:00. */
    private static final long DAY = 24000;
    private static final int WIDTH = 70;
    private static final int GAUGE = 4;

    @Override
    public HudAnchor anchor() {
        return HudAnchor.TOP_RIGHT;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HudWidgetContext.READOUT_HEIGHT + 2 + GAUGE;
    }

    @Override
    public boolean isShown(HudWidgetContext hud) {
        return hud.seat() == 0;
    }

    @Override
    public void render(HudWidgetContext hud) {
        long dayTime = hud.vehicle().entity().level().getDayTime();
        hud.readout(0, 0, WIDTH, "LOCAL", clock(dayTime), hud.color());
        hud.bar(0, HudWidgetContext.READOUT_HEIGHT + 2, WIDTH, GAUGE, Math.floorMod(dayTime, DAY) / (float) DAY,
                hud.withAlpha(hud.color(), 0.6F));
    }

    /** The time of day {@code HH:MM} of a world's day time in ticks. */
    public static String clock(long dayTime) {
        long ticks = Math.floorMod(dayTime + 6000, DAY);
        return String.format(Locale.ROOT, "%02d:%02d", ticks / 1000, ticks % 1000 * 60 / 1000);
    }
}
