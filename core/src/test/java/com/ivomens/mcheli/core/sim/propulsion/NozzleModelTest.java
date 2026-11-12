package com.ivomens.mcheli.core.sim.propulsion;

import com.ivomens.mcheli.core.sim.math.Attitude;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NozzleModelTest {

    private static final Attitude LEVEL = new Attitude(0.0F, 0.0F, 0.0F);

    @Test
    void unvectoredThrustPointsTenDegreesAboveTheNose() {
        NozzleModel.Thrust thrust = NozzleModel.thrust(LEVEL, 0.0F);

        assertEquals(Math.sin(Math.toRadians(10.0)), thrust.direction().y(), 1.0E-4);
        assertEquals(LEVEL, thrust.attitude());
    }

    @Test
    void verticalNozzlesPushStraightUpAndKeepLittleSideways() {
        NozzleModel.Thrust thrust = NozzleModel.thrust(new Attitude(0.0F, 0.0F, 0.0F), 90.0F);

        assertEquals(1.0, thrust.direction().y(), 1.0E-4);
        assertEquals(0.0, thrust.direction().z(), 1.0E-4);
    }

    @Test
    void vectoredThrustLiftsAtAnEighthOnceClearlyDeflected() {
        assertEquals(2.0, NozzleModel.liftDivisor(0.0F));
        assertEquals(2.0, NozzleModel.liftDivisor(0.005F));
        assertEquals(8.0, NozzleModel.liftDivisor(30.0F));
    }
}
