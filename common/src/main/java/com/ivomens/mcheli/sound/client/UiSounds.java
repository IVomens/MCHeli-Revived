package com.ivomens.mcheli.sound.client;

import com.ivomens.mcheli.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Sounds the player hears at the ear rather than somewhere in the world: clicks, beeps and warnings. */
public final class UiSounds {

    private UiSounds() {
    }

    /** The button click. */
    public static void click() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
    }

    public static void play(SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new AdjustableSound(sound, volume, pitch));
    }

    public static void play(ResourceLocation sound, float volume, float pitch) {
        ClientSoundRegistry.ensureRegistered(sound);
        play(ModSounds.getSound(sound), volume, pitch);
    }

    /** Plays a sound named like {@link ModSounds#toLocation}. */
    public static void play(String name, float volume, float pitch) {
        play(ModSounds.toLocation(name), volume, pitch);
    }
}
