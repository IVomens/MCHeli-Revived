package com.ivomens.mcheli.core.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyRampTest {

    @Test
    void aHeldKeyRampsToFullOverTheRampTime() {
        KeyRamp ramp = new KeyRamp();
        assertEquals(0.25F, ramp.update(false, true, 0.05F, 0.2F), 1.0e-6F);
        for (int i = 0; i < 10; i++) {
            ramp.update(false, true, 0.05F, 0.2F);
        }
        assertEquals(1.0F, ramp.value());
    }

    @Test
    void releasingOrHoldingBothDropsBackToZero() {
        KeyRamp ramp = new KeyRamp();
        ramp.update(true, false, 0.1F, 0.2F);
        assertEquals(-0.5F, ramp.value(), 1.0e-6F);
        assertEquals(0.0F, ramp.update(false, false, 0.1F, 0.2F));
        ramp.update(true, false, 0.1F, 0.2F);
        assertEquals(0.0F, ramp.update(true, true, 0.1F, 0.2F));
    }

    @Test
    void theOtherKeyReversesFromWhereTheAxisIs() {
        KeyRamp ramp = new KeyRamp();
        ramp.update(true, false, 0.15F, 0.2F);
        assertEquals(0.5F, ramp.update(false, true, 0.25F, 0.2F), 1.0e-6F);
    }

    @Test
    void noRampJumpsToFull() {
        KeyRamp ramp = new KeyRamp();
        assertEquals(1.0F, ramp.update(false, true, 0.001F, 0.0F));
        assertEquals(-1.0F, ramp.update(true, false, 0.001F, 0.0F));
    }
}
