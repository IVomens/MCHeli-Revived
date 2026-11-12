package com.ivomens.mcheli.combat.projectile.client;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.entityview.Riders;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.ArmedVehicle;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import com.ivomens.mcheli.util.Color4f;
import com.ivomens.mcheli.util.Ids;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Draws projectiles: the weapon's model (or the kind's default) pointing along the direction of travel,
 * tinted with the weapon's colour, plus the motor flame of missiles. One renderer per registered entity
 * type, set up for its {@link ProjectileKind}.
 */
public final class ProjectileRenderer extends McheliEntityRenderer<ProjectileEntity> {

    public static final ResourceLocation TEX_FLAME = Ids.of("textures/exhaust_flame.png");
    public static final ResourceLocation TEX_FLAME_1 = Ids.of("textures/exhaust_flame_1.png");

    private static final double MISSILE_FLAME_OFFSET = 0.8;
    private static final float MISSILE_FLAME_SIZE = 4.0F;
    private static final int WATER_PROBE_DEPTH = 3;
    private static final float MISSILE_SHADOW = 0.5F;

    private final ProjectileKind kind;
    /** Scratch output of {@link #travelYawPitch}: [yaw, pitch]; render thread only. */
    private final float[] orientation = new float[2];
    private final BlockPos.MutableBlockPos waterProbe = new BlockPos.MutableBlockPos();

    private ProjectileRenderer(EntityRendererProvider.Context context, ThermalVision thermal, ProjectileKind kind) {
        super(context, thermal);
        this.kind = kind;
        if (kind.isGuidedMissile()) {
            this.shadowRadius = MISSILE_SHADOW;
        } else if (kind == ProjectileKind.BOMB) {
            this.shadowRadius = 0.0F;
        }
    }

    public static EntityRendererProvider<ProjectileEntity> factory(ThermalVision thermal, ProjectileKind kind) {
        return context -> new ProjectileRenderer(context, thermal, kind);
    }

    /**
     * Writes the yaw/pitch (MCHeli bullet convention: yaw = atan2(z, x) - 90, pitch = -atan2(y, horizontal))
     * of the entity's direction of travel into {@code out}. Uses the last tick's position delta, then the
     * velocity, and falls back to the interpolated entity rotation when the projectile is not moving.
     */
    public static void travelYawPitch(Entity e, float partialTicks, float[] out) {
        double mx = e.getX() - e.xo;
        double my = e.getY() - e.yo;
        double mz = e.getZ() - e.zo;
        if (mx * mx + my * my + mz * mz < 1.0E-6) {
            Vec3 m = e.getDeltaMovement();
            mx = m.x;
            my = m.y;
            mz = m.z;
        }
        if (mx * mx + my * my + mz * mz < 1.0E-6) {
            out[0] = Mth.rotLerp(partialTicks, e.yRotO, e.getYRot());
            out[1] = Mth.lerp(partialTicks, e.xRotO, e.getXRot());
            return;
        }
        out[0] = (float) Math.toDegrees(Math.atan2(mz, mx)) - 90.0F;
        out[1] = -(float) Math.toDegrees(Math.atan2(my, Math.sqrt(mx * mx + mz * mz)));
    }

    @Override
    public void doRender(@NotNull ProjectileEntity entity, double x, double y, double z, float entityYaw,
                         float partialTicks) {
        WeaponSpec info = entity.spec();
        if (info != null) {
            Color4f color = this.isNearWater(entity) ? info.bulletColorInWater() : info.bulletColor();
            LegacyGL.color(color.r, color.g, color.b, color.a);
        }
        LegacyGL.enableCull();
        LegacyGL.enableBlend();
        if (this.isShown(entity, info)) {
            this.renderAlongTravel(entity, x, y, z, partialTicks);
        }
        LegacyGL.color(1.0F, 1.0F, 1.0F, 1.0F);
        LegacyGL.disableBlend();
        if (this.kind.isGuidedMissile()) {
            this.renderMissileFlame(entity, x, y, z);
        }
    }

    /**
     * A cluster bomb that splits at release is never drawn, only its bomblets; the crew flying a TV
     * missile through its camera does not see the missile itself.
     */
    private boolean isShown(ProjectileEntity entity, WeaponSpec info) {
        return switch (this.kind) {
            case BOMB -> info != null
                    && (entity.isBomblet() || info.submunition().count() <= 0 || info.bombletSTime() > 0);
            case TV_MISSILE, AT_MISSILE -> this.isMissileShown(entity);
            default -> true;
        };
    }

    private boolean isMissileShown(ProjectileEntity entity) {
        Player player = Minecraft.getInstance().player;
        Vehicle vehicle = Riders.vehicleOf(player);
        return !(vehicle instanceof ArmedVehicle armed) || armed.cameras().showsMissile(entity, player);
    }

    /** Water in the block above, at or below the projectile. */
    private boolean isNearWater(Entity entity) {
        this.waterProbe.set(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ());
        int baseY = this.waterProbe.getY();
        for (int offset = 0; offset < WATER_PROBE_DEPTH; offset++) {
            this.waterProbe.setY(baseY + 1 - offset);
            if (entity.level().getFluidState(this.waterProbe).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    private void renderAlongTravel(ProjectileEntity entity, double x, double y, double z, float partialTicks) {
        LegacyGL.pushMatrix();
        LegacyGL.translate(x, y, z);
        travelYawPitch(entity, partialTicks, this.orientation);
        LegacyGL.rotate(-this.orientation[0], 0.0F, 1.0F, 0.0F);
        LegacyGL.rotate(this.orientation[1], 1.0F, 0.0F, 0.0F);
        BulletModel model = ProjectileModels.of(entity);
        if (model != null && model.model != null) {
            LegacyGL.bindTexture(texture("textures/bullets/", model.name));
            model.model.renderAll();
        }
        LegacyGL.popMatrix();
    }

    /** A camera-facing flame sprite behind the missile, against its travel. */
    private void renderMissileFlame(ProjectileEntity entity, double x, double y, double z) {
        double mx = entity.getX() - entity.xo;
        double my = entity.getY() - entity.yo;
        double mz = entity.getZ() - entity.zo;
        double motionLen = Math.sqrt(mx * mx + my * my + mz * mz);
        if (motionLen <= 0.0001D) {
            Vec3 m = entity.getDeltaMovement();
            mx = m.x;
            my = m.y;
            mz = m.z;
            motionLen = m.length();
        }
        LegacyGL.pushMatrix();
        LegacyGL.pushAttrib();
        LegacyGL.translate(x, y, z);
        if (motionLen > 0.0001D) {
            LegacyGL.translate((-mx / motionLen) * MISSILE_FLAME_OFFSET, (-my / motionLen) * MISSILE_FLAME_OFFSET,
                    (-mz / motionLen) * MISSILE_FLAME_OFFSET);
        }
        LegacyGL.rotateToCamera();
        LegacyGL.bindTexture((entity.tickCount / 5) % 2 == 0 ? TEX_FLAME : TEX_FLAME_1);
        LegacyGL.color(1.0F, 1.0F, 1.0F, 1.0F);
        LegacyGL.enableBlend();
        LegacyGL.depthMask(false);
        LegacyGL.disableLighting();
        float size = MISSILE_FLAME_SIZE;
        VertexConsumer vc = LegacyGL.buffer();
        LegacyGL.vertex(vc, -size, -size, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F);
        LegacyGL.vertex(vc, size, -size, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F);
        LegacyGL.vertex(vc, size, size, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        LegacyGL.vertex(vc, -size, size, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        LegacyGL.popAttrib();
        LegacyGL.popMatrix();
    }
}
