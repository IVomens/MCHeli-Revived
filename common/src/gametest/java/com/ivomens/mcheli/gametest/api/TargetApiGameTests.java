package com.ivomens.mcheli.gametest.api;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.event.SeekerType;
import com.ivomens.mcheli.api.event.Subscription;
import com.ivomens.mcheli.api.target.IncomingMissile;
import com.ivomens.mcheli.api.target.TargetDomain;
import com.ivomens.mcheli.api.target.TargetLock;
import com.ivomens.mcheli.api.target.TargetProfile;
import com.ivomens.mcheli.api.target.TargetRegistry;
import com.ivomens.mcheli.api.target.TargetSignature;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.combat.guidance.ExternalTargets;
import com.ivomens.mcheli.combat.guidance.LockOnSeeker;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.combat.weapon.net.RadarContactKind;
import com.ivomens.mcheli.radar.RadarContactSync;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Addon targets through {@link TargetRegistry}: a minecart, which no seeker locks on its own, becomes an air
 * target for MCHeli's seekers and radar once a profile describes it; its heat, radar cross-section, flares and
 * chaff decide which seekers see it; a completed lock and a missile turned on it reach the profile; and an
 * exclusion hides a pig and a player. Only this test's entities match its registrations (by tag), as other tests
 * run at the same time.
 */
public final class TargetApiGameTests {

    private static final String BATCH = "mcheli_api";
    private static final String TARGET_TAG = "mcheli_api_target";
    private static final String HIDDEN_TAG = "mcheli_api_hidden";

    private TargetApiGameTests() {
    }

