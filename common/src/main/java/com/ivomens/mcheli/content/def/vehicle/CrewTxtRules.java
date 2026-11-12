package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.CrewEntries.CameraSpec;
import com.ivomens.mcheli.content.def.vehicle.CrewEntries.Mount;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile.RideRack;
import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.Schema;
import com.ivomens.mcheli.core.content.schema.TxtKey.TxtRule;
import com.ivomens.mcheli.core.content.schema.TxtTuple;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.content.schema.Vectors;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Legacy TXT lines about the crew: seats, racks, weapons, HUDs and seat exclusions. Two of them only say
 * where they belong once the whole file is read, so they are kept aside while reading and settled at the end:
 * <ul>
 *   <li>{@code HUD = heli, heli_gnr} names the HUD of each seat, which may come later; seats it leaves out show
 *       their kind's HUD, and a second name for a one-seat vehicle is its gunner-mode HUD;</li>
 *   <li>{@code ExclusionSeat = 2, 3} groups seats and racks, numbered together; a pair becomes the first
 *       one's {@code ExcludeWith}, a larger group stays an {@code ExclusiveSeats} group.</li>
 * </ul>
 */
final class CrewTxtRules {

    /** Kept in the values while a TXT file is read; never part of the definition. */
    private static final Field<VehicleFile, List<String>> HUD_LINE =
            Field.of("", ValueCodec.of(raw -> List.of(), List::of, names -> null), List.of(), file -> List.of());
    private static final Field<VehicleFile, List<List<Integer>>> EXCLUSION_LINES =
            Field.of("", VehicleCodecs.SEAT_GROUPS, List.of(), file -> List.of());

    private static final double TXT_RACK_RANGE = 6.0;
    /** Value counts of a gunner seat line: with a camera, and with the pair after the switch flag. */
    private static final int CAMERA_GIVEN = 6;
    private static final int PAIR_GIVEN = 9;
    private static final int MAX_RACK_ID = 10_000;

    /** {@code AddSeat = x, y, z[, rotSeat[, unmountX, unmountY, unmountZ]]}. */
    static final TxtTuple<Seat> ADD_SEAT = TxtTuple.of(SeatSchemas.SEAT).minimum(3)
            .vector(SeatSchemas.POSITION)
            .value(SeatSchemas.ROTATING)
            .vector(SeatSchemas.UNMOUNT_POSITION);

    /**
     * {@code AddGunnerSeat = x, y, z[, cameraX, cameraY, cameraZ[, switchGunner[, minPitch, maxPitch[, rotSeat[,
     * unmountX, unmountY, unmountZ]]]]]}. Without a camera the gunner looks from the seat; the pitch limits are
     * from -90 to 90, in either order.
     */
    static final TxtTuple<Seat> ADD_GUNNER_SEAT = TxtTuple.of(SeatSchemas.SEAT).minimum(3)
            .fixed(SeatSchemas.GUNNER, true)
            .vector(SeatSchemas.POSITION)
            .vector(SeatSchemas.CAMERA, seatLevelCamera())
            .value(SeatSchemas.SWITCH_GUNNER)
            .value(SeatSchemas.MIN_PITCH).value(SeatSchemas.MAX_PITCH)
            .value(SeatSchemas.ROTATING)
            .vector(SeatSchemas.UNMOUNT_POSITION)
            .then((seat, file, count) -> gunnerSeat(seat, count));

    /**
     * {@code AddFixRotSeat = x, y, z[, cameraX, cameraY, cameraZ[, switchGunner[, yaw, pitch[, rotSeat[,
     * unmountX, unmountY, unmountZ]]]]]}: a gunner seat whose view is fixed when the yaw and pitch are given.
     */
    static final TxtTuple<Seat> ADD_FIXED_SEAT = TxtTuple.of(SeatSchemas.SEAT).minimum(3)
            .fixed(SeatSchemas.GUNNER, true)
            .vector(SeatSchemas.POSITION)
            .vector(SeatSchemas.CAMERA, seatLevelCamera())
            .value(SeatSchemas.SWITCH_GUNNER)
            .value(SeatSchemas.FIXED_YAW).value(SeatSchemas.FIXED_PITCH)
            .value(SeatSchemas.ROTATING)
            .vector(SeatSchemas.UNMOUNT_POSITION)
            .then((seat, file, count) -> fixedSeat(seat, count));

    /**
     * {@code AddRack = names, x, y, z, entryX, entryY, entryZ[, range[, parachuteAltitude[, yaw[, pitch[,
     * rotSeat[, unmountX, unmountY, unmountZ]]]]]]}, names separated by {@code /}. The parachute altitude was
     * never used.
     */
    static final TxtTuple<Rack> ADD_RACK = TxtTuple.of(SeatSchemas.RACK).minimum(7)
            .value(SeatSchemas.RACK_NAMES)
            .vector(SeatSchemas.RACK_POSITION)
            .vector(SeatSchemas.RACK_ENTRY)
            .value(SeatSchemas.RACK_RANGE, (float) TXT_RACK_RANGE)
            .ignored()
            .value(SeatSchemas.RACK_YAW).value(SeatSchemas.RACK_PITCH)
            .value(SeatSchemas.RACK_ROTATING)
            .vector(SeatSchemas.RACK_UNMOUNT_POSITION);

