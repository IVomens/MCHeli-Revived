package com.ivomens.mcheli.vehicle.definition;

import com.ivomens.mcheli.core.sim.model.HandlingProfile;
import com.ivomens.mcheli.api.vehicle.VehicleKind;

import javax.annotation.Nullable;
import java.util.List;

/**
 * One vehicle as loaded from the content packs, frozen. Built once per load from the parsed
 * definition (see {@link VehicleDefinitions}); a reload builds a new one instead of changing this
 * one, so whoever holds a reference sees a consistent vehicle.
 * <p>
 * Only part of the parsed definition is here so far: what the physics reads, plus typed views of
 * the seats, weapon mounts and drawn parts. Everything else is still read from the parsed
 * definition.
 *
 * @param kind        the content type; names are unique per kind
 * @param name        the file name without extension, e.g. {@code ah-64}
 * @param handling    the values the motion model reads
 * @param body        size and step height
 * @param wingSweep   the swept-wing speed, for planes with a variable-sweep wing
 * @param targetDrone the autopilot setting, for target drones
 * @param seats       rider seats first, then racks
 * @param mounts      weapon mounts in definition order
 * @param parts       drawn parts in the order the renderer draws each kind
 */
public record VehicleDefinition(VehicleKind kind, String name, HandlingProfile handling, BodyDef body,
                                @Nullable WingSweepDef wingSweep, @Nullable TargetDroneDef targetDrone,
                                List<SeatDef> seats, List<MountDef> mounts, List<PartDef> parts) {

    public VehicleDefinition {
        seats = List.copyOf(seats);
        mounts = List.copyOf(mounts);
        parts = List.copyOf(parts);
    }

    /**
     * @param width      collision box width
     * @param height     collision box height
     * @param stepHeight how high the vehicle steps up while moving
     */
    public record BodyDef(float width, float height, float stepHeight) {
    }

    /** Variable-sweep wings move the top speed from the handling's towards {@code sweptSpeed}. */
    public record WingSweepDef(float sweptSpeed) {

        /** {@code sweep} is how far the wing has swept, from 0 to 1. */
        public float maxSpeed(float baseSpeed, float sweep) {
            float bonus = (this.sweptSpeed - baseSpeed) * sweep;
            return baseSpeed + bonus;
        }
    }

    /** @param circleRate yaw degrees per tick the drone turns while it flies its circles */
    public record TargetDroneDef(float circleRate) {
    }
}
