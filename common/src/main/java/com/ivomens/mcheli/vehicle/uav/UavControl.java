package com.ivomens.mcheli.vehicle.uav;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Public control facade for the McHeli UAV system. Addon mods (for example a custom UAV station) use
 * this to connect and disconnect an operator to a UAV without hand-wiring the sensitive mount / link /
 * teardown steps, and without ever touching the tracking or chunk-streaming machinery directly.
 *
 * <h3>What is automatic</h3>
 * The detached-camera support (extended entity tracking range, camera-aware entity visibility and
 * terrain chunk streaming around the UAV) is driven entirely off the {@link IUavStation} contract:
 * the tracker hook grants the extended range to whoever a UAV's {@link IUavStation#getOperator()}
 * reports, and the chunk streamer keeps terrain loaded around any UAV that an {@link IUavStation}
 * reports as {@link IUavStation#getControlled()}. Both engage and release purely from that interface
 * state, so any {@link Entity} implementing {@link IUavStation} gets the full feature set; a custom
 * station only has to establish and clear the operator/station/UAV link, which is exactly
 * {@link #connect} and {@link #disconnect}.
 * <p>
 * A UAV that is not loaded cannot be connected to: {@link UAVTracker#locateUAV} never loads chunks,
 * so {@link #connect} then reports {@link Result#NOT_FOUND} ("out of range").
 *
 * <p>All methods are server-side.
 */
public final class UavControl {

    private UavControl() {
    }

    public enum Result {
        /** Link established. */
        OK,
        /** The UAV is null, dead, or not loaded. */
        NOT_FOUND,
        /** The UAV exists but is destroyed and must not be controlled. */
        DESTROYED
    }

    /**
     * Connect an operator to a UAV through a station: seat the operator on the station entity (required
     * for the view/control link) and establish the two-way station/UAV link.
     *
     * @param operator the controlling player (server-side)
     * @param station  the station entity; must be both an {@link Entity} (to be ridden) and an
     *                 {@link IUavStation} (the control contract)
     * @param uav      the UAV to take control of
     * @return the outcome; only {@link Result#OK} establishes a link
     */
    public static <S extends Entity & IUavStation> Result connect(ServerPlayer operator, S station,
                                                                  @Nullable AircraftEntity uav) {
        if (uav == null || uav.isRemoved()) {
            return Result.NOT_FOUND;
        }
        if (uav.isDestroyed()) {
            return Result.DESTROYED;
        }
        if (operator.getVehicle() != station) {
            operator.startRiding(station, true);
        }
        uav.uavLink().link(station);
        station.setControlled(uav);
        return Result.OK;
    }

    /**
     * Sever an active link: unlink the controlled UAV and dismount the operator. Tracking and chunk
     * streaming then release on their own. Safe to call when there is no active link.
     */
    public static void disconnect(@Nullable IUavStation station) {
        if (station == null) {
            return;
        }
        AircraftEntity uav = station.getControlled();
        if (uav != null) {
            uav.uavLink().link(null);
        }
        station.setControlled(null);
        Entity operator = station.getOperator();
        if (operator != null) {
            operator.stopRiding();
        }
    }

    /** @return true if {@code station} currently has an operator controlling a (live) UAV. */
    public static boolean isActive(@Nullable IUavStation station) {
        return station != null && station.getOperator() != null
                && station.getControlled() != null && !station.getControlled().isRemoved();
    }
}
