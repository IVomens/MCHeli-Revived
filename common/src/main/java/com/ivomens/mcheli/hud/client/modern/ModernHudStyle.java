package com.ivomens.mcheli.hud.client.modern;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.aircraft.client.HudStyle;
import net.minecraft.world.entity.player.Player;

/** The {@link ModernHud} as a HUD style, in the colour and scale of the client config. */
public final class ModernHudStyle implements HudStyle {

    @Override
    public void draw(HudCanvas canvas, AircraftEntity vehicle, Player viewer, int hudSeat, float partialTicks,
                     boolean autoScaled) {
        // the overlays may have scaled the canvas; the modern HUD lays itself out from the raw screen size
        canvas.pushPose();
        canvas.resetTransform();
        try {
            ModernHud.render(canvas, partialTicks, viewer, vehicle,
                    new ModernHud.Style(McheliConfig.hudColor(), (float) McheliConfig.hud().scale()));
        } finally {
            canvas.popPose();
        }
    }
}
