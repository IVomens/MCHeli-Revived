package com.ivomens.mcheli.combat.weapon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The padded cooldowns are part of how every weapon fires; their table must not drift. */
class CooldownsTest {

    @Test
    void shortCooldownsGetAFewTicksMore() {
        assertEquals(1, Cooldowns.padded(0));
        assertEquals(4, Cooldowns.padded(3));
        assertEquals(6, Cooldowns.padded(4));
        assertEquals(8, Cooldowns.padded(6));
        assertEquals(10, Cooldowns.padded(7));
        assertEquals(12, Cooldowns.padded(9));
        assertEquals(16, Cooldowns.padded(10));
        assertEquals(25, Cooldowns.padded(19));
        assertEquals(30, Cooldowns.padded(20));
        assertEquals(39, Cooldowns.padded(29));
    }

    @Test
    void longCooldownsAreStoredNegative() {
        assertEquals(-40, Cooldowns.padded(30));
        assertEquals(-110, Cooldowns.padded(100));
    }
}
