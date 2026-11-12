package com.ivomens.mcheli.api.vehicle.extension;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * What a {@link MovementModel} reads and writes in one call: the vehicle's state, the pilot's keys and a few
 * questions about the terrain, and the velocity and yaw it decides on. The setters take effect when the call
 * returns; until then the getters show what was set.
 *
 * <p><b>Side and thread.</b> The model's own side, on the vehicle level's thread, and only during the call it is
 * handed to. Stability: since API 1.1; implemented by MCHeli only, so a minor version may add methods.
 */
public interface MovementContext {

    /** The vehicle that moves. */
    Vehicle vehicle();

    /** The level it moves in. */
    Level level();

    /** Whether this is a client predicting its copy of the vehicle. */
    boolean isClientSide();

    /** The pilot's keys and the throttle. */
    VehicleControls controls();

    /** Where the vehicle is: the bottom centre of its bounding box. */
    Vec3 position();

    /** Blocks per tick: in {@code step} the velocity the last tick ended with, in {@code afterMove} what collision left. */
    Vec3 velocity();

    /** The heading in degrees, Minecraft's convention: 0 faces south (+z), 90 west (-x). */
    float yaw();

    /** The nose's pitch in degrees, positive down; the vehicle kind's attitude handling owns it. */
    float pitch();

    /** The roll in degrees; the vehicle kind's attitude handling owns it. */
    float roll();

    /** Whether the vehicle rests on a block; in {@code afterMove}, whether it landed on one. */
    boolean onGround();

    /** Whether the vehicle overlaps water. */
    boolean inWater();

    /** In {@code afterMove}: whether a wall stopped the move sideways. False otherwise. */
    boolean horizontalCollision();

    /** Whether the vehicle is a wreck. */
    boolean isDestroyed();

    /** How much of the hull is under water, from 0 (dry) to 1 (submerged), in fifths. */
    double waterDepth();

    /**
     * The vehicle file's gravity, blocks per tick added to the vertical velocity: its {@code Gravity}, or
     * {@code GravityInWater} while the vehicle is in water. Negative pulls down.
     */
    double gravity();

    /**
     * How far the bottom of the vehicle is above the first block below it that something can stand on, in blocks,
     * searching at most {@code maxDepth} blocks down (at most 64); {@link Double#POSITIVE_INFINITY} when there is
     * none that close or its chunk is not loaded. Liquids do not count. 0 or less when the vehicle sits on or in a
     * block.
     */
    double heightAboveGround(int maxDepth);

    /** Sets the velocity, blocks per tick: the move in {@code step}, the next tick's start elsewhere. */
    void setVelocity(Vec3 velocity);

    /** Sets the heading in degrees. */
    void setYaw(float yaw);
}
