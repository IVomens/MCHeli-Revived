package com.ivomens.mcheli.core.wingman.brain;

import com.ivomens.mcheli.core.wingman.mission.MissionNode;
import com.ivomens.mcheli.core.wingman.mission.MissionOrder;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The autonomous flight of one wingman: its task (a node mission or a mission order), the phase it is
 * in and the little memory the phases keep between ticks. The brains in this package own every field;
 * the rest of the mod starts and stops tasks and reads the state for status lines.
 */
public final class Autopilot {

    FlightPhase phase = FlightPhase.NONE;
    /** Ticks in the current phase or node; what it counts depends on the phase. */
    int phaseTimer;
    /** The point the aircraft steers to. */
    double targetX;
    double targetY;
    double targetZ;
    /**
     * Where on its orbit the aircraft is, in radians, counter-clockwise from +X seen from above. The
     * patrol orbit, the strike passes and the attack orbit all use it, so an attack carries on around
     * the circle where the patrol was and the patrol picks up where the attack left off.
     */
    double orbitAngle;

    /** VTOL planes: the nozzle should be in hover position (vertical takeoff and landing). */
    boolean hoverMode;
    /**
     * The last nozzle command sent was "hover". A command is sent only when this differs from
     * {@link #hoverMode}, because the toggle cannot be repeated every tick while the nozzle moves.
     */
    boolean hoverCommandSent;

    /** Position before physics while taxiing, where the taxi push after physics measures from. */
    double taxiFromX;
    double taxiFromZ;
    /** Landing roll speed (blocks/tick), decaying to taxi speed; 0 when not rolling out. */
    double landingRollSpeed;
    /** Heading (Minecraft yaw) to hold while parked; NaN for none. */
    float parkingHeadingYaw = Float.NaN;
    /** Runway height the aircraft is held at during the first part of the takeoff roll; 0 when unset. */
    double groundRollY;

    /** Parking spot or helipad the aircraft starts from or returns to; empty for an airborne start. */
    String parkingId = "";
    /** Departure taxi route chosen for the order, by id because several routes can share a parking spot. */
    String departureRouteId = "";
    /** Marker ids to taxi through, in order. */
    List<String> taxiQueue = new ArrayList<>();
    int taxiIndex;

    private @Nullable NodeMission mission;
    private @Nullable OrderProgress order;

    /** Flying a node mission or a mission order rather than following a leader. */
    public boolean isAutonomous() {
        return (this.phase != FlightPhase.NONE && this.mission != null) || this.order != null;
    }

    public FlightPhase phase() {
        return this.phase;
    }

    public @Nullable NodeMission mission() {
        return this.mission;
    }

    public @Nullable OrderProgress order() {
        return this.order;
    }

    public boolean hasOrder() {
        return this.order != null;
    }

    public String parkingId() {
        return this.parkingId;
    }

    /** The VTOL nozzle should be down (vertical flight). */
    public boolean wantsHover() {
        return this.hoverMode;
    }

    /** Starts a node mission. A mission order would take precedence, so it is dropped. */
    public void startNodeMission(List<MissionNode> nodes) {
        this.order = null;
        this.mission = new NodeMission(nodes);
        this.phaseTimer = 0;
        this.phase = FlightPhase.ENROUTE;
    }

    /** Drops the node mission; the wingman stops flying on its own unless it has an order. */
    public void abortMission() {
        this.mission = null;
        this.phase = FlightPhase.NONE;
    }

    /**
     * Starts a mission order from scratch and puts the VTOL nozzle back to forward flight on the next
     * tick. A node mission is kept and resumes once the order is done, as it always has.
     */
    public void startOrder(MissionOrder newOrder) {
        this.order = new OrderProgress(newOrder);
        this.orbitAngle = 0.0;
        this.phase = FlightPhase.NONE;
        resetNozzle();
    }

    /** Drops the mission order. */
    public void abortOrder() {
        this.order = null;
        this.phase = FlightPhase.NONE;
    }

    /** The parking spot an order starts from, with the route to take from it; empty for an airborne start. */
    public void departFrom(String parking, String routeId) {
        this.parkingId = parking;
        this.departureRouteId = routeId;
    }

    /** Assigns the parking spot the next order starts from and parks there. */
    public void parkAt(String parking) {
        this.parkingId = parking;
        this.phase = FlightPhase.PARKED;
    }

    /**
     * Asks for the nozzle to return to forward flight. Marking hover as sent makes the next tick issue
     * the "off" command once, whatever position the nozzle was left in.
     */
    void resetNozzle() {
        this.hoverMode = false;
        this.hoverCommandSent = true;
    }

    /** Moves on to the next node of the node mission. */
    void advanceMission() {
        if (this.mission != null) {
            this.mission.advance();
        }
        this.phaseTimer = 0;
    }

    void endMission() {
        this.mission = null;
    }

    void endOrder() {
        this.order = null;
    }

    void steerTo(double x, double y, double z) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
    }

    /**
     * Moves {@code radiansPerTick} on around a circle and returns the new point on it.
     *
     * @param y the height of the returned point
     */
    Point orbit(double centerX, double y, double centerZ, double radius, double radiansPerTick) {
        this.orbitAngle = (this.orbitAngle + radiansPerTick) % (2.0 * Math.PI);
        return new Point(centerX + Math.cos(this.orbitAngle) * radius, y,
                centerZ + Math.sin(this.orbitAngle) * radius);
    }

    Point target() {
        return new Point(this.targetX, this.targetY, this.targetZ);
    }

    void enter(FlightPhase next) {
        this.phase = next;
    }

    /** Enters a phase whose timer starts from zero. */
    void restart(FlightPhase next) {
        this.phase = next;
        this.phaseTimer = 0;
    }
}