    public static List<TestFunction> targets() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return List.of();
        }
        return List.of(new TestFunction(BATCH, "api_external_targets", "mcheli:empty", 100, 0, true,
                TargetApiGameTests::externalTargets));
    }

    private static void externalTargets(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer shooter = h.makeMockServerPlayerInLevel();
        Vec3 standing = h.absoluteVec(new Vec3(8, 2, 2));
        shooter.moveTo(standing.x, standing.y, standing.z, 0.0F, 0.0F);
        Minecart cart = floating(new Minecart(EntityType.MINECART, level), h.absoluteVec(new Vec3(8, 12, 14)));
        Pig pig = floating(new Pig(EntityType.PIG, level), h.absoluteVec(new Vec3(8, 4, 0)));
        pig.setNoAi(true);
        pig.addTag(HIDDEN_TAG);
        LockOnSeeker radar = seeker(level, "aim120", false, true);
        LockOnSeeker heat = seeker(level, "aim9", true, false);
        check(!radar.canLock(cart, shooter), "an unregistered minecart is a target");

        Profile profile = new Profile();
        TargetRegistry targets = McheliApi.get().targets();
        List<Subscription> registrations = new ArrayList<>();
        try {
            registrations.add(targets.register(entity -> entity.getTags().contains(TARGET_TAG), profile));
            cart.addTag(TARGET_TAG);
            signatures(radar, heat, cart, shooter, profile);
            lockAndMissile(level, radar, cart, shooter, profile);
            radarContacts(level, cart, profile);
            boolean pigLockable = heat.canLock(pig, shooter);
            registrations.add(targets.exclude(entity -> entity.getTags().contains(HIDDEN_TAG)));
            check(!heat.canLock(pig, shooter) && targets.isExcluded(pig), "the excluded pig can be locked");
            shooter.addTag(HIDDEN_TAG);
            check(RadarContactSync.trackedKind(level, shooter, ExternalTargets.get()) == null,
                    "the excluded player shows on radar");
            registrations.forEach(Subscription::unsubscribe);
            registrations.clear();
            check(heat.canLock(pig, shooter) == pigLockable, "lifting the exclusion did not restore the pig");
            check(!radar.canLock(cart, shooter) && targets.signatureOf(cart).isEmpty(),
                    "the minecart stayed a target after unsubscribing");
            h.succeed();
        } finally {
            registrations.forEach(Subscription::unsubscribe);
            cart.discard();
            pig.discard();
            shooter.discard();
        }
    }

    /** Which seekers see the minecart as its signature changes. */
    private static void signatures(LockOnSeeker radar, LockOnSeeker heat, Entity cart, Entity shooter,
                                   Profile profile) {
        check(radar.canLock(cart, shooter) && heat.canLock(cart, shooter), "the registered minecart is no target");
        check(McheliApi.get().targets().signatureOf(cart).orElse(null) == profile.signature,
                "signatureOf is not the profile's answer");
        profile.signature = TargetSignature.AIR.withRadarCrossSection(0.0F);
        check(!radar.canLock(cart, shooter) && heat.canLock(cart, shooter), "radar cross-section 0");
        profile.signature = TargetSignature.AIR.withRadarCrossSection(0.5F);
        check(radar.filter().stealthFor(cart) == 0.5F && heat.filter().stealthFor(cart) == 0.0F,
                "half the radar cross-section is not half stealth to radar only");
        profile.signature = TargetSignature.AIR.withFlares(true);
        check(radar.canLock(cart, shooter) && !heat.canLock(cart, shooter), "flares");
        profile.signature = TargetSignature.AIR.withChaff(true);
        check(!radar.canLock(cart, shooter) && heat.canLock(cart, shooter), "chaff");
        if (!radar.filter().ground()) {
            profile.signature = TargetSignature.GROUND;
            check(!radar.canLock(cart, shooter), "an air-only seeker locks a ground target");
        }
        profile.signature = null;
        check(!radar.canLock(cart, shooter) && !heat.canLock(cart, shooter), "a null signature is a target");
        profile.signature = TargetSignature.AIR;
    }

    /** A radar seeker locks the minecart and the profile hears of it; then a missile turns on it. */
    private static void lockAndMissile(ServerLevel level, LockOnSeeker radar, Entity cart, ServerPlayer shooter,
                                       Profile profile) {
        aim(shooter, cart);
        for (int tick = 0; tick < radar.lockTicks() + 5 && !radar.isLockComplete(); tick++) {
            radar.track(shooter);
        }
        check(radar.isLockComplete() && radar.target() == cart, "the radar seeker did not lock the minecart: count "
                + radar.lockCount() + " of " + radar.lockTicks() + ", target " + radar.target() + ", in level "
                + (level.getEntity(cart.getId()) == cart));
        check(profile.locks.size() == 1, "the profile heard " + profile.locks.size() + " locks");
        check(profile.locks.getFirst().seeker() == SeekerType.RADAR && profile.locks.getFirst().user() == shooter,
                "the lock reached the profile wrong");

        ProjectileEntity missile = new ProjectileEntity(ProjectileKind.AA_MISSILE.entityType(), level);
        missile.setWeapon("aim120");
        missile.setShooter(shooter, shooter);
        missile.setTarget(cart);
        missile.setTarget(cart);
        check(profile.missiles.size() == 1, "the profile heard of " + profile.missiles.size() + " missiles");
        check(profile.missiles.getFirst().missile() == missile && "aim120".equals(profile.missiles.getFirst().weaponId()),
                "the missile reached the profile wrong");
        missile.setTarget(null);
        missile.discard();
    }

    /** The radar shows the minecart as the kind it is shown as, and not while radar cannot see it. */
    private static void radarContacts(ServerLevel level, Entity cart, Profile profile) {
        profile.signature = TargetSignature.GROUND.withShownAs(VehicleKind.VEHICLE).withDomain(TargetDomain.BY_HEIGHT);
        check(RadarContactSync.trackedKind(level, cart, ExternalTargets.get()) == RadarContactKind.GROUND_VEHICLE,
                "the minecart is not on radar as a ground vehicle");
        profile.signature = TargetSignature.GROUND.withRadarCrossSection(0.0F);
        check(RadarContactSync.trackedKind(level, cart, ExternalTargets.get()) == null,
                "a target radar cannot see is on radar");
        profile.signature = TargetSignature.AIR;
    }

    private static LockOnSeeker seeker(ServerLevel level, String weapon, boolean heatSeeker, boolean radar) {
        WeaponMount mount = WeaponMount.create(level, weapon, Vec3.ZERO, 0.0F, 0.0F, false, null);
        LockOnSeeker seeker = mount != null ? mount.seeker() : null;
        if (seeker == null) {
            throw new GameTestAssertException(weapon + " is no seeker weapon");
        }
        return seeker.withFilter(seeker.filter().withHeatSeeking(heatSeeker).withRadar(radar));
    }

    private static <T extends Entity> T floating(T entity, Vec3 at) {
        entity.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        entity.setNoGravity(true);
        entity.level().addFreshEntity(entity);
        return entity;
    }

    private static void aim(Entity shooter, Entity target) {
        double dx = target.getX() - shooter.getX();
        double dy = target.getY() + target.getBbHeight() / 2.0F - shooter.getY();
        double dz = target.getZ() - shooter.getZ();
        shooter.setYRot((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        shooter.setXRot(-(float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** A profile whose answer the test changes, recording what it hears. */
    private static final class Profile implements TargetProfile {

        @Nullable
        private TargetSignature signature = TargetSignature.AIR;
        private final List<TargetLock> locks = new ArrayList<>();
        private final List<IncomingMissile> missiles = new ArrayList<>();

        @Override
        public TargetSignature signature(Entity entity) {
            return this.signature;
        }

        @Override
        public void onLockedOn(TargetLock lock) {
            this.locks.add(lock);
        }

        @Override
        public void onMissileLaunched(IncomingMissile missile) {
            this.missiles.add(missile);
        }
    }
}
