package com.ivomens.mcheli.combat.explosion.net;

import com.ivomens.mcheli.golden.MinecraftBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The batched explosion effects: positions on the grid, sizes, blocks and limits. */
class ExplosionEffectsMessageTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.ensure();
    }

    @Test
    void positionsLandOnTheGridWithinHalfAStep() {
        double[] coordinates = {0.0, 0.1, -0.1, 12.345678, -29_999_983.987654, 29_999_983.4321, -64.0001, 319.9999};
        for (double coordinate : coordinates) {
            Vec3 pos = new ExplosionEffect(new Vec3(coordinate, coordinate, coordinate), 1.0F, false, List.of()).pos();
            assertTrue(Math.abs(pos.x - coordinate) <= 0.5 / ExplosionEffect.POSITION_STEPS, coordinate + " -> " + pos.x);
            assertEquals(pos, new ExplosionEffect(pos, 1.0F, false, List.of()).pos(), "the grid is stable");
        }
    }

    @Test
    void aTickOfExplosionsRoundTrips() {
        List<ExplosionEffect> effects = new ArrayList<>();
        effects.add(effect(new Vec3(1_000_123.37, 64.5, -2_000_456.01), 4.0F, false, 40));
        effects.add(effect(new Vec3(1_000_130.11, 63.2, -2_000_440.77), 4.0F, false, 0));
        effects.add(effect(new Vec3(1_000_130.11, 63.2, -2_000_440.77), 4.0F, false, 0));
        effects.add(effect(new Vec3(-5.5, -60.25, 7.75), 1.5F, true, 3));
        effects.add(effect(new Vec3(-29_999_000.5, 300.0, 29_999_000.5), 15.0F, false, 4096));
        ExplosionEffectsMessage message = new ExplosionEffectsMessage(effects);
        RegistryFriendlyByteBuf buf = buffer();
        ExplosionEffectsMessage.TYPE.codec().encode(buf, message);
        assertEquals(message, ExplosionEffectsMessage.TYPE.codec().decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void anExplosionWithoutBlocksNearTheOneBeforeIsAFewBytes() {
        ExplosionEffect first = effect(new Vec3(100.37, 64.5, -200.01), 2.0F, false, 0);
        ExplosionEffect second = effect(new Vec3(103.89, 65.1, -190.66), 2.0F, false, 0);
        RegistryFriendlyByteBuf one = buffer();
        ExplosionEffectsMessage.TYPE.codec().encode(one, new ExplosionEffectsMessage(List.of(first)));
        RegistryFriendlyByteBuf two = buffer();
        ExplosionEffectsMessage.TYPE.codec().encode(two, new ExplosionEffectsMessage(List.of(first, second)));
        // flags, offsets of 3.5, 0.6 and 9.4 blocks in 3, 2 and 3 bytes; the size repeats
        assertEquals(9, two.readableBytes() - one.readableBytes());
    }

    @Test
    void limitsAreKept() {
        List<ExplosionEffect> tooMany = Collections.nCopies(ExplosionEffectsMessage.MAX_EFFECTS + 1,
                effect(Vec3.ZERO, 1.0F, false, 0));
        assertThrows(EncoderException.class,
                () -> ExplosionEffectsMessage.TYPE.codec().encode(buffer(), new ExplosionEffectsMessage(tooMany)));
        List<ExplosionEffect> tooManyBlocks = Collections.nCopies(17, effect(Vec3.ZERO, 1.0F, false, 4096));
        assertThrows(EncoderException.class,
                () -> ExplosionEffectsMessage.TYPE.codec().encode(buffer(), new ExplosionEffectsMessage(tooManyBlocks)));

        RegistryFriendlyByteBuf hostile = buffer();
        VarInt.write(hostile, 1);
        hostile.writeByte(4); // has blocks
        hostile.writeByte(0).writeByte(0).writeByte(0);
        hostile.writeFloat(1.0F);
        VarInt.write(hostile, ExplosionEffect.MAX_POSITIONS + 1);
        assertThrows(DecoderException.class, () -> ExplosionEffectsMessage.TYPE.codec().decode(hostile));
    }

    @Test
    void theSampleIsCutAndLeftOutForModernClients() {
        List<BlockPos> blocks = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            blocks.add(new BlockPos(i % 20, i / 400, i % 7));
        }
        assertEquals(ExplosionEffect.MAX_POSITIONS,
                ExplosionEffect.of(Vec3.ZERO, 3.0F, false, blocks, true).affectedBlocks().size());
        assertEquals(blocks.subList(0, 10), ExplosionEffect.of(Vec3.ZERO, 3.0F, false, blocks, true)
                .affectedBlocks().subList(0, 10));
        assertTrue(ExplosionEffect.of(Vec3.ZERO, 3.0F, false, blocks, false).affectedBlocks().isEmpty());
    }

    private static ExplosionEffect effect(Vec3 pos, float size, boolean inWater, int blocks) {
        BlockPos center = BlockPos.containing(pos);
        List<BlockPos> list = new ArrayList<>();
        for (int i = 0; i < blocks; i++) {
            list.add(center.offset(i % 13 - 6, i / 169 % 13 - 6, i / 13 % 13 - 6));
        }
        return new ExplosionEffect(pos, size, inWater, list);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
