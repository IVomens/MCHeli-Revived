package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.util.Ids;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Analogue fuel gauge: dial background with a needle sweeping +-65 degrees. */
public class WidgetGauge {

    private static final ResourceLocation GAUGE = Ids.of("textures/gui/uigauge.png");
    private static final float MAX_NEEDLE_ANGLE = 65f;
    // Regions of the dial and the needle in uigauge.png (256x256).
    private static final int BG_U = 0, BG_V = 8;
    private static final int BASE_BG_W = 211;
    private static final int BASE_BG_H = 110;
    private static final int PTR_U = 220, PTR_V = 9;
    private static final int BASE_PTR_W = 32;
    private static final int BASE_PTR_H = 105;

    private float scale = 1.0f;

    public static WidgetGauge make(float scale) {
        return new WidgetGauge().setScale(scale);
    }

    public WidgetGauge setScale(float scale) {
        this.scale = scale;
        return this;
    }

    public int getWidth() {
        return (int) (BASE_BG_W * this.scale);
    }

    public int getHeight() {
        return (int) (BASE_BG_H * this.scale);
    }

    /** @param value fill ratio in [0, 1] */
    public void render(GuiGraphics g, int x, int y, float value) {
        int w = getWidth(), h = getHeight();
        g.blit(GAUGE, x, y, w, h, BG_U, BG_V, BASE_BG_W, BASE_BG_H, 256, 256);

        float angle = (Math.max(0.0F, Math.min(1.0F, value)) * 2.0f - 1.0f) * MAX_NEEDLE_ANGLE;

        // The needle turns around a pivot near its foot, which sits at the bottom centre of the dial.
        float needleWidth = BASE_PTR_W * scale + 2;
        float needleHeight = BASE_PTR_H * scale + 1;
        float pivotFromBottom = (9 * scale) + 2;
        float pivotX = (BASE_BG_W * scale) / 2f;
        float pivotY = (BASE_BG_H * scale) - pivotFromBottom;

        g.pose().pushPose();
        g.pose().translate(x + pivotX, y + pivotY, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        g.pose().translate(-needleWidth / 2f, -(needleHeight - pivotFromBottom) + 2, 0);
        g.pose().scale(needleWidth / BASE_PTR_W, needleHeight / BASE_PTR_H, 1.0F);
        g.blit(GAUGE, 0, 0, BASE_PTR_W, BASE_PTR_H, PTR_U, PTR_V, BASE_PTR_W, BASE_PTR_H, 256, 256);
        g.pose().popPose();
    }
}
