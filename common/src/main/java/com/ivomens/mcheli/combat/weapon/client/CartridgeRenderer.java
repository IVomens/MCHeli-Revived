package com.ivomens.mcheli.combat.weapon.client;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.combat.weapon.CartridgeEntity;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class CartridgeRenderer extends McheliEntityRenderer<CartridgeEntity> {

    public static EntityRendererProvider<CartridgeEntity> factory(ThermalVision thermal) {
        return context -> new CartridgeRenderer(context, thermal);
    }

    private CartridgeRenderer(EntityRendererProvider.Context context, ThermalVision thermal) {
        super(context, thermal);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void doRender(@NotNull CartridgeEntity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        IModelCustom model = entity.textureName().isEmpty() ? null : ModelCache.find("bullets", entity.textureName());
        if (model == null) {
            return;
        }

        LegacyGL.pushMatrix();
        LegacyGL.translate(x, y, z);

        float scale = entity.getScale();
        LegacyGL.scale(scale, scale, scale);

        LegacyGL.rotate(-interpolateRotation(entity.yRotO, entity.getYRot(), partialTicks), 0.0F, 1.0F, 0.0F);
        LegacyGL.rotate(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot()), 1.0F, 0.0F, 0.0F);

        LegacyGL.bindTexture(texture("textures/bullets/", entity.textureName()));
        model.renderAll();

        LegacyGL.popMatrix();
    }

    /** Interpolates along the shorter way round. */
    private static float interpolateRotation(float prev, float current, float partialTicks) {
        float delta = current - prev;

        while (delta < -180.0F) delta += 360.0F;
        while (delta >= 180.0F) delta -= 360.0F;

        return prev + partialTicks * delta;
    }
}
