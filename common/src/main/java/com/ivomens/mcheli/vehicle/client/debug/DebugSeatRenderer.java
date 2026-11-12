package com.ivomens.mcheli.vehicle.client.debug;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import com.ivomens.mcheli.core.math.Degrees;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

/** Test-mode box renderer (seats etc.). Draws nothing unless {@code TestMode} is enabled. */
public class DebugSeatRenderer<T extends Entity> extends McheliEntityRenderer<T> {

    /** The 10 pixel cube at 1.6 times its size: one block. */
    private static final float DEBUG_CUBE_SCALE = 1.6F;

    protected final DebugModel model;
    private final float offsetX;
    private final float offsetY;
    private final float offsetZ;
    private final ResourceLocation texture;

    private DebugSeatRenderer(EntityRendererProvider.Context context, ThermalVision thermal, float x, float y, float z,
                              String textureName) {
        super(context, thermal);
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
        this.texture = texture("textures/" + textureName + ".png");
        this.model = new DebugModel();
        this.shadowRadius = 0.0F;
    }

    public static <E extends Entity> EntityRendererProvider<E> factory(ThermalVision thermal, float x, float y, float z,
                                                                    String textureName) {
        return context -> new DebugSeatRenderer<>(context, thermal, x, y, z, textureName);
    }

    @Override
    public boolean shouldRender(@NotNull T entity, @NotNull Frustum frustum, double camX, double camY, double camZ) {
        return McheliConfig.debug().testMode() && super.shouldRender(entity, frustum, camX, camY, camZ);
    }

    @Override
    public void doRender(@NotNull T entity, double posX, double posY, double posZ, float entityYaw, float partialTicks) {
        if (!McheliConfig.debug().testMode()) {
            return;
        }
        LegacyGL.pushMatrix();
        LegacyGL.translate(posX + this.offsetX, posY + this.offsetY, posZ + this.offsetZ);
        LegacyGL.scale(entity.getBbWidth(), entity.getBbHeight(), entity.getBbWidth());
        LegacyGL.color(0.5F, 0.5F, 0.5F, 1.0F);
        float yaw = -Degrees.lerpRotation(partialTicks, entity.yRotO, entity.getYRot()) - 180.0F;
        float pitch = -Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        LegacyGL.rotate(yaw, 0.0F, 1.0F, 0.0F);
        LegacyGL.rotate(pitch, 1.0F, 0.0F, 0.0F);
        this.bindTexture(this.texture);
        this.model.render(DEBUG_CUBE_SCALE);
        LegacyGL.popMatrix();
    }
}
