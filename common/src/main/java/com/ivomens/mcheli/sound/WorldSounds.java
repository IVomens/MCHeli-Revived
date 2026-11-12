package com.ivomens.mcheli.sound;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Server side helpers for loud weapon sounds (cannons, artillery, missile launches, bombs and
 * explosions).
 * <p>
 * Variable range sound events are heard up to {@code 16 * max(volume, 1)} blocks on the client,
 * while the loudness itself is capped at 1. Heavy sounds are therefore sent with the volume raised
 * so the attenuation distance reaches {@link McheliConfig#VehicleSoundRange}, and the packet is only
 * sent to players inside that range instead of to the whole dimension.
 */
public final class WorldSounds {

    private WorldSounds() {
    }

    /** Range in blocks heavy weapon sounds are audible at. */
    public static double getHeavySoundRange() {
        double range = McheliConfig.vehicles().soundRange();
        return range > 0.0 ? range : 64.0;
    }

    public static Holder<SoundEvent> getSoundHolder(ResourceLocation name) {
        return BuiltInRegistries.SOUND_EVENT.getHolder(name)
                .<Holder<SoundEvent>>map(h -> h)
                .orElseGet(() -> Holder.direct(SoundEvent.createVariableRangeEvent(name)));
    }

    /** Plays a heavy weapon sound by name (a plain name is resolved in the {@code mcheli} namespace). */
    public static void playHeavySound(Level level, double x, double y, double z, @Nullable String name, float volume, float pitch) {
        if (name == null || name.isEmpty()) return;
        ResourceLocation id = name.indexOf(':') >= 0 ? ResourceLocation.parse(name) : Ids.of(name);
        playHeavySound(level, x, y, z, id, volume, pitch);
    }

    public static void playHeavySound(Level level, double x, double y, double z, @Nullable ResourceLocation name, float volume, float pitch) {
        if (name == null) return;
        playHeavySound(level, x, y, z, getSoundHolder(name), SoundSource.MASTER, volume, pitch);
    }

    public static void playHeavySound(Level level, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        double range = Math.max(getHeavySoundRange(), 16.0 * Math.max(volume, 1.0F));
        float rangeVolume = (float) Math.max(volume, range / 16.0);
        double rangeSq = range * range;
        long seed = level.getRandom().nextLong();
        ClientboundSoundPacket packet = null;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(x, y, z) <= rangeSq) {
                if (packet == null) {
                    packet = new ClientboundSoundPacket(sound, source, x, y, z, rangeVolume, pitch, seed);
                }
                player.connection.send(packet);
            }
        }
    }

    /** The vanilla explosion sound, audible over the heavy weapon sound range. */
    public static void playExplosionSound(Level level, double x, double y, double z) {
        float pitch = (1.0F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2F) * 0.7F;
        if (level.isClientSide) {
            level.playLocalSound(x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4.0F, pitch, false);
        } else {
            playHeavySound(level, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F, pitch);
        }
    }
}
