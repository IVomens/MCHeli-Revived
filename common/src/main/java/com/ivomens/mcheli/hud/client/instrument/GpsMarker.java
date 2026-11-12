package com.ivomens.mcheli.hud.client.instrument;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.client.render.ScreenProjection;
import com.ivomens.mcheli.combat.weapon.GPSPosition;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Marks the active GPS target with a screen-space icon and its distance. */
public final class GpsMarker implements Instrument {

    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(Ids.MOD_ID,
            "textures/gps_position.png");
    private static final int ICON_SIZE = 24;
    private static final double LOCK_ANGLE_DEG = 1.5;
    private static final int LOCKED_COLOR = 0xFF0000;
    private static final int TRACKED_COLOR = 0x00FF00;
    private static final HudCanvas.Tint LOCKED_TINT = new HudCanvas.Tint(1.0F, 0.0F, 0.0F, 1.0F);

    private final ScreenProjection projection;
    private final double[] projected = new double[3];

    /** @param projection where the marker goes on screen */
    public GpsMarker(ScreenProjection projection) {
        this.projection = projection;
    }

    @Override
    public void draw(HudCanvas canvas, float partialTicks, Player player, AircraftEntity ac) {
        GPSPosition gps = GPSPosition.currentClientGPSPosition;
        if (gps == null || !gps.isActive()) return;

        Vec3 eye = player.getEyePosition(partialTicks);
        double dx = gps.x() - eye.x();
        double dy = gps.y() - eye.y();
        double dz = gps.z() - eye.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-4) return;

        Vec3 look = player.getViewVector(partialTicks);
        double cos = Math.clamp((dx * look.x + dy * look.y + dz * look.z) / length, -1.0, 1.0);
        double angleDeg = Math.toDegrees(Math.acos(cos));
        boolean locked = angleDeg <= LOCK_ANGLE_DEG;

        if (!this.projection.project(gps.x(), gps.y() + 0.2, gps.z(), this.projected)) return;

        canvas.pushPose();
        canvas.translate(this.projected[0], this.projected[1]);
        canvas.rotate(ac.roll());
        float half = ICON_SIZE * 0.5f;
        HudCanvas.Tint tint = locked ? LOCKED_TINT : new HudCanvas.Tint(0.0F, 1.0F, 0.0F, alphaForAngle(angleDeg));
        canvas.texture(ICON, -half, -half, ICON_SIZE, ICON_SIZE, tint);
        String text = String.format("[GPS %.1fm]", Math.sqrt(player.distanceToSqr(gps.x(), gps.y(), gps.z())));
        canvas.text(text, -canvas.textWidth(text) / 2, (int) (half + 8.0f), locked ? LOCKED_COLOR : TRACKED_COLOR,
                false);
        canvas.popPose();
    }

    /** The marker fades the further it is from the view direction. */
    private static float alphaForAngle(double angleDeg) {
        if (angleDeg <= 3.0) return 1.0f;
        if (angleDeg <= 6.0) return 0.8f;
        if (angleDeg <= 9.0) return 0.6f;
        return 0.4f;
    }
}
