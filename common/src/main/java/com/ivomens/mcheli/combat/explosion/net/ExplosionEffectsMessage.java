package com.ivomens.mcheli.combat.explosion.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The explosions of one server tick that a player is shown, in the order they went off, in one message
 * (one payload id and frame for the whole tick instead of one per explosion).
 * <p>
 * Encoding: the count, then per explosion a flags byte (under water, same size as the one before, has
 * blocks), its position in grid steps ({@link ExplosionEffect#POSITION_STEPS}) as zigzag VarLongs, the
 * first absolute and every other relative to the one before (explosions of a tick lie close together,
 * so about three bytes an axis), the size as a float unless it repeats, and the blocks, if any, as their
 * count and zigzag VarInt offsets from the block of the position, a byte or two each.
 */
public record ExplosionEffectsMessage(List<ExplosionEffect> effects) implements ClientboundMessage {

    /** Explosions in one message at most; the sender starts another message beyond it. */
    public static final int MAX_EFFECTS = 1024;
    /** Affected blocks of all the explosions of one message at most, which keeps it well below a payload's limit. */
    public static final int MAX_TOTAL_BLOCKS = 65536;

    private static final int IN_WATER = 1;
    private static final int SAME_SIZE = 2;
    private static final int HAS_BLOCKS = 4;

    private static final StreamCodec<ByteBuf, ExplosionEffectsMessage> CODEC = StreamCodec.of(
            ExplosionEffectsMessage::write, ExplosionEffectsMessage::read);

    public static final MessageType<ExplosionEffectsMessage> TYPE =
            MessageType.clientbound("combat/explosions", CODEC);

    public ExplosionEffectsMessage {
        effects = List.copyOf(effects);
    }

    @Override
    public MessageType<ExplosionEffectsMessage> messageType() {
        return TYPE;
    }

    private static void write(ByteBuf buf, ExplosionEffectsMessage message) {
        List<ExplosionEffect> effects = message.effects;
        if (effects.size() > MAX_EFFECTS) {
            throw new EncoderException(effects.size() + " explosions in one message, at most " + MAX_EFFECTS);
        }
        int totalBlocks = 0;
        for (ExplosionEffect effect : effects) {
            if (effect.affectedBlocks().size() > ExplosionEffect.MAX_POSITIONS) {
                throw new EncoderException("Explosion with " + effect.affectedBlocks().size() + " blocks, at most "
                        + ExplosionEffect.MAX_POSITIONS);
            }
            totalBlocks += effect.affectedBlocks().size();
        }
        if (totalBlocks > MAX_TOTAL_BLOCKS) {
            throw new EncoderException(totalBlocks + " blocks in one message, at most " + MAX_TOTAL_BLOCKS);
        }
        VarInt.write(buf, effects.size());
        long x = 0;
        long y = 0;
        long z = 0;
        float size = Float.NaN;
        for (ExplosionEffect effect : effects) {
            boolean sameSize = Float.floatToIntBits(effect.size()) == Float.floatToIntBits(size);
            buf.writeByte((effect.inWater() ? IN_WATER : 0) | (sameSize ? SAME_SIZE : 0)
                    | (effect.affectedBlocks().isEmpty() ? 0 : HAS_BLOCKS));
            long nextX = ExplosionEffect.steps(effect.pos().x);
            long nextY = ExplosionEffect.steps(effect.pos().y);
            long nextZ = ExplosionEffect.steps(effect.pos().z);
            writeSigned(buf, nextX - x);
            writeSigned(buf, nextY - y);
            writeSigned(buf, nextZ - z);
            x = nextX;
            y = nextY;
            z = nextZ;
            if (!sameSize) {
                buf.writeFloat(effect.size());
                size = effect.size();
            }
            if (!effect.affectedBlocks().isEmpty()) {
                writeBlocks(buf, effect);
            }
        }
    }

    private static void writeBlocks(ByteBuf buf, ExplosionEffect effect) {
        VarInt.write(buf, effect.affectedBlocks().size());
        BlockPos center = BlockPos.containing(effect.pos());
        for (BlockPos block : effect.affectedBlocks()) {
            writeSigned(buf, block.getX() - center.getX());
            writeSigned(buf, block.getY() - center.getY());
            writeSigned(buf, block.getZ() - center.getZ());
        }
    }

    private static ExplosionEffectsMessage read(ByteBuf buf) {
        int count = VarInt.read(buf);
        if (count < 0 || count > MAX_EFFECTS) {
            throw new DecoderException(count + " explosions in one message, at most " + MAX_EFFECTS);
        }
        List<ExplosionEffect> effects = new ArrayList<>(count);
        long x = 0;
        long y = 0;
        long z = 0;
        float size = Float.NaN;
        int blocksLeft = MAX_TOTAL_BLOCKS;
        for (int i = 0; i < count; i++) {
            int flags = buf.readUnsignedByte();
            x += readSigned(buf);
            y += readSigned(buf);
            z += readSigned(buf);
            if ((flags & SAME_SIZE) == 0) {
                size = buf.readFloat();
            }
            Vec3 pos = new Vec3(x / (double) ExplosionEffect.POSITION_STEPS, y / (double) ExplosionEffect.POSITION_STEPS,
                    z / (double) ExplosionEffect.POSITION_STEPS);
            List<BlockPos> blocks = List.of();
            if ((flags & HAS_BLOCKS) != 0) {
                blocks = readBlocks(buf, pos, blocksLeft);
                blocksLeft -= blocks.size();
            }
            effects.add(new ExplosionEffect(pos, size, (flags & IN_WATER) != 0, blocks));
        }
        return new ExplosionEffectsMessage(effects);
    }

    private static List<BlockPos> readBlocks(ByteBuf buf, Vec3 pos, int blocksLeft) {
        int count = VarInt.read(buf);
        if (count < 0 || count > Math.min(ExplosionEffect.MAX_POSITIONS, blocksLeft)) {
            throw new DecoderException("Explosion with " + count + " blocks, at most "
                    + Math.min(ExplosionEffect.MAX_POSITIONS, blocksLeft));
        }
        BlockPos center = BlockPos.containing(pos);
        List<BlockPos> blocks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blocks.add(center.offset(readSignedInt(buf), readSignedInt(buf), readSignedInt(buf)));
        }
        return blocks;
    }

    private static void writeSigned(ByteBuf buf, long value) {
        VarLong.write(buf, value << 1 ^ value >> 63);
    }

    private static long readSigned(ByteBuf buf) {
        long value = VarLong.read(buf);
        return value >>> 1 ^ -(value & 1);
    }

    private static void writeSigned(ByteBuf buf, int value) {
        VarInt.write(buf, value << 1 ^ value >> 31);
    }

    private static int readSignedInt(ByteBuf buf) {
        int value = VarInt.read(buf);
        return value >>> 1 ^ -(value & 1);
    }
}
