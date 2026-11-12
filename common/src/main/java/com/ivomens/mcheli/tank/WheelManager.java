package com.ivomens.mcheli.tank;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.HitBoxEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.util.math.Rotations;
import com.ivomens.mcheli.vehicle.VehicleMotion;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Suspension of a tank: a pair of {@link WheelProbe} probes per axle that follow the terrain, from which the
 * hull's target pitch and roll are derived.
 */
public class WheelManager {

    /** Small lift that keeps the wheel probes out of the collision fudge zone. */
    private static final double WHEEL_Y_COMP = 0.35D;
    /** Below this horizontal speed the vehicle counts as stationary. */
    private static final double GROUND_HORZ_SPEED_EPS = 0.06D;
    private static final float MAX_DOWN_PITCH_STATIONARY = 3.5F;
    /** How far a wheel may lag behind its mount point before it is pulled along. */
    private static final double MAX_WHEEL_LAG = 2.0;

    public final AircraftEntity parent;
    /** Left/right pairs, ordered by axle. */
    private WheelProbe[] wheels = new WheelProbe[0];
    private Vec3 weightedCenter = Vec3.ZERO;
    private float targetPitch;
    private float targetRoll;
    private float prevYaw;
    /** Front-to-back length of the wheelbase. */
    private double wheelbase;
    /** Solid entity shapes around the wheels, rebuilt once per move instead of once per wheel. */
    private final List<VoxelShape> entityShapes = new ArrayList<>();
    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();
    private final RandomSource dustRandom;

    public float targetPitch() {
        return this.targetPitch;
    }

    public float targetRoll() {
        return this.targetRoll;
    }

    public Vec3 weightedCenter() {
        return this.weightedCenter;
    }

    public WheelProbe[] wheels() {
        return this.wheels;
    }

    /** @param dustRandom picks the wheels that kick up dust; client effects only, never the physics */
    public WheelManager(AircraftEntity vehicle, RandomSource dustRandom) {
        this.parent = vehicle;
        this.dustRandom = dustRandom;
    }

    public void createWheels(Level level, List<VehicleParts.Wheel> axles, Vec3 weightedCenter) {
        wheels = new WheelProbe[axles.size() * 2];
        this.weightedCenter = weightedCenter;
        double minZ = 999999.0;
        double maxZ = -999999.0;
        for (int i = 0; i < wheels.length; i++) {
            Vec3 axle = axles.get(i / 2).pos();
            WheelProbe wheel = new WheelProbe(level, new Vec3(i % 2 == 0 ? axle.x : -axle.x, axle.y, axle.z),
                    weightedCenter);
            Vec3 worldPos = parent.body().toWorld(wheel.mountPoint().x, wheel.mountPoint().y, wheel.mountPoint().z);
            wheel.setPos(worldPos.x, worldPos.y + 1.0, worldPos.z);
            wheels[i] = wheel;
            minZ = Math.min(minZ, wheel.mountPoint().z);
            maxZ = Math.max(maxZ, wheel.mountPoint().z);
        }
        wheelbase = maxZ - minZ;
    }

    /**
     * Gathers the collision boxes of solid, non-living entities near the wheels (other vehicles,
     * boats, ...) with a single query covering every wheel's sweep.
     */
    private void collectEntityShapes() {
        entityShapes.clear();
        if (wheels.length == 0) {
            return;
        }

        AABB area = null;
        for (WheelProbe wheel : wheels) {
            AABB swept = wheel.reach();
            area = area == null ? swept : area.minmax(swept);
        }

        area = area.inflate(0.25 + WheelProbe.STEP_HEIGHT);
        List<Entity> nearby = parent.level().getEntities(parent, area, entity ->
                !(entity instanceof LivingEntity) && !(entity instanceof SeatEntity) &&
                        !(entity instanceof HitBoxEntity) && entity != parent && entity.canBeCollidedWith());
        for (Entity entity : nearby) {
            entityShapes.add(Shapes.create(entity.getBoundingBox()));
        }
    }

    /** Moves the wheels along with the hull's motion and recomputes the target attitude. */
    public void move(double x, double y, double z) {
        AircraftEntity vehicle = parent;
        if (vehicle.spec() == null) {
            return;
        }

        for (WheelProbe wheel : wheels) {
            Vec3 mount = vehicle.body().toWorld(wheel.mountPoint().x, wheel.mountPoint().y, wheel.mountPoint().z);
            wheel.setMotion(mount.x - wheel.getX() + x, (mount.y + WHEEL_Y_COMP - wheel.getY()) * 0.15,
                    mount.z - wheel.getZ() + z);
        }

        collectEntityShapes();
        for (WheelProbe wheel : wheels) {
            wheel.moveTowardsMount(entityShapes);
        }

        groundOutermostAxles();
        updateTargetAttitude(vehicle);
        keepWheelsNearMounts(vehicle);
    }

