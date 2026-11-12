package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.sound.ModSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;

/**
 * A two-state animated part (canopy, hatch, landing gear, nozzle, folding wing). Its state is one
 * bit of a synced integer; the rotation swings between 0 and {@link #rotationMax} with optional
 * sounds at the start and end of each movement.
 */
public class Parts {

    public final Entity parent;
    public final SynchedEntityData dataManager;
    /** Bit of the synced value that holds this part's state. */
    public final int shift;
    public final EntityDataAccessor<Integer> dataKey;
    public final String partName;
    private float prevRotation;
    private float rotation;
    private float rotationMax = 90.0F;
    /** Degrees moved per tick. */
    private float rotationInv = 1.0F;
    private Parts.Sound switchingSound = Sound.NONE;
    private Parts.Sound startOnSound = Sound.NONE;
    private Parts.Sound endOnSound = Sound.NONE;
    private Parts.Sound startOffSound = Sound.NONE;
    private Parts.Sound endOffSound = Sound.NONE;
    private boolean status;

    public float prevRotation() {
        return this.prevRotation;
    }

    public float rotation() {
        return this.rotation;
    }

    public float rotationMax() {
        return this.rotationMax;
    }

    public void setRotationInv(float rotationInv) {
        this.rotationInv = rotationInv;
    }

    public void setRotationMax(float rotationMax) {
        this.rotationMax = rotationMax;
    }

    /** Played whenever the server switches the part, either way. */
    public void setSwitchingSound(Parts.Sound sound) {
        this.switchingSound = sound;
    }

    /** Played when the part starts and when it ends moving to on. */
    public void setOnSounds(Parts.Sound start, Parts.Sound end) {
        this.startOnSound = start;
        this.endOnSound = end;
    }

    /** Played when the part starts and when it ends moving to off. */
    public void setOffSounds(Parts.Sound start, Parts.Sound end) {
        this.startOffSound = start;
        this.endOffSound = end;
    }

    public Parts(Entity parent, int shiftBit, EntityDataAccessor<Integer> dataKey, String name) {
        this.parent = parent;
        this.dataManager = parent.getEntityData();
        this.shift = shiftBit;
        this.dataKey = dataKey;
        this.status = (this.getDataWatcherValue() & 1 << this.shift) != 0;
        this.partName = name;
    }

    public int getDataWatcherValue() {
        return this.dataManager.get(this.dataKey);
    }

    public void setStatusServer(boolean status) {
        this.setStatusServer(status, true);
    }

    public void setStatusServer(boolean status, boolean playSound) {
        if (this.parent.level().isClientSide || this.getStatus() == status) {
            return;
        }
        this.updateDataWatcher(status);
        if (playSound) {
            this.playSound(this.switchingSound);
            this.playSound(status ? this.startOnSound : this.startOffSound);
        }
        this.update();
    }

    protected void updateDataWatcher(boolean status) {
        int mask = 1 << this.shift;
        int current = this.getDataWatcherValue();
        this.dataManager.set(this.dataKey, status ? current | mask : current & ~mask);
        this.status = status;
    }

    public boolean getStatus() {
        return this.status;
    }

    public boolean isOFF() {
        return !this.status && this.rotation <= 0.02F;
    }

    public boolean isON() {
        return this.status && this.rotation >= this.rotationMax - 0.02F;
    }

    /** Client: takes the state from the synced value. */
    public void updateStatusClient(int syncedValue) {
        if (this.parent.level().isClientSide) {
            this.status = (syncedValue & 1 << this.shift) != 0;
        }
    }

    public void update() {
        this.prevRotation = this.rotation;
        if (this.getStatus()) {
            if (this.rotation < this.rotationMax) {
                this.rotation += this.rotationInv;
                if (this.rotation >= this.rotationMax) {
                    this.playSound(this.endOnSound);
                }
            }
        } else if (this.rotation > 0.0F) {
            this.rotation -= this.rotationInv;
            if (this.rotation <= 0.0F) {
                this.playSound(this.endOffSound);
            }
        }
    }

    /** Sets the state and jumps straight to the end of the movement. */
    public void forceSwitch(boolean status) {
        this.updateDataWatcher(status);
        this.rotation = this.prevRotation = this.rotationMax;
    }

    public float getFactor() {
        return this.rotationMax > 0.0F ? this.rotation / this.rotationMax : 0.0F;
    }

    public void playSound(Parts.Sound sound) {
        if (!sound.name().isEmpty() && !this.parent.level().isClientSide) {
            this.parent.playSound(ModSounds.getSound(sound.name()), sound.volume(), sound.pitch());
        }
    }

    /** A sound a part plays as it moves; an empty name plays nothing. */
    public record Sound(String name, float volume, float pitch) {

        /** Plays nothing. */
        public static final Sound NONE = new Sound("", 1.0F, 1.0F);
    }
}
