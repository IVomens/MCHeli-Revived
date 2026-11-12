package com.ivomens.mcheli.api.vehicle.extension;

/**
 * Something an addon adds to vehicles of the existing kinds: a winch, a beacon, a warning siren. A vehicle file
 * opts in by naming the behaviour type in its {@code Behaviours} section; each vehicle then gets its own
 * behaviour, made by the type's {@link BehaviourFactory}. Every hook does nothing unless overridden.
 *
 * <pre>{@code
 * final class Beacon implements VehicleBehaviour {
 *     private final BeaconSettings settings;
 *
 *     Beacon(BeaconSettings settings, BehaviourContext context) {
 *         this.settings = settings;
 *     }
 *
 *     public void clientTick(BehaviourContext context) {
 *         Entity hull = context.vehicle().entity();
 *         if (hull.tickCount % settings.interval() == 0) {
 *             context.level().addParticle(ParticleTypes.END_ROD, hull.getX(), hull.getY() + 3, hull.getZ(), 0, 0.1, 0);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>Order.</b> {@link #added} comes first, on the vehicle's first tick that knows its type. Then
 * {@link #serverTick} or {@link #clientTick} once per tick, at the type's {@link BehaviourTiming}; the
 * behaviours of one vehicle tick in the order its file lists them. {@link #destroyed} comes once, on the first
 * tick the behaviour sees the vehicle wrecked (also for a wreck loaded from a save). {@link #removed} comes last,
 * when the vehicle leaves the level for any reason (killed, discarded, unloaded, changed dimension) or a content
 * reload replaced the behaviour. A vehicle that is resting (parked, nobody aboard) skips its tick, and with it its
 * behaviours' ticks.
 *
 * <p><b>Side and thread.</b> Every vehicle exists on the server and on each client that sees it, and each copy
 * has its own behaviour: the {@code serverTick} hook runs on the server, the {@code clientTick} hook on clients,
 * and the others on both, all on the vehicle level's thread. Nothing a behaviour holds is saved or synced; keep
 * game state on the server and send what clients need yourself. A hook that throws is logged, and the behaviour
 * is dropped from that vehicle; the vehicle carries on.
 *
 * <p><b>Stability.</b> Since API 1.1. Addons implement it; new hooks only arrive as default methods.
 */
public interface VehicleBehaviour {

    /** The behaviour joined its vehicle. */
    default void added(BehaviourContext context) {
    }

    /** One tick on the server. */
    default void serverTick(BehaviourContext context) {
    }

    /** One tick on a client. */
    default void clientTick(BehaviourContext context) {
    }

    /** The vehicle's hit points ran out and it became a wreck. */
    default void destroyed(BehaviourContext context) {
    }

    /** The behaviour leaves its vehicle; no hook follows. */
    default void removed(BehaviourContext context) {
    }
}
