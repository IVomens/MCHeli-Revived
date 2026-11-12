package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.client.model.BakedModelBase;
import com.ivomens.mcheli.client.model.ModelBounds;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.vehicle.client.OnDemandModels;
import com.ivomens.mcheli.vehicle.client.VehicleModelRegistry;
import com.ivomens.mcheli.vehicle.client.render.StaticTracks;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/** Slowly rotating 3D preview of the aircraft model inside a GUI rectangle. */
public class WidgetAircraftViewport {

    private final ResourceLocation location;
    @Nullable
    private final VehicleSpec info;

    public WidgetAircraftViewport(ResourceLocation location, @Nullable VehicleSpec info) {
        this.location = location;
        this.info = info;
    }

    public void render(GuiGraphics g, int x, int y, int width, int height, float partialTicks) {
        // looked up every frame: on-demand loading swaps the placeholder for the real model later
        if (this.info == null || !(VehicleModelRegistry.of(this.info).body() instanceof BakedModelBase baked)) {
            // Asks the on-demand loader for the model; it shows up in a later frame.
            if (this.info != null) {
                OnDemandModels.notifyRendered(this.info);
            }
            return;
        }
        ModelBounds bounds = baked.bounds();
        double modelWidth = Math.max(Math.max(bounds.sizeX(), bounds.sizeZ()), 0.001);
        double modelHeight = Math.max(bounds.sizeY(), 0.001);
        double scale = 0.9 * Math.min(width / modelWidth, height / modelHeight);

        g.enableScissor(x, y, x + width, y + height);
        g.pose().pushPose();
        g.pose().translate(x + width / 2.0, y + height / 2.0, 150.0);
        g.pose().scale((float) scale, (float) -scale, (float) scale);
        // One turn every 18 seconds.
        float angle = (Util.getMillis() % 18000L) / 18000.0f * 360.0f;
        g.pose().mulPose(Axis.YP.rotationDegrees(angle));
        g.pose().translate(-(bounds.minX() + bounds.maxX()) / 2.0, -(bounds.minY() + bounds.maxY()) / 2.0,
                -(bounds.minZ() + bounds.maxZ()) / 2.0);

        Lighting.setupFor3DItems();
        LegacyGL.begin(g.pose(), g.bufferSource(), LightTexture.FULL_BRIGHT);
        try {
            LegacyGL.color(1.0F, 1.0F, 1.0F, 1.0F);
            LegacyGL.bindTexture(this.location);
            StaticTracks.renderStatic(baked, this.info);
        } finally {
            LegacyGL.end();
        }
        g.flush();
        Lighting.setupForFlatItems();
        g.pose().popPose();
        g.disableScissor();
    }
}
