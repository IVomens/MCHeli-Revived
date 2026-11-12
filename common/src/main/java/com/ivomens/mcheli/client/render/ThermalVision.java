package com.ivomens.mcheli.client.render;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.resources.ResourceLocation;

/**
 * Thermal-camera texture replacement.
 * <p>
 * While the player looks through a camera in thermal mode (camera mode 2), every texture the mod's renderers
 * bind is replaced by {@link #THERMAL_TEXTURE}; the thermal post shader covers the rest of the scene. One per
 * client: the client tick sets the camera mode every frame, the renderers ask for their textures.
 */
public final class ThermalVision {

    /** What every texture becomes while the thermal camera is on. */
    public static final ResourceLocation THERMAL_TEXTURE = Ids.of("textures/test.png");
    private static final int THERMAL_MODE = 2;

    /** The camera mode of the view: 0 normal, 1 night vision, 2 thermal. */
    private int cameraMode;

    /** Called every frame with the camera mode of the view. */
    public void setCameraMode(int mode) {
        this.cameraMode = mode;
    }

    /** Whether textures are replaced this frame: the view is thermal and the config allows it. */
    public boolean isActive() {
        return McheliConfig.render().enableReplaceTextureManager() && this.cameraMode == THERMAL_MODE;
    }

    /** The texture to bind for {@code texture} this frame. */
    public ResourceLocation remap(ResourceLocation texture) {
        return this.isActive() ? THERMAL_TEXTURE : texture;
    }
}
