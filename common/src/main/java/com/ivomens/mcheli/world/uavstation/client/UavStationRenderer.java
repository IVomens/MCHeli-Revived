package com.ivomens.mcheli.world.uavstation.client;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.entity.McheliEntity;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import com.ivomens.mcheli.vehicle.uav.IUavStation.StationType;
import com.ivomens.mcheli.world.uavstation.UavStationEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class UavStationRenderer extends McheliEntityRenderer<UavStationEntity> {

    public static EntityRendererProvider<UavStationEntity> factory(ThermalVision thermal) {
        return context -> new UavStationRenderer(context, thermal);
    }

    public static final String[] MODELS = {"uav_station", "uav_portable_controller"};
    public static final String[] TEX_ON = {"uav_station_on", "uav_portable_controller_on"};
    public static final String[] TEX_OFF = {"uav_station", "uav_portable_controller"};

    private UavStationRenderer(EntityRendererProvider.Context context, ThermalVision thermal) {
        super(context, thermal);
        this.shadowRadius = 0F;
    }

    @Override
    public void doRender(@NotNull UavStationEntity entity, double x, double y, double z, float entityYaw, float tickTime) {
        StationType type = entity.getStationType();
        int index = type == StationType.SMALL ? 1 : 0;

        LegacyGL.pushMatrix();
        LegacyGL.translate(x, y + McheliEntity.GLOBAL_Y_OFFSET, z);
        LegacyGL.enableCull();
        LegacyGL.rotate(entity.getYRot(), 0.0F, -1.0F, 0.0F);
        LegacyGL.rotate(entity.getXRot(), 1.0F, 0.0F, 0.0F);
        LegacyGL.color(0.75F, 0.75F, 0.75F, 1.0F);
        LegacyGL.enableBlend();

        // the screen lights up while someone flies a UAV from the station, or once the controller is open
        boolean screenOn = switch (type) {
            case DEFAULT -> entity.getControlled() != null && entity.getRiddenByEntity() != null;
            case SMALL -> entity.coverRotation() > 0.95F;
        };
        this.bindTexture(texture("textures/", screenOn ? TEX_ON[index] : TEX_OFF[index]));

        switch (type) {
            case DEFAULT -> ModelCache.render(MODELS[index]);
            case SMALL -> this.renderPortableController(entity, MODELS[index], tickTime);
        }

        LegacyGL.disableBlend();
        LegacyGL.popMatrix();
    }

    /** The portable controller unfolds: cover, laptop lid and display open as it is deployed. */
    private void renderPortableController(UavStationEntity uav, String model, float tickTime) {
        ModelCache.renderPart(model, "$body");
        float rot = Mth.lerp(tickTime, uav.prevCoverRotation(), uav.coverRotation());
        this.renderRotPart(model, "$cover", rot * 60.0F, 0.0, -0.1812, -0.3186);
        this.renderRotPart(model, "$laptop_cover", rot * 95.0F, 0.0, -0.1808, -0.0422);
        this.renderRotPart(model, "$display", rot * -85.0F, 0.0, -0.1807, 0.2294);
    }

    /** Draws a part hinged on the X axis through {@code (x, y, z)}. */
    private void renderRotPart(String model, String part, float rot, double x, double y, double z) {
        LegacyGL.pushMatrix();
        LegacyGL.rotateAround(rot, -1.0F, 0.0F, 0.0F, x, y, z);
        ModelCache.renderPart(model, part);
        LegacyGL.popMatrix();
    }
}
