package com.ivomens.mcheli.core.wingman.mission;

import java.util.Locale;

/**
 * One step of a node-list mission. Immutable so that GUIs can build and share them freely.
 *
 * <p>Nodes round-trip through a compact text form ({@link #parse}/{@link #serialize}) which is what the
 * route file and the commands use.
 */
public class MissionNode {

    public enum Type {
        /** Fly to (x, y, z). */
        FLY_TO,
        /** Take off from the runway {@code runwayId}. */
        TAKEOFF,
        /** Land on the runway {@code runwayId}. */
        LAND,
        /** Attack enemies within {@code attackRadius} of the current position, then continue. */
        ATTACK,
        /** Orbit in place for {@code durationTicks}. */
        LOITER,
        /** Taxi to the parking spot {@code parkingId} and stop. */
        PARK
    }

    private static final double DEFAULT_ATTACK_RADIUS = 200.0;
    private static final int DEFAULT_LOITER_TICKS = 600;

    public final Type type;
    /** FLY_TO target. */
    public final double x, y, z;
    /** TAKEOFF / LAND runway marker id. */
    public final String runwayId;
    /** ATTACK search radius. */
    public final double attackRadius;
    /** LOITER duration. */
    public final int durationTicks;
    /** PARK parking marker id. */
    public final String parkingId;

    public static MissionNode flyTo(double x, double y, double z) {
        return new MissionNode(Type.FLY_TO, x, y, z, null, DEFAULT_ATTACK_RADIUS, 0, null);
    }

    public static MissionNode takeoff(String runwayId) {
        return new MissionNode(Type.TAKEOFF, 0, 0, 0, runwayId, 0, 0, null);
    }

    public static MissionNode land(String runwayId) {
        return new MissionNode(Type.LAND, 0, 0, 0, runwayId, 0, 0, null);
    }

    public static MissionNode attack(double radius) {
        return new MissionNode(Type.ATTACK, 0, 0, 0, null, radius, 0, null);
    }

    public static MissionNode loiter(int ticks) {
        return new MissionNode(Type.LOITER, 0, 0, 0, null, 0, ticks, null);
    }

    public static MissionNode park(String parkingId) {
        return new MissionNode(Type.PARK, 0, 0, 0, null, 0, 0, parkingId);
    }

    private MissionNode(Type type, double x, double y, double z, String runwayId, double attackRadius,
                        int durationTicks, String parkingId) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.runwayId = runwayId;
        this.attackRadius = attackRadius;
        this.durationTicks = durationTicks;
        this.parkingId = parkingId;
    }

    /**
     * Parses one node from its text form: {@code flyto:x,y,z}, {@code takeoff:id}, {@code land:id},
     * {@code attack[:radius]}, {@code loiter[:ticks]} or {@code park:id}.
     */
    public static MissionNode parse(String token) {
        String[] parts = token.split(":", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1] : "";
        return switch (command) {
            case "flyto" -> {
                String[] xyz = arg.split(",");
                yield flyTo(Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2]));
            }
            case "takeoff" -> takeoff(arg);
            case "land" -> land(arg);
            case "attack" -> attack(arg.isEmpty() ? DEFAULT_ATTACK_RADIUS : Double.parseDouble(arg));
            case "loiter" -> loiter(arg.isEmpty() ? DEFAULT_LOITER_TICKS : Integer.parseInt(arg));
            case "park" -> park(arg);
            default -> throw new IllegalArgumentException("Unknown node type: " + command);
        };
    }

    /** Text form understood by {@link #parse}. */
    public String serialize() {
        return switch (type) {
            case FLY_TO -> "flyto:" + (int) x + "," + (int) y + "," + (int) z;
            case TAKEOFF -> "takeoff:" + runwayId;
            case LAND -> "land:" + runwayId;
            case ATTACK -> "attack:" + (int) attackRadius;
            case LOITER -> "loiter:" + durationTicks;
            case PARK -> "park:" + parkingId;
        };
    }

    @Override
    public String toString() {
        return serialize();
    }
}
