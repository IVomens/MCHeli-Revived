package com.ivomens.mcheli.multiplay.client;

import com.ivomens.mcheli.fx.client.legacy.BaseParticle;
import com.ivomens.mcheli.multiplay.TargetMarkerObject;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.NotNull;

/**
 * Team map marker. Draws nothing itself; while it is alive and on screen it feeds its camera-relative
 * position to the target marker overlay every frame.
 */
public class MarkPointParticle extends BaseParticle implements TargetMarkerObject {

    final Team team;
    private final TeamMarkers markers;

    public MarkPointParticle(Level level, double x, double y, double z, Team team, TeamMarkers markers) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.setParticleMaxAge(30);
        this.team = team;
        this.markers = markers;
        this.hasPhysics = false;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        // only team mates see the marker
        Player player = Minecraft.getInstance().player;
        if (player == null || this.team == null || !player.isAlliedTo(this.team)) {
            this.setExpired();
        }
    }

    @Override
    public void render(@NotNull VertexConsumer buffer, @NotNull Camera camera, float partialTicks) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        // the camera position already includes the third person offset (and its block clipping)
        Vec3 cam = camera.getPosition();
        double renderX = Mth.lerp(partialTicks, this.xo, this.x) - cam.x;
        double renderY = Mth.lerp(partialTicks, this.yo, this.y) - cam.y;
        double renderZ = Mth.lerp(partialTicks, this.zo, this.z) - cam.z;

        double distanceScale = Math.sqrt(renderX * renderX + renderY * renderY + renderZ * renderZ) / 10.0;
        if (distanceScale < 1.0) {
            distanceScale = 1.0;
        }

        this.markers.queue(TeamMarkers.RESERVE_POINT, this, renderX / distanceScale, renderY / distanceScale,
                renderZ / distanceScale);
    }

    @Override
    public double getX() {
        return this.x;
    }

    @Override
    public double getY() {
        return this.y;
    }

    @Override
    public double getZ() {
        return this.z;
    }
}
