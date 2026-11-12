package com.ivomens.mcheli.event;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.event.CountermeasureDeployed;
import com.ivomens.mcheli.api.event.CountermeasureType;
import com.ivomens.mcheli.api.event.LockOnCompleted;
import com.ivomens.mcheli.api.event.LockOnStarted;
import com.ivomens.mcheli.api.event.ProjectileImpact;
import com.ivomens.mcheli.api.event.SeatBoarded;
import com.ivomens.mcheli.api.event.SeatLeft;
import com.ivomens.mcheli.api.event.VehicleDestroyed;
import com.ivomens.mcheli.api.event.VehicleRemoved;
import com.ivomens.mcheli.api.event.VehicleSpawned;
import com.ivomens.mcheli.api.event.WeaponFired;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Where the game hands its events to the API's {@link ApiEventBus}: one method per event type, called by the
 * hook in the vehicle component, weapon or mixin where the event happens. An event nobody listens to is not
 * even built. The callers decide the side; each event type documents it.
 */
public final class ApiEvents {

    private ApiEvents() {
    }

    private static ApiEventBus bus() {
        return (ApiEventBus) McheliApi.get().events();
    }

    public static void vehicleSpawned(Vehicle vehicle) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(VehicleSpawned.class)) {
            bus.post(VehicleSpawned.class, new Spawned(vehicle));
        }
    }

    public static void vehicleRemoved(Vehicle vehicle, Entity.RemovalReason reason) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(VehicleRemoved.class)) {
            bus.post(VehicleRemoved.class, new Removed(vehicle, reason));
        }
    }

    public static void vehicleDestroyed(Vehicle vehicle, @Nullable Entity attacker) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(VehicleDestroyed.class)) {
            bus.post(VehicleDestroyed.class, new Destroyed(vehicle, attacker));
        }
    }

    public static void seatBoarded(Vehicle vehicle, Entity rider, int seatId) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(SeatBoarded.class)) {
            bus.post(SeatBoarded.class, new Boarded(vehicle, rider, seatId));
        }
    }

    public static void seatLeft(Vehicle vehicle, Entity rider, int seatId) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(SeatLeft.class)) {
            bus.post(SeatLeft.class, new Left(vehicle, rider, seatId));
        }
    }

    /**
     * A shot is about to be fired.
     *
     * @return whether it may go ahead; false when a listener cancelled it
     */
    public static boolean weaponFired(ServerLevel level, @Nullable Vehicle vehicle, @Nullable Entity shooter,
                                      Entity platform, String weaponId, Vec3 origin, float yaw, float pitch) {
        ApiEventBus bus = bus();
        if (!bus.hasListeners(WeaponFired.class)) {
            return true;
        }
        Fired event = new Fired(level, vehicle, shooter, platform, weaponId, origin, yaw, pitch);
        return !bus.post(WeaponFired.class, event).isCancelled();
    }

    public static void projectileImpact(Entity projectile, String weaponId, @Nullable Entity shooter, HitResult hit) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(ProjectileImpact.class)) {
            bus.post(ProjectileImpact.class, new Impact(projectile, weaponId, shooter, hit));
        }
    }

    public static void lockOnStarted(Entity user, Entity target, @Nullable Vehicle vehicle) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(LockOnStarted.class)) {
            bus.post(LockOnStarted.class, new LockStarted(user, target, vehicle));
        }
    }

    public static void lockOnCompleted(Entity user, Entity target, @Nullable Vehicle vehicle) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(LockOnCompleted.class)) {
            bus.post(LockOnCompleted.class, new LockCompleted(user, target, vehicle));
        }
    }

    public static void countermeasureDeployed(Vehicle vehicle, CountermeasureType type) {
        ApiEventBus bus = bus();
        if (bus.hasListeners(CountermeasureDeployed.class)) {
            bus.post(CountermeasureDeployed.class, new Deployed(vehicle, type));
        }
    }

    private record Spawned(Vehicle vehicle) implements VehicleSpawned {
    }

    private record Removed(Vehicle vehicle, Entity.RemovalReason reason) implements VehicleRemoved {
    }

    private record Destroyed(Vehicle vehicle, @Nullable Entity attacker) implements VehicleDestroyed {
    }

    private record Boarded(Vehicle vehicle, Entity rider, int seatId) implements SeatBoarded {
    }

    private record Left(Vehicle vehicle, Entity rider, int seatId) implements SeatLeft {
    }

    private record Impact(Entity projectile, String weaponId, @Nullable Entity shooter, HitResult hit)
            implements ProjectileImpact {
    }

    private record LockStarted(Entity user, Entity target, @Nullable Vehicle vehicle) implements LockOnStarted {
    }

    private record LockCompleted(Entity user, Entity target, @Nullable Vehicle vehicle) implements LockOnCompleted {
    }

    private record Deployed(Vehicle vehicle, CountermeasureType type) implements CountermeasureDeployed {
    }

    /** The one mutable event: listeners may cancel the shot. */
    private static final class Fired implements WeaponFired {

        private final ServerLevel level;
        @Nullable
        private final Vehicle vehicle;
        @Nullable
        private final Entity shooter;
        private final Entity platform;
        private final String weaponId;
        private final Vec3 origin;
        private final float yaw;
        private final float pitch;
        private boolean cancelled;

        Fired(ServerLevel level, @Nullable Vehicle vehicle, @Nullable Entity shooter, Entity platform,
              String weaponId, Vec3 origin, float yaw, float pitch) {
            this.level = level;
            this.vehicle = vehicle;
            this.shooter = shooter;
            this.platform = platform;
            this.weaponId = weaponId;
            this.origin = origin;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        public ServerLevel level() {
            return this.level;
        }

        @Nullable
        @Override
        public Vehicle vehicle() {
            return this.vehicle;
        }

        @Nullable
        @Override
        public Entity shooter() {
            return this.shooter;
        }

        @Override
        public Entity platform() {
            return this.platform;
        }

        @Override
        public String weaponId() {
            return this.weaponId;
        }

        @Override
        public Vec3 origin() {
            return this.origin;
        }

        @Override
        public float yaw() {
            return this.yaw;
        }

        @Override
        public float pitch() {
            return this.pitch;
        }

        @Override
        public boolean isCancelled() {
            return this.cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }
}
