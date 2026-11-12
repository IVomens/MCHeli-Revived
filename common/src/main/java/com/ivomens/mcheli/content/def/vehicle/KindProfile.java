package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.CrewEntries.WheelHitBox;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.IntFunction;

/**
 * What a vehicle file of one kind gets when it leaves a setting out, and the limits that depend on the kind.
 *
 * @param maxSpeed      the highest {@code PhysicalProperties.Speed}
 * @param minPitch      the lowest pitch limit, and the default for pitch and roll
 * @param maxPitch      the highest pitch limit, and the default for pitch and roll
 * @param zoom          the camera zoom levels
 * @param soundRange    how far the engine is heard
 * @param rotorSpeed    the rotor speed, in degrees per tick, the game uses for an unset {@code RotorSpeed}
 * @param wheels        where the vehicle touches the ground without {@code Wheels.Hitboxes}
 * @param hudBySeat     the HUD a seat shows when the file names none, by seat
 */
public record KindProfile(float maxSpeed, float minPitch, float maxPitch, int zoom, float soundRange,
                          float rotorSpeed, List<WheelHitBox> wheels, IntFunction<String> hudBySeat) {

    /** Roll limits do not depend on the kind. */
    public static final float MIN_ROLL = -80.0F;
    public static final float MAX_ROLL = 80.0F;

    private static final float SLOW = 0.8F;
    private static final float FAST = 1.8F;
    private static final float WIDE_MIN_PITCH = -89.9F;
    private static final float WIDE_MAX_PITCH = 80.0F;
    private static final List<WheelHitBox> TANK_WHEELS = List.of(
            new WheelHitBox(new Vec3(1.5, -0.24, 2.0), 1.0F), new WheelHitBox(new Vec3(1.5, -0.24, -2.0), 1.0F));

    public static KindProfile of(VehicleKind kind) {
        return switch (kind) {
            case HELICOPTER -> new KindProfile(SLOW, -20.0F, 20.0F, 8, 80.0F, 79.99F, List.of(),
                    seat -> seat <= 0 ? "heli" : seat == 1 ? "heli_gnr" : "gunner");
            case PLANE, SHIP -> new KindProfile(FAST, WIDE_MIN_PITCH, WIDE_MAX_PITCH, 8, 100.0F, 47.94F, List.of(),
                    seat -> seat <= 1 ? "plane" : "gunner");
            case TANK -> new KindProfile(FAST, WIDE_MIN_PITCH, WIDE_MAX_PITCH, 8, 50.0F, 47.94F, TANK_WHEELS,
                    seat -> seat <= 1 ? "tank" : "gunner");
            case VEHICLE -> new KindProfile(SLOW, -90.0F, 90.0F, 1, 100.0F, 0.0F, List.of(), seat -> "vehicle");
        };
    }

    public KindProfile {
        wheels = List.copyOf(wheels);
    }

    /** The HUD a seat shows when the file names none. */
    public String hudFor(int seat) {
        return this.hudBySeat.apply(seat);
    }
}
