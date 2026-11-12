package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.combat.countermeasure.ChaffEntity;
import com.ivomens.mcheli.combat.countermeasure.FlareEntity;
import com.ivomens.mcheli.combat.guidance.LockOnSeeker;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.client.overlay.HudOverlay;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.util.EntityIds;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The lock-on box the local player's guided weapon draws around possible targets: green while
 * tracking, red when locked, faded when outside the seeker's limits, with the range (and the radar
 * name for radar missiles) above it.
 */
public final class LockOnMarkerRenderer {

    private static final double MAX_MARKER_DISTANCE_SQ = 1000.0 * 1000.0;
    /** Distances over which the box and the label grow from their near size to their far size. */
    private static final double NEAR_DISTANCE = 10.0;
    private static final double GROWTH_DISTANCE = 290.0;
    private static final float MAX_BOX_SIZE = 300.0F;
    private static final float BILLBOARD_SCALE = 0.02666667F;
    private static final float FADED_ALPHA = 0.2F;

    private LockOnMarkerRenderer() {
    }

    /** The marker inside an open {@link LegacyGL} frame posed at the entity. */
    public static void renderInFrame(Entity entity) {
        if (LegacyGL.active()) {
            render(entity, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        }
    }

    /** The marker for callers outside an entity renderer (e.g. RenderLivingEvent.Post); pose at the entity. */
    public static void render(Entity entity, PoseStack poseStack, MultiBufferSource buffers, float partialTicks) {
        LegacyGL.begin(poseStack, buffers, LightTexture.FULL_BRIGHT);
        try {
            render(entity, partialTicks);
        } finally {
            LegacyGL.end();
        }
    }

    /** Draws the marker for {@code target}; the current pose must be at the target's render origin. */
    public static void render(Entity target, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Entity view = mc.getCameraEntity();
        if (player == null || view == null || EntityIds.same(player, target)) {
            return;
        }
        AircraftEntity ac = controlledAircraft(player);
        if (ac == null || EntityIds.same(ac, target)) {
            return;
        }
        WeaponGroup weaponSet = ac.weapons().current(player);
        if (weaponSet == null) {
            return;
        }
        var weapon = weaponSet.currentMount();
        LockOnSeeker guidance = weapon != null ? weapon.seeker() : null;
        if (guidance == null || !guidance.canLock(target, player)) {
            return;
        }

        WeaponSpec info = weapon.spec();
        double distanceSq = target.distanceToSqr(view);
        if (distanceSq >= MAX_MARKER_DISTANCE_SQ) {
            return;
        }
        double distance = Math.sqrt(distanceSq);
        if (info != null && info.missile().enableBvr() && distance > info.missile().minRangeBvr()) {
            return;
        }
        if (!hasLineOfSight(mc, player, target)) {
            return;
        }

        boolean locked = guidance.isLocking(target);
        boolean outsideLimits = info != null
                && (aspectAngle(ac, target) > info.missile().pdhdnMaxDegree() || distance > info.missile().lockOn().maxRange());
        drawMarker(mc, target, ac, guidance, distance, locked, outsideLimits ? FADED_ALPHA : 1.0F, partialTicks);
    }

    /** The box and label above the target, and the lock line from the vehicle once locked. */
    private static void drawMarker(Minecraft mc, Entity target, AircraftEntity ac, LockOnSeeker guidance,
                                   double distance, boolean locked, float alpha, float partialTicks) {
        float height = target.getBbHeight();

        LegacyGL.pushMatrix();
        LegacyGL.translate(0.0F, height + 2.0F, 0.0F);
        LegacyGL.rotateToCamera();
        LegacyGL.scale(-BILLBOARD_SCALE, -BILLBOARD_SCALE, BILLBOARD_SCALE);
        LegacyGL.translate(0.0F, 9.374999F, 0.0F);
        float size = boxSize(target, distance);
        renderBox(size, locked, alpha);
        renderLabel(target, ac, guidance, size, distance);
        LegacyGL.popMatrix();

        if (!ac.isUAV() && locked && mc.options.getCameraType().isFirstPerson()) {
            renderLockLine(target, ac, height, partialTicks);
        }
        LegacyGL.enableLighting();
        LegacyGL.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static AircraftEntity controlledAircraft(Player player) {
        return switch (player.getVehicle()) {
            case AircraftEntity aircraft -> aircraft;
            case SeatEntity seat -> seat.getParent();
            case IUavStation station -> station.getControlled();
            case null, default -> null;
        };
    }

    private static boolean hasLineOfSight(Minecraft mc, Player player, Entity target) {
        Vec3 from = mc.gameRenderer.getMainCamera().getPosition();
        ClipContext context = new ClipContext(from, target.position(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, player);
        return player.level().clip(context).getType() == HitResult.Type.MISS;
    }

    /** Angle in degrees between the two flight paths, folded to 0..90 (head-on counts as aligned). */
    private static float aspectAngle(AircraftEntity ac, Entity target) {
        Vec3 own = ac.getDeltaMovement();
        Vec3 theirs = target.getDeltaMovement();
        double ownSpeed = own.length();
        double theirSpeed = theirs.length();
        if (ownSpeed <= 0.001 || theirSpeed <= 0.001) {
            return 0.0F;
        }
        double cos = Mth.clamp(own.dot(theirs) / (ownSpeed * theirSpeed), -1.0, 1.0);
        double angle = Math.acos(cos);
        return (float) Math.toDegrees(angle > Math.PI / 2 ? Math.PI - angle : angle);
    }

    private static float boxSize(Entity target, double distance) {
        float extent = Math.max(target.getBbWidth(), target.getBbHeight());
        boolean airborneTarget = target instanceof AircraftEntity || target instanceof FlareEntity
                || target instanceof ChaffEntity;
        float nearSize = extent * (airborneTarget ? 40.0F : 20.0F);
        float growth = (float) ((distance - NEAR_DISTANCE) / GROWTH_DISTANCE);
        return Math.clamp(nearSize + growth * (MAX_BOX_SIZE - nearSize), nearSize, MAX_BOX_SIZE);
    }

    private static void renderBox(float size, boolean locked, float alpha) {
        float red = locked ? 1.0F : 0.0F;
        float green = locked ? 0.0F : 1.0F;
        float lineWidth = HudOverlay.guiScale(Minecraft.getInstance()) * (locked ? 2.5F : 1.5F);
        VertexConsumer lines = LegacyGL.lineBuffer(lineWidth, true);
        float left = -size - 1.0F;
        float right = size + 1.0F;
        float top = size * 2.0F;
        LegacyGL.line(lines, left, 0.0, 0.0, left, top, 0.0, red, green, 0.0F, alpha);
        LegacyGL.line(lines, left, top, 0.0, right, top, 0.0, red, green, 0.0F, alpha);
        LegacyGL.line(lines, right, top, 0.0, right, 0.0, 0.0, red, green, 0.0F, alpha);
        LegacyGL.line(lines, right, 0.0, 0.0, left, 0.0, 0.0, red, green, 0.0F, alpha);
    }

    private static void renderLabel(Entity target, AircraftEntity ac, LockOnSeeker guidance, float boxSize,
                                    double distance) {
        LegacyGL.pushMatrix();
        LegacyGL.translate(0.0F, boxSize * 2.0F + 1.0F, 0.0F);
        float fontSize = Math.clamp(5.0F + (float) ((distance - NEAR_DISTANCE) / GROWTH_DISTANCE) * 35.0F, 5.0F, 40.0F);
        LegacyGL.scale(fontSize, fontSize, fontSize);

        String range = String.valueOf((int) distance);
        String text = range;
        if (guidance.filter().radar()) {
            String radarName = target instanceof AircraftEntity targetAc ? targetAc.radar().labelOnRadarOf(ac) : "?";
            if (!radarName.isEmpty()) {
                text = radarName + " " + range;
            }
        }
        LegacyGL.disableLighting();
        LegacyGL.drawString(text, -LegacyGL.stringWidth(text) / 2.0F, 0, 0xFF00FF00, true, 0);
        LegacyGL.popMatrix();
    }

    /** First person: a red line from the locked target down to the own aircraft. */
    private static void renderLockLine(Entity target, AircraftEntity ac, float targetHeight, float partialTicks) {
        double x = Mth.lerp(partialTicks, target.xOld, target.getX());
        double y = Mth.lerp(partialTicks, target.yOld, target.getY());
        double z = Mth.lerp(partialTicks, target.zOld, target.getZ());
        VertexConsumer line = LegacyGL.lineBuffer(1.0F, true);
        LegacyGL.line(line, 0.0, targetHeight / 2.0F, 0.0, ac.xOld - x, ac.yOld - 1.0D - y, ac.zOld - z,
                1.0F, 0.0F, 0.0F, 1.0F);
    }
}
