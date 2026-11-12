package com.ivomens.mcheli.core.sim.body;

import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.Vec3d;

/**
 * The moving body at one instant, as the motion models see it.
 *
 * @param velocity blocks per tick
 * @param attitude orientation in degrees
 * @param onGround whether the last move ended resting on a block
 * @param inWater  whether the body overlaps water
 * @param wrecked  whether the vehicle is destroyed; wrecks lose lift, steering and buoyancy
 */
public record BodyState(Vec3d velocity, Attitude attitude, boolean onGround, boolean inWater, boolean wrecked) {

    public BodyState withVelocity(Vec3d newVelocity) {
        return new BodyState(newVelocity, this.attitude, this.onGround, this.inWater, this.wrecked);
    }

    public BodyState withAttitude(Attitude newAttitude) {
        return new BodyState(this.velocity, newAttitude, this.onGround, this.inWater, this.wrecked);
    }

    /** The body after a move: the collider decides velocity and ground contact. */
    public BodyState after(MoveResult moved, Vec3d dampedVelocity) {
        return new BodyState(dampedVelocity, this.attitude, moved.onGround(), this.inWater, this.wrecked);
    }
}
