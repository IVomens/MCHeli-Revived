package com.ivomens.mcheli.api.wingman;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * A mission order for {@link Wingmen#dispatch}: what to do, where, and from which base. The same order the
 * mission planner screen and {@code /wingman order dispatch} give.
 *
 * <pre>{@code
 * MissionRequest patrol = MissionRequest.builder(MissionKind.CAP)
 *         .target(1200, -300)
 *         .baseId("north_field")
 *         .timeLimitSeconds(300)
 *         .build();
 * }</pre>
 *
 * <p>The wingman starts in the air, flies to the target point at the cruise altitude, does its mission, and
 * returns to the base's runway or helipad when the time limit or its fuel runs out (or after its strike passes
 * or ferry flight), lands and parks. Without a base marker of that id it cannot land, and stops where it is.
 *
 * <p>Immutable; any thread. Since API 1.1. Builder methods may be added in a minor version.
 */
public final class MissionRequest {

    /** The planner's time limit, in seconds, when none is set. */
    public static final int DEFAULT_TIME_LIMIT_SECONDS = 600;
    /** The planner's orbit radius around the target point, in blocks, when none is set. */
    public static final double DEFAULT_ORBIT_RADIUS = 300.0;
    /** The planner's cruise altitude, as a y coordinate, when none is set. */
    public static final double DEFAULT_CRUISE_ALTITUDE = 80.0;
    /** The planner's number of strike passes when none is set. */
    public static final int DEFAULT_STRIKE_PASSES = 2;

    private final Set<MissionKind> kinds;
    private final Set<String> weapons;
    private final String baseId;
    private final double targetX;
    private final double targetZ;
    private final double orbitRadius;
    private final double cruiseAltitude;
    private final int strikePasses;
    private final int timeLimitSeconds;

    private MissionRequest(Builder builder) {
        this.kinds = Collections.unmodifiableSet(EnumSet.copyOf(builder.kinds));
        this.weapons = Collections.unmodifiableSet(new LinkedHashSet<>(builder.weapons));
        this.baseId = builder.baseId;
        this.targetX = builder.targetX;
        this.targetZ = builder.targetZ;
        this.orbitRadius = builder.orbitRadius;
        this.cruiseAltitude = builder.cruiseAltitude;
        this.strikePasses = builder.strikePasses;
        this.timeLimitSeconds = builder.timeLimitSeconds;
    }

    /** A new request for the given kinds of mission; at least one. */
    public static Builder builder(MissionKind kind, MissionKind... more) {
        return new Builder(kind, more);
    }

    /** The kinds of mission, never empty. */
    public Set<MissionKind> kinds() {
        return this.kinds;
    }

    /**
     * The weapon types (such as {@code aamissile} or {@code gun}) the wingman uses, lower case; empty for the
     * defaults of the mission kinds.
     */
    public Set<String> weapons() {
        return this.weapons;
    }

    /** The id of the base (its airfield markers) the wingman returns to; empty for none. */
    public String baseId() {
        return this.baseId;
    }

    public double targetX() {
        return this.targetX;
    }

    public double targetZ() {
        return this.targetZ;
    }

    /** The radius the wingman orbits the target point at, in blocks. */
    public double orbitRadius() {
        return this.orbitRadius;
    }

    /** The y coordinate the wingman cruises at. */
    public double cruiseAltitude() {
        return this.cruiseAltitude;
    }

    /** How many attack passes a strike flies. */
    public int strikePasses() {
        return this.strikePasses;
    }

    /** Seconds on station before returning to base; 0 for no limit. */
    public int timeLimitSeconds() {
        return this.timeLimitSeconds;
    }

    /** Collects a {@link MissionRequest}; not thread-safe. Each setter returns the builder. */
    public static final class Builder {

        private final Set<MissionKind> kinds = EnumSet.noneOf(MissionKind.class);
        private final Set<String> weapons = new LinkedHashSet<>();
        private String baseId = "";
        private double targetX;
        private double targetZ;
        private double orbitRadius = DEFAULT_ORBIT_RADIUS;
        private double cruiseAltitude = DEFAULT_CRUISE_ALTITUDE;
        private int strikePasses = DEFAULT_STRIKE_PASSES;
        private int timeLimitSeconds = DEFAULT_TIME_LIMIT_SECONDS;

        private Builder(MissionKind kind, MissionKind... more) {
            this.kinds.add(Objects.requireNonNull(kind, "kind"));
            for (MissionKind other : more) {
                this.kinds.add(Objects.requireNonNull(other, "kind"));
            }
        }

        /** The point the mission is flown at. */
        public Builder target(double x, double z) {
            this.targetX = x;
            this.targetZ = z;
            return this;
        }

        /** The base the wingman returns to and lands at. */
        public Builder baseId(String id) {
            this.baseId = Objects.requireNonNull(id, "id");
            return this;
        }

        /** Adds a weapon type to use instead of the mission's defaults, such as {@code aamissile}. */
        public Builder weapon(String type) {
            this.weapons.add(Objects.requireNonNull(type, "type").trim().toLowerCase(Locale.ROOT));
            return this;
        }

        public Builder orbitRadius(double radius) {
            this.orbitRadius = radius;
            return this;
        }

        public Builder cruiseAltitude(double y) {
            this.cruiseAltitude = y;
            return this;
        }

        public Builder strikePasses(int passes) {
            this.strikePasses = passes;
            return this;
        }

        /** Seconds on station; 0 for no limit. */
        public Builder timeLimitSeconds(int seconds) {
            this.timeLimitSeconds = seconds;
            return this;
        }

        public MissionRequest build() {
            return new MissionRequest(this);
        }
    }
}
