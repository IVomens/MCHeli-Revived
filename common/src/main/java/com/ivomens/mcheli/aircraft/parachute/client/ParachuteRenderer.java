package com.ivomens.mcheli.aircraft.parachute.client;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import com.ivomens.mcheli.core.math.Degrees;
import com.ivomens.mcheli.aircraft.parachute.ParachuteEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class ParachuteRenderer extends McheliEntityRenderer<ParachuteEntity> {

    public static EntityRendererProvider<ParachuteEntity> factory(ThermalVision thermal) {
        return context -> new ParachuteRenderer(context, thermal);
    }
    private static final ResourceLocation TEX_1 = texture("textures/parachute1.png");
    private static final ResourceLocation TEX_2 = texture("textures/parachute2.png");

    private ParachuteRenderer(EntityRendererProvider.Context context, ThermalVision thermal) {
        super(context, thermal);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void doRender(@NotNull ParachuteEntity entity, double posX, double posY, double posZ, float entityYaw,
                         float tickTime) {
        int type = entity.getParachuteType();
        if (type <= 0) {
            return;
        }
        LegacyGL.pushMatrix();
        LegacyGL.enableCull();
        LegacyGL.translate(posX, posY, posZ);
        LegacyGL.rotate(Degrees.lerpRotation(tickTime, entity.yRotO, entity.getYRot()), 0.0F, -1.0F, 0.0F);
        LegacyGL.color(0.75F, 0.75F, 0.75F, 1.0F);
        LegacyGL.enableBlend();
        switch (type) {
            case ParachuteEntity.TYPE_HANDHELD -> {
                this.bindTexture(TEX_1);
                ModelCache.render("parachute1");
            }
            case ParachuteEntity.TYPE_EJECTION_SEAT -> {
                this.bindTexture(TEX_2);
                ModelCache.renderPart("parachute2", entity.isOpenParachute() ? "$parachute" : "$seat");
            }
            case ParachuteEntity.TYPE_CANOPY -> {
                this.bindTexture(TEX_2);
                ModelCache.renderPart("parachute2", "$parachute");
            }
            default -> {
            }
        }
        LegacyGL.disableBlend();
        LegacyGL.popMatrix();
    }
}