    /**
     * {@code AddWeapon = type, x, y, z[, yaw[, pitch[, canUsePilot[, seat[, defaultYaw[, minYaw[, maxYaw[,
     * minPitch[, maxPitch]]]]]]]]]}; {@code AddTurretWeapon} the same for a weapon that turns with the turret.
     */
    static final TxtTuple<Mount> ADD_WEAPON = mount(false);
    static final TxtTuple<Mount> ADD_TURRET_WEAPON = mount(true);

    private CrewTxtRules() {
    }

    static void register(Schema.Builder<VehicleFile> builder, KindProfile kind) {
        builder.txtRule("addseat", seat(ADD_SEAT))
                .txtRule("addgunnerseat", seat(ADD_GUNNER_SEAT))
                .txtRule("addfixrotseat", seat(ADD_FIXED_SEAT))
                .txtRule("addrack", ADD_RACK.appendingTo(CrewSchema.RACKS))
                .txtRule("addweapon", ADD_WEAPON.appendingTo(CrewSchema.WEAPONS))
                .txtRule("addturretweapon", ADD_TURRET_WEAPON.appendingTo(CrewSchema.WEAPONS))
                .txtRule("riderack", CrewTxtRules::rideRack)
                .txtRule("hud", (values, text, context) -> values.set(HUD_LINE, List.of(Codecs.splitTxt(text))))
                .txtRule("exclusionseat", CrewTxtRules::exclusionSeat)
                .txtRule("cameraposition", CrewTxtRules::cameraPosition)
                .afterTxt(values -> settleHuds(values, kind))
                .afterTxt(CrewTxtRules::settleExclusions);
    }

    /** Seats past the most a vehicle can have are dropped. */
    private static TxtRule seat(TxtTuple<Seat> tuple) {
        return (values, text, context) -> {
            if (values.get(CrewSchema.SEATS).size() < VehicleRules.MAX_SEATS) {
                tuple.appendingTo(CrewSchema.SEATS).apply(values, text, context);
            }
        };
    }

    /** A gunner seat without a camera gets one at the model's origin, raised like every camera. */
    private static CameraSpec seatLevelCamera() {
        return new CameraSpec(Vec3.ZERO, false, 0.0F, 0.0F);
    }

    /** The camera inverts when it is given; the pitch limits only count as a pair. */
    private static void gunnerSeat(Values seat, int count) {
        seat.set(SeatSchemas.INVERT_CAMERA, count >= CAMERA_GIVEN);
        if (count < PAIR_GIVEN) {
            seat.set(SeatSchemas.MIN_PITCH, SeatSchemas.DEFAULT_MIN_PITCH);
            seat.set(SeatSchemas.MAX_PITCH, SeatSchemas.DEFAULT_MAX_PITCH);
            return;
        }
        float first = clampPitch(seat.get(SeatSchemas.MIN_PITCH));
        float second = clampPitch(seat.get(SeatSchemas.MAX_PITCH));
        seat.set(SeatSchemas.MIN_PITCH, Math.min(first, second));
        seat.set(SeatSchemas.MAX_PITCH, Math.max(first, second));
    }

