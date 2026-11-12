package com.ivomens.mcheli.combat.countermeasure.client;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.combat.countermeasure.FlareEntity;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class FlareRenderer extends McheliEntityRenderer<FlareEntity> {

    public static EntityRendererProvider<FlareEntity> factory(ThermalVision thermal) {
        return context -> new FlareRenderer(context, thermal);
    }
    private static final ResourceLocation TEX = texture("textures/flare.png");
    private final ModelFlare model = new ModelFlare();

    private FlareRenderer(EntityRendererProvider.Context context, ThermalVision thermal) {
        super(context, thermal);
    }

    @Override
    public void doRender(@NotNull FlareEntity entity, double posX, double posY, double posZ, float entityYaw,
                         float partialTicks) {
        LegacyGL.color(1.0F, 1.0F, 0.5F, 1.0F);
        this.bindTexture(TEX);
        this.model.render(entity, posX, posY, posZ, 1.0F);
    }
}
