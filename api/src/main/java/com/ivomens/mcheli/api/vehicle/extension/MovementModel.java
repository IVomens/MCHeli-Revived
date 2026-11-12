package com.ivomens.mcheli.api.vehicle.extension;

/**
 * The physics of a vehicle, supplied by an addon in place of its kind's own: a vehicle file picks it with
 * {@code Movement: <id>}. Everything else of the kind stays as it is: the entity, seats, weapons, HUD, camera,
 * attitude and networking. In API 1.1 the {@code VEHICLE} kind (emplaced guns and light ground vehicles) can use
 * a movement model.
 *
 * <p><b>Each tick, on the server,</b> MCHeli calls {@link #step}, where the model reads the vehicle's state and
 * the pilot's keys from the context and writes the velocity (blocks per tick) and the yaw it wants. MCHeli then
 * moves the vehicle by that velocity with vanilla collision (walls stop it, it steps up blocks and lands on the
 * ground) and calls {@link #afterMove}, where the context shows what collision left: a model can damp the velocity
 * there for the next tick. The kind's pitch and roll handling, the engine throttle and sound, and the weapons all
 * run as they do for the kind's own movement.
 *
 * <pre>{@code
 * final class Hovercraft implements MovementModel {
 *     public void step(MovementContext c) {
 *         Vec3 v = c.velocity();
 *         double lift = c.heightAboveGround(4) < 1.5 ? 0.06 : 0.0;
 *         double push = c.controls().forward() ? 0.04 : c.controls().back() ? -0.02 : 0.0;
 *         float yaw = c.yaw() + (c.controls().left() ? -3 : c.controls().right() ? 3 : 0);
 *         double yawRad = Math.toRadians(yaw);
 *         c.setYaw(yaw);
 *         c.setVelocity(new Vec3(v.x - Math.sin(yawRad) * push, v.y + c.gravity() + lift, v.z + Math.cos(yawRad) * push));
 *     }
 *
 *     public void afterMove(MovementContext c) {
 *         c.setVelocity(c.velocity().multiply(0.95, 0.9, 0.95));
 *     }
 * }
 * }</pre>
 *
 * <p><b>On clients,</b> the vehicle moves the way it does for every kind: each position update from the server is
 * blended in over a few ticks, and between updates the client dead-reckons, moving the vehicle by its last
 * velocity and then calling {@link #predict}, where a model can change the velocity it keeps predicting with (a
 * drag, for example). The pilot's client predicts its own vehicle the same way.
 *
 * <p><b>Determinism.</b> Use no randomness or wall clock time in a model if its vehicles should behave the same
 * every time; MCHeli's own models are fully deterministic.
 *
 * <p><b>Side and thread.</b> {@link #step} and {@link #afterMove} run on the server, {@link #predict} on clients,
 * all on the vehicle level's thread. A model is made per vehicle ({@link MovementModelFactory}) and may keep
 * state; nothing it holds is saved or synced. A wrecked vehicle and a resting one (parked, nobody aboard) still
 * call the model, except while resting skips the whole tick.
 *
 * <p><b>Stability.</b> Since API 1.1. Addons implement it; new hooks only arrive as default methods.
 */
public interface MovementModel {

    /** Server: the forces of one tick. Write the velocity to move by, and the yaw, through the context. */
    void step(MovementContext context);

    /** Server: after the move, with the velocity collision left; may change it for the next tick. */
    default void afterMove(MovementContext context) {
    }

    /** Client, between server updates: after a predicted move; may change the velocity the next one uses. */
    default void predict(MovementContext context) {
    }
}
