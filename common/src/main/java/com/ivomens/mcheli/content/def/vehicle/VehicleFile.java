package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.CrewEntries.CameraSpec;
import com.ivomens.mcheli.content.def.vehicle.CrewEntries.HitBox;
import com.ivomens.mcheli.content.def.vehicle.CrewEntries.Mount;
import com.ivomens.mcheli.content.def.vehicle.CrewEntries.Splash;
import com.ivomens.mcheli.content.def.vehicle.CrewEntries.WheelHitBox;
import com.ivomens.mcheli.core.content.schema.ContentDefinition;
import com.ivomens.mcheli.core.content.schema.LocalizedName;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Everything a vehicle file says, read through {@link VehicleSchema}: helicopters, planes, ships, tanks and
 * ground vehicles share the top level and differ in their feature section and drawn parts. The nested records
 * mirror the YAML sections; list entries have records of their own.
 * <p>
 * Values are kept the way the YAML file spells them; legacy TXT spellings are normalised into that form when
 * read, so a converted file loads exactly like the original. What the game derives from them (a rotor speed
 * trimmed by 0.01, a pitch on the ground with the opposite sign, positions raised by the entity offset, the
 * speed scaled by the server config) is worked out by {@link VehicleRules} and the core when it builds the vehicle.
 *
 * @param kind           which kind of vehicle the file is, from its folder
 * @param rotorSpeed     degrees per tick, or null for the kind's own speed
 */
