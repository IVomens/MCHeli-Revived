package com.ivomens.mcheli.core.ballistics;

import com.ivomens.mcheli.core.guidance.LockCone;
import com.ivomens.mcheli.core.guidance.Pursuit;
import com.ivomens.mcheli.core.guidance.Seeker;
import com.ivomens.mcheli.core.math.Degrees;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ballistics and guidance math of {@code :core} gives, bit for bit, what the combat code computed before
 * it moved there. Each legacy method below is the old code as it was, on Minecraft's {@code Vec3} and
 * {@code Mth}, with the entity reads turned into parameters; both sides run over the same grid of inputs,
 * which mixes ordinary values with zeros, tiny and huge ones, and both signs.
 */
class LegacyBallisticsEquivalenceTest {

    private static final int SAMPLES = 20_000;
    private static final double[] SPECIAL = {0.0, -0.0, 1.0E-7, -1.0E-7, 1.0E-5, 0.3, -0.3, 1.0, -2.5, 4.0, 100.0,
            -1234.5678, 3.0E7};

    private final Random random = new Random(20261108L);

    private double value() {
        return switch (this.random.nextInt(4)) {
            case 0 -> SPECIAL[this.random.nextInt(SPECIAL.length)];
            case 1 -> this.random.nextGaussian() * 0.05;
            case 2 -> this.random.nextGaussian() * 3.0;
            default -> (this.random.nextDouble() - 0.5) * 4000.0;
        };
    }

    private float floatValue() {
        return (float) value();
    }

    @Test
    void forcesMatchTheLegacyFlightModel() {
        for (int i = 0; i < SAMPLES; i++) {
            Vec3 motion = new Vec3(value(), value(), value());
            boolean inWater = this.random.nextBoolean();
            boolean boosting = this.random.nextBoolean();
            float speedFactor = floatValue();
            float gravity = floatValue();
            float gravityInWater = floatValue();
            double drag = this.random.nextBoolean() ? 0.0 : value();

            double[] legacy = legacyForces(motion, inWater, boosting, speedFactor, gravity, gravityInWater, drag);

            Vector3d m = new Vector3d(motion.x, motion.y, motion.z);
            boolean boosted = false;
            if (inWater) {
                ProjectileForces.fall(m, gravityInWater);
            } else {
                boosted = boosting && ProjectileForces.boost(m, speedFactor);
                ProjectileForces.fall(m, gravity);
                ProjectileForces.drag(m, drag);
            }
            assertSame(legacy[0], m.x);
            assertSame(legacy[1], m.y);
            assertSame(legacy[2], m.z);
            assertEquals(legacy[3] != 0.0, boosted);
        }
    }

    @Test
    void steeringMatchesTheLegacyFlightModel() {
        for (int i = 0; i < SAMPLES; i++) {
            Vec3 motion = this.random.nextInt(10) == 0 ? Vec3.ZERO : new Vec3(value(), value(), value());
            Vec3 dir = new Vec3(value(), value(), value()).normalize();
            double target = Math.abs(value());
            double maxSpeed = this.random.nextBoolean() ? Double.MAX_VALUE : Math.abs(value());
            double turn = this.random.nextDouble();
            Vec3 legacy = legacySteer(target, motion, dir.x, dir.y, dir.z, maxSpeed, turn);
            Vector3d turned = Pursuit.turn(motion.x, motion.y, motion.z, dir.x, dir.y, dir.z, target, maxSpeed, turn);
            assertSame(legacy.x, turned.x);
            assertSame(legacy.y, turned.y);
            assertSame(legacy.z, turned.z);
            assertSame((float) (Math.atan2(legacy.z, legacy.x) * 180.0 / Math.PI) - 90.0F,
                    Pursuit.headingYaw(turned.x, turned.z));
        }
    }

    @Test
    void leadMatchesTheLegacyHoming() {
        for (int i = 0; i < SAMPLES; i++) {
            Vec3 missile = new Vec3(value(), value(), value());
            Vec3 motion = this.random.nextInt(10) == 0 ? Vec3.ZERO : new Vec3(value(), value(), value());
            float nominal = Math.abs(floatValue()) + 0.1F;
            Vec3 target = new Vec3(value(), value(), value());
            Vec3 targetMotion = new Vec3(value(), value(), value());

            double speed = motion.length();
            if (speed < 1.0E-4) speed = nominal;
            double dx = missile.x - target.x;
            double dy = missile.y - target.y;
            double dz = missile.z - target.z;
            double timeToTarget = Math.sqrt(dx * dx + dy * dy + dz * dz) / speed;
            double legacyX = target.x + targetMotion.x * timeToTarget;
            double legacyY = target.y + targetMotion.y * timeToTarget;
            double legacyZ = target.z + targetMotion.z * timeToTarget;

            Vector3d led = Pursuit.lead(vector(missile), vector(motion), nominal, vector(target), vector(targetMotion));
            assertSame(legacyX, led.x);
            assertSame(legacyY, led.y);
            assertSame(legacyZ, led.z);
        }
    }

