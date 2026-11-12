package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.CrewEntries.CameraSpec;
import com.ivomens.mcheli.content.def.vehicle.CrewEntries.Mount;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile.Crew;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile.RideRack;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition;
import com.ivomens.mcheli.core.content.schema.EntrySchema;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Ref;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.content.schema.Vectors;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Function;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.FLOAT;
import static com.ivomens.mcheli.core.content.schema.Codecs.INT;
import static com.ivomens.mcheli.core.content.schema.Codecs.LOWER_TEXT;
import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * Who rides and what they fire: YAML {@code Weapons}, {@code Seats}, {@code GunnerHud}, {@code Racks},
 * {@code ExclusiveSeats} and {@code RideRack}.
 * <pre>
 * Weapons:
 * - Type: m230                   # a weapon file name
 *   Position: [0.0, -1.0, 3.0]
 *   SeatID: 2                    # the seat that fires it, from 1; 1 is the pilot
 * Seats:
 * - Position: [0.0, 1.0, 2.0]
 *   Hud: heli
 * - Position: [0.0, 1.0, 1.0]
 *   Gunner: true
 *   Camera: { Pos: [0.0, 1.0, -2.0] }
 *   ExcludeWith: { Seats: [3] }
 * Racks:
 * - Position: [0.0, -1.0, 0.0]
 *   Camera: { Pos: [0.0, 2.5, 0.0] }    # where a vehicle has to be to be loaded
 *   Names: [container, fuel_tank]
 *   Range: 6.0
 * </pre>
 */
final class CrewSchema {

    static final Field<CameraSpec, Vec3> CAMERA_POSITION =
            Field.of("Pos", Vectors.VEC3, Vec3.ZERO, CameraSpec::position).alias("Position").alwaysWritten();
    static final Field<CameraSpec, Boolean> CAMERA_FIXED =
            Field.of("FixedRot", BOOLEAN, false, CameraSpec::fixedRotation);
    static final Field<CameraSpec, Float> CAMERA_YAW = Field.of("Yaw", FLOAT, 0.0F, CameraSpec::yaw);
    static final Field<CameraSpec, Float> CAMERA_PITCH = Field.of("Pitch", FLOAT, 0.0F, CameraSpec::pitch);
    static final EntrySchema<CameraSpec> CAMERA = EntrySchema.of("camera position",
            v -> new CameraSpec(v.get(CAMERA_POSITION), v.get(CAMERA_FIXED), v.get(CAMERA_YAW), v.get(CAMERA_PITCH)),
            CAMERA_POSITION, CAMERA_FIXED, CAMERA_YAW, CAMERA_PITCH);

    static final Field<Mount, Ref<WeaponDefinition>> MOUNT_WEAPON =
            Field.of("Type", Ref.<WeaponDefinition>lowerCase(), null, Mount::weapon).alias("WeaponType");
    static final Field<Mount, Vec3> MOUNT_POSITION =
            Field.of("Position", Vectors.FLOAT_VEC3, Vec3.ZERO, Mount::position).alias("Pos").alwaysWritten();
    static final Field<Mount, Float> MOUNT_YAW = Field.of("Yaw", FLOAT, 0.0F, Mount::yaw);
    static final Field<Mount, Float> MOUNT_PITCH = Field.of("Pitch", FLOAT, 0.0F, Mount::pitch);
    /** The seat that fires it, numbered from 1 in the file and from 0 in the game; 0 or 1 is the pilot. */
    static final Field<Mount, Integer> MOUNT_SEAT =
            Field.of("SeatID", INT.map(seat -> Math.max(0, seat - 1), index -> index + 1), 0, Mount::seat)
                    .txt(ints(1, VehicleRules.MAX_SEATS).map(seat -> seat - 1, index -> index + 1));
    /** The pilot can always fire the pilot seat's weapons. */
    static final Field<Mount, Boolean> MOUNT_PILOT_CAN_USE =
            Field.of("CanUsePilot", BOOLEAN, true, Mount::pilotCanUse);
    static final Field<Mount, Float> MOUNT_DEFAULT_YAW =
            Field.of("DefaultYaw", FLOAT.map(Mth::wrapDegrees, yaw -> yaw), 0.0F, Mount::defaultYaw);
    static final Field<Mount, Float> MOUNT_MIN_YAW = Field.of("MinYaw", FLOAT, 0.0F, Mount::minYaw);
    static final Field<Mount, Float> MOUNT_MAX_YAW = Field.of("MaxYaw", FLOAT, 0.0F, Mount::maxYaw);
    static final Field<Mount, Float> MOUNT_MIN_PITCH = Field.of("MinPitch", FLOAT, 0.0F, Mount::minPitch);
    static final Field<Mount, Float> MOUNT_MAX_PITCH = Field.of("MaxPitch", FLOAT, 0.0F, Mount::maxPitch);
    static final Field<Mount, Boolean> MOUNT_TURRET = Field.of("Turret", BOOLEAN, false, Mount::turret);
    static final EntrySchema<Mount> MOUNT = new EntrySchema<>("weapon",
            Section.root(MOUNT_WEAPON, MOUNT_POSITION, MOUNT_YAW, MOUNT_PITCH, MOUNT_SEAT, MOUNT_PILOT_CAN_USE,
                    MOUNT_DEFAULT_YAW, MOUNT_MIN_YAW, MOUNT_MAX_YAW, MOUNT_MIN_PITCH, MOUNT_MAX_PITCH, MOUNT_TURRET)
                    .unsupported("MuzzleFlashPos").requires(MOUNT_WEAPON),
            CrewSchema::mount);

