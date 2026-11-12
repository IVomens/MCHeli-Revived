package com.ivomens.mcheli.world.chain.client;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import com.ivomens.mcheli.world.chain.ChainEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

/** Tow chain drawn as a row of chain-link models between the towing and the towed entity. */
public class ChainRenderer extends McheliEntityRenderer<ChainEntity> {

    public static EntityRendererProvider<ChainEntity> factory(ThermalVision thermal) {
        return context -> new ChainRenderer(context, thermal);
    }
    private static final ResourceLocation TEX = texture("textures/chain.png");
    private static final float LINK_LENGTH = 0.95F;

    private ChainRenderer(EntityRendererProvider.Context context, ThermalVision thermal) {
        super(context, thermal);
    }

    @Override
    public boolean shouldRender(@NotNull ChainEntity chain, @NotNull Frustum frustum, double camX, double camY,
                                double camZ) {
        if (chain.towed() == null || chain.puller() == null) {
            return false;
        }
        // the chain spans both ends, which may be far from the chain entity itself
        AABB box = chain.towed().getBoundingBox().minmax(chain.puller().getBoundingBox()).inflate(1.0);
        return frustum.isVisible(box);
    }

    @Override
    public void doRender(@NotNull ChainEntity chain, double posX, double posY, double posZ, float entityYaw,
                         float partialTicks) {
        Entity towed = chain.towed();
        Entity tow = chain.puller();
        if (towed == null || tow == null) {
            return;
        }
        LegacyGL.pushMatrix();
        LegacyGL.enableCull();
        LegacyGL.color(0.5F, 0.5F, 0.5F, 1.0F);
        // the pose is at this entity's interpolated position; the links start at the towed entity
        double originX = Mth.lerp(partialTicks, chain.xOld, chain.getX());
        double originY = Mth.lerp(partialTicks, chain.yOld, chain.getY());
        double originZ = Mth.lerp(partialTicks, chain.zOld, chain.getZ());
        LegacyGL.translate(towed.xOld - originX, towed.yOld - originY, towed.zOld - originZ);
        this.bindTexture(TEX);

        double dx = tow.xOld - towed.xOld;
        double dy = tow.yOld - towed.yOld;
        double dz = tow.zOld - towed.zOld;
        double remaining = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double stepX = dx * LINK_LENGTH / remaining;
        double stepY = dy * LINK_LENGTH / remaining;
        double stepZ = dz * LINK_LENGTH / remaining;
        float yaw = (float) Math.toDegrees(Math.atan2(stepZ, stepX));
        float roll = (float) Math.toDegrees(Math.atan2(stepY, Math.sqrt(stepX * stepX + stepZ * stepZ)));

        for (; remaining > LINK_LENGTH; remaining -= LINK_LENGTH) {
            LegacyGL.translate(stepX, stepY, stepZ);
            LegacyGL.pushMatrix();
            LegacyGL.rotate(yaw, 0.0F, -1.0F, 0.0F);
            LegacyGL.rotate(roll, 0.0F, 0.0F, 1.0F);
            ModelCache.render("chain");
            LegacyGL.popMatrix();
        }
        LegacyGL.popMatrix();
    }
}
