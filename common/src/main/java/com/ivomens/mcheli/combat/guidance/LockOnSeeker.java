package com.ivomens.mcheli.combat.guidance;

import com.ivomens.mcheli.entityview.Riders;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.event.ApiEvents;
import com.ivomens.mcheli.sound.ModSounds;
import com.ivomens.mcheli.core.guidance.LockCone;
import com.ivomens.mcheli.core.guidance.Seeker;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * The seeker of a lock-on weapon: finds the target nearest to the aim inside the lock cone that its
 * {@link TargetFilter} accepts, and builds the lock up tick by tick while the target stays in view.
 * <p>
 * The shooter's client runs it while the trigger is held ({@link #lock}); {@link #track} is the same
 * step without the seeker tones, on either side.
 */
public final class LockOnSeeker {

    /** A lock survives this many ticks out of the cone at most, or a third of the lock time. */
    private static final int MAX_LOCK_GRACE_TICKS = 20;

    private final Level level;
    private final TargetFilter filter;
    private final double range;
    private final int angle;
    private final int lockTicks;
    /** Pulse doppler radars lose planes crossing the line of sight more steeply than this (notching). */
    private final float notchAngle;
    /** Entities this seeker never locks, such as the crew of its own vehicle. */
    @Nullable
    private final Predicate<Entity> excluded;
    private int lockCount;
    private int prevLockCount;
    private int graceTicks;
    @Nullable
    private Entity target;
    @Nullable
    private Entity lastLocked;
    /** Whether the API's listeners heard that the current lock is complete. */
    private boolean completionAnnounced;

    public LockOnSeeker(Level level, TargetFilter filter, double range, int angle, int lockTicks, float notchAngle,
                        @Nullable Predicate<Entity> excluded) {
        this.level = level;
        this.filter = filter;
        this.range = range;
        this.angle = angle;
        this.lockTicks = Math.max(lockTicks, 1);
        this.notchAngle = notchAngle;
        this.excluded = excluded;
    }

    /** The same seeker accepting other targets, starting without a lock. */
    public LockOnSeeker withFilter(TargetFilter accepted) {
        return new LockOnSeeker(this.level, accepted, this.range, this.angle, this.lockTicks, this.notchAngle,
                this.excluded);
    }

    /** The same seeker with another reach, starting without a lock. */
    public LockOnSeeker withRange(double reach) {
        return new LockOnSeeker(this.level, this.filter, reach, this.angle, this.lockTicks, this.notchAngle,
                this.excluded);
    }

    /** Points the seeker at a target someone else picked, such as a radar's beyond-visual-range lock. */
    public void designate(@Nullable Entity designated) {
        this.target = designated;
    }

    public TargetFilter filter() {
        return this.filter;
    }

    public double range() {
        return this.range;
    }

    public int angle() {
        return this.angle;
    }

    /** Whether the seeker accepts the entity as a target at all, wherever it is aimed. */
    public boolean canLock(@Nullable Entity entity, @Nullable Entity shooter) {
        return this.filter.accepts(entity, shooter) && (this.excluded == null || !this.excluded.test(entity));
    }

    /** Lock time in ticks; stealthy targets take longer. */
    public int lockTicks() {
        return LockCone.lockTicks(this.lockTicks, this.filter.stealthFor(this.target));
    }

    public int lockCount() {
        return this.lockCount;
    }

    public boolean isLockComplete() {
        return this.lockCount == this.lockTicks() && this.lastLocked != null;
    }

    @Nullable
    public Entity target() {
        return this.target;
    }

    /** The target of the last completed lock, kept after the lock is released. */
    @Nullable
    public Entity lastLocked() {
        return this.lastLocked;
    }

    public boolean isLocking(Entity entity) {
        return this.lockingTarget() == entity;
    }

    /** The target being locked or locked, or null. */
    @Nullable
    public Entity lockingTarget() {
        return this.lockCount > 0 && this.target != null && !this.target.isRemoved() ? this.target : null;
    }

    /** Client, once per tick: drops the lock when {@link #lock} was not called since, the trigger being released. */
    public void update() {
        if (!this.level.isClientSide()) {
            return;
        }
        if (this.lockCount != this.prevLockCount) {
            this.prevLockCount = this.lockCount;
        } else {
            this.lockCount = this.prevLockCount = 0;
        }
    }

    /** Client: one tick of holding the trigger, with the seeker tones. Returns whether the lock is complete. */
    public boolean lock(Entity user) {
        if (!this.level.isClientSide()) {
            return false;
        }
        boolean locked = this.track(user);
        String sound = locked ? "ir_basic_tone" : "ir_lock_tone";
        this.level.playLocalSound(user.getX(), user.getY(), user.getZ(), ModSounds.getSound(sound), SoundSource.PLAYERS,
                1.0F, 1.0F, false);
        return locked;
    }

