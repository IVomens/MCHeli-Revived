package com.ivomens.mcheli.core.wingman.mission;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A mission order built in the base planner or with {@code /wingman order dispatch}: what to do, where,
 * and from which base. Immutable; {@link #builder()} starts from the planner's defaults.
 *
 * @param missionTypes     selected mission types (exclusivity already checked by the sender)
 * @param weapons          selected weapon categories
 * @param targetX          centre of the target area
 * @param timeLimitSeconds time on station; 0 means unlimited
 * @param baseId           departure and home base id
 * @param orbitRadius      orbit radius for CAP/CAS, in blocks
 * @param cruiseAlt        cruise altitude (absolute Y)
 * @param ferryDestBase    destination base id, FERRY only
 * @param orbitAttack      keep orbiting while firing to the side (AC-130 style) instead of flying
 *                         stand-off attack passes
 * @param useVstol         short takeoff: VTOL planes hold the nozzle at 45 degrees during the takeoff roll
 * @param arrivalRouteId   optional dedicated arrival taxi route; empty falls back to the departure route
 */
public record MissionOrder(Set<MissionType> missionTypes, Set<String> weapons, double targetX, double targetZ,
                           int timeLimitSeconds, String baseId, double orbitRadius, double cruiseAlt,
                           int strikePasses, String ferryDestBase, boolean orbitAttack, boolean useVstol,
                           String arrivalRouteId) {

    public static final int DEFAULT_TIME_LIMIT_SECONDS = 600;
    public static final double DEFAULT_ORBIT_RADIUS = 300.0;
    public static final double DEFAULT_CRUISE_ALT = 80.0;
    public static final int DEFAULT_STRIKE_PASSES = 2;

    private static final int TICKS_PER_SECOND = 20;

    public static Builder builder() {
        return new Builder();
    }

    public int timeLimitTicks() {
        return this.timeLimitSeconds * TICKS_PER_SECOND;
    }

    public boolean hasType(MissionType type) {
        return this.missionTypes.contains(type);
    }

    /** The first non-gun weapon, used as the primary attack weapon; null (all weapons) for gun-only orders. */
    public String primaryWeapon() {
        for (String weapon : this.weapons) {
            if (!"gun".equals(weapon)) {
                return weapon;
            }
        }
        return null;
    }

    /** Collects an order; the defaults are the base planner's. */
    public static final class Builder {

        // hash sets, as orders always used: the primary weapon is the first one in their order
        private final Set<MissionType> missionTypes = new HashSet<>();
        private final Set<String> weapons = new HashSet<>();
        private double targetX;
        private double targetZ;
        private int timeLimitSeconds = DEFAULT_TIME_LIMIT_SECONDS;
        private String baseId = "";
        private double orbitRadius = DEFAULT_ORBIT_RADIUS;
        private double cruiseAlt = DEFAULT_CRUISE_ALT;
        private int strikePasses = DEFAULT_STRIKE_PASSES;
        private String ferryDestBase = "";
        private boolean orbitAttack;
        private boolean useVstol;
        private String arrivalRouteId = "";

        private Builder() {
        }

        public Builder type(MissionType type) {
            this.missionTypes.add(type);
            return this;
        }

        public boolean hasTypes() {
            return !this.missionTypes.isEmpty();
        }

        public Builder weapon(String weapon) {
            this.weapons.add(weapon);
            return this;
        }

        /** Adds the default weapons of every mission type added so far. */
        public Builder defaultWeapons() {
            for (MissionType type : this.missionTypes) {
                this.weapons.addAll(type.defaultWeapons());
            }
            return this;
        }

        public Builder target(double x, double z) {
            this.targetX = x;
            this.targetZ = z;
            return this;
        }

        public Builder timeLimitSeconds(int seconds) {
            this.timeLimitSeconds = seconds;
            return this;
        }

        public Builder baseId(String id) {
            this.baseId = id;
            return this;
        }

        public Builder orbitRadius(double radius) {
            this.orbitRadius = radius;
            return this;
        }

        public Builder cruiseAlt(double altitude) {
            this.cruiseAlt = altitude;
            return this;
        }

        public Builder strikePasses(int passes) {
            this.strikePasses = passes;
            return this;
        }

        public Builder ferryDestBase(String id) {
            this.ferryDestBase = id;
            return this;
        }

        public Builder orbitAttack(boolean orbit) {
            this.orbitAttack = orbit;
            return this;
        }

        public Builder useVstol(boolean vstol) {
            this.useVstol = vstol;
            return this;
        }

        public Builder arrivalRouteId(String id) {
            this.arrivalRouteId = id;
            return this;
        }

        /** The order; call once, the builder is spent afterwards. */
        public MissionOrder build() {
            return new MissionOrder(Collections.unmodifiableSet(this.missionTypes),
                    Collections.unmodifiableSet(this.weapons), this.targetX, this.targetZ, this.timeLimitSeconds,
                    this.baseId, this.orbitRadius, this.cruiseAlt, this.strikePasses, this.ferryDestBase,
                    this.orbitAttack, this.useVstol, this.arrivalRouteId);
        }
    }
}
