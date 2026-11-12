package com.ivomens.mcheli.equipment.throwable.client;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.core.content.def.throwable.ThrowableDefinition;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import com.ivomens.mcheli.equipment.throwable.ThrowableEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.NotNull;

public class ThrowableRenderer extends McheliEntityRenderer<ThrowableEntity> {

    public static EntityRendererProvider<ThrowableEntity> factory(ThermalVision thermal) {
        return context -> new ThrowableRenderer(context, thermal);
    }

    private ThrowableRenderer(EntityRendererProvider.Context context, ThermalVision thermal) {
        super(context, thermal);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void doRender(@NotNull ThrowableEntity entity, double posX, double posY, double posZ, float entityYaw,
                         float tickTime) {
        ThrowableDefinition info = entity.info();
        if (info == null) {
            return;
        }
        LegacyGL.pushMatrix();
        LegacyGL.translate(posX, posY, posZ);
        LegacyGL.rotate(entity.getYRot(), 0.0F, -1.0F, 0.0F);
        LegacyGL.rotate(entity.getXRot(), 1.0F, 0.0F, 0.0F);
        this.setCommonRenderParam();
        IModelCustom model = ModelCache.find("throwable", info.name());
        if (model != null) {
            this.bindTexture(texture("textures/throwable/", info.name()));
            model.renderAll();
        }
        this.restoreCommonRenderParam();
        LegacyGL.popMatrix();
    }
}