    /**
     * When no axle behind (or in front of) the weighted center touches the ground, the outermost one on that
     * side counts as touching, so a hull resting on one end still tilts towards it.
     */
    private void groundOutermostAxles() {
        int axleCount = wheels.length / 2;
        int rearmost = -1;
        for (int i = 0; i < axleCount; i++) {
            rearmost = i;
            if (!wheels[i * 2].isFrontOfCenter() && axleTouches(i)) {
                rearmost = -1;
                break;
            }
        }
        if (rearmost >= 0) {
            setAxleOnGround(rearmost);
        }

        int frontmost = -1;
        for (int i = axleCount - 1; i >= 0; i--) {
            frontmost = i;
            if (wheels[i * 2].isFrontOfCenter() && axleTouches(i)) {
                frontmost = -1;
                break;
            }
        }
        if (frontmost >= 0) {
            setAxleOnGround(frontmost);
        }
    }

    private boolean axleTouches(int axle) {
        return wheels[axle * 2].touchesGround() || wheels[axle * 2 + 1].touchesGround();
    }

    private void setAxleOnGround(int axle) {
        wheels[axle * 2].countAsTouchingGround();
        wheels[axle * 2 + 1].countAsTouchingGround();
    }

    /** Derives pitch and roll from the ground normal spanned by each grounded axle and the weighted center. */
    private void updateTargetAttitude(AircraftEntity vehicle) {
        Vec3 normalSum = groundNormalSum(vehicle);
        double normalLength = Math.sqrt(normalSum.x * normalSum.x + normalSum.y * normalSum.y + normalSum.z * normalSum.z);
        if (normalLength <= 1.0E-4) {
            return;
        }
        double normalX = normalSum.x / normalLength;
        double normalY = normalSum.y / normalLength;
        double normalZ = normalSum.z / normalLength;

        // on a slope the hull slides gently downhill
        if (normalY > 0.01 && normalY < 0.7) {
            Vec3 motion = vehicle.getDeltaMovement();
            vehicle.setDeltaMovement(motion.x + normalX / 50.0, motion.y, motion.z + normalZ / 50.0);
        }
        aimAt(vehicle, normalX, normalY, normalZ);
    }

    /**
     * The sum of the ground normals each axle spans with the weighted center, weighted by the axle's distance
     * from the center; airborne axles do not count.
     */
    private Vec3 groundNormalSum(AircraftEntity vehicle) {
        Vec3 center = vehicle.body().toWorld(weightedCenter);
        double centerX = vehicle.getX() + (center.x - vehicle.getX());
        double centerY = vehicle.getY() + weightedCenter.y + WHEEL_Y_COMP;
        double centerZ = vehicle.getZ() + (center.z - vehicle.getZ());
        double normalX = 0.0;
        double normalY = 0.0;
        double normalZ = 0.0;
        for (int i = 0; i < wheels.length / 2; i++) {
            WheelProbe left = wheels[i * 2];
            WheelProbe right = wheels[i * 2 + 1];
            double ax = left.getX() - centerX, ay = left.getY() - centerY, az = left.getZ() - centerZ;
            double bx = right.getX() - centerX, by = right.getY() - centerY, bz = right.getZ() - centerZ;
            // cross product, oriented so the normal points up for front and rear axles alike
            double cx, cy, cz;
            if (left.mountPoint().z >= 0.0) {
                cx = by * az - bz * ay;
                cy = bz * ax - bx * az;
                cz = bx * ay - by * ax;
            } else {
                cx = ay * bz - az * by;
                cy = az * bx - ax * bz;
                cz = ax * by - ay * bx;
            }

            double length = Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (length < 1.0E-4) {
                continue;
            }
            // axles further from the center weigh more; airborne axles not at all
            double weight = left.touchesGround() || right.touchesGround() ? Math.abs(left.mountPoint().z / wheelbase) : 0.0;
            normalX += cx / length * weight;
            normalY += cy / length * weight;
            normalZ += cz / length * weight;
        }
        return new Vec3(normalX, normalY, normalZ);
    }

    /** The target pitch and roll that tilt the hull onto the unit ground normal, limited per tick. */
    private void aimAt(AircraftEntity vehicle, double normalX, double normalY, double normalZ) {
        // rotate into vehicle space (same as Vec3#yRot)
        float yawRad = (float) (vehicle.getYRot() * Math.PI / 180.0);
        double cos = Mth.cos(yawRad);
        double sin = Mth.sin(yawRad);
        double localX = normalX * cos + normalZ * sin;
        double localZ = normalZ * cos - normalX * sin;
        float pitch = (float) (90.0 - Math.atan2(normalY, localZ) * 180.0 / Math.PI);
        float roll = -((float) (90.0 - Math.atan2(normalY, localX) * 180.0 / Math.PI));

        pitch = limitStep(pitch, vehicle.getXRot(), vehicle.spec().file().physics().groundTilt().pitch());
        roll = limitStep(roll, vehicle.roll(), vehicle.spec().file().physics().groundTilt().roll());

        // when essentially stationary on the ground, avoid nose digging
        boolean onSurface = vehicle.onGround() || !VehicleMotion.isClearBelow(vehicle, 1, 2);
        if (onSurface && vehicle.getDeltaMovement().horizontalDistance() < GROUND_HORZ_SPEED_EPS) {
            if (pitch - vehicle.getXRot() < -MAX_DOWN_PITCH_STATIONARY) {
                pitch = vehicle.getXRot() - MAX_DOWN_PITCH_STATIONARY;
            }
            pitch = vehicle.getXRot() + (pitch - vehicle.getXRot()) * 0.45F;
        }

        targetPitch = pitch;
        targetRoll = roll;
    }

