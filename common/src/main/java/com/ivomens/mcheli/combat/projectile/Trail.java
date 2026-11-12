package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.ParticleParams;
import net.minecraft.world.phys.Vec3;

/**
 * The smoke a projectile leaves behind on the client: three puffs per tick of the weapon's trajectory
 * particle between its last positions, sized by the weapon's smoke size. Kinds differ in when they
 * smoke and how big the puffs are.
 */
public enum Trail {

    NONE(0.0F),
    /** Rockets a weapon fired, not the rockets they split into. */
    MAIN_ROCKET(2.5F),
    /** Missiles after their first ticks, once clear of the launcher. */
    MISSILE(3.5F),
    /** Cruise missiles, not their bomblets. */
    CRUISE_MISSILE(5.0F),
    /** Steered missiles, unless their shooter watches through the missile camera. */
    STEERED_MISSILE(2.5F),
    /** Torpedoes in the water. */
    UNDERWATER(2.5F),
    /** Dropped containers, with puffs twice the size of the others. */
    CONTAINER(7.0F);

    private static final int PUFFS_PER_TICK = 3;
    private static final int MISSILE_CLEAR_TICKS = 4;
    private static final int MAX_PUFFS = 50;

    /** Puff size per unit of the weapon's smoke size. */
    private final float sizeFactor;

    Trail(float sizeFactor) {
        this.sizeFactor = sizeFactor;
    }

    /** Client: the puffs of this tick, if the projectile smokes now. */
    public void emit(ProjectileEntity projectile) {
        WeaponSpec info = projectile.spec();
        if (this == NONE || info == null || info.render().smoke().disabled() || !this.smokesNow(projectile)) {
            return;
        }
        puffs(projectile, info.render().trajectoryParticle(), PUFFS_PER_TICK,
                this.sizeFactor * info.render().smoke().size());
    }

    private boolean smokesNow(ProjectileEntity projectile) {
        return switch (this) {
            case MAIN_ROCKET -> !projectile.isBomblet();
            case MISSILE -> projectile.age() > MISSILE_CLEAR_TICKS;
            case CRUISE_MISSILE -> projectile.syncedBombletFlag() == 0;
            case STEERED_MISSILE -> !projectile.isTrailHidden();
            case UNDERWATER -> projectile.isInWater();
            case CONTAINER, NONE -> true;
        };
    }

    /** Client: {@code count} particles between the last two tick positions. */
    static void puffs(ProjectileEntity projectile, String name, int count, float size) {
        if (!projectile.level().isClientSide || name == null || name.isEmpty() || count < 1 || count > MAX_PUFFS) {
            return;
        }
        Vec3 tail = projectile.trailTail;
        double stepX = (projectile.getX() - projectile.xo) / count;
        double stepY = (projectile.getY() - projectile.yo) / count;
        double stepZ = (projectile.getZ() - projectile.zo) / count;
        double tailStepX = (projectile.xo - tail.x) / count;
        double tailStepY = (projectile.yo - tail.y) / count;
        double tailStepZ = (projectile.zo - tail.z) / count;
        boolean explode = name.equals("explode");
        for (int i = 0; i < count; i++) {
            double x = (projectile.xo + stepX * i + (tail.x + tailStepX * i)) / 2.0;
            double y = (projectile.yo + stepY * i + (tail.y + tailStepY * i)) / 2.0;
            double z = (projectile.zo + stepZ * i + (tail.z + tailStepZ * i)) / 2.0;
            if (explode) {
                ParticleParams prm = new ParticleParams(projectile.level(), "smoke", x, y, z);
                prm.setSize(size + projectile.getRandom().nextFloat());
                Effects.get().spawn(prm);
            } else {
                Effects.get().vanilla(name, x, y, z, 0.0, 0.0, 0.0);
            }
        }
    }
}
