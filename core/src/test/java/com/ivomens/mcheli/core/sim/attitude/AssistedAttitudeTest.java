package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.assist.AutoLevel;
import com.ivomens.mcheli.core.sim.assist.CoordinatedTurn;
import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import org.junit.jupiter.api.Test;

import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.EULER;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.LEVEL;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.NO_KEYS;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.QUATERNION;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.STICK;
import static com.ivomens.mcheli.core.sim.attitude.AttitudeFakes.flying;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rudder input and the attitude assists, flown through the real attitude models. */
class AssistedAttitudeTest {

    private static final RotorcraftAttitude HELI = new RotorcraftAttitude();
    private static final RotorcraftAttitude.Setup HELI_FLYING = new RotorcraftAttitude.Setup(STICK, 0.0F, false, false);
    private static final FixedWingAttitude PLANE = new FixedWingAttitude();
    private static final FixedWingAttitude.Setup PLANE_FLYING = new FixedWingAttitude.Setup(STICK, 0.0F,
            new GroundTurning(1.0F, true), 1.0F, 0.0F, false, FixedWingAttitude.Vtol.NONE, false, false);

    private static AttitudeState<RotorcraftAttitude.Memory> heli(Attitude attitude) {
        return new AttitudeState<>(attitude, null, new RotorcraftAttitude.Memory(1.0F));
    }

    private static AttitudeState<FixedWingAttitude.Memory> plane(Attitude attitude) {
        return new AttitudeState<>(attitude, null, new FixedWingAttitude.Memory(0.0F, false));
    }

    @Test
    void noRudderTurnsExactlyAsTheMouseAlwaysDid() {
        AttitudeInput mouse = new AttitudeInput(13.7F, -4.2F, false);
        AttitudeInput withZeroRudder = new AttitudeInput(13.7F, -4.2F, false, 0.0F);
        for (boolean quaternion : new boolean[]{false, true}) {
            AttitudeContext context = flying(quaternion ? QUATERNION : EULER);
            assertEquals(HELI.integrate(heli(LEVEL), mouse, NO_KEYS, HELI_FLYING, context, 0.05F),
                    HELI.integrate(heli(LEVEL), withZeroRudder, NO_KEYS, HELI_FLYING, context, 0.05F));
            assertEquals(PLANE.integrate(plane(LEVEL), mouse, NO_KEYS, PLANE_FLYING, context, 0.05F),
                    PLANE.integrate(plane(LEVEL), withZeroRudder, NO_KEYS, PLANE_FLYING, context, 0.05F));
        }
    }

    @Test
    void rudderYawsWithoutRollingAHelicopter() {
        AttitudeState<RotorcraftAttitude.Memory> after = HELI.integrate(heli(LEVEL),
                new AttitudeInput(0.0F, 0.0F, false, 10.0F), NO_KEYS, HELI_FLYING, flying(EULER), 0.05F);
        assertEquals(0.5F, after.attitude().yaw(), 1.0e-4F);
        assertEquals(0.0F, after.attitude().roll(), 1.0e-4F);
    }

    @Test
    void rudderAddsToThePlanesStickYawAndIsIgnoredInFreeLook() {
        AttitudeState<FixedWingAttitude.Memory> after = PLANE.integrate(plane(LEVEL),
                new AttitudeInput(0.0F, 0.0F, false, 10.0F), NO_KEYS, PLANE_FLYING, flying(EULER), 0.05F);
        assertEquals(10.0F * 0.8F * 0.05F, after.attitude().yaw(), 1.0e-4F);
        AttitudeState<FixedWingAttitude.Memory> looking = PLANE.integrate(plane(LEVEL),
                new AttitudeInput(0.0F, 0.0F, true, 10.0F), NO_KEYS, PLANE_FLYING, flying(EULER), 0.05F);
        assertEquals(0.0F, looking.attitude().yaw(), 1.0e-6F);
    }

    @Test
    void autoLevelLeavesTheStickAloneWhileThePilotSteers() {
        AttitudeInput steering = new AttitudeInput(10.0F, 0.0F, false);
        assertSame(steering, AutoLevel.DEFAULT.apply(steering, new Attitude(0.0F, 20.0F, 30.0F)));
        assertSame(AttitudeInput.CENTRED, AutoLevel.DEFAULT.apply(AttitudeInput.CENTRED, LEVEL));
    }

    @Test
    void autoLevelBringsAHelicopterBackToLevelWithoutTurningIt() {
        AttitudeState<RotorcraftAttitude.Memory> state = heli(new Attitude(0.0F, 25.0F, 30.0F));
        AttitudeState<RotorcraftAttitude.Memory> unassisted = state;
        for (int step = 0; step < 60; step++) {
            AttitudeInput stick = AutoLevel.DEFAULT.apply(AttitudeInput.CENTRED, state.attitude());
            state = HELI.integrate(state, stick, NO_KEYS, HELI_FLYING, flying(QUATERNION), 0.05F);
            unassisted = HELI.integrate(unassisted, AttitudeInput.CENTRED, NO_KEYS, HELI_FLYING, flying(QUATERNION),
                    0.05F);
        }
        assertTrue(Math.abs(state.attitude().pitch()) < 1.0F, "pitch " + state.attitude().pitch());
        assertTrue(Math.abs(state.attitude().roll()) < 1.0F, "roll " + state.attitude().roll());
        assertEquals(25.0F, unassisted.attitude().pitch(), 0.5F, "without the assist the nose stays down");
        assertTrue(Math.abs(state.attitude().yaw()) < 5.0F, "yaw " + state.attitude().yaw());
    }

    @Test
    void coordinatedTurnYawsTowardsTheBank() {
        CoordinatedTurn turn = CoordinatedTurn.withGain(1.0F);
        AttitudeInput banked = turn.apply(AttitudeInput.CENTRED, new Attitude(0.0F, 0.0F, 30.0F), true);
        assertEquals(20.0F, banked.rudder(), 1.0e-4F);
        AttitudeInput other = turn.apply(AttitudeInput.CENTRED, new Attitude(0.0F, 0.0F, -30.0F), true);
        assertEquals(-20.0F, other.rudder(), 1.0e-4F);
        assertSame(AttitudeInput.CENTRED, turn.apply(AttitudeInput.CENTRED, new Attitude(0.0F, 0.0F, 30.0F), false),
                "not on the ground");
        assertSame(AttitudeInput.CENTRED, turn.apply(AttitudeInput.CENTRED, new Attitude(0.0F, 0.0F, 0.5F), true),
                "not in level flight");
    }

    @Test
    void aBankedPlaneWithAutoRudderTurnsTheWayItLeans() {
        CoordinatedTurn turn = CoordinatedTurn.withGain(1.0F);
        AttitudeState<FixedWingAttitude.Memory> state = plane(new Attitude(0.0F, 0.0F, 30.0F));
        for (int step = 0; step < 20; step++) {
            state = PLANE.integrate(state, turn.apply(AttitudeInput.CENTRED, state.attitude(), true), NO_KEYS,
                    PLANE_FLYING, flying(QUATERNION), 0.05F);
        }
        assertTrue(state.attitude().yaw() > 5.0F, "yaw " + state.attitude().yaw());
    }
}