    @Test
    void flightAnglesMatchTheLegacyFaceMotion() {
        for (int i = 0; i < SAMPLES; i++) {
            Vec3 m = new Vec3(value(), value(), value());
            double horizontal = Math.sqrt(m.x * m.x + m.z * m.z);
            boolean legacyTurns = horizontal * horizontal + m.y * m.y > 1.0E-6;
            assertEquals(legacyTurns, FlightAngles.hasDirection(m.x, m.y, m.z));
            assertSame((float) Math.toDegrees(Math.atan2(m.z, m.x)) - 90.0F, FlightAngles.yaw(m.x, m.z));
            assertSame(-(float) Math.toDegrees(Math.atan2(m.y, horizontal)), FlightAngles.pitch(m.x, m.y, m.z));
        }
    }

    @Test
    void chunkChecksMatchTheLegacyFlightModel() {
        for (int i = 0; i < SAMPLES; i++) {
            double x0 = value();
            double z0 = value();
            double x1 = x0 + value() / 10.0;
            double z1 = z0 + value() / 10.0;
            int holeX = Mth.floor(x0 + (x1 - x0) * this.random.nextDouble()) >> 4;
            int holeZ = Mth.floor(z0 + (z1 - z0) * this.random.nextDouble()) >> 4;
            ProjectileSteps.LoadedChunks chunks = (cx, cz) -> cx != holeX || cz != holeZ;
            assertEquals(legacySegmentLoaded(chunks, x0, z0, x1, z1),
                    ProjectileSteps.isSegmentLoaded(x0, z0, x1, z1, chunks));
            assertEquals(Mth.floor(x0), ProjectileSteps.floor(x0));
        }
        for (double special : new double[]{Double.NaN, 3.0E10, -3.0E10, -0.0, -1.0E-300}) {
            assertEquals(Mth.floor(special), ProjectileSteps.floor(special));
        }
    }

    @Test
    void stepFactorsMatchTheLegacyProjectile() {
        for (int i = 0; i < SAMPLES; i++) {
            float acceleration = Math.abs(floatValue());
            boolean multiStep = this.random.nextBoolean();
            double legacy = multiStep && acceleration > 4.0F ? acceleration / 4.0F : 1.0;
            assertSame(legacy, ProjectileSteps.stepFactor(multiStep, acceleration));
        }
    }

    @Test
    void impactPredictionMatchesTheLegacySight() {
        for (int i = 0; i < 2_000; i++) {
            double groundY = this.random.nextGaussian() * 20.0;
            double waterTop = groundY + Math.abs(this.random.nextGaussian() * 10.0);
            double edge = Math.abs(this.random.nextGaussian() * 300.0);
            double height = Math.abs(this.random.nextGaussian() * 80.0);
            Vec3 start = new Vec3(value() / 10.0, groundY + height, value() / 10.0);
            Vec3 launch = new Vec3(this.random.nextGaussian(), this.random.nextGaussian(), this.random.nextGaussian());
            ImpactPrediction.Fall fall = new ImpactPrediction.Fall(-Math.abs(floatValue() / 100.0F),
                    floatValue() / 100.0F, this.random.nextBoolean() ? 0.0F : this.random.nextFloat());
            double stepFactor = this.random.nextBoolean() ? 1.0 : 1.0 + this.random.nextFloat() * 3.0F;
            int maxSteps = 1 + this.random.nextInt(512);
            Pond world = new Pond(groundY, waterTop, edge);

            Vec3 legacy = legacyImpact(world, start, launch, fall, stepFactor, maxSteps, start.y - 100.0);
            Vec3 predicted = ImpactPrediction.impact(start.x, start.y, start.z, launch.x, launch.y, launch.z, fall,
                    stepFactor, maxSteps, start.y - 100.0, world);
            assertEquals(legacy, predicted);
        }
    }

