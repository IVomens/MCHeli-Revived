package com.ivomens.mcheli.combat.weapon.fire;

import com.ivomens.mcheli.combat.CombatRandom;
import com.ivomens.mcheli.combat.strike.A10StrikeEntity;
import com.ivomens.mcheli.combat.weapon.FireMode;
import com.ivomens.mcheli.combat.weapon.Shot;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.combat.weapon.WeaponRayUtil;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.sound.WorldSounds;
import com.ivomens.mcheli.core.math.Degrees;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Close air support: the shooter designates the block it looks at and three A-10 strafing runs follow,
 * the first on the point and the other two offset to either side. Fired from a vehicle or from the
 * laser designator, which has stats of its own.
 */
public final class CloseAirSupport implements FireMode {

    private static final int RUN_COUNT = 3;
    private static final int FIRST_RUN_DELAY = 50;
    private static final int RUN_SPACING = 45;
    private static final double RUN_SPREAD = 10.0;
    private static final double RUN_ALTITUDE = 20.0;
    /** The A-10 starts this far before the target. */
    private static final double APPROACH_DISTANCE = 90.0;
    private static final double A10_SPEED = 3.0;
    private static final double DESIGNATE_RANGE_CLIENT = 80.0;
    private static final double DESIGNATE_RANGE_SERVER = 150.0;
    /** The client refuses targets closer than this (squared distance, 20 blocks). */
    private static final double MIN_TARGET_DIST_SQ = 400.0;
    /** The designator vehicle mount sits this far above the vehicle. */
    private static final double VEHICLE_DESIGNATOR_HEIGHT = 2.0;

    private final String weaponName;
    private final int power;
    private final int explosionPower;
    private final float speed;
    private Vec3 target = Vec3.ZERO;
    /** Flight direction of the runs: 0 south, 1 west, 2 north, 3 east. */
    private int direction;
    private int ticks;
    private int nextRunTick;
    private int runsDone = RUN_COUNT;
    @Nullable
    private Entity user;
    @Nullable
    private Entity platform;

    /** @param weaponName the weapon the A-10's rounds are named after */
    public CloseAirSupport(String weaponName, int power, int explosionPower, float speed) {
        this.weaponName = weaponName;
        this.power = power;
        this.explosionPower = explosionPower;
        this.speed = speed;
    }

    public CloseAirSupport(WeaponMount mount) {
        this(mount.name(), mount.stats().power(), mount.stats().explosionPower(), mount.stats().speed());
    }

    @Override
    public boolean predict(Shot shot) {
        return this.fireFromPlatform(shot);
    }

    @Override
    public boolean fire(Shot shot) {
        return this.fireFromPlatform(shot);
    }

    private boolean fireFromPlatform(Shot shot) {
        Entity platform = shot.platform();
        if (shot.user() == null) return false;
        boolean designated = this.designate(shot.user(), platform,
                platform.position().add(0.0, VEHICLE_DESIGNATOR_HEIGHT, 0.0));
        if (designated && shot.isClient()) {
            LocalClient.get().playUiSound(shot.mount().spec().sound().locations().fire(), 1.0F, 1.0F);
        }
        return designated;
    }

    /**
     * Designates the block the user looks at from {@code eye} and schedules the runs.
     *
     * @param platform the vehicle the strike is called from, or null for the laser designator
     * @return whether a block was designated
     */
    public boolean designate(Entity user, @Nullable Entity platform, Vec3 eye) {
        Level level = user.level();
        float yaw = user.getYRot();
        double range = level.isClientSide ? DESIGNATE_RANGE_CLIENT : DESIGNATE_RANGE_SERVER;
        Vec3 dst = eye.add(Vec3.directionFromRotation(user.getXRot(), yaw).scale(range));
        Vec3 hit = WeaponRayUtil.clipLoaded(level, eye, dst, user, null);
        if (hit == null || (level.isClientSide && hit.distanceToSqr(eye) < MIN_TARGET_DIST_SQ)) {
            return false;
        }
        this.target = hit;
        // fly across the line of sight, from a random side
        int facing = (int) Degrees.wrap360(yaw + 45.0F) / 90;
        this.direction = Math.floorMod(facing + (CombatRandom.of(level).aim().nextBoolean() ? -1 : 1), 4);
        this.user = user;
        this.platform = platform;
        this.ticks = 0;
        this.nextRunTick = FIRST_RUN_DELAY;
        this.runsDone = 0;
        return true;
    }

    @Override
    public void tick(WeaponMount mount, int cooldown) {
        this.advance(mount.isClientSide(), cooldown);
    }

    /** Once per tick of the weapon's cooldown: the next run comes in on schedule (server). */
    public void advance(boolean client, int cooldown) {
        if (cooldown != 0) {
            this.ticks++;
        }
        if (client || this.runsDone >= RUN_COUNT || cooldown == 0 || this.ticks != this.nextRunTick
                || this.user == null) {
            return;
        }
        double offsetX = 0.0;
        double offsetZ = 0.0;
        if (this.runsDone >= 1) {
            RandomSource random = CombatRandom.of(this.user.level()).aim();
            double side = this.runsDone == 1 ? 1.0 : -1.0;
            double across = random.nextDouble() * RUN_SPREAD * side;
            double along = (random.nextDouble() - 0.5) * RUN_SPREAD;
            boolean flyingNorthSouth = this.direction == 0 || this.direction == 2;
            offsetX = flyingNorthSouth ? across : along;
            offsetZ = flyingNorthSouth ? along : across;
        }
        this.spawnA10(this.target.add(offsetX, RUN_ALTITUDE, offsetZ));
        this.nextRunTick = this.ticks + RUN_SPACING;
        this.runsDone++;
    }

    private void spawnA10(Vec3 over) {
        Vec3 velocity = switch (this.direction) {
            case 0 -> new Vec3(0.0, 0.0, A10_SPEED);
            case 1 -> new Vec3(-A10_SPEED, 0.0, 0.0);
            case 2 -> new Vec3(0.0, 0.0, -A10_SPEED);
            default -> new Vec3(A10_SPEED, 0.0, 0.0);
        };
        Vec3 start = over.subtract(velocity.scale(APPROACH_DISTANCE / A10_SPEED));
        Level level = this.user.level();
        A10StrikeEntity a10 = new A10StrikeEntity(level, start.x, start.y, start.z);
        a10.arm(this.weaponName, this.direction, this.user, this.platform, this.power, this.explosionPower, this.speed);
        a10.setDeltaMovement(velocity);
        level.addFreshEntity(a10);
        WorldSounds.playHeavySound(level, start.x, start.y, start.z, "mcheli:a-10_snd", 150.0F, 1.0F);
    }
}
