package com.ivomens.mcheli.vehicle;

import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.model.GroundGrip;
import com.ivomens.mcheli.core.sim.model.GroundSettle;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.util.Terrain;
import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Small movement helpers shared by the vehicle entities. */
public final class VehicleMotion {

    private VehicleMotion() {
    }

    /** True if no solid block is within {@code depth} blocks below the entity, in a {@code size} wide column. */
    public static boolean isClearBelow(Entity entity, int size, int depth) {
        return !Terrain.hasBlockY(entity, size, -depth);
    }

    /** Rotates a vehicle-local offset into world orientation. */
    public static Vec3 rotate(AircraftEntity vehicle, Vec3 localOffset) {
        return Rotations.rotate(localOffset, -vehicle.getYRot(), -vehicle.getXRot(), -vehicle.roll());
    }

    /** Client-side dead reckoning for flying vehicles between server position updates. */
    public static void predictFlightMovement(AircraftEntity vehicle) {
        Vec3 motion = vehicle.getDeltaMovement();
        vehicle.setPos(vehicle.getX() + motion.x, vehicle.getY() + motion.y, vehicle.getZ() + motion.z);
        double motionX = motion.x;
        double motionZ = motion.z;
        if (!vehicle.isDestroyed() && (vehicle.onGround() || !isClearBelow(vehicle, 1, 2))) {
            motionX *= 0.95;
            motionZ *= 0.95;
            settleOnGround(vehicle, 0.95F);
        }
        if (vehicle.isInWater()) {
            motionX *= 0.99;
            motionZ *= 0.99;
        }
        if (motionX != motion.x || motionZ != motion.z) {
            vehicle.setDeltaMovement(motionX, motion.y, motionZ);
        }
    }

    /** Eases the airframe towards its resting pitch and wings level, by {@code factor} per tick. */
    private static void settleOnGround(AircraftEntity vehicle, float factor) {
        if (vehicle.spec() != null) {
            vehicle.setXRot(GroundSettle.level(vehicle.body().attitude(),
                    vehicle.spec().file().physics().onGroundPitch(), factor).pitch());
        }
        vehicle.rollAngle().set(vehicle.roll() * factor);
    }

    /** Server: with Collision_DestroyBlock, driving over snow layers, lily pads and cakes breaks them. */
    public static void trampleSoftBlocks(Entity vehicle) {
        if (!McheliConfig.combat().collisionDestroyBlock()) {
            return;
        }
        int baseY = Mth.floor(vehicle.getY());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int corner = 0; corner < 4; corner++) {
            int x = Mth.floor(vehicle.getX() + (corner % 2 - 0.5) * 0.8);
            int z = Mth.floor(vehicle.getZ() + (corner / 2D - 0.5) * 0.8);
            for (int dy = 0; dy < 2; dy++) {
                pos.set(x, baseY + dy, z);
                BlockState state = vehicle.level().getBlockState(pos);
                if (state.is(Blocks.SNOW)) {
                    vehicle.level().removeBlock(pos, false);
                } else if (state.is(Blocks.LILY_PAD) || state.is(Blocks.CAKE)) {
                    vehicle.level().destroyBlock(pos, false, vehicle);
                }
            }
        }
    }

    /** Tankers and supply trucks only serve vehicles parked on solid ground, or afloat if they can float. */
    public static boolean isParkedForSupply(AircraftEntity vehicle) {
        boolean groundBelow = Terrain.hasBlockY(vehicle, 1, -3);
        return vehicle.canFloatWater() ? groundBelow : groundBelow && !vehicle.isInWater();
    }

    /** Ground grip on the entity's own velocity; see {@link GroundGrip}. */
    public static void applyGroundGrip(Entity vehicle, float yaw, GroundGrip.Grip grip) {
        Vec3 motion = vehicle.getDeltaMovement();
        Vec3d gripped = GroundGrip.apply(new Vec3d(motion.x, motion.y, motion.z), yaw, grip);
        vehicle.setDeltaMovement(gripped.x(), gripped.y(), gripped.z());
    }

    /** Speed along the vehicle's nose; negative when reversing. */
    public static double getSignedForwardSpeed(Entity vehicle, float yaw) {
        Vec3 motion = vehicle.getDeltaMovement();
        return GroundGrip.signedForwardSpeed(new Vec3d(motion.x, motion.y, motion.z), yaw);
    }

    /** True if the block just below is water or air, i.e. the vehicle is not resting on solid ground. */
    public static boolean isAboveWaterOrAir(Entity vehicle) {
        Block below = Terrain.firstBlockY(vehicle, 3, -2, false);
        return below == Blocks.WATER || below == Blocks.AIR;
    }
}
