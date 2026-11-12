package com.ivomens.mcheli.world.gunner;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.guidance.TargetFilter;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import net.minecraft.world.phys.AABB;

/**
 * A gunner releases its vehicle's countermeasures when a guided munition is homing on it: flares,
 * chaff, the ECM jammer and the active protection system, whichever the vehicle carries, then waits a
 * moment before it does so again.
 */
final class GunnerCountermeasures {

    /** Radius searched for missiles homing on the host aircraft. */
    private static final double THREAT_SEARCH_RANGE = 220.0;
    private static final int COOLDOWN = 30;
    /** The search runs every this many ticks, staggered per gunner. */
    private static final int CHECK_INTERVAL = 5;
    /** Flare type that only decoys, preferred for automatic release. */
    private static final int DECOY_FLARE_TYPE = 10;

    private final GunnerEntity gunner;
    private int cooldown;

    GunnerCountermeasures(GunnerEntity gunner) {
        this.gunner = gunner;
    }

    /** The cooldown, once per tick on both sides. */
    void tickCooldown() {
        if (this.cooldown > 0) {
            this.cooldown--;
        }
    }

    /**
     * Server: when a guided munition is homing on the host, uses whatever countermeasures the host has.
     * The wide missile search only runs when at least one of them is available.
     */
    void defend(AircraftEntity ac) {
        if (ac == null || this.gunner.level().isClientSide || ac.isDestroyed()) {
            return;
        }
        if (this.cooldown > 0 || (this.gunner.tickCount + this.gunner.getId()) % CHECK_INTERVAL != 0) {
            return;
        }

        int flareType = flareType(ac);
        boolean canFlare = flareType > 0 && ac.countermeasures().canUseFlare();
        boolean canChaff = ac.countermeasures().canUseChaff();
        boolean canEcm = ac.spec() != null && ac.spec().file().features().ecm().enabled();
        boolean canAps = ac.countermeasures().canUseAPS();
        if (!canFlare && !canChaff && !canEcm && !canAps) {
            return;
        }
        if (!hasIncomingGuidedThreat(ac)) {
            return;
        }

        boolean used = canFlare && ac.countermeasures().useFlare(flareType);
        used |= canChaff && ac.countermeasures().useChaff();
        used |= canEcm && ac.countermeasures().useECMJammer();
        used |= canAps && ac.countermeasures().useAPS();
        if (used) {
            this.cooldown = COOLDOWN;
        }
    }

    /** The flare type to release: the decoy type if the host has it, else its current or first type. */
    private static int flareType(AircraftEntity ac) {
        if (!ac.countermeasures().haveFlare() || ac.spec() == null || ac.spec().armament().flare() == null
                || ac.spec().armament().flare().types == null) {
            return 0;
        }
        int[] types = ac.spec().armament().flare().types;
        for (int type : types) {
            if (type == DECOY_FLARE_TYPE) {
                return DECOY_FLARE_TYPE;
            }
        }
        int current = ac.countermeasures().currentFlareType();
        if (current > 0) {
            return current;
        }
        return types.length > 0 ? types[0] : 0;
    }

    private boolean hasIncomingGuidedThreat(AircraftEntity ac) {
        AABB box = ac.getBoundingBox().inflate(THREAT_SEARCH_RANGE);
        return !this.gunner.level().getEntitiesOfClass(ProjectileEntity.class, box, missile ->
                !missile.isRemoved() && missile.spec() != null && TargetFilter.isHomingOn(missile, ac)
                        && TargetFilter.isGuidedThreat(missile)).isEmpty();
    }
}
