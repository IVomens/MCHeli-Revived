package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.content.def.vehicle.KindProfile;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.def.vehicle.VehicleKinds;
import com.ivomens.mcheli.content.def.vehicle.VehicleRules;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.content.registry.ContentId;
import com.ivomens.mcheli.vehicle.definition.VehicleDefinition;
import com.ivomens.mcheli.vehicle.definition.VehicleDefinitions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A vehicle type as the game uses it, built once when its file loads: the {@link VehicleFile} with everything
 * the file says, the core's frozen {@link VehicleDefinition}, and the layouts run-time code reads, in which the
 * game's derivations (seat camera offsets, opening factors, the settings that cancel each other) are worked
 * out. A reload builds a new one instead of changing this one.
 *
 * @param speed    the top speed after the server's speed setting, as it was when the vehicle loaded
 * @param textures the file's own texture first, then the extra ones the player can switch to
 */
public record VehicleSpec(VehicleFile file, VehicleDefinition definition, float speed, CrewLayout crew,
                          Armament armament, DrawnParts parts, KindParts kindParts, HitBoxes hitBoxes,
                          List<String> textures) {

    public VehicleSpec {
        textures = List.copyOf(textures);
    }

    /** Builds the run-time layout of a vehicle file. */
    public static VehicleSpec of(ContentEntry<VehicleFile> entry) {
        VehicleFile file = entry.definition();
        boolean legacyTxt = entry.isLegacyTxt();
        return new VehicleSpec(file, VehicleDefinitions.build(file), VehicleRules.speed(file),
                VehicleLayouts.crew(file), VehicleLayouts.armament(file), VehicleLayouts.drawnParts(file, legacyTxt),
                VehicleLayouts.kindParts(file, legacyTxt), VehicleLayouts.hitBoxes(file, legacyTxt),
                VehicleLayouts.textures(file));
    }

    /** The file name without extension, which items, saved vehicles and other content refer to. */
    public String name() {
        return this.file.name();
    }

    /** The vehicle kind, from the folder the file was loaded from. */
    public VehicleKind kind() {
        return this.file.kind();
    }

    public ContentId id() {
        return new ContentId(VehicleKinds.contentType(kind()), name());
    }

    /** The kind's name in rack and HUD settings: {@code helicopter}, {@code plane} and so on. */
    public String kindName() {
        return kind().name().toLowerCase(Locale.ROOT);
    }

    /** A target drone is always a UAV. */
    public boolean isUAV() {
        return VehicleRules.isUav(this.file);
    }

    /** A UAV always shows its camera view. */
    public boolean alwaysCameraView() {
        return VehicleRules.alwaysCameraView(this.file);
    }

    /** Whether the crew can drop by parachute; repelling hooks take the place of parachutes. */
    public boolean dropsCrew() {
        return VehicleRules.dropsCrew(this.file);
    }

    /** Degrees per tick the rotors turn. */
    public float rotorSpeed() {
        return VehicleRules.rotorSpeed(this.file);
    }

    public boolean isFuelValid(@Nullable Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.FLUID.getKey(fluid);
        Map<String, Float> types = this.file.fuel().types();
        return types.containsKey(key.toString()) || types.containsKey(key.getPath());
    }

    public float getFuelConsumption(String fluidName) {
        return this.file.fuel().types().getOrDefault(fluidName, 1.0F);
    }

    /** The highest speed a file of this kind may give. */
    public float getMaxSpeed() {
        return KindProfile.of(kind()).maxSpeed();
    }

    public float getMinRotationPitch() {
        return KindProfile.of(kind()).minPitch();
    }

    public float getMaxRotationPitch() {
        return KindProfile.of(kind()).maxPitch();
    }

    public float getMinRotationRoll() {
        return KindProfile.MIN_ROLL;
    }

    public float getMaxRotationRoll() {
        return KindProfile.MAX_ROLL;
    }

    /** The HUD a seat without one shows. */
    public String getDefaultHudName(int seatId) {
        return VehicleRules.defaultHud(this.file, seatId);
    }

    /** The texture after {@code current} in the vehicle's list, for the player cycling through them. */
    public String getNextTextureName(String current) {
        if (this.textures.size() >= 2) {
            for (int i = 0; i < this.textures.size(); i++) {
                if (this.textures.get(i).equalsIgnoreCase(current)) {
                    return this.textures.get((i + 1) % this.textures.size());
                }
            }
        }
        return current;
    }

    @Override
    public String toString() {
        return "VehicleSpec{name='" + name() + "', kind=" + kind() + "}";
    }
}
