package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.content.def.vehicle.CrewEntries.CameraSpec;
import com.ivomens.mcheli.content.def.vehicle.CrewEntries.Mount;
import com.ivomens.mcheli.entity.McheliEntity;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * How the game reads a {@link VehicleFile}: the values it derives from what the file says, and the rules
 * where one setting overrides another. The run-time layouts and {@code VehicleDefinitions} both go through
 * these, so they agree.
 */
public final class VehicleRules {

    /** The most seats a vehicle can have; further seats are dropped. */
    public static final int MAX_SEATS = 30;
    /** A rotor speed above this (either way) is written 0.01 above the speed the rotor turns at. */
    private static final float ROTOR_SPEED_TRIM = 0.01F;

    private VehicleRules() {
    }

    /** Degrees per tick the rotors turn: as written less 0.01, or the kind's own speed. */
    public static float rotorSpeed(VehicleFile file) {
        Float written = file.rotorSpeed();
        if (written == null) {
            return KindProfile.of(file.kind()).rotorSpeed();
        }
        float speed = written;
        if (speed > ROTOR_SPEED_TRIM) {
            speed -= ROTOR_SPEED_TRIM;
        }
        if (speed < -ROTOR_SPEED_TRIM) {
            speed += ROTOR_SPEED_TRIM;
        }
        return speed;
    }

    /** The top speed after the server's speed setting for the kind. */
    public static float speed(VehicleFile file) {
        return scaled(file.physics().speed(), file.kind());
    }

    /** The swept-wing top speed after the server's speed setting. */
    public static float sweptSpeed(VehicleFile file) {
        return scaled(file.kindFeatures().sweptSpeed(), file.kind());
    }

    private static float scaled(float speed, VehicleKind kind) {
        return switch (kind) {
            case HELICOPTER -> (float) (speed * McheliConfig.flight().heliSpeed());
            case PLANE -> (float) (speed * McheliConfig.flight().planeSpeed());
            case SHIP -> (float) (speed * McheliConfig.flight().shipSpeed());
            case TANK -> (float) (speed * McheliConfig.flight().tankSpeed());
            case VEHICLE -> speed;
        };
    }

    /**
     * How much higher a camera sits than written, on top of the entity offset every position gets. McHeli
     * raised its TXT cameras by this when it read them; files in both formats leave it out.
     */
    public static final double CAMERA_RAISE = McheliEntity.GLOBAL_Y_OFFSET;

    /**
     * Where a camera sits: raised by {@link #CAMERA_RAISE}, then by the entity offset as a mount is, since
     * positions are given from the model's origin and the entity sits a little lower.
     */
    public static Vec3 cameraPosition(CameraSpec camera) {
        return raisedCamera(camera).add(0.0, McheliEntity.GLOBAL_Y_OFFSET, 0.0);
    }

    /** A camera's position raised by {@link #CAMERA_RAISE} alone, for code that adds the entity offset itself. */
    public static Vec3 raisedCamera(CameraSpec camera) {
        return camera.position().add(0.0, CAMERA_RAISE, 0.0);
    }

    /** Where a mounted weapon fires from, with the same offset, kept as floats. */
    public static Vec3 mountPosition(Mount mount) {
        Vec3 written = mount.position();
        return new Vec3((float) written.x, (float) written.y + McheliEntity.GLOBAL_Y_OFFSET, (float) written.z);
    }

    /** A target drone is always a UAV. */
    public static boolean isUav(VehicleFile file) {
        return file.uav().uav() || file.uav().targetDrone();
    }

    /** A UAV always shows its camera view. */
    public static boolean alwaysCameraView(VehicleFile file) {
        return file.camera().alwaysCameraView() || isUav(file);
    }

    /** The rider seats; a UAV without seats gets one at its origin for the operator. */
    public static List<Seat> riderSeats(VehicleFile file) {
        if (file.crew().seats().isEmpty() && isUav(file)) {
            return List.of(new Seat(Vec3.ZERO, null, false, false, false, 0.0F, 0.0F, SeatSchemas.DEFAULT_MIN_PITCH,
                    SeatSchemas.DEFAULT_MAX_PITCH, false, false, null, null, SeatExclusions.NONE));
        }
        return file.crew().seats();
    }

    /** Parachute drops and repelling hooks share the crew's way out; a vehicle with both has neither. */
    public static boolean dropsCrew(VehicleFile file) {
        return file.features().parachuting().enabled() && file.components().repellingHooks().isEmpty();
    }

    /** The repelling hooks; see {@link #dropsCrew}. */
    public static List<Parts.RepellingHook> repellingHooks(VehicleFile file) {
        return file.features().parachuting().enabled() ? List.of() : file.components().repellingHooks();
    }

    /** Hatches and wings share a control on planes and ships, so a plane with both has neither. */
    public static boolean hatchesAndWingsClash(VehicleFile file) {
        boolean planeLike = file.kind() == VehicleKind.PLANE || file.kind() == VehicleKind.SHIP;
        return planeLike && !file.components().hatches().isEmpty() && !file.components().wings().isEmpty();
    }

    /**
     * A weapon bay the game keeps, with the weapon sets it opens for.
     *
     * @param weaponSets indexes into the vehicle's weapon sets, see {@link #weaponSetTypes}
     */
    public record WeaponBayLink(Parts.WeaponBay bay, List<Integer> weaponSets) {
    }

    /** The types of the vehicle's weapon sets: mounts of one type that follow each other form one set. */
    public static List<String> weaponSetTypes(VehicleFile file) {
        List<String> types = new ArrayList<>();
        for (Mount mount : file.weapons()) {
            String type = mount.weapon().name();
            if (types.isEmpty() || !types.getLast().equals(type)) {
                types.add(type);
            }
        }
        return types;
    }

    /**
     * The weapon bays with the weapon sets each opens for. A bay that names no mounted weapon is dropped; the
     * legacy check then skips the bay after it, which keeps its place but opens for nothing.
     */
    public static List<WeaponBayLink> weaponBays(VehicleFile file) {
        List<String> sets = weaponSetTypes(file);
        List<WeaponBayLink> bays = new ArrayList<>();
        file.components().weaponBays().forEach(bay -> bays.add(new WeaponBayLink(bay, List.of())));
        for (int i = 0; i < bays.size(); i++) {
            List<Integer> found = new ArrayList<>();
            for (String name : bays.get(i).bay().weapons().split("\\s*/\\s*")) {
                int set = indexIgnoringCase(sets, name);
                if (set >= 0) {
                    found.add(set);
                }
            }
            if (found.isEmpty()) {
                bays.remove(i);
            } else {
                bays.set(i, new WeaponBayLink(bays.get(i).bay(), List.copyOf(found)));
            }
        }
        return List.copyOf(bays);
    }

    private static int indexIgnoringCase(List<String> names, String name) {
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /** The HUD a seat without one shows, by kind. */
    public static String defaultHud(VehicleFile file, int seat) {
        return KindProfile.of(file.kind()).hudFor(seat);
    }
}
