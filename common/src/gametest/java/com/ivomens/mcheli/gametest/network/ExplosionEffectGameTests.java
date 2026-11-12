package com.ivomens.mcheli.gametest.network;

import com.ivomens.mcheli.combat.explosion.ExplosionSpec;
import com.ivomens.mcheli.combat.explosion.McheliExplosion;
import com.ivomens.mcheli.combat.explosion.client.ExplosionEffects;
import com.ivomens.mcheli.combat.explosion.net.ExplosionEffect;
import com.ivomens.mcheli.combat.explosion.net.ExplosionEffectsMessage;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.ParticleParams;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.util.ModLogger;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The explosion effects a client draws from the batched message are the ones it drew from the message of
 * protocol 12, one per explosion. Real explosions are set off in blocks of several kinds (and one under
 * water); each is sent both ways, and the client's drawing code ({@link ExplosionEffects#draw}) runs on
 * both with the same random numbers, for the legacy style (MCHeli debris and vanilla debris) and for the
 * modern style, which is sent no blocks. The calls it makes (particles, block dust, flares, the modern
 * explosion) must be the same: the same calls in the same order, the same whole numbers, and positions
 * and motions apart by no more than the position grid moves them.
 */
public final class ExplosionEffectGameTests {

    private static final String BATCH = "mcheli_network";
    /** A position moves at most half a grid step an axis; motions follow it. */
    private static final double TOLERANCE = 0.01;
    private static final float[] SIZES = {1.0F, 2.0F, 3.0F, 4.0F, 6.0F, 10.0F, 15.0F};
    private static final Block[] GROUND = {Blocks.STONE, Blocks.DIRT, Blocks.OAK_PLANKS, Blocks.GLASS, Blocks.SAND,
            Blocks.OBSIDIAN};

    private ExplosionEffectGameTests() {
    }

    public static List<TestFunction> effects() {
        return List.of(new TestFunction(BATCH, "explosion_effects_draw_as_before", "mcheli:empty", 20, 0, true,
                ExplosionEffectGameTests::drawAsBefore));
    }

    /** One explosion as the server set it off. */
    private record Blast(Vec3 at, float size, boolean inWater, List<BlockPos> blocks) {
    }