    @Test
    void lockConeMatchesTheLegacySeeker() {
        for (int i = 0; i < SAMPLES; i++) {
            double dx = value();
            double dy = value();
            double dz = value();
            float yaw = floatValue();
            float pitch = (float) (this.random.nextGaussian() * 45.0);
            float angle = Math.abs(floatValue()) % 180.0F;
            assertEquals(legacyInLockAngle(dx, dy, dz, yaw, pitch, angle), LockCone.contains(dx, dy, dz, yaw, pitch,
                    angle));

            int lockAngle = this.random.nextInt(90);
            int lockTicks = this.random.nextInt(100);
            float stealth = this.random.nextFloat();
            double range = Math.abs(value());
            float visibility = 1.0F - stealth;
            assertSame(lockAngle * (visibility / 2.0F + 0.5F), LockCone.searchAngle(lockAngle, stealth));
            assertSame(range * (1.0F - stealth), LockCone.reach(range, stealth));
            assertEquals((int) (lockTicks + lockTicks * stealth), LockCone.lockTicks(lockTicks, stealth));
        }
    }

    @Test
    void seekerLimitsMatchTheLegacyHomingAndNotching() {
        for (int i = 0; i < SAMPLES; i++) {
            Vector3f flight = new Vector3f(floatValue(), floatValue(), floatValue());
            Vector3f toTarget = new Vector3f(floatValue(), floatValue(), floatValue());
            Vector3f targetMotion = new Vector3f(floatValue(), floatValue(), floatValue());
            int fieldOfView = this.random.nextInt(180);
            float gate = this.random.nextFloat() * 180.0F;
            assertEquals(flight.angle(toTarget) > Math.toRadians(fieldOfView),
                    Seeker.outsideFieldOfView(flight, toTarget, fieldOfView));
            assertEquals(flight.angle(targetMotion) > Math.toRadians(gate),
                    Seeker.outsideDopplerGate(flight, targetMotion, gate));
            float notch = this.random.nextFloat() * 90.0F;
            assertEquals(legacyNotching(flight, targetMotion, notch), Seeker.isNotching(flight, targetMotion, notch));
        }
    }

    // ---- the legacy code, as it was before the move ----

    /** FlightModel.applyForces; returns the motion and whether the boost applied (1) or not (0). */
    private static double[] legacyForces(Vec3 m, boolean inWater, boolean boosting, float speedFactor, float gravity,
                                         float gravityInWater, double dragInAir) {
        double mx = m.x;
        double my = m.y;
        double mz = m.z;
        double boosted = 0.0;
        if (inWater) {
            my += gravityInWater;
        } else {
            if (boosting) {
                double speed = Math.sqrt(mx * mx + my * my + mz * mz);
                if (speed > 0) {
                    mx += mx / speed * speedFactor;
                    my += my / speed * speedFactor;
                    mz += mz / speed * speedFactor;
                    boosted = 1.0;
                }
            }
            my += gravity;
            double drag = dragInAir;
            if (drag != 0.0) {
                double speed = Math.sqrt(mx * mx + my * my + mz * mz);
                if (speed > 0.0) {
                    mx -= mx / speed * drag;
                    mz -= mz / speed * drag;
                }
            }
        }
        return new double[]{mx, my, mz, boosted};
    }

    /** FlightModel.steer, with the projectile's speed as a parameter. */
    private static Vec3 legacySteer(double target, Vec3 motion, double dirX, double dirY, double dirZ,
                                    double maxSpeed, double turnFactor) {
        double speed = motion.length();
        if (speed < 1.0E-6) {
            return new Vec3(
                    motion.x + (dirX * target - motion.x) * turnFactor,
                    motion.y + (dirY * target - motion.y) * turnFactor,
                    motion.z + (dirZ * target - motion.z) * turnFactor);
        }
        double newSpeed = Math.min(speed + (target - speed) * turnFactor, maxSpeed);
        double curX = motion.x / speed;
        double curY = motion.y / speed;
        double curZ = motion.z / speed;
        double newX = curX + (dirX - curX) * turnFactor;
        double newY = curY + (dirY - curY) * turnFactor;
        double newZ = curZ + (dirZ - curZ) * turnFactor;
        double length = Math.sqrt(newX * newX + newY * newY + newZ * newZ);
        if (length < 1.0E-6) {
            newX = dirX;
            newY = dirY;
            newZ = dirZ;
            length = 1.0;
        }
        return new Vec3(newX / length * newSpeed, newY / length * newSpeed, newZ / length * newSpeed);
    }

