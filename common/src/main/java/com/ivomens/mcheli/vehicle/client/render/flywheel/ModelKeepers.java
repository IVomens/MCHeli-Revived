package com.ivomens.mcheli.vehicle.client.render.flywheel;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import net.minecraft.core.Vec3i;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the part models vehicles use resident in Flywheel, and their spare instances for reuse.
 * <p>
 * Flywheel keeps all meshes in one pool and uploads the whole pool again whenever a mesh is added or
 * freed, and it frees a model's mesh as soon as its instancer has no instance left. With vehicle-sized
 * meshes one such upload costs far more than a frame (a fleet's pool holds about a million vertices,
 * 36 MB). So every model in use has a {@link Keeper}: one instance with a zero matrix, which draws
 * nothing, in the model's instancer, kept while vehicles use the model and for {@link #KEEP_IDLE_NANOS}
 * after the last one went.
 * <p>
 * A vehicle that goes (it left the tracking range, was destroyed, or Flywheel recreates every visual)
 * gives its instances back parked instead of deleting them, and the next vehicle using the model takes
 * them: deleting an instance makes Flywheel move every later instance of the instancer down and upload
 * them again, and creating one grows and re-uploads the instancer. Spares nobody took for
 * {@link #POOL_IDLE_NANOS} are deleted, all at once.
 * <p>
 * When Flywheel moves its render origin it clears every instancer and recreates every visual. The
 * keepers of the old origin are then moved to the new one in the same frame, before Flywheel would
 * drop the instancers left empty, so no mesh leaves the pool.
 */
final class ModelKeepers {

    /**
     * How long a model nobody uses keeps its mesh on the GPU. Vehicles that leave the view distance and
     * come back, or are destroyed and replaced, cost no upload; a type that is gone for good stops
     * costing its (empty) draw calls after this.
     */
    static final long KEEP_IDLE_NANOS = 60_000_000_000L;
    /**
     * How long a parked spare instance waits for a vehicle. Each one is drawn (as nothing) with its
     * model's full mesh, so they do not stay forever.
     */
    static final long POOL_IDLE_NANOS = 10_000_000_000L;
    private static final long SWEEP_INTERVAL_NANOS = 1_000_000_000L;

    /** The keepers of the visualization and render origin in use. */
    private static final AtomicReference<Retained> RETAINED = new AtomicReference<>();
    private static final AtomicLong NEXT_SWEEP = new AtomicLong();
    /** How long models may be added once admission opens: the frame it opened in, roughly. */
    private static final long ADMIT_WINDOW_NANOS = 50_000_000L;
    private static final long ADMIT_INTERVAL_NANOS = 2_000_000_000L;
    /** When the next admission window may open; also the lock of both. */
    private static final AtomicLong ADMISSION = new AtomicLong(System.nanoTime());
    /** When the open admission window closes. */
    private static final AtomicLong ADMISSION_CLOSES = new AtomicLong(System.nanoTime());

    private ModelKeepers() {
    }

    /**
     * A vehicle uses the model: keeps its mesh resident until {@link #release} and the idle time after.
     * Returns null when the model is not resident and may not be added yet (see {@link #admits}); the
     * vehicle asks again next frame and is drawn by the immediate renderer meanwhile.
     */
    @Nullable
    static Keeper acquire(Model model, VisualizationContext context, InstanceType<TransformedInstance> type) {
        Retained retained = current(context);
        if (!retained.keepers.containsKey(model) && !admits(System.nanoTime())) {
            return null;
        }
        return retained.keepers.compute(model, (key, keeper) -> {
            Keeper used = keeper != null ? keeper : new Keeper(retained, key, type, System.nanoTime());
            used.users++;
            return used;
        });
    }

    /**
     * Whether a model may be made resident now. Every model added makes Flywheel upload its whole mesh
     * pool in that frame; models added within {@link #ADMIT_WINDOW_NANOS} of each other share one
     * upload, and after a window closes the next opens {@link #ADMIT_INTERVAL_NANOS} after it opened.
     * Vehicles of new types (or reaching the far level for the first time) that arrive in between wait,
     * drawn by the immediate renderer, and are added together, so a camera flying over a fleet it has
     * not seen yet costs a pool upload every two seconds at most instead of one in most frames.
     */
    private static boolean admits(long now) {
        synchronized (ADMISSION) {
            if (now - ADMISSION_CLOSES.get() < 0) {
                return true;
            }
            if (now - ADMISSION.get() < 0) {
                return false;
            }
            ADMISSION_CLOSES.set(now + ADMIT_WINDOW_NANOS);
            ADMISSION.set(now + ADMIT_INTERVAL_NANOS);
            return true;
        }
    }

    /** Lets the next model in at once: for tests, which do not wait for the interval. */
    static void openAdmission() {
        synchronized (ADMISSION) {
            long now = System.nanoTime();
            ADMISSION.set(now);
            ADMISSION_CLOSES.set(now);
        }
    }

    /** A vehicle stopped using the keeper's model. */
    static void release(Keeper keeper) {
        long now = System.nanoTime();
        keeper.retained.keepers.computeIfPresent(keeper.model, (key, current) -> {
            if (current == keeper && --current.users <= 0) {
                current.idleSince = now;
            }
            return current;
        });
    }

    /**
     * The keepers of {@code context} at its render origin. A new origin of the same visualization takes
     * over every keeper of the old one (whose instances Flywheel just cleared) with a fresh instance.
     */
    private static Retained current(VisualizationContext context) {
        Retained retained = RETAINED.get();
        while (retained == null || retained.context != context || !retained.origin.equals(context.renderOrigin())) {
            Retained fresh = new Retained(context, context.renderOrigin());
            if (RETAINED.compareAndSet(retained, fresh)) {
                if (retained != null) {
                    retained.stale = true;
                    if (retained.context == context) {
                        FlywheelVehicleStats.originChanged();
                        fresh.takeOver(retained);
                    }
                }
                return fresh;
            }
            retained = RETAINED.get();
        }
        return retained;
    }

    /**
     * Frees the meshes of the models nobody used for {@link #KEEP_IDLE_NANOS} and the spares nobody took
     * for {@link #POOL_IDLE_NANOS}, all at once, so Flywheel uploads its mesh pool once for all of them.
     * Called every frame by every visual; does its work at most once a second. While Flywheel may not
     * draw vehicles ({@code keepIdle} false) unused models and spares go at once, so a switch to the
     * immediate renderer leaves no empty draw calls behind.
     */
    static void sweep(boolean keepIdle) {
        long now = System.nanoTime();
        long next = NEXT_SWEEP.get();
        if (now - next >= 0 && NEXT_SWEEP.compareAndSet(next, now + SWEEP_INTERVAL_NANOS)) {
            sweepNow(keepIdle, now);
        }
    }

    /** {@link #sweep} without the once a second limit. */
    static void sweepNow(boolean keepIdle, long now) {
        Retained retained = RETAINED.get();
        if (retained == null) {
            return;
        }
        for (Model model : retained.keepers.keySet()) {
            retained.keepers.computeIfPresent(model, (key, keeper) -> {
                if (keeper.users <= 0 && (!keepIdle || now - keeper.idleSince >= KEEP_IDLE_NANOS)) {
                    keeper.delete();
                    return null;
                }
                keeper.trimSpares(keepIdle, now);
                return keeper;
            });
        }
    }

    /** The keepers of one visualization at one render origin. */
    private static final class Retained {

        private final VisualizationContext context;
        private final Vec3i origin;
        private final Map<Model, Keeper> keepers = new ConcurrentHashMap<>();
        /** Flywheel cleared every instance of this origin: its instances are gone. */
        private volatile boolean stale;

        Retained(VisualizationContext context, Vec3i origin) {
            this.context = context;
            this.origin = origin;
        }

        /**
         * Keeps every model {@code old} kept, with the time it has been idle and as many instances as it
         * had (lent or spare), all parked: the recreated visuals take them back in this frame, and the
         * vehicles that come back later take the rest instead of making instances then.
         */
        void takeOver(Retained old) {
            long now = System.nanoTime();
            old.keepers.forEach((model, keeper) -> this.keepers.computeIfAbsent(model, key -> {
                Keeper taken = new Keeper(this, key, keeper.type, keeper.users > 0 ? now : keeper.idleSince);
                taken.addSpares(keeper.instanceCount(), now);
                return taken;
            }));
        }
    }

    /**
     * The instance that keeps a model's mesh resident, how many vehicles use the model, and the parked
     * instances vehicles gave back.
     */
    static final class Keeper {

        private final Retained retained;
        private final Model model;
        private final InstanceType<TransformedInstance> type;
        private final TransformedInstance instance;
        private final ArrayDeque<TransformedInstance> spares = new ArrayDeque<>();
        private long sparedSince;
        /** Instances taken and not given back. */
        private int lent;
        private int users;
        private long idleSince;

        Keeper(Retained retained, Model model, InstanceType<TransformedInstance> type, long idleSince) {
            this.retained = retained;
            this.model = model;
            this.type = type;
            this.idleSince = idleSince;
            FlywheelVehicleStats.keeperCreated();
            // a zero matrix draws nothing; a hidden instance would not count, and the mesh would go
            this.instance = this.create();
        }

        /** A parked instance of the model: a spare if there is one, otherwise a new one. */
        synchronized TransformedInstance take() {
            TransformedInstance spare = this.retained.stale ? null : this.spares.pollLast();
            this.lent++;
            return spare != null ? spare : this.create();
        }

        /** Takes back instances that are parked (zero matrix) and no longer used. */
        synchronized void giveBack(List<TransformedInstance> parked) {
            if (this.retained.stale) {
                // Flywheel already cleared them with the old origin
                return;
            }
            this.spares.addAll(parked);
            this.lent -= parked.size();
            this.sparedSince = System.nanoTime();
        }

        /** The vehicles' instances of the model: lent to vehicles or spare. */
        private synchronized int instanceCount() {
            return this.lent + this.spares.size();
        }

        /** Parked instances made now; they wait {@link #POOL_IDLE_NANOS} from now for a vehicle. */
        private synchronized void addSpares(int count, long now) {
            for (int i = 0; i < count; i++) {
                this.spares.add(this.create());
            }
            this.sparedSince = now;
        }

        private TransformedInstance create() {
            TransformedInstance created = this.retained.context.instancerProvider().instancer(this.type, this.model)
                    .createInstance();
            created.setZeroTransform().setChanged();
            FlywheelVehicleStats.instancesCreated(1);
            return created;
        }

        private synchronized void trimSpares(boolean keepIdle, long now) {
            if (!this.spares.isEmpty() && (!keepIdle || now - this.sparedSince >= POOL_IDLE_NANOS)) {
                this.deleteSpares();
            }
        }

        private synchronized void delete() {
            this.deleteSpares();
            this.instance.delete();
            FlywheelVehicleStats.instancesDeleted(1);
        }

        private void deleteSpares() {
            for (TransformedInstance spare : this.spares) {
                spare.delete();
            }
            FlywheelVehicleStats.instancesDeleted(this.spares.size());
            this.spares.clear();
        }
    }
}
