package com.ivomens.mcheli.vehicle.world;

import com.ivomens.mcheli.core.sim.body.BodyState;
import com.ivomens.mcheli.core.sim.body.MoveResult;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.port.Collider;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Moves a vehicle with vanilla {@code Entity.move}: swept-box collision, step height, the vehicle's
 * own move hooks (wall damage, tank hull boxes) and the carrier-deck collision mixin all apply.
 */
public final class EntityCollider implements Collider {

    private static final Runnable NOTHING = () -> {
    };

    private final AircraftEntity vehicle;
    private final Runnable beforeMove;

    public EntityCollider(AircraftEntity vehicle) {
        this(vehicle, NOTHING);
    }

    /**
     * @param beforeMove runs after the velocity is set and before the move, for parts that follow
     *                   the planned motion (tank wheels)
     */
    public EntityCollider(AircraftEntity vehicle, Runnable beforeMove) {
        this.vehicle = vehicle;
        this.beforeMove = beforeMove;
    }

    @Override
    public MoveResult move(BodyState body) {
        Attitude attitude = body.attitude();
        this.vehicle.setYRot(attitude.yaw());
        this.vehicle.setXRot(attitude.pitch());
        this.vehicle.rollAngle().set(attitude.roll());

        Vec3d velocity = body.velocity();
        Vec3 motion = new Vec3(velocity.x(), velocity.y(), velocity.z());
        this.vehicle.setDeltaMovement(motion);
        this.beforeMove.run();
        this.vehicle.move(MoverType.SELF, motion);

        Vec3 after = this.vehicle.getDeltaMovement();
        return new MoveResult(new Vec3d(after.x, after.y, after.z), this.vehicle.onGround(),
                this.vehicle.horizontalCollision);
    }
}
