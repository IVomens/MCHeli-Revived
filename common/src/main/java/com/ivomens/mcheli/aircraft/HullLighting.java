package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.vehicle.component.SearchLightComponent;
import com.ivomens.mcheli.vehicle.component.VehicleComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/**
 * The light a vehicle is drawn with. The sky part fades slowly towards the light at the hull, so
 * flying under a roof or out of a hangar does not flicker; a lit search light draws full bright.
 */
public final class HullLighting implements VehicleComponent {

    private static final int FULL_BRIGHT = 15728880;
    private static final int MAX_SKY_BRIGHTNESS = 240;

    private final AircraftEntity vehicle;
    private final SearchLightComponent searchLight;
    private int skyBrightness = MAX_SKY_BRIGHTNESS;

    HullLighting(AircraftEntity vehicle, SearchLightComponent searchLight) {
        this.vehicle = vehicle;
        this.searchLight = searchLight;
    }

    /** Packed block and sky light for rendering. */
    public int packedLight() {
        AircraftEntity v = this.vehicle;
        if (this.searchLight.isFitted() && this.searchLight.isOn()) {
            return FULL_BRIGHT;
        }
        Level level = v.level();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(v.getX()), level.getMinBuildHeight(),
                Mth.floor(v.getZ()));
        if (!level.hasChunkAt(pos)) {
            return 0;
        }

        VehicleSpec info = v.spec();
        float sampleOffset = info != null ? info.file().armor().submergedDamageHeight() : 0.0F;
        if (v.canFloatWater()) {
            sampleOffset = Math.abs(info.file().physics().floatOffset()) + 1.0F;
        }
        double bodyOffset = (v.getBoundingBox().maxY - v.getBoundingBox().minY) * 0.66;
        pos.setY(Mth.floor(v.getY() + sampleOffset + bodyOffset));
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos) << 4;
        int skyLight = level.getBrightness(LightLayer.SKY, pos) << 4;
        if (skyLight < this.skyBrightness) {
            // game time, not the tick counter: a parked vehicle stops ticking but must still darken
            if (level.getGameTime() % 2 == 0) {
                this.skyBrightness--;
            }
        } else if (skyLight > this.skyBrightness) {
            this.skyBrightness = Math.min(this.skyBrightness + 4, MAX_SKY_BRIGHTNESS);
        }
        return this.skyBrightness << 16 | blockLight;
    }
}
