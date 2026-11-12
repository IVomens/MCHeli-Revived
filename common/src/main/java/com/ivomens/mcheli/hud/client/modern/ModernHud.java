package com.ivomens.mcheli.hud.client.modern;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.hud.client.ThreatLabels;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.vehicle.sensor.IncomingMissiles;
import com.ivomens.mcheli.vehicle.sensor.client.IncomingMissileReader;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * The modern vehicle HUD: hologram-style symbology with a heading tape, pitch ladder, speed and
 * altitude tapes, a weapon card and system bars for aircraft, and a gunner sight for ground
 * vehicles.
 * <p>
 * It reads everything from the vehicle each frame and keeps no state, so it works the same for
 * every vehicle a content pack defines. The legacy scripted HUD stays available through config.
 */
public final class ModernHud {

    /** The colour of cautions: low fuel, reloading. */
    public static final int CAUTION = 0xFFFFB547;
    /** The colour of warnings: incoming missile, failed engine, low hull. */
    public static final int WARNING = 0xFFFF6B5B;
    private static final float PIXELS_PER_DEGREE = 6F;
    private static final float TICKS_PER_SECOND = 20F;
    private static final float LOW_FUEL = 0.15F;
    private static final float HULL_WARNING = 0.25F;

    private ModernHud() {
    }

    /** Settings the player chooses in the client config. */
    public record Style(int color, float scale) {
        public static final Style DEFAULT = new Style(0xFF7FFFD4, 1.0F);
    }

    public static void render(HudCanvas canvas, float partialTicks, Player player, AircraftEntity aircraft, Style style) {
        HologramPen c = new HologramPen(canvas);
        c.begin(style.scale());
        try {
            int color = style.color();
            if (drawsFlightLayout(aircraft, player)) {
                drawFlight(c, partialTicks, player, aircraft, color, style.scale());
            } else {
                drawGunnerSight(c, player, aircraft, color);
            }
            drawWeaponCard(c, player, aircraft, color);
            drawAlerts(c, aircraft, color);
        } finally {
            c.end();
        }
    }

    /**
     * Whether the viewer sees the flight layout (tapes, pitch ladder, system bars) rather than the gunner sight:
     * in a helicopter or plane, unless he looks through the gunner view.
     */
    public static boolean drawsFlightLayout(AircraftEntity aircraft, Player player) {
        boolean flying = aircraft instanceof HelicopterEntity || aircraft instanceof PlaneEntity;
        return flying && !aircraft.modes().usesGunnerView(player);
    }

    // Aircraft layout

    private static void drawFlight(HologramPen c, float partialTicks, Player player, AircraftEntity ac, int color, float scale) {
        float yaw = Mth.rotLerp(partialTicks, ac.yRotO, ac.getYRot());
        float pitch = Mth.lerp(partialTicks, ac.xRotO, ac.getXRot());
        float roll = ac.roll();

        drawHeadingTape(c, heading(yaw), color);
        drawPitchLadder(c, pitch, roll, color);
        drawBoresight(c, color);

        Vec3 motion = ac.getDeltaMovement();
        float speedKmh = (float) (motion.length() * TICKS_PER_SECOND * 3.6);
        float verticalSpeed = (float) (motion.y * TICKS_PER_SECOND);
        float altitude = (float) ac.getY();
        int ground = ac.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(ac.getX()), Mth.floor(ac.getZ()));
        float radarAltitude = (float) (ac.getY() - ground);

        drawTape(c, 70, "KM/H", speedKmh, 20, true, color);
        drawTape(c, 570, "ALT", altitude, 20, false, color);
        c.mono(String.format(Locale.ROOT, "R %.0f", Math.max(0, radarAltitude)), 588, 86, color);
        c.mono(String.format(Locale.ROOT, "VS %+.1f", verticalSpeed), 576, 264, color);

