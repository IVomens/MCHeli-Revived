package com.ivomens.mcheli.hud.client.widget;

import com.ivomens.mcheli.api.client.HudAnchor;
import com.ivomens.mcheli.api.client.HudWidget;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.world.entity.player.Player;

/**
 * Draws the addon HUD widgets on top of the seat HUD: lays the widgets that show out in the slots of their
 * anchors ({@link WidgetSlots}) and lets each draw in its box, in the modern HUD's units and scale.
 * <p>
 * A widget that throws is logged once and not drawn again until it is registered anew; the rest still draw. With
 * no widget registered {@link #draw} returns at once. The layer belongs to one viewer and allocates nothing per
 * frame itself.
 */
public final class HudWidgetLayer {

    private static final HudAnchor[] ANCHORS = HudAnchor.values();

    private final HudWidgetRegistry registry;
    private final WidgetFrame frame = new WidgetFrame();

    public HudWidgetLayer(HudWidgetRegistry registry) {
        this.registry = registry;
    }

    /**
     * Draws one frame of widgets.
     *
     * @param seat   the viewer's seat, or -1 when he is in none
     * @param layout the HUD drawn below, which decides where the slots are
     * @param color  the HUD colour, ARGB
     * @param scale  the HUD scale setting
     */
    public void draw(HudCanvas canvas, Vehicle vehicle, Player viewer, int seat, float partialTick, HudLayout layout,
                     int color, float scale) {
        HudWidgetRegistry.Entry[] entries = this.registry.entries();
        if (entries.length == 0) return;
        this.frame.begin(canvas, vehicle, viewer, seat, partialTick, layout, color, scale);
        // the overlays may have scaled the canvas; widgets lay themselves out from the raw screen size, as the
        // modern HUD does
        canvas.pushPose();
        canvas.resetTransform();
        this.frame.pen().begin(scale);
        try {
            for (HudAnchor anchor : ANCHORS) {
                drawSlot(canvas, entries, anchor, WidgetSlots.of(layout, anchor));
            }
        } finally {
            this.frame.pen().end();
            canvas.popPose();
            this.frame.end();
        }
    }

    /** Stacks the widgets of one anchor in its slot, skipping those that do not fit. */
    private void drawSlot(HudCanvas canvas, HudWidgetRegistry.Entry[] entries, HudAnchor anchor,
                          WidgetSlots.Slot slot) {
        float used = 0;
        for (HudWidgetRegistry.Entry entry : entries) {
            if (entry.isBroken()) continue;
            HudWidget widget = entry.widget();
            int width;
            int height;
            try {
                if (widget.anchor() != anchor) continue;
                width = widget.width();
                height = widget.height();
                if (width <= 0 || height <= 0 || width > slot.width() || used + height > slot.height()) continue;
                this.frame.place(width, height, false);
                if (!widget.isShown(this.frame)) continue;
            } catch (RuntimeException e) {
                fail(entry, e);
                continue;
            }
            float x = switch (slot.align()) {
                case LEFT -> slot.x();
                case RIGHT -> slot.x() + slot.width() - width;
                case CENTER -> slot.x() + (slot.width() - width) / 2F;
            };
            float y = slot.upward() ? slot.y() + slot.height() - used - height : slot.y() + used;
            used += height + WidgetSlots.GAP;
            render(canvas, entry, x, y, width, height);
        }
    }

    private void render(HudCanvas canvas, HudWidgetRegistry.Entry entry, float x, float y, int width, int height) {
        this.frame.place(width, height, true);
        canvas.pushPose();
        try {
            canvas.translate(x, y);
            entry.widget().render(this.frame);
        } catch (RuntimeException e) {
            fail(entry, e);
        } finally {
            this.frame.place(width, height, false);
            canvas.popPose();
        }
    }

    private static void fail(HudWidgetRegistry.Entry entry, RuntimeException e) {
        entry.markBroken();
        ModLogger.error("HUD widget " + entry.id() + " failed; it is not drawn again until it is registered anew",
                e);
    }
}
