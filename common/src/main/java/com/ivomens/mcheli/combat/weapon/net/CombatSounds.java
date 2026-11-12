package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.Network;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Sounds the server plays for nearby players, such as a weapon's hit sound. */
public final class CombatSounds {

    private CombatSounds() {
    }

    /** Plays the sound for every player within {@code range}; a null or empty sound is ignored. */
    public static void playAround(Level level, Vec3 pos, double range, @Nullable String sound, boolean distort) {
        if (sound != null && !sound.isEmpty()) {
            Network.sendToAllAround(level, pos, range, SoundMessage.at(pos, sound, distort));
        }
    }
}
