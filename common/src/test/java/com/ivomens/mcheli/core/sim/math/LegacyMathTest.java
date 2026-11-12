package com.ivomens.mcheli.core.sim.math;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The physics core reproduces the game's sine table instead of calling it. This pins the copy to
 * the original, bit for bit, so a game update that changes the table cannot slip through.
 */
class LegacyMathTest {

    @Test
    void sineTableMatchesTheGameForEveryEntry() {
        for (int i = -70000; i <= 70000; i++) {
            float radians = i / 10430.378F;
            assertEquals(Mth.sin(radians), LegacyMath.sin(radians), "sin " + radians);
            assertEquals(Mth.cos(radians), LegacyMath.cos(radians), "cos " + radians);
        }
    }

    @Test
    void sineTableMatchesTheGameForDegreeAngles() {
        for (int tenths = -7200; tenths <= 7200; tenths++) {
            float radians = LegacyMath.floatRadians(tenths / 10.0F);
            assertEquals(Mth.sin(radians), LegacyMath.sin(radians));
            assertEquals(Mth.cos(radians), LegacyMath.cos(radians));
        }
    }

    @Test
    void clampMatchesTheGame() {
        assertEquals(Mth.clamp(5.0F, -1.0F, 1.0F), LegacyMath.clamp(5.0F, -1.0F, 1.0F));
        assertEquals(Mth.clamp(-5.0F, -1.0F, 1.0F), LegacyMath.clamp(-5.0F, -1.0F, 1.0F));
        assertEquals(Mth.clamp(0.25F, -1.0F, 1.0F), LegacyMath.clamp(0.25F, -1.0F, 1.0F));
    }
}
