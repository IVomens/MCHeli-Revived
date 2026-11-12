package com.ivomens.mcheli.equipment.gltd.client;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.client.render.RenderLib;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import com.ivomens.mcheli.equipment.gltd.GltdEntity;
import com.ivomens.mcheli.platform.LocalClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/** Ground laser target designator: a tripod body, a head aimed by the operator, and its laser. */
public class GltdRenderer extends McheliEntityRenderer<GltdEntity> {

    public static EntityRendererProvider<GltdEntity> factory(ThermalVision thermal) {
        return context -> new GltdRenderer(context, thermal);
    }

    private static final ResourceLocation TEX = texture("textures/gltd.png");
    private static final Vec3[] LASER = {new Vec3(0.0, 0.2, 0.0), new Vec3(0.0, 0.2, 100.0)};
    /** Light green; a random extra alpha of up to 63 makes the beam flicker. */
    private static final int LASER_COLOR = 0x6080FF80;
    private static final double HEAD_PIVOT_Y = 0.45;
    private static final double LASER_LENGTH = 100.0;

    private final RandomSource random = RandomSource.create();

    private GltdRenderer(EntityRendererProvider.Context context, ThermalVision thermal) {
        super(context, thermal);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void doRender(@NotNull GltdEntity entity, double posX, double posY, double posZ, float entityYaw,
                         float tickTime) {
        IModelCustom model = ModelCache.find("gltd");
        if (model == null) {
            return;
        }
        LegacyGL.pushMatrix();
        LegacyGL.translate(posX, posY + 0.25, posZ);
        this.setCommonRenderParam();
        this.bindTexture(TEX);

        Entity operator = entity.getRiddenByEntity();
        boolean hideHead = false;
        if (operator != null) {
            entity.setIsUsedPlayer(true);
            entity.setRenderRotationYaw(operator.getYRot());
            entity.setRenderRotationPitch(operator.getXRot());
            // the operator looks through the head in first person
            hideHead = Minecraft.getInstance().options.getCameraType().isFirstPerson()
                    && LocalClient.get().isLocalPlayer(operator);
        }

        // once used, the head keeps the operator's aim and only the body turns with the entity
        if (entity.isUsedPlayer()) {
            LegacyGL.pushMatrix();
        }
        LegacyGL.rotate(-entity.getYRot(), 0.0F, 1.0F, 0.0F);
        model.renderPart("$body");
        if (entity.isUsedPlayer()) {
            LegacyGL.popMatrix();
            LegacyGL.translate(0.0, HEAD_PIVOT_Y, 0.0);
            LegacyGL.rotate(entity.renderRotationYaw(), 0.0F, -1.0F, 0.0F);
            LegacyGL.rotate(entity.renderRotationPitch(), 1.0F, 0.0F, 0.0F);
            LegacyGL.translate(0.0, -HEAD_PIVOT_Y, 0.0);
        }
        if (!hideHead) {
            model.renderPart("$head");
        }

        LegacyGL.translate(0.0, HEAD_PIVOT_Y, 0.0);
        this.restoreCommonRenderParam();
        LegacyGL.disableLighting();
        RenderLib.drawLine(LASER, LASER_COLOR | this.random.nextInt(64) << 24);
        LegacyGL.enableLighting();
        LegacyGL.popMatrix();
    }

    @Override
    public boolean shouldRender(@NotNull GltdEntity entity, @NotNull Frustum frustum, double camX, double camY, double camZ) {
        // the laser can be on screen while the device itself is not
        return isVisible(entity, frustum, LASER_LENGTH, camX, camY, camZ);
    }
}