    private static float limitStep(float target, float current, float maxStep) {
        if (target - current > maxStep) {
            target = current + maxStep;
        }
        if (target - current < -maxStep) {
            target = current - maxStep;
        }
        return target;
    }

    /** Wheels that fell too far behind (stuck on a ledge, teleports) are pulled back to their mount. */
    private void keepWheelsNearMounts(AircraftEntity vehicle) {
        for (WheelProbe wheel : wheels) {
            Vec3 mount = getTransformedPosition(wheel.mountPoint().x, wheel.mountPoint().y, wheel.mountPoint().z, vehicle, vehicle.getYRot(),
                    targetPitch, targetRoll);
            double liftedY = mount.y + WheelProbe.STEP_HEIGHT / 2.0F;
            double x = wheel.getX();
            double y = wheel.getY();
            double z = wheel.getZ();
            if (x > mount.x + MAX_WHEEL_LAG) {
                x = mount.x + MAX_WHEEL_LAG;
                y = liftedY;
            }
            if (x < mount.x - MAX_WHEEL_LAG) {
                x = mount.x - MAX_WHEEL_LAG;
                y = liftedY;
            }
            if (z > mount.z + MAX_WHEEL_LAG) {
                z = mount.z + MAX_WHEEL_LAG;
                y = liftedY;
            }
            if (z < mount.z - MAX_WHEEL_LAG) {
                z = mount.z - MAX_WHEEL_LAG;
                y = liftedY;
            }
            wheel.setPos(x, y, z);
        }
    }

    public Vec3 getTransformedPosition(double x, double y, double z, AircraftEntity vehicle, float yaw, float pitch,
                                       float roll) {
        return Rotations.rotate(x, y, z, -yaw, -pitch, -roll).add(vehicle.getX(), vehicle.getY(), vehicle.getZ());
    }

    /** Wheels crush snow layers, lily pads and cakes. */
    public void updateBlock() {
        if (!McheliConfig.combat().collisionDestroyBlock()) {
            return;
        }

        Level level = parent.level();
        for (WheelProbe wheel : wheels) {
            Vec3 pos = parent.body().toWorld(wheel.mountPoint());
            scratchPos.set(Mth.floor(pos.x + 0.5), Mth.floor(pos.y + 0.5), Mth.floor(pos.z + 0.5));
            if (!level.hasChunkAt(scratchPos)) {
                continue;
            }

            BlockState state = level.getBlockState(scratchPos);
            if (state.getBlock() == Blocks.SNOW) {
                level.removeBlock(scratchPos, false);
            } else if (state.is(Blocks.LILY_PAD) || state.is(Blocks.CAKE)) {
                level.destroyBlock(scratchPos.immutable(), false);
            }
        }
    }

    /** Dust kicked up by two random wheels while moving or turning. Client side. */
    public void particleLandingGear() {
        if (wheels.length == 0) {
            return;
        }

        AircraftEntity vehicle = parent;
        Vec3 motion = vehicle.getDeltaMovement();
        double activity = motion.x * motion.x + motion.z * motion.z + Math.abs(prevYaw - vehicle.getYRot());
        prevYaw = vehicle.getYRot();
        if (activity <= 0.001) {
            return;
        }

        Level level = vehicle.level();
        for (int i = 0; i < 2; i++) {
            WheelProbe wheel = wheels[this.dustRandom.nextInt(wheels.length)];
            Vec3 pos = vehicle.body().toWorld(wheel.mountPoint());
            int x = Mth.floor(pos.x + 0.5);
            int y = Mth.floor(pos.y - 0.5);
            int z = Mth.floor(pos.z + 0.5);
            BlockState state = level.getBlockState(scratchPos.set(x, y, z));
            if (state.isAir()) {
                y = Mth.floor(pos.y + 0.5);
                state = level.getBlockState(scratchPos.set(x, y, z));
            }
            if (state.isAir()) {
                continue;
            }

            Effects.get().blockCrack(level, x, y, z,
                    pos.x + (this.dustRandom.nextFloat() - 0.5),
                    pos.y + 0.1,
                    pos.z + (this.dustRandom.nextFloat() - 0.5),
                    -motion.x * 4.0 + (this.dustRandom.nextFloat() - 0.5) * 0.1,
                    this.dustRandom.nextFloat() * 0.5,
                    -motion.z * 4.0 + (this.dustRandom.nextFloat() - 0.5) * 0.1);
        }
    }
}
