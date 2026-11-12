package com.ivomens.mcheli.sound;

import com.ivomens.mcheli.util.Ids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sound events are not put into the vanilla registry: every name is played through an unregistered
 * variable-range {@link SoundEvent} (sent to clients inline), and resolved on the client through the
 * {@code sounds.json} files of the mod and of addon packs.
 */
public final class ModSounds {

    private static final Map<ResourceLocation, SoundEvent> EVENTS = new ConcurrentHashMap<>();

    private ModSounds() {
    }

    public static void playSound(Level level, double x, double y, double z, ResourceLocation name, float volume, float pitch) {
        level.playSound(null, x, y, z, getSound(name), SoundSource.MASTER, volume, pitch);
    }

    public static void playSound(Level level, double x, double y, double z, String name, float volume, float pitch) {
        playSound(level, x, y, z, toLocation(name), volume, pitch);
    }

    @NotNull
    public static SoundEvent getSound(ResourceLocation location) {
        return EVENTS.computeIfAbsent(location, rl -> {
            SoundRegistry.INSTANCE.soundSet.add(rl);
            return SoundEvent.createVariableRangeEvent(rl);
        });
    }

    @NotNull
    public static SoundEvent getSound(String name) {
        return getSound(toLocation(name));
    }

    /** A sound name, defaulting to the {@code mcheli} namespace, as a resource location. */
    public static ResourceLocation toLocation(String name) {
        return name.indexOf(':') >= 0 ? ResourceLocation.parse(name) : Ids.of(name);
    }
}
