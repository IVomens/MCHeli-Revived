package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.core.ballistics.FlightAngles;
import com.ivomens.mcheli.core.ballistics.ProjectileForces;
import com.ivomens.mcheli.core.ballistics.ProjectileSteps;
import com.ivomens.mcheli.core.guidance.Pursuit;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.ParticleParams;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * How projectiles move: the forces of a tick (boost, gravity, drag, water), steering towards a
 * direction, and the move itself, with the chunk checks that keep a projectile from loading chunks. The
 * math is {@code :core}'s ({@link ProjectileForces}, {@link Pursuit}, {@link FlightAngles},
 * {@link ProjectileSteps}); this class applies it to the projectile entity.
 * <p>
 * Fast weapons move {@link ProjectileEntity#stepFactor} steps per tick rather than having their speed
 * raised above {@link ProjectileEntity#MAX_SPEED}.
 */
public final class FlightModel {

    private FlightModel() {
    }

    /** Boost phase, gravity and air drag, or the gravity under water. */
    static void applyForces(ProjectileEntity projectile, WeaponSpec info) {
        Vec3 m = projectile.getDeltaMovement();
        Vector3d motion = new Vector3d(m.x, m.y, m.z);
        if (projectile.isInWater()) {
            ProjectileForces.fall(motion, gravityInWater(projectile, info));
        } else {
            if (ProjectileForces.isBoosting(projectile.tickCount, info.ballistics().speedFactorStartTick(),
                    info.ballistics().speedFactorEndTick())
                    && ProjectileForces.boost(motion, info.ballistics().speedFactor())) {
                projectile.setSpeed(projectile.speed() + info.ballistics().speedFactor());
            }
            ProjectileForces.fall(motion, gravity(projectile, info));
            ProjectileForces.drag(motion, info.missile().ballistic().dragInAir());
        }
        projectile.setDeltaMovement(motion.x, motion.y, motion.z);
    }

    /** The weapon's gravity; the bomblets of a cruise missile drift slowly upwards instead. */
    public static float gravity(ProjectileEntity projectile, WeaponSpec info) {
        return driftsUp(projectile) ? BOMBLET_LIFT : info.ballistics().gravity();
    }

    private static float gravityInWater(ProjectileEntity projectile, WeaponSpec info) {
        return driftsUp(projectile) ? BOMBLET_LIFT : info.ballistics().gravityInWater();
    }

    /** Cruise missile bomblets rise at this rate instead of falling. */
    private static final float BOMBLET_LIFT = -0.03F;

    private static boolean driftsUp(ProjectileEntity projectile) {
        return projectile.kind() == ProjectileKind.AS_MISSILE && projectile.syncedBombletFlag() == 1;
    }

    /**
     * Turns the motion towards the unit direction (dirX, dirY, dirZ). Speed and heading are eased
     * separately: lerping the velocity per axis would cut the chord of a hard turn and bleed speed.
     *
     * @param maxSpeed   cap of the eased speed
     * @param turnFactor fraction of the way to turn and accelerate this tick
     */
    public static Vec3 steer(ProjectileEntity projectile, Vec3 motion, double dirX, double dirY, double dirZ,
                             double maxSpeed, double turnFactor) {
        Vector3d turned = Pursuit.turn(motion.x, motion.y, motion.z, dirX, dirY, dirZ, projectile.speed(), maxSpeed,
                turnFactor);
        return new Vec3(turned.x, turned.y, turned.z);
    }

    /** Points the projectile along its motion. */
    public static void faceMotion(ProjectileEntity projectile) {
        Vec3 m = projectile.getDeltaMovement();
        if (FlightAngles.hasDirection(m.x, m.y, m.z)) {
            projectile.setYRot(FlightAngles.yaw(m.x, m.z));
            projectile.setXRot(FlightAngles.pitch(m.x, m.y, m.z));
        }
    }

    /**
     * Server: whether the chunks of this tick's move are loaded. If not, the projectile is removed or,
     * with a delay fuze, stops where it is until the fuze runs out.
     */
    static boolean isNextStepLoaded(ProjectileEntity projectile, WeaponSpec info) {
        Vec3 m = projectile.getDeltaMovement();
        double nextX = projectile.getX() + m.x * projectile.stepFactor();
        double nextZ = projectile.getZ() + m.z * projectile.stepFactor();
        if (isSegmentLoaded(projectile.level(), projectile.getX(), projectile.getZ(), nextX, nextZ)) {
            return true;
        }
        if (info.explosion().fuseDelay() <= 0) {
            projectile.discard();
        } else {
            projectile.fuzes().startDelay(info);
            projectile.setDeltaMovement(Vec3.ZERO);
            ProjectileSync.disturbed(projectile);
        }
        return false;
    }

    /**
     * True if every chunk crossed by the segment is loaded (never loads anything). Checked before ray
     * tracing, since block lookups in unloaded chunks would load them.
     */
    static boolean isSegmentLoaded(Level level, double x0, double z0, double x1, double z1) {
        return ProjectileSteps.isSegmentLoaded(x0, z0, x1, z1, level::hasChunk);
    }

    /** Moves by the motion times the step factor and turns along the motion; splashes and bubbles on a client. */
    static void move(ProjectileEntity projectile) {
        Vec3 m = projectile.getDeltaMovement();
        double factor = projectile.stepFactor();
        double px = projectile.getX() + m.x * factor;
        double py = projectile.getY() + m.y * factor;
        double pz = projectile.getZ() + m.z * factor;
        if (projectile.level().isClientSide) {
            splash(projectile, px, py, pz);
            if (projectile.isInWater()) {
                projectile.level().addParticle(ParticleTypes.BUBBLE, px - m.x * 0.25, py - m.y * 0.25, pz - m.z * 0.25,
                        m.x, m.y, m.z);
            }
        }
        projectile.setPos(px, py, pz);
        faceMotion(projectile);
    }

    private static boolean isWaterAt(Level level, BlockPos.MutableBlockPos pos, double x, double y, double z) {
        pos.set((int) (x + 0.5), (int) (y + 0.5), (int) (z + 0.5));
        return level.getBlockState(pos).is(Blocks.WATER);
    }

    /** Client: water splash where the projectile enters the water this tick. */
    private static void splash(ProjectileEntity projectile, double nextX, double nextY, double nextZ) {
        WeaponSpec info = projectile.spec();
        if (info == null || info.definition().baseDamage() <= 0) return;
        Level level = projectile.level();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double x = projectile.getX();
        double y = projectile.getY();
        double z = projectile.getZ();
        if (isWaterAt(level, pos, x, y, z) || !isWaterAt(level, pos, nextX, nextY, nextZ)) return;
        double dx = nextX - x;
        double dy = nextY - y;
        double dz = nextZ - z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0.15) return;
        for (int i = 0; i <= length; i++) {
            x += dx / length;
            y += dy / length;
            z += dz / length;
            if (isWaterAt(level, pos, x, y, z)) {
                splashParticles(projectile, x, y, z, Math.min(info.definition().baseDamage(), 20));
                return;
            }
        }
    }

    private static void splashParticles(ProjectileEntity projectile, double x, double y, double z, int power) {
        int count = projectile.getRandom().nextInt(1 + power / 3) + power / 2 + 1;
        float strength = power * 0.03F;
        for (int i = 0; i < count; i++) {
            Effects.get().spawn(new ParticleParams(projectile.level(), "splash", x, y + 0.5, z,
                    strength * (projectile.getRandom().nextDouble() - 0.5) * 0.3,
                    strength * (projectile.getRandom().nextDouble() * 0.5 + 0.5) * 1.8,
                    strength * (projectile.getRandom().nextDouble() - 0.5) * 0.3, strength * 5.0F));
        }
    }
}
