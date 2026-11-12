package com.ivomens.mcheli.example;

import com.ivomens.mcheli.api.target.IncomingMissile;
import com.ivomens.mcheli.api.target.TargetLock;
import com.ivomens.mcheli.api.target.TargetProfile;
import com.ivomens.mcheli.api.target.TargetRegistry;
import com.ivomens.mcheli.api.target.TargetSignature;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Makes "target drones" of armor stands: an armor stand tagged {@value #DRONE_TAG} (for example with
 * {@code /tag @e[type=armor_stand,limit=1,sort=nearest] add mcheli_example_drone}) shows up on MCHeli's radar
 * and can be locked like a small jet, and one also tagged {@value #CHAFF_TAG} hides from radar seekers behind its
 * chaff. The drone hears of every lock and missile aimed at it; this addon only counts and logs them, where a
 * vehicle mod would warn its pilot.
 */
public final class ExampleTargets implements TargetProfile {

    public static final String DRONE_TAG = "mcheli_example_drone";
    public static final String CHAFF_TAG = "mcheli_example_chaff";

    private static final Logger LOGGER = LoggerFactory.getLogger(ExampleAddon.MOD_ID);
    /** A small jet: hot engines, a smaller radar return than a fighter. Kept, since signatures never change. */
    private static final TargetSignature DRONE = TargetSignature.AIR.withRadarCrossSection(0.6F);
    private static final TargetSignature DRONE_WITH_CHAFF = DRONE.withChaff(true);
    private static final AtomicLong LOCKS = new AtomicLong();
    private static final AtomicLong MISSILES = new AtomicLong();

    private ExampleTargets() {
    }

    /** Registers the drones. Called once, from the addon's constructor, so the server and every client agree. */
    static void register(TargetRegistry targets) {
        targets.register(ExampleTargets::isDrone, new ExampleTargets());
    }

    /** Locks on drones heard since the game started, on either side. */
    public static long locksHeard() {
        return LOCKS.get();
    }

    /** Missiles launched at drones since the game started. */
    public static long missilesHeard() {
        return MISSILES.get();
    }

    private static boolean isDrone(Entity entity) {
        return entity instanceof ArmorStand && entity.getTags().contains(DRONE_TAG);
    }

    /** Both sides, often: no allocation, only the two constants. Tags are synced, so both sides agree. */
    @Override
    public TargetSignature signature(Entity drone) {
        return drone.getTags().contains(CHAFF_TAG) ? DRONE_WITH_CHAFF : DRONE;
    }

    @Override
    public void onLockedOn(TargetLock lock) {
        LOCKS.incrementAndGet();
        LOGGER.debug("{} locked a target drone with a {} seeker", lock.user().getName().getString(), lock.seeker());
    }

    @Override
    public void onMissileLaunched(IncomingMissile missile) {
        MISSILES.incrementAndGet();
        LOGGER.info("A {} ({} seeker) is homing on a target drone", missile.weaponId(), missile.seeker());
    }
}
