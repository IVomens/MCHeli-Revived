package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.attitude.StickIntegration.Freedom;
import com.ivomens.mcheli.core.sim.attitude.StickIntegration.Turn;
import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.control.ControlInput;

/**
 * Emplaced guns and light ground vehicles. The mouse aims the gun and the server steers the hull, so
 * the stick never turns it; the step only carries pitch and roll through the shared stick integration,
 * which in quaternion mode holds them at the kept orientation. Yaw belongs to the server's steering
 * and is passed through untouched.
 */
public final class GroundVehicleAttitude
        implements AttitudeModel<StickHandling, GroundVehicleAttitude.NoMemory> {

    /** The type remembers nothing between steps. */
    public enum NoMemory {
        NONE
    }

    @Override
    public NoMemory initialMemory() {
        return NoMemory.NONE;
    }

    @Override
    public AttitudeState<NoMemory> integrate(AttitudeState<NoMemory> state, AttitudeInput mouse, ControlInput keys,
                                             StickHandling stick, AttitudeContext context, float dtSeconds) {
        Freedom free = Freedom.of(context);
        // a zero rate still goes through the axis scaling: pitch comes out as -0, which the
        // quaternion mode carries into the angles
        float yaw = free.yaw() ? StickIntegration.axis(0.0F, stick.yawRate(), 1.0F, dtSeconds) : 0.0F;
        float pitch = free.pitch() ? StickIntegration.axis(0.0F, stick.pitchRate(), -1.0F, dtSeconds) : 0.0F;
        float roll = free.roll() ? StickIntegration.axis(0.0F, stick.rollRate(), 1.0F, dtSeconds) : 0.0F;
        AttitudeState<NoMemory> turned = StickIntegration.integrate(state, new Turn(yaw, pitch, roll),
                stick.limits(), context.settings().quaternionAttitude(), settled -> settled);
        return turned.withAttitude(turned.attitude().withYaw(state.attitude().yaw()));
    }

    @Override
    public float maxTurnRate(StickHandling stick) {
        return 0.0F;
    }

    /** The steering keys turn the hull on the server; the pilot's client never reports yaw back. */
    @Override
    public boolean serverOwnsYaw() {
        return true;
    }
}
