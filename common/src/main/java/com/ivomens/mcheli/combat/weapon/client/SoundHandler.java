package com.ivomens.mcheli.combat.weapon.client;

import com.ivomens.mcheli.combat.weapon.net.SoundMessage;
import com.ivomens.mcheli.networking.ClientMessageHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Plays a sound the server sent, such as a weapon's hit sound. */
final class SoundHandler implements ClientMessageHandler<SoundMessage> {

    /** Volumes above 1 carry further: the attenuation distance is 16 blocks times the volume. */
    private static final float VOLUME = 100.0F;

    private final RandomSource random = RandomSource.create();

    @Override
    public void handle(SoundMessage message) {
        ResourceLocation id = ResourceLocation.tryParse(message.sound());
        if (id == null) {
            return;
        }
        float pitch = message.distort() ? 1.0F / (random.nextFloat() * 0.4F + 0.8F) : 1.0F;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(SoundEvent.createVariableRangeEvent(id),
                SoundSource.MASTER, VOLUME, pitch, random, message.x(), message.y(), message.z()));
    }
}