        drawSystems(c, ac, color);
        drawInfoBlocks(c, player, ac, color);
    }

    private static void drawHeadingTape(HologramPen c, float heading, int color) {
        float cx = HologramPen.WIDTH / 2, y = 24, half = 120;
        float pxPerDeg = 2.5F;
        c.line(cx - half, y, cx + half, y, color);
        int first = (int) Math.floor((heading - half / pxPerDeg) / 5) * 5;
        for (int deg = first; deg <= heading + half / pxPerDeg; deg += 5) {
            float x = cx + (deg - heading) * pxPerDeg;
            if (x < cx - half || x > cx + half) continue;
            boolean major = Math.floorMod(deg, 30) == 0;
            c.line(x, y, x, y - (major ? 6 : 3), color);
            if (major) c.monoCentered(headingLabel(Math.floorMod(deg, 360)), x, y - 16, color);
        }
        c.readout(cx - 20, y + 4, 40, 15, String.format(Locale.ROOT, "%03d", Math.round(heading) % 360), color);
    }

    private static void drawPitchLadder(HologramPen c, float pitch, float roll, int color) {
        float cx = HologramPen.WIDTH / 2, cy = HologramPen.HEIGHT / 2;
        c.canvas().pushPose();
        c.canvas().translate(cx, cy);
        c.canvas().rotate(-roll);
        // Minecraft pitch is positive nose-down; the ladder moves the opposite way.
        float offset = pitch * PIXELS_PER_DEGREE;
        for (int deg = -90; deg <= 90; deg += 10) {
            float y = offset - deg * PIXELS_PER_DEGREE;
            if (Math.abs(y) > 120) continue;
            if (deg == 0) {
                c.line(-150, y, -24, y, color);
                c.line(24, y, 150, y, color);
                continue;
            }
            String label = Integer.toString(Math.abs(deg));
            if (deg > 0) {
                c.line(-90, y, -30, y, color);
                c.line(-30, y, -30, y + 5, color);
                c.line(30, y, 90, y, color);
                c.line(30, y, 30, y + 5, color);
            } else {
                c.dashedLine(-90, y, -30, y, 6, 4, color);
                c.line(-30, y, -30, y - 5, color);
                c.dashedLine(30, y, 90, y, 6, 4, color);
                c.line(30, y, 30, y - 5, color);
            }
            c.monoRight(label, -94, y - 3, color);
            c.mono(label, 94, y - 3, color);
        }
        c.canvas().popPose();
    }

    private static void drawBoresight(HologramPen c, int color) {
        float cx = HologramPen.WIDTH / 2, cy = HologramPen.HEIGHT / 2;
        c.circle(cx, cy, 4, color);
        c.line(cx - 14, cy, cx - 5, cy, color);
        c.line(cx + 5, cy, cx + 14, cy, color);
        c.line(cx, cy - 11, cx, cy - 5, color);
    }

    /** A vertical tape with the current value boxed in the middle. */
    private static void drawTape(HologramPen c, float x, String unit, float value, float step, boolean leftSide, int color) {
        float cy = HologramPen.HEIGHT / 2, half = 80;
        float pxPerUnit = 30F / step;
        c.label(unit, leftSide ? x - 30 : x - 4, cy - half - 14, color);
        c.line(x, cy - half, x, cy + half, color);
        float first = (float) Math.floor((value - half / pxPerUnit) / step) * step;
        for (float v = first; v <= value + half / pxPerUnit; v += step) {
            float y = cy - (v - value) * pxPerUnit;
            if (y < cy - half || y > cy + half) continue;
            boolean underReadout = Math.abs(y - cy) < 11;
            String text = underReadout ? "" : String.format(Locale.ROOT, "%.0f", v);
            if (leftSide) {
                c.line(x - 5, y, x, y, color);
                c.monoRight(text, x - 8, y - 3, HologramPen.withAlpha(color, 0.75F));
            } else {
                c.line(x, y, x + 5, y, color);
                c.mono(text, x + 8, y - 3, HologramPen.withAlpha(color, 0.75F));
            }
        }
        float boxX = leftSide ? x - 46 : x + 4;
        c.readout(boxX, cy - 8, 42, 16, String.format(Locale.ROOT, "%.0f", value), color);
    }

    private static void drawSystems(HologramPen c, AircraftEntity ac, int color) {
        float x = 20, y = 304;
        systemBar(c, "THR", (float) ac.engine().throttle(), x, y, color);
        float fuel = ac.fuel().percentage();
        systemBar(c, "FUEL", fuel, x, y + 14, fuel < LOW_FUEL ? CAUTION : color);
        float hull = hullFraction(ac);
        systemBar(c, "HULL", hull, x, y + 28, hull < HULL_WARNING ? WARNING : color);
    }

    private static void systemBar(HologramPen c, String name, float fraction, float x, float y, int color) {
        c.mono(name, x, y, color);
        c.bar(x + 36, y + 2, 70, 5, fraction, color);
    }

    private static void drawInfoBlocks(HologramPen c, Player player, AircraftEntity ac, int color) {
        int dim = HologramPen.withAlpha(color, 0.85F);
        c.mono(String.format(Locale.ROOT, "X %.0f  Y %.0f  Z %.0f", ac.getX(), ac.getY(), ac.getZ()), 20, 56, dim);
        String mode = ac.modes().isHovering() ? "HOVER" : ac.modes().isFreeLook() ? "FREE LOOK" : "";
        if (!mode.isEmpty()) c.mono("MODE " + mode, 20, 70, dim);
        if (ac.parts().canFoldLandingGear() || ac.parts().canUnfoldLandingGear()) {
            c.monoRight(ac.parts().isLandingGearFolded() ? "GEAR UP" : "GEAR DOWN", 620, 70, dim);
        }
        c.monoRight(vehicleName(ac), 620, 56, dim);
    }

    // Ground vehicle / gunner layout

    private static void drawGunnerSight(HologramPen c, Player player, AircraftEntity ac, int color) {
        float cx = HologramPen.WIDTH / 2, cy = HologramPen.HEIGHT / 2;
        c.line(cx - 160, cy, cx - 20, cy, color);
        c.line(cx + 20, cy, cx + 160, cy, color);
        c.line(cx, cy + 10, cx, cy + 150, color);
        c.line(cx - 12, cy, cx, cy - 12, color);
        c.line(cx, cy - 12, cx + 12, cy, color);
        for (int i = 1; i <= 4; i++) {
            float y = cy + i * 20, w = 12 - i * 1.5F;
            c.line(cx - w, y, cx + w, y, color);
            c.mono(Integer.toString(i * 4), cx + w + 4, y - 3, HologramPen.withAlpha(color, 0.8F));
        }
        for (int i = 1; i <= 4; i++) {
            c.line(cx - 20 - i * 30, cy - 3, cx - 20 - i * 30, cy + 3, HologramPen.withAlpha(color, 0.5F));
            c.line(cx + 20 + i * 30, cy - 3, cx + 20 + i * 30, cy + 3, HologramPen.withAlpha(color, 0.5F));
        }

        Vec3 motion = ac.getDeltaMovement();
        float speedKmh = (float) (Math.sqrt(motion.x * motion.x + motion.z * motion.z) * TICKS_PER_SECOND * 3.6);
        c.label("SPEED", 20, 20, color);
        c.mono(String.format(Locale.ROOT, "%.0f KM/H", speedKmh), 20, 32, color);
        float hull = hullFraction(ac);
        c.label("HULL", 20, 52, color);
        c.bar(20, 64, 80, 5, hull, hull < HULL_WARNING ? WARNING : color);
        c.label("FUEL", 20, 76, color);
        c.bar(20, 88, 80, 5, ac.fuel().percentage(), color);
        c.monoRight(vehicleName(ac), 620, 20, HologramPen.withAlpha(color, 0.85F));
        c.monoRight(String.format(Locale.ROOT, "HDG %03d", Math.round(heading(player.getYRot())) % 360), 620, 32, color);
    }

    // Shared

    private static void drawWeaponCard(HologramPen c, Player player, AircraftEntity ac, int color) {
        WeaponGroup ws = ac.weapons().current(player);
        if (ws == null || ws.spec() == null) return;
        float x = 470, y = 282, w = 150, h = 66;
        c.fill(x, y, w, h, HologramPen.BACKING);
        c.rect(x, y, w, h, color);
        c.label("WEAPON", x + 6, y + 5, HologramPen.withAlpha(color, 0.8F));
        c.label(ws.displayName().toUpperCase(Locale.ROOT), x + 6, y + 17, color);

        int magazine = ws.magazineSize();
        int loaded = ws.ammo();
        int reserve = ws.reserveAmmo();
        String status = ws.reloadTicks() > 0 ? "RELOADING" : loaded <= 0 && reserve <= 0 ? "EMPTY" : "RDY";
        int statusColor = "RDY".equals(status) ? color : CAUTION;
        c.mono(status, x + 6, y + 34, statusColor);
        c.monoRight(magazine > 0 ? loaded + " / " + reserve : "--", x + w - 6, y + 34, color);

        if (magazine > 0 && magazine <= 16) {
            float pipW = (w - 12 - (magazine - 1) * 2F) / magazine;
            for (int i = 0; i < magazine; i++) {
                float px = x + 6 + i * (pipW + 2);
                if (i < loaded) c.fill(px, y + 54, pipW, 4, color);
                else c.rect(px, y + 54, pipW, 4, HologramPen.withAlpha(color, 0.6F));
            }
        } else if (magazine > 0) {
            c.bar(x + 6, y + 54, w - 12, 5, loaded / (float) magazine, color);
        }
    }

    private static void drawAlerts(HologramPen c, AircraftEntity ac, int color) {
        float cx = HologramPen.WIDTH / 2;
        float y = 48;
        IncomingMissiles incoming = IncomingMissileReader.read(ac);
        if (incoming.any() || ac.countermeasures().isLockedByMissile()) {
            String counts = ThreatLabels.counts(incoming);
            c.tag(counts.isEmpty() ? "MISSILE" : "MISSILE  " + counts, cx, y, WARNING);
            y += 15;
            y = drawCue(c, ac, incoming, cx, y);
        }
        if (ac.engine().isCrippled()) {
            c.tag("ENGINE FAILURE", cx, y, WARNING);
            y += 15;
        }
        if (ac.fuel().percentage() < LOW_FUEL) {
            c.tag("LOW FUEL", cx, y, CAUTION);
        }
    }

    /** The countermeasure to drop, blinking under the missile warning; returns where the next alert goes. */
    private static float drawCue(HologramPen c, AircraftEntity ac, IncomingMissiles incoming, float cx, float y) {
        boolean flares = ac.countermeasures().haveFlare();
        boolean chaff = ac.countermeasures().haveChaff();
        String cue = ThreatLabels.cue(incoming, flares, chaff);
        if (cue.isEmpty()) {
            return y;
        }
        if (ThreatLabels.cueLit(ac.level().getGameTime())) {
            c.tag(cue, cx, y, ThreatLabels.cueColor(incoming, flares, chaff));
        }
        return y + 15;
    }

    private static float hullFraction(AircraftEntity ac) {
        int max = ac.damage().maxHp();
        return max <= 0 ? 1 : 1 - Mth.clamp(ac.damage().damageTaken() / (float) max, 0, 1);
    }

    /** Minecraft yaw 0 faces south; compass heading 0 is north. */
    private static float heading(float yaw) {
        return Mth.positiveModulo(yaw + 180F, 360F);
    }

    private static String headingLabel(int degrees) {
        return switch (degrees) {
            case 0 -> "N";
            case 90 -> "E";
            case 180 -> "S";
            case 270 -> "W";
            default -> String.format(Locale.ROOT, "%02d", degrees / 10);
        };
    }

    private static String vehicleName(AircraftEntity ac) {
        VehicleSpec info = ac.spec();
        String name = info != null && info.file().displayName().name() != null ? info.file().displayName().name()
                : ac.identity().typeName();
        return name == null ? "" : name.toUpperCase(Locale.ROOT);
    }
}