    private static void drawAsBefore(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        buildGround(h);
        List<Blast> blasts = new ArrayList<>();
        for (int i = 0; i < SIZES.length; i++) {
            Vec3 at = h.absoluteVec(new Vec3(8.37 + 2.11 * i, 5.03 + 0.29 * i, 9.71 + 1.53 * i));
            blasts.add(blast(level, at, SIZES[i], false));
        }
        blasts.add(blast(level, h.absoluteVec(new Vec3(27.43, 3.38, 27.19)), 3.0F, true));

        int oldBytes = 0;
        List<ExplosionEffect> withBlocks = new ArrayList<>();
        List<ExplosionEffect> withoutBlocks = new ArrayList<>();
        List<Blast> before = new ArrayList<>();
        for (Blast blast : blasts) {
            ByteBuf old = Unpooled.buffer();
            writeOld(old, blast);
            oldBytes += old.readableBytes();
            before.add(readOld(old));
            withBlocks.add(ExplosionEffect.of(blast.at(), blast.size(), blast.inWater(), blast.blocks(), true));
            withoutBlocks.add(ExplosionEffect.of(blast.at(), blast.size(), blast.inWater(), blast.blocks(), false));
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        List<ExplosionEffect> legacy = roundTrip(buf, withBlocks);
        int newBytes = buf.writerIndex();
        buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        List<ExplosionEffect> modern = roundTrip(buf, withoutBlocks);
        int modernBytes = buf.writerIndex();

        List<String> failures = new ArrayList<>();
        double[] worst = new double[1];
        int calls = 0;
        for (int i = 0; i < blasts.size(); i++) {
            Blast old = before.get(i);
            ExplosionEffect now = legacy.get(i);
            check(failures, old.blocks().equals(now.affectedBlocks()), i, "blocks differ");
            check(failures, old.size() == now.size() && old.inWater() == now.inWater(), i, "size or medium differ");
            check(failures, old.at().distanceTo(now.pos()) <= Math.sqrt(3) / 8192, i, "position moved too far");
            check(failures, modern.get(i).affectedBlocks().isEmpty(), i, "blocks sent to a modern client");
            check(failures, old.blocks().size() > 0 || old.inWater(), i, "the blast reached no block");
            calls += compare(level, old, now, false, false, i, failures, worst);
            calls += compare(level, old, now, false, true, i, failures, worst);
            calls += compare(level, old, modern.get(i), true, false, i, failures, worst);
        }
        ModLogger.get().info("[explosion effects] {} explosions, {} drawing calls compared, largest difference {};"
                        + " protocol 12 {} bytes, now {} bytes with blocks, {} without", blasts.size(), calls,
                worst[0], oldBytes, newBytes, modernBytes);
        if (failures.isEmpty()) {
            h.succeed();
        } else {
            h.fail(String.join("; ", failures));
        }
    }

    private static void buildGround(GameTestHelper h) {
        for (int x = 2; x < 30; x++) {
            for (int z = 2; z < 30; z++) {
                for (int y = 1; y <= 4; y++) {
                    boolean pool = x >= 23 && z >= 23 && y >= 2;
                    h.setBlock(x, y, z, pool ? Blocks.WATER : GROUND[(x * 7 + z * 3 + y) % GROUND.length]);
                }
            }
        }
    }

    /** Sets a real explosion off, without breaking blocks, and keeps what it reached in the rays' order. */
    private static Blast blast(ServerLevel level, Vec3 at, float size, boolean inWater) {
        ExplosionSpec spec = ExplosionSpec.at(at, size).sound(false).underWater(inWater).build();
        McheliExplosion explosion = new McheliExplosion(level, null, null, spec, false);
        explosion.detonate();
        List<BlockPos> blocks = explosion.getAffectedBlockPositions();
        return new Blast(at, size, inWater, List.copyOf(blocks.subList(0, Math.min(blocks.size(), 4096))));
    }

    private static List<ExplosionEffect> roundTrip(RegistryFriendlyByteBuf buf, List<ExplosionEffect> effects) {
        ExplosionEffectsMessage.TYPE.codec().encode(buf, new ExplosionEffectsMessage(effects));
        ExplosionEffectsMessage decoded = ExplosionEffectsMessage.TYPE.codec().decode(
                new RegistryFriendlyByteBuf(buf.duplicate(), buf.registryAccess()));
        return decoded.effects();
    }

    /** Draws the explosion as protocol 12 sent it and as it is sent now, and compares the calls. */
    private static int compare(ServerLevel level, Blast old, ExplosionEffect now, boolean modern, boolean vanillaDebris,
                               int index, List<String> failures, double[] worst) {
        Recorder before = new Recorder(modern);
        ExplosionEffects.draw(level, old.at(), old.size(), old.inWater(), old.blocks(), before, vanillaDebris,
                RandomSource.create(7L + index), RandomSource.create(11L + index), before::flare);
        Recorder after = new Recorder(modern);
        ExplosionEffects.draw(level, now.pos(), now.size(), now.inWater(), now.affectedBlocks(), after, vanillaDebris,
                RandomSource.create(7L + index), RandomSource.create(11L + index), after::flare);
        String style = modern ? "modern" : vanillaDebris ? "vanilla debris" : "mcheli debris";
        if (before.calls.size() != after.calls.size()) {
            failures.add("explosion " + index + " (" + style + "): " + before.calls.size() + " calls before, "
                    + after.calls.size() + " now");
            return 0;
        }
        for (int c = 0; c < before.calls.size(); c++) {
            Call a = before.calls.get(c);
            Call b = after.calls.get(c);
            if (!a.name().equals(b.name()) || a.values().length != b.values().length) {
                failures.add("explosion " + index + " (" + style + ") call " + c + ": " + a.name() + " / " + b.name());
                return 0;
            }
            for (int v = 0; v < a.values().length; v++) {
                double difference = Math.abs(a.values()[v] - b.values()[v]);
                worst[0] = Math.max(worst[0], difference);
                if (difference > TOLERANCE) {
                    failures.add("explosion " + index + " (" + style + ") call " + c + " " + a.name() + " value " + v
                            + ": " + a.values()[v] + " / " + b.values()[v]);
                    return 0;
                }
            }
        }
        if (before.calls.isEmpty()) {
            failures.add("explosion " + index + " (" + style + ") drew nothing");
        }
        return before.calls.size();
    }

    private static void check(List<String> failures, boolean ok, int index, String what) {
        if (!ok) {
            failures.add("explosion " + index + ": " + what);
        }
    }

    // protocol 12: one message per explosion, the position as three doubles

    private static void writeOld(ByteBuf buf, Blast blast) {
        Codecs.VEC3.encode(buf, blast.at());
        buf.writeFloat(blast.size());
        VarInt.write(buf, -1);
        buf.writeBoolean(blast.inWater());
        VarInt.write(buf, blast.blocks().size());
        BlockPos center = BlockPos.containing(blast.at());
        for (BlockPos block : blast.blocks()) {
            writeSigned(buf, block.getX() - center.getX());
            writeSigned(buf, block.getY() - center.getY());
            writeSigned(buf, block.getZ() - center.getZ());
        }
    }

    private static Blast readOld(ByteBuf buf) {
        Vec3 pos = Codecs.VEC3.decode(buf);
        float size = buf.readFloat();
        VarInt.read(buf);
        boolean inWater = buf.readBoolean();
        int count = VarInt.read(buf);
        BlockPos center = BlockPos.containing(pos);
        List<BlockPos> blocks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blocks.add(center.offset(readSigned(buf), readSigned(buf), readSigned(buf)));
        }
        return new Blast(pos, size, inWater, blocks);
    }