    /** The camera inverts when it is given; the view is fixed only when yaw and pitch are both given. */
    private static void fixedSeat(Values seat, int count) {
        seat.set(SeatSchemas.INVERT_CAMERA, count >= CAMERA_GIVEN);
        boolean fixed = count >= PAIR_GIVEN;
        seat.set(SeatSchemas.FIXED_ROTATION, fixed);
        if (!fixed) {
            seat.set(SeatSchemas.FIXED_YAW, 0.0F);
            seat.set(SeatSchemas.FIXED_PITCH, 0.0F);
        }
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90.0F, Math.min(90.0F, pitch));
    }

    private static TxtTuple<Mount> mount(boolean turret) {
        return TxtTuple.of(CrewSchema.MOUNT).minimum(4)
                .fixed(CrewSchema.MOUNT_TURRET, turret)
                .value(CrewSchema.MOUNT_WEAPON)
                .vector(CrewSchema.MOUNT_POSITION)
                .value(CrewSchema.MOUNT_YAW).value(CrewSchema.MOUNT_PITCH)
                .value(CrewSchema.MOUNT_PILOT_CAN_USE)
                .value(CrewSchema.MOUNT_SEAT)
                .value(CrewSchema.MOUNT_DEFAULT_YAW)
                .value(CrewSchema.MOUNT_MIN_YAW).value(CrewSchema.MOUNT_MAX_YAW)
                .value(CrewSchema.MOUNT_MIN_PITCH).value(CrewSchema.MOUNT_MAX_PITCH);
    }

    /**
     * {@code RideRack = vehicle, rack}: the vehicle can ride on that rack of the named vehicle. A further rack of a
     * vehicle already named joins its racks, as the YAML form keeps them; the order does not matter to the game.
     */
    private static void rideRack(Values values, String text, ReadContext context) {
        String[] parts = Codecs.splitTxt(text);
        if (parts.length >= 2) {
            int rack = Codecs.clamp(Codecs.parseInt(parts[1]), 1, MAX_RACK_ID, context);
            String vehicle = parts[0].trim().toLowerCase(Locale.ROOT);
            List<RideRack> racks = new ArrayList<>(values.get(CrewSchema.RIDE_RACKS));
            int at = racks.size();
            while (at > 0 && !racks.get(at - 1).name().equals(vehicle)) {
                at--;
            }
            racks.add(at == 0 ? racks.size() : at, new RideRack(vehicle, rack));
            values.set(CrewSchema.RIDE_RACKS, racks);
        }
    }

    /** {@code ExclusionSeat = seat, seat[, ...]}, numbered from 1 with racks after the seats. */
    private static void exclusionSeat(Values values, String text, ReadContext context) {
        String[] parts = Codecs.splitTxt(text);
        if (parts.length < 2) {
            return;
        }
        List<Integer> group = new ArrayList<>();
        for (String seat : parts) {
            group.add(Codecs.clamp(Codecs.parseInt(seat), 1, MAX_RACK_ID, context));
        }
        values.append(EXCLUSION_LINES, List.copyOf(group));
    }

    /**
     * {@code CameraPosition = x, y, z[, alwaysCameraView[, yaw[, pitch]]]}: a third-person camera position,
     * fixed when a yaw is given. Each line also sets whether the camera view is always on.
     */
    private static void cameraPosition(Values values, String text, ReadContext context) {
        String[] parts = Codecs.splitTxt(text);
        if (parts.length < 3) {
            return;
        }
        values.set(ViewSchemas.ALWAYS_CAMERA_VIEW, parts.length >= 4 && Codecs.BOOLEAN.fromText(parts[3], false,
                context));
        Vec3 position = Vectors.VEC3.fromText(String.join(", ", parts[0], parts[1], parts[2]), null, context);
        float yaw = parts.length >= 5 ? Codecs.parseFloat(parts[4]) : 0.0F;
        float pitch = parts.length >= 6 ? Codecs.parseFloat(parts[5]) : 0.0F;
        values.append(ViewSchemas.POSITIONS, new CameraSpec(position, parts.length >= 5, yaw, pitch));
    }

    /** Gives each seat the HUD the {@code HUD} line names for it, or its kind's. */
    private static void settleHuds(Values values, KindProfile kind) {
        List<String> named = values.get(HUD_LINE);
        List<Seat> seats = new ArrayList<>(values.get(CrewSchema.SEATS));
        for (int i = 0; i < seats.size(); i++) {
            seats.set(i, seats.get(i).withHud(i < named.size() ? named.get(i) : kind.hudFor(i)));
        }
        values.set(CrewSchema.SEATS, List.copyOf(seats));
        if (seats.size() == 1 && named.size() >= 2) {
            values.set(CrewSchema.GUNNER_HUD, named.get(1));
        }
    }

    /** Pairs become the first seat's {@code ExcludeWith}; larger groups stay groups. */
    private static void settleExclusions(Values values) {
        int seatCount = values.get(CrewSchema.SEATS).size();
        for (List<Integer> group : values.get(EXCLUSION_LINES)) {
            if (group.size() == 2) {
                excludePair(values, seatCount, group.get(0) - 1, group.get(1) - 1);
            } else {
                values.append(CrewSchema.EXCLUSIVE_SEATS, group);
            }
        }
    }

    private static void excludePair(Values values, int seatCount, int owner, int other) {
        if (owner < seatCount) {
            List<Seat> seats = new ArrayList<>(values.get(CrewSchema.SEATS));
            seats.set(owner, seats.get(owner).withExclusions(excluding(seats.get(owner).excludeWith(), other,
                    seatCount)));
            values.set(CrewSchema.SEATS, List.copyOf(seats));
            return;
        }
        List<Rack> racks = new ArrayList<>(values.get(CrewSchema.RACKS));
        int rack = owner - seatCount;
        if (rack < racks.size()) {
            racks.set(rack, racks.get(rack).withExclusions(excluding(racks.get(rack).excludeWith(), other,
                    seatCount)));
            values.set(CrewSchema.RACKS, List.copyOf(racks));
        } else {
            values.append(CrewSchema.EXCLUSIVE_SEATS, List.of(owner + 1, other + 1));
        }
    }

    private static SeatExclusions excluding(SeatExclusions exclusions, int other, int seatCount) {
        return other < seatCount ? exclusions.withSeat(other + 1) : exclusions.withRack(other - seatCount + 1);
    }
}
