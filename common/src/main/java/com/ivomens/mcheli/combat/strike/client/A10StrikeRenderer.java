package com.ivomens.mcheli.combat.strike.client;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.combat.strike.A10StrikeEntity;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class A10StrikeRenderer extends McheliEntityRenderer<A10StrikeEntity> {

    public static EntityRendererProvider<A10StrikeEntity> factory(ThermalVision thermal) {
        return context -> new A10StrikeRenderer(context, thermal);
    }
    private static final ResourceLocation TEX = texture("textures/bullets/a10.png");

    private A10StrikeRenderer(EntityRendererProvider.Context context, ThermalVision thermal) {
        super(context, thermal);
        this.shadowRadius = 10.5F;
    }

    @Override
    public void doRender(A10StrikeEntity entity, double posX, double posY, double posZ, float yaw, float tickTime) {
        if (!entity.isRender()) {
            return;
        }
        LegacyGL.pushMatrix();
        LegacyGL.translate(posX, posY, posZ);
        LegacyGL.rotate(-Mth.lerp(tickTime, entity.yRotO, entity.getYRot()), 0.0F, 1.0F, 0.0F);
        LegacyGL.rotate(-Mth.lerp(tickTime, entity.xRotO, entity.getXRot()), 1.0F, 0.0F, 0.0F);
        LegacyGL.bindTexture(TEX);
        ModelCache.render("a-10");
        LegacyGL.popMatrix();
    }
}
