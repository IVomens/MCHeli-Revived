package com.ivomens.mcheli.world.cargo.client;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import com.ivomens.mcheli.core.math.Degrees;
import com.ivomens.mcheli.world.cargo.CargoContainerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class ContainerRenderer extends McheliEntityRenderer<CargoContainerEntity> {

    public static EntityRendererProvider<CargoContainerEntity> factory(ThermalVision thermal) {
        return context -> new ContainerRenderer(context, thermal);
    }
    private static final ResourceLocation TEX = texture("textures/container.png");

    private ContainerRenderer(EntityRendererProvider.Context context, ThermalVision thermal) {
        super(context, thermal);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void doRender(@NotNull CargoContainerEntity entity, double posX, double posY, double posZ, float entityYaw,
                         float tickTime) {
        LegacyGL.pushMatrix();
        LegacyGL.enableCull();
        LegacyGL.translate(posX, posY + 0.3, posZ);
        LegacyGL.rotate(Degrees.lerpRotation(tickTime, entity.yRotO, entity.getYRot()), 0.0F, -1.0F, 0.0F);
        LegacyGL.rotate(Degrees.lerpRotation(tickTime, entity.xRotO, entity.getXRot()), 1.0F, 0.0F, 0.0F);
        LegacyGL.color(0.75F, 0.75F, 0.75F, 1.0F);
        this.bindTexture(TEX);
        ModelCache.render("container");
        LegacyGL.popMatrix();
    }
}