    /** FlightModel.isSegmentLoaded, with the level's chunk lookup as a parameter. */
    private static boolean legacySegmentLoaded(ProjectileSteps.LoadedChunks level, double x0, double z0, double x1,
                                               double z1) {
        int cx0 = Mth.floor(x0) >> 4;
        int cz0 = Mth.floor(z0) >> 4;
        int cx1 = Mth.floor(x1) >> 4;
        int cz1 = Mth.floor(z1) >> 4;
        if (!level.has(cx1, cz1)) return false;
        if (cx0 == cx1 && cz0 == cz1) return level.has(cx0, cz0);
        double dx = x1 - x0;
        double dz = z1 - z0;
        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dz)) / 8.0);
        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            if (!level.has(Mth.floor(x0 + dx * t) >> 4, Mth.floor(z0 + dz * t) >> 4)) return false;
        }
        return true;
    }

    /** Trajectories.simulate, with the level's lookups as the same port the new code uses. */
    private static Vec3 legacyImpact(Pond level, Vec3 start, Vec3 launch, ImpactPrediction.Fall fall, double stepFactor,
                                     int maxSteps, double lowestY) {
        double x = start.x;
        double y = start.y;
        double z = start.z;
        double mx = launch.x;
        double my = launch.y;
        double mz = launch.z;
        for (int i = 0; i < maxSteps; i++) {
            if (!level.isLoaded(x, y, z)) {
                return null;
            }
            if (level.isWater(x, y, z)) {
                my += (float) fall.gravityInWater();
                if ((float) fall.velocityInWater() > 0.0F) {
                    mx *= (float) fall.velocityInWater();
                    my *= (float) fall.velocityInWater();
                    mz *= (float) fall.velocityInWater();
                }
            } else {
                my += (float) fall.gravity();
            }
            double nextX = x + mx * stepFactor;
            double nextY = y + my * stepFactor;
            double nextZ = z + mz * stepFactor;
            if (!level.isLoaded(nextX, nextY, nextZ)) {
                return null;
            }
            Vec3 hit = level.hit(x, y, z, nextX, nextY, nextZ);
            if (hit != null) {
                return hit;
            }
            x = nextX;
            y = nextY;
            z = nextZ;
            if (y < lowestY) return null;
        }
        return null;
    }

    /** LockOnSeeker.inLockAngle, with the offset to the target's centre as parameters. */
    private static boolean legacyInLockAngle(double dx, double dy, double dz, float rotationYaw, float rotationPitch,
                                             float lockAngle) {
        float entityYaw = (float) Degrees.wrap360(rotationYaw);
        float targetYaw = (float) Degrees.wrap360(Math.toDegrees(Math.atan2(dz, dx)));
        float yawDiff = (float) Degrees.wrap360(targetYaw - entityYaw - 90.0F);
        float targetPitch = -((float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        float pitchDiff = targetPitch - rotationPitch;
        return (yawDiff < lockAngle || yawDiff > 360.0F - lockAngle) && Math.abs(pitchDiff) < lockAngle;
    }

    /** LockOnSeeker.isNotching, with the two velocities as parameters. */
    private static boolean legacyNotching(Vector3f shooterVelocity, Vector3f targetVelocity, float notchAngle) {
        if (shooterVelocity.length() <= 0.001f || targetVelocity.length() <= 0.001f) {
            return false;
        }
        float cos = shooterVelocity.dot(targetVelocity) / (shooterVelocity.length() * targetVelocity.length());
        float crossing = (float) Math.acos(Math.clamp(cos, -1f, 1f));
        if (crossing > Math.PI / 2) crossing = (float) (Math.PI - crossing);
        return Math.toDegrees(crossing) > notchAngle;
    }

    // ---- helpers ----

    private static Vector3d vector(Vec3 v) {
        return new Vector3d(v.x, v.y, v.z);
    }

    private static void assertSame(double expected, double actual) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual),
                () -> expected + " != " + actual);
    }

    private static void assertSame(float expected, float actual) {
        assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(actual),
                () -> expected + " != " + actual);
    }

    /** A flat sea floor under water, loaded out to a square edge. */
    private record Pond(double groundY, double waterTop, double edge) implements ImpactPrediction.Surroundings<Vec3> {

        @Override
        public boolean isLoaded(double x, double y, double z) {
            return Math.abs(x) < this.edge && Math.abs(z) < this.edge;
        }

        @Override
        public boolean isWater(double x, double y, double z) {
            return Mth.floor(y) < this.waterTop;
        }

        @Override
        public Vec3 hit(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
            if (toY > this.groundY || fromY <= toY) {
                return null;
            }
            double t = (fromY - this.groundY) / (fromY - toY);
            return new Vec3(fromX + (toX - fromX) * t, this.groundY, fromZ + (toZ - fromZ) * t);
        }
    }
}
