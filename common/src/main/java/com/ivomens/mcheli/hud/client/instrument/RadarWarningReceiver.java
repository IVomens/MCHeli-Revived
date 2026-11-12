package com.ivomens.mcheli.hud.client.instrument;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.hud.client.ThreatLabels;
import com.ivomens.mcheli.vehicle.sensor.IncomingMissiles;
import com.ivomens.mcheli.vehicle.sensor.client.IncomingMissileReader;
import com.ivomens.mcheli.vehicle.sensor.RWRThreatEvent;
import com.ivomens.mcheli.vehicle.sensor.client.RWRThreatClientTracker;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Radar warning receiver: a ring showing who is painting the vehicle with radar, by bearing and
 * signal strength. Missile threats blink red; a digital set tags each with its seeker ("IR", "RDR"), and
 * below the ring the countermeasure to drop blinks when the vehicle carries one that would help.
 */
public final class RadarWarningReceiver implements Instrument {

    private static final ResourceLocation RWR = texture("rwr");
    private static final ResourceLocation RWR_HELI = texture("rwr_heli");
    private static final ResourceLocation RWR_TANK = texture("rwr_tank");
    private static final ResourceLocation RWR_FAC = texture("rwr_fac");

    /** Layout sizes are given for a 520 pixel high screen and scaled to the actual height. */
    private static final double DESIGN_SCREEN_HEIGHT = 520;
    private static final Layout AIR_LAYOUT = new Layout(180, 100, 280, 50.0, 4096.0);
    private static final Layout GROUND_LAYOUT = new Layout(160, 220, 370, 15.0, 800.0);
    private static final int MISSILE_BLINK_TICKS = 3;
    private static final float LABEL_SCALE = 0.9F;
    private static final float TAG_SCALE = 0.75F;
    /** Height of a label line on screen, for the seeker tag drawn under it. */
    private static final double LABEL_LINE = 8.0;
    private static final int CUE_BACKING = 0xB0000000;

    @Override
    public void draw(HudCanvas canvas, float partialTicks, Player player, AircraftEntity ac) {
        VehicleSpec info = ac.spec();
        if (info == null || !info.file().features().rwr().enabled()) return;

        ResourceLocation texture;
        Layout layout = AIR_LAYOUT;
        if (ac instanceof PlaneEntity) {
            texture = info.file().physics().canFloat() ? RWR_FAC : RWR;
            if (info.file().physics().canFloat()) layout = GROUND_LAYOUT;
        } else if (ac instanceof HelicopterEntity) {
            texture = RWR_HELI;
        } else if (ac instanceof TankEntity) {
            texture = RWR_TANK;
            layout = GROUND_LAYOUT;
        } else if (ac instanceof GroundVehicleEntity) {
            texture = RWR_FAC;
            layout = GROUND_LAYOUT;
        } else {
            texture = RWR;
        }

        double scale = canvas.height() / DESIGN_SCREEN_HEIGHT;
        double centerX = layout.centerX * scale;
        double centerY = layout.centerY * scale;
        double halfSize = layout.size * scale / 2.0;
        canvas.texture(texture, centerX - halfSize, centerY - halfSize, halfSize * 2, halfSize * 2,
                HudCanvas.Tint.WHITE);
        drawThreats(canvas, ac, info, centerX, centerY, halfSize, layout);
        drawCue(canvas, ac, centerX, centerY + halfSize + 2.0);
    }

    /** The countermeasure to drop, blinking under the ring while a missile it fools is inbound. */
    private static void drawCue(HudCanvas canvas, AircraftEntity ac, double x, double y) {
        IncomingMissiles incoming = IncomingMissileReader.read(ac);
        boolean flares = ac.countermeasures().haveFlare();
        boolean chaff = ac.countermeasures().haveChaff();
        String cue = ThreatLabels.cue(incoming, flares, chaff);
        if (cue.isEmpty() || !ThreatLabels.cueLit(ac.level().getGameTime())) {
            return;
        }
        double halfWidth = canvas.textWidth(cue) * LABEL_SCALE / 2.0 + 2.0;
        canvas.fillRect(x - halfWidth, y - 1.0, x + halfWidth, y + LABEL_LINE + 1.0, CUE_BACKING);
        drawLabel(canvas, cue, x, y, ThreatLabels.cueColor(incoming, flares, chaff), LABEL_SCALE);
    }

    private static void drawThreats(HudCanvas canvas, AircraftEntity ac, VehicleSpec info, double centerX,
                                    double centerY, double halfSize, Layout layout) {
        double outerRadius = halfSize * 0.88D;
        double innerRadius = Math.max(10.0D, outerRadius * 0.22D);
        double minDistance = layout.minDistance;
        double maxDistance = layout.maxDistance;
        if (info.file().features().radar().maxTargetRange() > 0.0F) {
            maxDistance = Math.min(maxDistance, info.file().features().radar().maxTargetRange());
        }

        long now = ac.level().getGameTime();
        boolean tagsSeekers = IncomingMissileReader.hasDigitalRwr(ac);
        for (RWRThreatEvent threat : RWRThreatClientTracker.getEvents(ac.getId())) {
            if (threat == null || threat.emitterEntityId() == ac.getId() || "?".equals(threat.sourceName())) continue;

            // Stronger signals sit closer to the centre.
            double distance = maxDistance - threat.strength() * (maxDistance - minDistance);
            distance = Math.max(minDistance, Math.min(maxDistance, distance));
            double rangeFraction = (distance - minDistance) / Math.max(1.0D, maxDistance - minDistance);
            double ringRadius = innerRadius + (outerRadius - innerRadius) * rangeFraction;
            double angle = Math.toRadians(threat.angleDeg() - 90.0D);
            double x = centerX + Math.cos(angle) * ringRadius;
            double y = centerY + Math.sin(angle) * ringRadius;
            drawLabel(canvas, threat.sourceName(), x, y, threatColor(threat, now), LABEL_SCALE);
            if (tagsSeekers && threat.isMissile()) {
                drawLabel(canvas, ThreatLabels.seeker(threat.seeker()), x, y + LABEL_LINE,
                        ThreatLabels.color(threat.seeker()), TAG_SCALE);
            }
        }
    }

    private static int threatColor(RWRThreatEvent threat, long now) {
        if (threat.isMissile()) {
            boolean bright = ((now / MISSILE_BLINK_TICKS) & 1L) == 0L;
            return bright ? 0xFFFF2D2D : 0xFFCC3030;
        }
        if (threat.threatMode() == RWRThreatEvent.MODE_STT) return 0xFFFF4A4A;
        if (threat.emitterKind() == RWRThreatEvent.EMITTER_AIRCRAFT) return 0xFF00FF00;
        return 0xFFFFE400;
    }

    private static void drawLabel(HudCanvas canvas, String text, double x, double y, int color, float scale) {
        canvas.pushPose();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        canvas.text(text, -canvas.textWidth(text) / 2, 0, color, false);
        canvas.popPose();
    }

    private static ResourceLocation texture(String name) {
        return Ids.of("textures/" + name + ".png");
    }

    /** Ring position and range scale; ground vehicles use a smaller ring with a shorter range. */
    private record Layout(int size, int centerX, int centerY, double minDistance, double maxDistance) {
    }
}
