package com.ivomens.mcheli.content.def.vehicle;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * YAML {@code AircraftFeatures}: equipment and abilities shared by every vehicle kind.
 *
 * @param throttleUpDown         how fast the throttle moves
 * @param throttleUpDownOnEntity how fast the throttle moves while the vehicle rides another
 * @param engineShutdownHp       the engine stops below this percentage of health
 * @param explosionDamageFactor  explosions hurt the vehicle this many times as much
 */
public record AircraftFeatures(boolean gunnerMode, int inventorySize, boolean nightVision, boolean entityRadar,
                               boolean canReverse, boolean concurrentGunner, boolean ejectionSeat,
                               float throttleUpDown, float throttleUpDownOnEntity, Parachuting parachuting,
                               Flare flare, int engineShutdownHp, Aps aps, float explosionDamageFactor,
                               Radar radar, Rwr rwr, Ecm ecm) {

    /** The same equipment with other flares and chaff. */
    public AircraftFeatures withFlare(Flare changed) {
        return new AircraftFeatures(this.gunnerMode, this.inventorySize, this.nightVision, this.entityRadar,
                this.canReverse, this.concurrentGunner, this.ejectionSeat, this.throttleUpDown,
                this.throttleUpDownOnEntity, this.parachuting, changed, this.engineShutdownHp, this.aps,
                this.explosionDamageFactor, this.radar, this.rwr, this.ecm);
    }

    /** The same equipment with another active protection system. */
    public AircraftFeatures withAps(Aps changed) {
        return new AircraftFeatures(this.gunnerMode, this.inventorySize, this.nightVision, this.entityRadar,
                this.canReverse, this.concurrentGunner, this.ejectionSeat, this.throttleUpDown,
                this.throttleUpDownOnEntity, this.parachuting, this.flare, this.engineShutdownHp, changed,
                this.explosionDamageFactor, this.radar, this.rwr, this.ecm);
    }

    /**
     * YAML {@code Parachuting}: the crew jumps out with parachutes during a drop.
     *
     * @param position where they jump out
     * @param interval ticks between jumps
     */
    public record Parachuting(boolean enabled, Vec3 position, int interval) {
    }

    /**
     * YAML {@code Flare}: flares, and chaff in the same section.
     *
     * @param types the flare patterns, by their legacy numbers (see {@code FlareType})
     */
    public record Flare(Vec3 position, List<Integer> types, boolean chaff, int chaffUseTime, int chaffWaitTime) {

        public Flare {
            types = List.copyOf(types);
        }

        public Flare withChaff(boolean fitted) {
            return new Flare(this.position, this.types, fitted, this.chaffUseTime, this.chaffWaitTime);
        }
    }

    /** YAML {@code APS}: the active protection system. */
    public record Aps(boolean enabled, int useTime, int waitTime, int range) {

        /** The legacy spelling of the iron curtain: an APS with exactly this range. */
        public static final int IRON_CURTAIN_RANGE = 100;

        /** Whether this declares the iron curtain rather than a system that shoots projectiles down. */
        public boolean isIronCurtain() {
            return this.range == IRON_CURTAIN_RANGE;
        }
    }

    /** YAML {@code Radar}. */
    public record Radar(boolean enabled, RadarType type, float maxTargetRange, float scanAzimuth,
                        float scanElevation, int scanTick, boolean bvr, boolean passive) {
    }

    /** YAML {@code RWR}: the radar warning receiver, and the name the vehicle shows on others'. */
    public record Rwr(boolean enabled, RWRType type, String name) {
    }

    /** YAML {@code ECM}: the radar jammer. */
    public record Ecm(boolean enabled, int waitTime, int useTime, int type) {
    }
}
