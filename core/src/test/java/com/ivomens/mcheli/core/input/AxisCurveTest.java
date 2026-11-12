package com.ivomens.mcheli.core.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AxisCurveTest {

    @Test
    void aLinearAxisPassesItsReadingThrough() {
        for (float raw = -1.0F; raw <= 1.0F; raw += 0.125F) {
            assertEquals(raw, AxisCurve.LINEAR.shape(raw));
        }
    }

    @Test
    void theDeadZoneReadsAsZeroAndTheTravelStartsAtItsEdge() {
        AxisCurve curve = new AxisCurve(false, 0.2F, 0.0F, 1.0F);
        assertEquals(0.0F, curve.shape(0.15F));
        assertEquals(0.0F, curve.shape(-0.2F));
        // just past the edge it is close to zero, not 0.2: no jump
        assertEquals(0.0125F, curve.shape(0.21F), 1.0e-5F);
        assertEquals(0.5F, curve.shape(0.6F), 1.0e-6F);
        assertEquals(-1.0F, curve.shape(-1.0F));
        assertFalse(curve.deflected(0.1F));
        assertTrue(curve.deflected(-0.3F));
    }

    @Test
    void expoSoftensSmallDeflectionsButKeepsTheEnds() {
        AxisCurve curve = new AxisCurve(false, 0.0F, 1.0F, 1.0F);
        assertEquals(0.125F, curve.shape(0.5F), 1.0e-6F);
        assertEquals(1.0F, curve.shape(1.0F));
        AxisCurve half = new AxisCurve(false, 0.0F, 0.5F, 1.0F);
        assertEquals(0.5F * 0.5F + 0.5F * 0.125F, half.shape(0.5F), 1.0e-6F);
    }

    @Test
    void sensitivityScalesAndTheResultStaysInRange() {
        AxisCurve curve = new AxisCurve(false, 0.0F, 0.0F, 2.0F);
        assertEquals(0.6F, curve.shape(0.3F), 1.0e-6F);
        assertEquals(1.0F, curve.shape(0.8F));
        assertEquals(-1.0F, curve.shape(-0.8F));
    }

    @Test
    void invertFlipsTheAxisAndReadingsOutsideTheRangeAreClamped() {
        AxisCurve curve = AxisCurve.LINEAR.inverted(true);
        assertEquals(-0.4F, curve.shape(0.4F));
        assertEquals(-1.0F, curve.shape(3.0F));
    }

    @Test
    void outOfRangeSettingsAreHeldToSaneValues() {
        AxisCurve curve = new AxisCurve(false, 2.0F, -1.0F, -3.0F);
        assertEquals(0.9F, curve.deadZone());
        assertEquals(0.0F, curve.expo());
        assertEquals(0.0F, curve.sensitivity());
    }
}