public record VehicleFile(String name, VehicleKind kind, LocalizedName displayName, String author, int itemId,
                          String category, Recipe recipe, boolean canRide, @Nullable Float rotorSpeed,
                          Vec3 turretPosition, @Nullable Vec3 unmountPosition, boolean creativeOnly,
                          boolean regeneration, boolean invulnerable, Fuel fuel, int maxHp, float stealth,
                          float ammoSupplyRange, Repair repairOtherVehicles, RadarNames radarNames,
                          int explosionSizeByCrash, float throttleDownFactor, CameraSettings camera,
                          Sound sound, Physics physics, Render render, Armor armor, List<Mount> weapons,
                          Crew crew, List<HitBox> boundingBoxes, Wheels wheels, Uav uav,
                          AircraftFeatures features, KindFeatures kindFeatures, Components components)
        implements ContentDefinition {

    public VehicleFile {
        weapons = List.copyOf(weapons);
        boundingBoxes = List.copyOf(boundingBoxes);
    }

    /** The same file with other weapon mounts; the link step leaves out those of weapons that are not loaded. */
    public VehicleFile withWeapons(List<Mount> mounts) {
        return new VehicleFile(this.name, this.kind, this.displayName, this.author, this.itemId, this.category,
                this.recipe, this.canRide, this.rotorSpeed, this.turretPosition, this.unmountPosition,
                this.creativeOnly, this.regeneration, this.invulnerable, this.fuel, this.maxHp, this.stealth,
                this.ammoSupplyRange, this.repairOtherVehicles, this.radarNames, this.explosionSizeByCrash,
                this.throttleDownFactor, this.camera, this.sound, this.physics, this.render, this.armor, mounts,
                this.crew, this.boundingBoxes, this.wheels, this.uav, this.features, this.kindFeatures,
                this.components);
    }

    /** The same file with other equipment. */
    public VehicleFile withFeatures(AircraftFeatures changed) {
        return new VehicleFile(this.name, this.kind, this.displayName, this.author, this.itemId, this.category,
                this.recipe, this.canRide, this.rotorSpeed, this.turretPosition, this.unmountPosition,
                this.creativeOnly, this.regeneration, this.invulnerable, this.fuel, this.maxHp, this.stealth,
                this.ammoSupplyRange, this.repairOtherVehicles, this.radarNames, this.explosionSizeByCrash,
                this.throttleDownFactor, this.camera, this.sound, this.physics, this.render, this.armor,
                this.weapons, this.crew, this.boundingBoxes, this.wheels, this.uav, changed, this.kindFeatures,
                this.components);
    }

    /** YAML {@code Recepie}: how the vehicle's item is crafted. */
    public record Recipe(boolean shaped, List<String> pattern) {

        public Recipe {
            pattern = List.copyOf(pattern);
        }
    }

    /**
     * Fuel settings, spread over the top level ({@code MaxFuel}, {@code FuelType}, {@code FuelConsumption},
     * {@code FuelSupplyRange}) and the {@code FuelSupply} and {@code Electric} sections.
     *
     * @param types         fluids the vehicle burns, by name, with how fast each is used up
     * @param supplyRange   how far the vehicle refuels others; 0 for no fuel supply
     * @param supplyType    the fluid it hands out
     * @param electric      the vehicle runs on charge instead of fluid
     * @param batteries     batteries in the fuel slot extend the charge
     */
    public record Fuel(int maxFuel, Map<String, Float> types, float consumption, float supplyRange,
                       boolean infiniteSupply, String supplyType, boolean electric, boolean batteries) {

        public Fuel {
            types = Map.copyOf(types);
        }
    }

    /** YAML {@code RepairOtherVehicles}: repairing vehicles nearby. */
    public record Repair(float range, int value) {
    }

    /** How the vehicle is labelled on the four radar screens. */
    public record RadarNames(String modernAirToAir, String earlyAirToAir, String modernAirToSurface,
                             String earlyAirToSurface) {
    }

    /**
     * YAML {@code Camera}.
     *
     * @param zoom      the zoom levels of the camera
     * @param positions third person camera positions, cycled through by the player
     */
    public record CameraSettings(float thirdPersonDistance, boolean alwaysCameraView, int zoom,
                                 boolean defaultFreeLook, float rotationSpeed, List<CameraSpec> positions) {

        public CameraSettings {
            positions = List.copyOf(positions);
        }
    }

    /** YAML {@code Sound}: the engine sound. */
    public record Sound(@Nullable ResourceLocation moveSound, float volume, float pitch, float range) {
    }

    /**
     * YAML {@code Render}.
     *
     * @param textures extra textures the player can switch to, after the one named like the file
     */
    public record Render(List<String> textures, boolean smoothShading, boolean hideRiders, float modelWidth,
                         float modelHeight, float modelPitch, float modelRoll, float particleScale,
                         boolean seaSurfaceParticles, List<Splash> splashParticles) {

        public Render {
            textures = List.copyOf(textures);
            splashParticles = List.copyOf(splashParticles);
        }
    }

    /** YAML {@code Armor}. */
    public record Armor(float armorDamageFactor, float armorMinDamage, float armorMaxDamage, float damageFactor,
                        float submergedDamageHeight) {
    }

    /**
     * Seats, racks and how they relate.
     *
     * @param exclusiveSeats groups of seats (numbered from 1, racks after the seats) of which only one may be
     *                       taken at a time, as legacy TXT {@code ExclusionSeat} lines give them
     * @param gunnerHud      the HUD the pilot of a one-seat vehicle sees in gunner mode, or null for the kind's
     * @param rideRacks      racks of other vehicles this one can ride on, by vehicle name, in file order
     */
    public record Crew(List<Seat> seats, List<Rack> racks, List<List<Integer>> exclusiveSeats,
                       @Nullable String gunnerHud, List<RideRack> rideRacks) {

        public Crew {
            seats = List.copyOf(seats);
            racks = List.copyOf(racks);
            exclusiveSeats = exclusiveSeats.stream().<List<Integer>>map(List::copyOf).toList();
            rideRacks = List.copyOf(rideRacks);
        }
    }

    /** One entry of YAML {@code RideRack}: the vehicle can ride rack {@code rack} of vehicle {@code name}. */
    public record RideRack(String name, int rack) {
    }

    /**
     * YAML {@code Wheels}.
     *
     * @param hitBoxes      where the vehicle touches the ground
     * @param wheelRotation how fast the drawn wheels turn with speed
     * @param trackRotation how fast the drawn track rollers turn with speed
     */
    public record Wheels(List<WheelHitBox> hitBoxes, float wheelRotation, float trackRotation) {

        public Wheels {
            hitBoxes = List.copyOf(hitBoxes);
        }
    }

    /**
     * YAML {@code Uav}.
     *
     * @param range the control range in blocks, or -1 for the wingman controller's range
     */
    public record Uav(boolean uav, boolean small, boolean newStyle, boolean targetDrone, int range) {
    }
}