    /** One tick of locking: search, drop a lost target, or build the lock up. Returns whether it is complete. */
    public boolean track(Entity user) {
        if (this.lockCount == 0) {
            this.search(user);
            return false;
        }
        if (this.target == null || this.target.isRemoved() || !this.canContinue(user)) {
            this.clearLock();
            return false;
        }
        this.advance(user);
        boolean locked = this.lockCount >= this.lockTicks();
        if (locked) {
            this.lastLocked = this.target;
        }
        if (locked != this.completionAnnounced && this.target != null) {
            this.completionAnnounced = locked;
            if (locked) {
                ApiEvents.lockOnCompleted(user, this.target, Riders.vehicleOf(user));
                ExternalTargets.get().lockedOn(user, this.target, Riders.vehicleOf(user),
                        ExternalTargets.seekerOf(this.filter));
            }
        }
        return locked;
    }

    public void clearLock() {
        this.target = null;
        this.lockCount = 0;
        this.graceTicks = 0;
        this.completionAnnounced = false;
    }

    private void search(Entity user) {
        this.completionAnnounced = false;
        List<Entity> candidates = this.level.getEntities(user, user.getBoundingBox().inflate(this.range),
                entity -> this.canLock(entity, user));
        Entity eye = eyeOf(user);
        Entity nearest = null;
        double rangeSq = this.range * this.range;
        double nearestDistSq = rangeSq * 2.0D;
        for (Entity candidate : candidates) {
            double distSq = candidate.distanceToSqr(user);
            if (distSq >= rangeSq || distSq >= nearestDistSq) continue;
            // stealth narrows the cone down to half its size
            float cone = LockCone.searchAngle(this.angle, this.filter.stealthFor(candidate));
            if (!inLockAngle(eye, user.getYRot(), user.getXRot(), candidate, cone)) continue;
            Vec3 center = new Vec3(candidate.getX(), candidate.getY() + candidate.getBbHeight() / 2.0F, candidate.getZ());
            BlockHitResult hit = this.level.clip(new ClipContext(eye.position(), center, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, eye));
            if (hit.getType() == HitResult.Type.MISS) {
                nearest = candidate;
                nearestDistSq = distSq;
            }
        }
        this.target = nearest;
        if (nearest != null) {
            this.lockCount++;
            ApiEvents.lockOnStarted(user, nearest, Riders.vehicleOf(user));
        }
    }

    private boolean canContinue(Entity user) {
        Entity locked = this.target;
        ExternalTargets external = ExternalTargets.get();
        if (external.isActive()) {
            if (external.isExcluded(locked)) return false;
            ExternalTarget registered = external.find(locked);
            if (registered != null) return this.filter.keepsExternal(locked, registered.signature(locked));
        }
        if (locked instanceof Vehicle vehicle && this.filter.radar() && vehicle.signature().announcedChaffTime() > 0) {
            return false;
        }
        if (!this.filter.water() && locked.isInWater()) return false;
        if (!this.filter.acceptsAltitude(locked)) return false;
        Vehicle shooter = Riders.vehicleOf(user);
        if (isPlane(shooter) && locked instanceof Vehicle plane && isPlane(plane)) {
            return !this.isNotching(shooter.entity(), locked);
        }
        return true;
    }

    private static boolean isPlane(@Nullable Vehicle vehicle) {
        return vehicle != null && vehicle.kind() == VehicleKind.PLANE;
    }

    /** Whether the target plane flies across the shooter's course, where the radar loses it in the clutter. */
    private boolean isNotching(Entity shooter, Entity plane) {
        Vector3f shooterVelocity = shooter.getDeltaMovement().toVector3f();
        Vector3f targetVelocity = plane.getDeltaMovement().toVector3f();
        return Seeker.isNotching(shooterVelocity, targetVelocity, this.notchAngle);
    }

    private void advance(Entity user) {
        Entity locked = this.target;
        double distSq = locked.distanceToSqr(user);
        double reach = LockCone.reach(this.range, this.filter.stealthFor(locked));
        if (distSq >= reach * reach) {
            this.clearLock();
            return;
        }
        // out of the cone the lock decays, after a short grace period once it was complete
        if (inLockAngle(eyeOf(user), user.getYRot(), user.getXRot(), locked, (float) this.angle)) {
            if (this.lockCount < this.lockTicks()) this.lockCount++;
        } else if (this.graceTicks > 0) {
            if (--this.graceTicks <= 0 && this.lockCount > 0) this.lockCount--;
        } else {
            this.graceTicks = 0;
            this.lockCount--;
        }
        if (this.lockCount >= this.lockTicks()) {
            if (this.graceTicks <= 0) {
                this.graceTicks = Math.min(this.lockTicks() / 3, MAX_LOCK_GRACE_TICKS);
            }
            this.lastLocked = locked;
        }
    }

    /** Whether the target's centre lies within {@code lockAngle} degrees of the given aim. */
    public static boolean inLockAngle(Entity entity, float rotationYaw, float rotationPitch, Entity target, float lockAngle) {
        double dx = target.getX() - entity.getX();
        double dy = target.getY() + (double) (target.getBbHeight() / 2.0F) - entity.getY();
        double dz = target.getZ() - entity.getZ();
        return LockCone.contains(dx, dy, dz, rotationYaw, rotationPitch, lockAngle);
    }

    /** Where the seeker looks from: the UAV for a UAV station operator, the user otherwise. */
    public static Entity eyeOf(Entity user) {
        return Riders.eyeOf(user);
    }

}
