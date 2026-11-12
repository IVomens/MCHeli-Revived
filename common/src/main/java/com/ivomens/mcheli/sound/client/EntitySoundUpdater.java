package com.ivomens.mcheli.sound.client;

import com.ivomens.mcheli.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.entity.Entity;

/** Drives one looping, linearly attenuated sound attached to an entity. */
public class EntitySoundUpdater {

    protected final SoundManager soundManager;
    protected AdjustableSound sound;

    public EntitySoundUpdater(Minecraft minecraft, Entity entity) {
        this.soundManager = minecraft.getSoundManager();
    }

    public void initEntitySound(String name) {
        this.sound = new AdjustableSound(ModSounds.toLocation(name), 1.0F, 1.0F, 0.0, 0.0, 0.0);
        this.sound.setRepeat(true);
        this.sound.setAttenuation(SoundInstance.Attenuation.LINEAR);
    }

    public boolean isValidSound() {
        return this.sound != null;
    }

    public void playEntitySound(Entity entity, float volume, float pitch) {
        if (this.isValidSound()) {
            this.sound.setSoundParam(entity, volume, pitch);
            this.soundManager.play(this.sound);
        }
    }

    public void stopEntitySound() {
        if (this.isValidSound()) {
            this.soundManager.stop(this.sound);
        }
    }

    public boolean isEntitySoundPlaying() {
        return this.isValidSound() && this.soundManager.isActive(this.sound);
    }

    public void setEntitySoundPitch(float pitch) {
        if (this.isValidSound()) {
            this.sound.setPitch(pitch);
        }
    }

    public void setEntitySoundVolume(float volume) {
        if (this.isValidSound()) {
            this.sound.setVolume(volume);
        }
    }

    public void updateSoundLocation(Entity entity) {
        if (this.isValidSound()) {
            this.sound.setPosition(entity);
        }
    }

    public void updateSoundLocation(double x, double y, double z) {
        if (this.isValidSound()) {
            this.sound.setPosition(x, y, z);
        }
    }
}