    /** The weapons mounted on the vehicle, each naming a weapon file and the seat that fires it. */
    static final Field<VehicleFile, List<Mount>> WEAPONS = Field.listOf("Weapons", () -> MOUNT,
            (VehicleFile file) -> file.weapons());
    /** The seats; the first is the pilot or driver. */
    static final Field<VehicleFile, List<Seat>> SEATS = Field.listOf("Seats", () -> SeatSchemas.SEAT,
            crew(Crew::seats));
    /** The HUD the pilot of a one-seat vehicle sees in gunner mode; the kind's own when left out. */
    static final Field<VehicleFile, String> GUNNER_HUD = Field.of("GunnerHud", LOWER_TEXT, null, crew(Crew::gunnerHud));
    /** Racks that carry other vehicles or items. */
    static final Field<VehicleFile, List<Rack>> RACKS = Field.listOf("Racks", () -> SeatSchemas.RACK,
            crew(Crew::racks));
    /**
     * Groups of seats of which only one may be taken at a time, by number from 1 with racks after the seats.
     * Usually written as {@code ExcludeWith} on the seats; converted TXT files use it for groups of three or
     * more.
     */
    static final Field<VehicleFile, List<List<Integer>>> EXCLUSIVE_SEATS = Field.of("ExclusiveSeats",
            VehicleCodecs.SEAT_GROUPS, List.of(), crew(Crew::exclusiveSeats));
    /** The racks of other vehicles this one can ride on. */
    static final Field<VehicleFile, List<RideRack>> RIDE_RACKS = Field.of("RideRack", VehicleCodecs.RIDE_RACKS,
            List.of(), crew(Crew::rideRacks));

    private CrewSchema() {
    }

    static Crew build(Values v) {
        return new Crew(v.get(SEATS), v.get(RACKS), v.get(EXCLUSIVE_SEATS), v.get(GUNNER_HUD), v.get(RIDE_RACKS));
    }

    /**
     * The legacy TXT spelling of a seat or rack camera: its position, read as doubles and raised by the given
     * steps in turn, so it lands where YAML puts the same camera. Both formats write a camera without the
     * raise the game gives it ({@link VehicleRules#cameraPosition}).
     */
    static ValueCodec<CameraSpec> txtCamera(double... raises) {
        return ValueCodec.of(raw -> {
            throw new UnsupportedOperationException("YAML cameras are read through their entry schema");
        }, text -> {
            Vec3 position = Vectors.VEC3.fromText(text, null, message -> {
            });
            for (double raise : raises) {
                position = position.add(0.0, raise, 0.0);
            }
            return new CameraSpec(position, false, 0.0F, 0.0F);
        }, camera -> null);
    }

    private static Mount mount(Values v) {
        int seat = v.get(MOUNT_SEAT);
        return new Mount(v.get(MOUNT_WEAPON), v.get(MOUNT_POSITION), v.get(MOUNT_YAW), v.get(MOUNT_PITCH),
                v.get(MOUNT_PILOT_CAN_USE) || seat == 0, seat, v.get(MOUNT_DEFAULT_YAW), v.get(MOUNT_MIN_YAW),
                v.get(MOUNT_MAX_YAW), v.get(MOUNT_MIN_PITCH), v.get(MOUNT_MAX_PITCH), v.get(MOUNT_TURRET));
    }

    private static <T> Function<VehicleFile, T> crew(Function<Crew, T> getter) {
        return file -> getter.apply(file.crew());
    }
}
