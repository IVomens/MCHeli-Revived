package com.ivomens.mcheli.sound.client;

import com.ivomens.mcheli.sound.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/** A sound whose position, volume and pitch may be changed while it plays. */
public class AdjustableSound extends AbstractTickableSoundInstance {

    protected AdjustableSound(ResourceLocation sound, float volume, float pitch, double x, double y, double z) {
        super(resolve(sound), SoundSource.MASTER, SoundInstance.createUnseededRandom());
        this.setVolumeAndPitch(volume, pitch);
        this.setPosition(x, y, z);
    }

    protected AdjustableSound(ResourceLocation sound, float volume, float pitch) {
        this(resolve(sound), volume, pitch);
    }

    /** Non-positional sound, played at the listener (UI feedback). */
    protected AdjustableSound(SoundEvent soundEvent, float volume, float pitch) {
        super(soundEvent, SoundSource.MASTER, SoundInstance.createUnseededRandom());
        this.setVolumeAndPitch(volume, pitch);
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = true;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
    }

    private static SoundEvent resolve(ResourceLocation sound) {
        ClientSoundRegistry.ensureRegistered(sound);
        return ModSounds.getSound(sound);
    }

    public void setRepeat(boolean repeat) {
        this.looping = repeat;
        this.delay = 0;
    }

    public void setAttenuation(SoundInstance.Attenuation attenuation) {
        this.attenuation = attenuation;
    }

    public void setSoundParam(Entity entity, float volume, float pitch) {
        this.setPosition(entity);
        this.setVolumeAndPitch(volume, pitch);
    }

    public void setVolumeAndPitch(float volume, float pitch) {
        this.setVolume(volume);
        this.setPitch(pitch);
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setPosition(Entity entity) {
        this.setPosition(entity.getX(), entity.getY(), entity.getZ());
    }

    @Override
    public boolean canStartSilent() {
        return this.looping;
    }

    /** Position, volume and pitch are pushed by the owner instead of being updated here. */
    @Override
    public void tick() {
    }
}
