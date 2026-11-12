package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.core.ballistics.ImpactPrediction;
import com.ivomens.mcheli.core.ballistics.ProjectileSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Where a shot would land (the CCIP sight): the projectile's flight simulated step by step with the
 * weapon's gravity and water drag ({@link ImpactPrediction}), through the shooter's level. The simulation
 * stops at unloaded chunks instead of loading them.
 */
final class Trajectories {

    /** The speed the projectile entities are capped to. */
    private static final float MAX_PROJECTILE_SPEED = 3.9F;
    /** The simulation gives up once the projectile falls this far below the muzzle. */
    private static final double MAX_DROP = 100.0;

    private Trajectories() {
    }

    @Nullable
    static Vec3 impact(Shot shot) {
        WeaponMount mount = shot.mount();
        WeaponSpec info = mount.spec();
        WeaponKind.Trajectory trajectory = mount.kind().trajectory();
        double stepFactor = stepFactor(trajectory, info);
        Vec3 motion = launchMotion(shot, trajectory, Shots.launchDirection(shot).normalize());
        double muzzleOffset = trajectory == WeaponKind.Trajectory.GUN ? 0.5 : 0.0;
        Vec3 pos = shot.origin().add(motion.x * muzzleOffset, motion.y * muzzleOffset, motion.z * muzzleOffset);
        int maxSteps = info.explosion().fuseTime() > 0
                ? Math.min(ImpactPrediction.MAX_STEPS, info.explosion().fuseTime()) : ImpactPrediction.MAX_STEPS;
        return simulate(shot, info, pos, motion, stepFactor, maxSteps);
    }

    @Nullable
    private static Vec3 simulate(Shot shot, WeaponSpec info, Vec3 start, Vec3 launch, double stepFactor, int maxSteps) {
        ImpactPrediction.Fall fall = new ImpactPrediction.Fall(info.ballistics().gravity(),
                info.ballistics().gravityInWater(), info.ballistics().velocityInWater());
        return ImpactPrediction.impact(start.x, start.y, start.z, launch.x, launch.y, launch.z, fall, stepFactor,
                maxSteps, shot.origin().y - MAX_DROP, new LevelSurroundings(shot.level(), shot.platform()));
    }

    /** Rounds and rockets faster than one step per tick move several steps per tick. */
    private static double stepFactor(WeaponKind.Trajectory trajectory, WeaponSpec info) {
        boolean multiStep = trajectory == WeaponKind.Trajectory.GUN || trajectory == WeaponKind.Trajectory.ROCKET;
        return ProjectileSteps.stepFactor(multiStep, info.ballistics().acceleration());
    }

    /** The shooter's level as the prediction sees it; reuses one block position and never loads a chunk. */
    private record LevelSurroundings(Level level, Entity platform, BlockPos.MutableBlockPos pos)
            implements ImpactPrediction.Surroundings<Vec3> {

        LevelSurroundings(Level level, Entity platform) {
            this(level, platform, new BlockPos.MutableBlockPos());
        }

        @Override
        public boolean isLoaded(double x, double y, double z) {
            return this.level.hasChunkAt(this.pos.set(x, y, z));
        }

        @Override
        public boolean isWater(double x, double y, double z) {
            return this.level.getFluidState(this.pos.set(x, y, z)).is(FluidTags.WATER);
        }

        @Override
        @Nullable
        public Vec3 hit(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
            BlockHitResult hit = this.level.clip(new ClipContext(new Vec3(fromX, fromY, fromZ), new Vec3(toX, toY, toZ),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.platform));
            return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : null;
        }
    }

    private static Vec3 launchMotion(Shot shot, WeaponKind.Trajectory trajectory, Vec3 look) {
        return switch (trajectory) {
            case BOMB -> shot.platform().getDeltaMovement();
            case TORPEDO -> look.scale(shot.mount().spec().ballistics().acceleration()).add(shot.platform().getDeltaMovement());
            case DIRECT, GUN, ROCKET -> look.scale(Math.min(shot.mount().stats().speed(), MAX_PROJECTILE_SPEED));
        };
    }
}
