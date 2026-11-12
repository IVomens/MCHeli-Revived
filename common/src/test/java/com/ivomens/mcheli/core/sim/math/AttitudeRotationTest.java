package com.ivomens.mcheli.core.sim.math;

import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The attitude maths was moved out of the entities; these pin the copies to the game's own helpers
 * bit for bit, over random attitudes including the poles.
 */
class AttitudeRotationTest {

    private static final int SAMPLES = 20000;
    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180.0F;

    private static Attitude randomAttitude(Random random) {
        float pitch = random.nextInt(10) == 0 ? (random.nextBoolean() ? 90.0F : -90.0F) : random.nextFloat() * 180.0F - 90.0F;
        return new Attitude(random.nextFloat() * 720.0F - 360.0F, pitch, random.nextFloat() * 360.0F - 180.0F);
    }

    private static void assertSame(float[] legacyPitchYawRoll, Attitude attitude) {
        assertEquals(legacyPitchYawRoll[0], attitude.pitch());
        assertEquals(legacyPitchYawRoll[1], attitude.yaw());
        assertEquals(legacyPitchYawRoll[2], attitude.roll());
    }

    @Test
    void orientationMatchesTheWorldQuaternionHelpers() {
        Random random = new Random(1);
        for (int i = 0; i < SAMPLES; i++) {
            Attitude start = randomAttitude(random);
            Attitude turn = new Attitude(random.nextFloat() * 4.0F - 2.0F, random.nextFloat() * 4.0F - 2.0F,
                    random.nextFloat() * 4.0F - 2.0F);
            Quaternionf legacy = Rotations.worldOrientation(start.yaw(), start.pitch(), start.roll())
                    .mul(Rotations.worldOrientation(turn.yaw(), turn.pitch(), turn.roll()));
            legacy.normalize();
            Orientation turned = Orientation.of(start).turnedBy(Orientation.of(turn));
            assertEquals(Orientation.from(legacy), turned);
            assertSame(Rotations.worldEuler(legacy), turned.toAttitude());
        }
    }

    @Test
    void zxyTurnMatchesTheMatrixReadBack() {
        Random random = new Random(2);
        for (int i = 0; i < SAMPLES; i++) {
            Attitude current = randomAttitude(random);
            Attitude turn = new Attitude(random.nextFloat() * 4.0F - 2.0F, random.nextFloat() * 4.0F - 2.0F,
                    random.nextFloat() * 4.0F - 2.0F);
            Quaternionf legacy = new Quaternionf()
                    .rotateZ(turn.roll() * DEGREES_TO_RADIANS)
                    .rotateX(turn.pitch() * DEGREES_TO_RADIANS)
                    .rotateY(turn.yaw() * DEGREES_TO_RADIANS)
                    .rotateZ(current.roll() * DEGREES_TO_RADIANS)
                    .rotateX(current.pitch() * DEGREES_TO_RADIANS)
                    .rotateY(current.yaw() * DEGREES_TO_RADIANS);
            assertSame(Rotations.legacyEuler(legacy),
                    ZxyRotation.turn(current, turn.yaw(), turn.pitch(), turn.roll()));
        }
    }

    @Test
    void wrapAndLimitMatchTheGame() {
        Random random = new Random(3);
        for (int i = 0; i < SAMPLES; i++) {
            float angle = random.nextFloat() * 2000.0F - 1000.0F;
            assertEquals(Mth.wrapDegrees(angle), LegacyMath.wrapDegrees(angle));
            assertEquals(LegacyMath.limit(angle, -90.0F, 90.0F), LegacyMath.limit(angle, -90.0F, 90.0F));
            assertEquals(LegacyMath.limit(angle, 30.0F, -30.0F), LegacyMath.limit(angle, 30.0F, -30.0F));
        }
    }
}
