package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Client-side CCIP (Continuously Computed Impact Point) fire-control overlay.
 *
 * <p>The marker is a world-space billboard drawn at the weapon's predicted impact point
 * ({@link AircraftEntity#getPredictedImpactPoint}); it is scaled to a roughly constant screen size
 * and rendered procedurally (a ring + ticks + centre cross), without a texture asset. {@code ccipFactor}
 * scales the marker. A single instance is driven by the level render hook.</p>
 */
public class CcipRenderer {

    /** Base marker radius in GUI pixels (before ccipFactor). */
    private static final float BASE_RADIUS_PX = 14.0F;
    private static final int RING_SEGMENTS = 24;
    /** The marker turns red when something is this close to the predicted impact. */
    private static final double TARGET_RADIUS = 3.0;
    private static final float[] RING_COS = new float[RING_SEGMENTS + 1];
    private static final float[] RING_SIN = new float[RING_SEGMENTS + 1];

    static {
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double ang = Math.PI * 2.0D * i / RING_SEGMENTS;
            RING_COS[i] = (float) Math.cos(ang);
            RING_SIN[i] = (float) Math.sin(ang);
        }
    }

    private long targetCheckTime = Long.MIN_VALUE;
    private boolean targetNearImpact;

    /** Draws the marker; called once particles have been rendered. */
    public void renderAfterParticles(Camera camera, @Nullable PoseStack levelPoseStack) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        AircraftEntity ac = resolveAircraft(player);
        if (ac == null) {
            return;
        }

        WeaponGroup currentWs = ac.weapons().current(player);
        if (currentWs == null || currentWs.spec() == null || currentWs.spec().definition().type() == null
                || !currentWs.spec().ccip()
                || !WeaponSpec.isCCIPSupportedType(currentWs.spec().definition().type())) {
            return;
        }

        Vec3 impact = ac.weapons().impact().getPredictedImpactPoint(player);
        if (impact == null) {
            return;
        }

        Vec3 cam = camera.getPosition();
        double x = impact.x - cam.x;
        double y = impact.y - cam.y + 0.2D;
        double z = impact.z - cam.z;
        double dist = Math.sqrt(x * x + y * y + z * z);
        if (dist < 0.5D) {
            return;
        }

        // Keep the marker a constant screen size: world-units-per-GUI-pixel at this distance.
        int scaledHeight = mc.getWindow().getGuiScaledHeight();
        double fovRad = Math.toRadians(mc.options.fov().get());
        float blocksPerPixel = (float) ((2.0D * dist * Math.tan(fovRad * 0.5D)) / (double) scaledHeight);

        float radius = BASE_RADIUS_PX * Mth.clamp(currentWs.spec().camera().ccipFactor(), 0.1F, 10.0F);
        boolean hot = this.hasEntityAroundImpact(ac, player, impact, mc.level.getGameTime());
        drawPipper(mc, levelPoseStack, new Vec3(x, y, z), ac.roll(), blocksPerPixel, radius, hot);
    }

    /**
     * The pipper at the camera-relative position, facing the camera and rolled with the aircraft: red when
     * something stands near the impact point, green otherwise.
     */
    private static void drawPipper(Minecraft mc, @Nullable PoseStack levelPoseStack, Vec3 at, float roll,
                                   float blocksPerPixel, float radius, boolean hot) {
        float r = hot ? 1.0F : 0.1F;
        float g = hot ? 0.1F : 1.0F;
        float b = 0.1F;
        float a = 0.9F;

        PoseStack poseStack = levelPoseStack != null ? levelPoseStack : new PoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        poseStack.pushPose();
        LegacyGL.begin(poseStack, buffers, LightTexture.FULL_BRIGHT);
        try {
            LegacyGL.translate(at.x, at.y, at.z);
            LegacyGL.rotateToCamera();
            LegacyGL.rotate(roll, 0.0F, 0.0F, 1.0F);
            LegacyGL.scale(-blocksPerPixel, -blocksPerPixel, blocksPerPixel);

            VertexConsumer lines = LegacyGL.lineBuffer(2.0F, true);
            // ring
            for (int i = 0; i < RING_SEGMENTS; i++) {
                LegacyGL.line(lines, RING_COS[i] * radius, RING_SIN[i] * radius, 0.0, RING_COS[i + 1] * radius,
                        RING_SIN[i + 1] * radius, 0.0, r, g, b, a);
            }

            // four cardinal ticks (from 0.55R out to R) + a short centre cross
            float inner = radius * 0.55F;
            float cross = radius * 0.30F;
            LegacyGL.line(lines, inner, 0.0, 0.0, radius, 0.0, 0.0, r, g, b, a);
            LegacyGL.line(lines, -inner, 0.0, 0.0, -radius, 0.0, 0.0, r, g, b, a);
            LegacyGL.line(lines, 0.0, inner, 0.0, 0.0, radius, 0.0, r, g, b, a);
            LegacyGL.line(lines, 0.0, -inner, 0.0, 0.0, -radius, 0.0, r, g, b, a);
            LegacyGL.line(lines, -cross, 0.0, 0.0, cross, 0.0, 0.0, r, g, b, a);
            LegacyGL.line(lines, 0.0, -cross, 0.0, 0.0, cross, 0.0, r, g, b, a);
        } finally {
            LegacyGL.end();
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    private static AircraftEntity resolveAircraft(Player player) {
        Entity riding = player.getVehicle();
        if (riding instanceof AircraftEntity aircraft) {
            return aircraft;
        }
        if (riding instanceof SeatEntity seat) {
            return seat.getParent();
        }
        if (riding instanceof IUavStation uav) {
            return uav.getControlled();
        }
        return null;
    }

    /** Whether something other than the shooter is near the impact point; queried once per tick. */
    private boolean hasEntityAroundImpact(AircraftEntity ac, Player player, Vec3 impact, long gameTime) {
        if (gameTime != this.targetCheckTime) {
            this.targetCheckTime = gameTime;
            AABB box = new AABB(impact.x - TARGET_RADIUS, impact.y - TARGET_RADIUS, impact.z - TARGET_RADIUS,
                    impact.x + TARGET_RADIUS, impact.y + TARGET_RADIUS, impact.z + TARGET_RADIUS);
            this.targetNearImpact = !ac.level().getEntities((Entity) null, box,
                    e -> e != player && e != ac && e.isAlive()).isEmpty();
        }
        return this.targetNearImpact;
    }
}