    private static void writeSigned(ByteBuf buf, int value) {
        VarInt.write(buf, value << 1 ^ value >> 31);
    }

    private static int readSigned(ByteBuf buf) {
        int value = VarInt.read(buf);
        return value >>> 1 ^ -(value & 1);
    }

    /** One call of the drawing code, with its numbers. */
    private record Call(String name, double[] values) {
    }

    /** Effects that write down what they are asked to draw. */
    private static final class Recorder implements Effects {

        private final List<Call> calls = new ArrayList<>();
        private final boolean whole;

        Recorder(boolean whole) {
            this.whole = whole;
        }

        void flare(Entity flare) {
            Vec3 motion = flare.getDeltaMovement();
            this.calls.add(new Call("flare", new double[]{flare.getX(), flare.getY(), flare.getZ(), motion.x, motion.y,
                    motion.z}));
        }

        @Override
        public void spawn(ParticleParams p) {
            this.calls.add(new Call("spawn " + p.name(), new double[]{p.x(), p.y(), p.z(), p.motionX(), p.motionY(),
                    p.motionZ(), p.size(), p.age()}));
        }

        @Override
        public void vanilla(String name, double x, double y, double z, double mx, double my, double mz, int... params) {
            this.calls.add(new Call("vanilla " + name, new double[]{x, y, z, mx, my, mz}));
        }

        @Override
        public boolean blockDust(Level level, int blockX, int blockY, int blockZ, double x, double y, double z,
                                 double mx, double my, double mz, float scale) {
            this.calls.add(new Call("blockDust", new double[]{blockX, blockY, blockZ, x, y, z, mx, my, mz, scale}));
            return true;
        }

        @Override
        public boolean explosion(double x, double y, double z, float size) {
            this.calls.add(new Call("explosion", new double[]{x, y, z, size}));
            return this.whole;
        }

        @Override
        public boolean waterSplash(double x, double y, double z, float size) {
            this.calls.add(new Call("waterSplash", new double[]{x, y, z, size}));
            return this.whole;
        }

        @Override
        public boolean drawsWholeExplosions() {
            return this.whole;
        }
    }
}
