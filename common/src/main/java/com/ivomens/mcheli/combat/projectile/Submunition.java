package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.Weapons;
import net.minecraft.world.phys.Vec3;

/**
 * How a cluster weapon's projectile releases its bomblets once its deploy time runs out, and the
 * stream of sub-projectiles some weapons spread while flying.
 * <p>
 * The order in which a bomblet takes its weapon and its parent's warhead is part of the recorded
 * behaviour: split-off rockets keep the parent's piercing, dropped bomblets take the weapon's own.
 */
public enum Submunition {

    NONE,
    /** Rockets that fly on along the parent's heading with a random spread. */
    ROCKETS,
    /** Bomblets that keep half the parent's horizontal speed (cruise missiles). */
    SCATTERED,
    /** Bomblets that fall on with the parent's speed (bombs, containers). */
    FALLING;

    /** Server: counts the deploy time down and releases the bomblets, ending the parent. */
    static void tickRelease(ProjectileEntity parent) {
        WeaponSpec info = parent.spec();
        if (parent.level().isClientSide || parent.cluster.releaseTimer <= 0 || parent.isRemoved() || info == null) {
            return;
        }
        if (--parent.cluster.releaseTimer == 0) {
            for (int i = 0; i < info.submunition().count(); i++) {
                parent.kind().submunition().release(parent, info);
            }
            parent.discard();
        }
    }

    private void release(ProjectileEntity parent, WeaponSpec info) {
        switch (this) {
            case ROCKETS -> splitRocket(parent, info);
            case SCATTERED -> dropBomblet(parent, info, 0.5);
            case FALLING -> dropBomblet(parent, info, 1.0);
            case NONE -> {
            }
        }
    }

    /** Releases all the bomblets now, as a cruise missile does near its target point. */
    public static void releaseAll(ProjectileEntity parent, WeaponSpec info) {
        for (int i = 0; i < info.submunition().count(); i++) {
            parent.kind().submunition().release(parent, info);
        }
    }

    private static void splitRocket(ProjectileEntity parent, WeaponSpec info) {
        Vec3 m = parent.getDeltaMovement();
        ProjectileEntity rocket = ProjectileEntity.create(ProjectileKind.ROCKET, parent.level(), parent.position(), m,
                parent.getYRot(), parent.getXRot(), parent.speed());
        rocket.setWeapon(parent.weaponName());
        inherit(rocket, parent);
        addRandomSpread(parent, rocket, info.submunition().spread());
        rocket.markBomblet();
        parent.level().addFreshEntity(rocket);
    }

    /**
     * A bomblet of the parent's own kind with the parent's velocity, halved vertically, the horizontal
     * part scaled by {@code horizontalScale}, plus a random spread.
     */
    private static void dropBomblet(ProjectileEntity parent, WeaponSpec info, double horizontalScale) {
        Vec3 m = parent.getDeltaMovement();
        ProjectileEntity bomblet = ProjectileEntity.create(parent.kind(), parent.level(), parent.position(), m,
                parent.getRandom().nextInt(360), 0.0F, parent.speed());
        inherit(bomblet, parent);
        bomblet.setWeapon(parent.weaponName());
        float spread = info.submunition().spread();
        bomblet.setDeltaMovement(
                m.x * horizontalScale + (parent.getRandom().nextFloat() - 0.5F) * spread,
                m.y * horizontalScale / 2.0 + (parent.getRandom().nextFloat() - 0.5F) * spread / 2.0F,
                m.z * horizontalScale + (parent.getRandom().nextFloat() - 0.5F) * spread);
        bomblet.markBomblet();
        parent.level().addFreshEntity(bomblet);
    }

    /** The child takes the warhead, the shooters and the airburst distance of the projectile it splits off. */
    private static void inherit(ProjectileEntity child, ProjectileEntity parent) {
        child.warhead().copyFrom(parent.warhead());
        child.setShooter(parent.shooter(), parent.platform());
        child.fuzes().setAirburstDistance(parent.fuzes().airburstDistance());
    }

    private static void addRandomSpread(ProjectileEntity parent, ProjectileEntity child, float spread) {
        child.setDeltaMovement(child.getDeltaMovement().add(
                (parent.getRandom().nextFloat() - 0.5) * spread,
                (parent.getRandom().nextFloat() - 0.5) * spread,
                (parent.getRandom().nextFloat() - 0.5) * spread));
    }

    /**
     * Server: streams sub-projectiles forward, either as a generic in-air spawner
     * ({@code SpawnBulletInAir}) or as AHEAD cluster slugs once the airburst fuze armed the round. Each
     * child is a round of the {@code BombletModel} weapon.
     */
    static void spread(ProjectileEntity parent, WeaponSpec info) {
        if (parent.level().isClientSide) return;
        boolean spawning = info.submunition().spawnInAir()
                || (info.submunition().ahead() && parent.fuzes().isAheadArmed());
        if (spawning && parent.cluster.salvosSpread < info.submunition().spawnMaxNum() && parent.tickCount > 5
                && info.spawnBulletIntervalTick() > 0 && parent.tickCount % info.spawnBulletIntervalTick() == 0) {
            parent.cluster.salvosSpread++;
            WeaponSpec childInfo = Weapons.get(info.render().bombletModel()).orElse(null);
            if (childInfo != null) {
                for (int i = 0; i < info.submunition().spawnPerNum(); i++) {
                    spawnChild(parent, info, childInfo);
                }
            }
        }
        if (info.submunition().destructAfterSpawn()
                && parent.cluster.salvosSpread >= info.submunition().spawnMaxNum()) {
            parent.discard();
        }
    }

    private static void spawnChild(ProjectileEntity parent, WeaponSpec info, WeaponSpec childInfo) {
        Vec3 m = parent.getDeltaMovement();
        boolean inherit = info.submunition().spawnInheritSpeed();
        ProjectileEntity child = ProjectileEntity.create(ProjectileKind.BULLET, parent.level(), parent.position(),
                inherit ? m : new Vec3(1.0E-6, 1.0E-6, 1.0E-6), parent.getYRot(), parent.getXRot(),
                inherit ? parent.speed() : 0.001);
        child.setWeapon(info.render().bombletModel());
        inherit(child, parent);
        // the child uses its own warhead and never airbursts again
        child.warhead().setPower(childInfo.definition().baseDamage());
        child.warhead().setExplosionPower(childInfo.explosion().power(), childInfo.explosionInWater());
        child.fuzes().setAirburstDistance(0);
        addRandomSpread(parent, child, info.submunition().spread());
        parent.level().addFreshEntity(child);
    }
}
