package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.CrewEntries.CameraSpec;
import com.ivomens.mcheli.core.content.schema.EntrySchema;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.content.schema.Vectors;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.FLOAT;
import static com.ivomens.mcheli.core.content.schema.Codecs.LOWER_TEXT;

/** The entries of YAML {@code Seats} and {@code Racks}; see {@link CrewSchema} for an example. */
final class SeatSchemas {

    /** How far a seated gunner may look down and up when the file does not say. */
    static final float DEFAULT_MIN_PITCH = -30.0F;
    static final float DEFAULT_MAX_PITCH = 70.0F;
    /** Legacy TXT rack entry points are written this much lower than they are used. */
    private static final double TXT_RACK_RAISE = 1.5;

    static final Field<Seat, Vec3> POSITION = Field.of("Position", Vectors.VEC3, null, Seat::position)
            .alias("Pos");
    static final Field<Seat, Vec3> UNMOUNT_POSITION =
            Field.of("UnmountPos", Vectors.VEC3, null, Seat::unmountPosition);
    static final Field<Seat, Boolean> GUNNER = Field.of("Gunner", BOOLEAN, false, Seat::gunner);
    static final Field<Seat, Boolean> SWITCH_GUNNER = Field.of("SwitchGunner", BOOLEAN, false, Seat::switchGunner);
    static final Field<Seat, Boolean> FIXED_ROTATION = Field.of("FixRot", BOOLEAN, false, Seat::fixedRotation);
    static final Field<Seat, Float> FIXED_YAW = Field.of("FixYaw", FLOAT, 0.0F, Seat::fixedYaw);
    static final Field<Seat, Float> FIXED_PITCH = Field.of("FixPitch", FLOAT, 0.0F, Seat::fixedPitch);
    static final Field<Seat, Float> MIN_PITCH = Field.of("MinPitch", FLOAT, DEFAULT_MIN_PITCH, Seat::minPitch);
    static final Field<Seat, Float> MAX_PITCH = Field.of("MaxPitch", FLOAT, DEFAULT_MAX_PITCH, Seat::maxPitch);
    static final Field<Seat, Boolean> ROTATING = Field.of("RotSeat", BOOLEAN, false, Seat::rotating);
    static final Field<Seat, Boolean> INVERT_CAMERA = Field.of("InvCamPos", BOOLEAN, false, Seat::invertCamera);
    /** Where the gunner looks from. TXT spells it with the seat, as the same position. */
    static final Field<Seat, CameraSpec> CAMERA = Field.entry("Camera", () -> CrewSchema.CAMERA, Seat::camera)
            .alias("Cam").txt(CrewSchema.txtCamera());
    /** The HUD shown in the seat; a seat without one shows none. */
    static final Field<Seat, String> HUD = Field.of("Hud", LOWER_TEXT, null, Seat::hud);
    static final Field<Seat, SeatExclusions> EXCLUDE_WITH =
            Field.of("ExcludeWith", SeatExclusions.CODEC, SeatExclusions.NONE, Seat::excludeWith);
    static final EntrySchema<Seat> SEAT = new EntrySchema<>("seat",
            Section.root(POSITION, UNMOUNT_POSITION, GUNNER, SWITCH_GUNNER, FIXED_ROTATION, FIXED_YAW, FIXED_PITCH,
                    MIN_PITCH, MAX_PITCH, ROTATING, INVERT_CAMERA, CAMERA, HUD, EXCLUDE_WITH).requires(POSITION),
            SeatSchemas::seat);

    static final Field<Rack, Vec3> RACK_POSITION = Field.of("Position", Vectors.VEC3, null, Rack::position)
            .alias("Pos");
    static final Field<Rack, Vec3> RACK_UNMOUNT_POSITION =
            Field.of("UnmountPos", Vectors.VEC3, null, Rack::unmountPosition);
    /** Where a vehicle has to be to be loaded. TXT writes it 1.5 lower than YAML. */
    static final Field<Rack, CameraSpec> RACK_ENTRY = Field.entry("Camera", () -> CrewSchema.CAMERA, Rack::entry)
            .alias("Cam").txt(CrewSchema.txtCamera(TXT_RACK_RAISE));
    static final Field<Rack, List<String>> RACK_NAMES =
            Field.of("Names", VehicleCodecs.ONE_OR_MORE_NAMES, List.of(), Rack::names).alias("Name");
    /** How close a vehicle must come; TXT racks default to 6 blocks. */
    static final Field<Rack, Float> RACK_RANGE = Field.of("Range", FLOAT, 0.0F, Rack::range);
    static final Field<Rack, Float> RACK_YAW = Field.of("Yaw", FLOAT, 0.0F, Rack::yaw);
    static final Field<Rack, Float> RACK_PITCH = Field.of("Pitch", FLOAT, 0.0F, Rack::pitch);
    static final Field<Rack, Boolean> RACK_ROTATING = Field.of("RotSeat", BOOLEAN, false, Rack::rotating);
    static final Field<Rack, SeatExclusions> RACK_EXCLUDE_WITH =
            Field.of("ExcludeWith", SeatExclusions.CODEC, SeatExclusions.NONE, Rack::excludeWith);
    static final EntrySchema<Rack> RACK = new EntrySchema<>("rack",
            Section.root(RACK_POSITION, RACK_UNMOUNT_POSITION, RACK_ENTRY, RACK_NAMES, RACK_RANGE, RACK_YAW,
                            RACK_PITCH, RACK_ROTATING, RACK_EXCLUDE_WITH)
                    .unsupported("OpenParaAlt").requires(RACK_POSITION).requires(RACK_ENTRY),
            SeatSchemas::rack);

    private SeatSchemas() {
    }

    private static Seat seat(Values v) {
        return new Seat(v.get(POSITION), v.get(UNMOUNT_POSITION), v.get(GUNNER), v.get(SWITCH_GUNNER),
                v.get(FIXED_ROTATION), v.get(FIXED_YAW), v.get(FIXED_PITCH), v.get(MIN_PITCH), v.get(MAX_PITCH),
                v.get(ROTATING), v.get(INVERT_CAMERA), v.get(CAMERA), v.get(HUD), v.get(EXCLUDE_WITH));
    }

    private static Rack rack(Values v) {
        return new Rack(v.get(RACK_POSITION), v.get(RACK_UNMOUNT_POSITION), v.get(RACK_ENTRY), v.get(RACK_NAMES),
                v.get(RACK_RANGE), v.get(RACK_YAW), v.get(RACK_PITCH), v.get(RACK_ROTATING),
                v.get(RACK_EXCLUDE_WITH));
    }
}
