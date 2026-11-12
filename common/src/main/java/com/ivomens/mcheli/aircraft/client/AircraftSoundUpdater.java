package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleClientHooks;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.sound.client.EntitySoundUpdater;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Looping engine, rotor or track sound of one aircraft.
 * <p>
 * The sound uses linear attenuation over 16 blocks; to make engines audible from far away (up to
 * {@code max(info.soundRange, VehicleSoundRange)} blocks), the sound source is placed on the line
 * from the listener to the aircraft, scaled down by {@code range / 16}. Riders hear it at the
 * aircraft itself. Passing aircraft get a little Doppler shift.
 */
public class AircraftSoundUpdater extends EntitySoundUpdater implements VehicleClientHooks.EngineSoundPlayer {

    private static final double ATTENUATION_DISTANCE = 16.0;
    private static final double DEFAULT_HEARING_RANGE = 256.0;
    private static final float MAX_DOPPLER_SHIFT = 0.2F;
    /** Ticks to wait before restarting a sound the engine stopped. */
    private static final int RESTART_DELAY_TICKS = 20;

    private final AircraftEntity aircraft;
    private final LocalPlayer player;
    private boolean isMoving;
    private boolean silent;
    private float pitch;
    private float volume;
    private float dopplerShift;
    private boolean isFirstUpdate = true;
    private double prevDistance;
    private int restartDelay = 0;

    public AircraftSoundUpdater(Minecraft mc, AircraftEntity aircraft, LocalPlayer player) {
        super(mc, aircraft);
        this.aircraft = aircraft;
        this.player = player;
    }

    private static double getHearingRange(VehicleSpec info) {
        double configured = McheliConfig.vehicles().soundRange();
        return Math.max(ATTENUATION_DISTANCE, Math.max(info.file().sound().range(), configured));
    }

    @Override
    public void update() {
        if (this.aircraft.sound().name().isEmpty() || this.aircraft.spec() == null || this.player == null) {
            return;
        }
        if (this.isFirstUpdate) {
            this.isFirstUpdate = false;
            this.initEntitySound(this.aircraft.sound().name());
        }

        VehicleSpec info = this.aircraft.spec();
        boolean wasMoving = this.isMoving;
        if (this.aircraft.isRemoved()) {
            this.stopEntitySound();
            this.silent = true;
            return;
        }
        if (!this.silent && this.volume == 0.0F) {
            this.silent = true;
        }

        boolean isRiding = this.aircraft.seats().seatIdOf(this.player) >= 0;
        restartIfStopped();

        float prevVolume = this.volume;
        float prevPitch = this.pitch;
        followEngine(info, isRiding, wasMoving);

        if (!this.silent) {
            pushChanges(prevPitch, prevVolume);
        }

        if (this.volume > 0.0F) {
            updateSourcePosition(isRiding, info);
        } else if (this.isEntitySoundPlaying()) {
            this.stopEntitySound();
        }
    }

    /** A sound that should be heard but stopped (the sound engine dropped it) starts again after a delay. */
    private void restartIfStopped() {
        if (!this.isEntitySoundPlaying() && this.volume > 0.0F) {
            if (this.restartDelay > 0) {
                this.restartDelay--;
            } else {
                this.restartDelay = RESTART_DELAY_TICKS;
                this.playEntitySound(this.aircraft, this.volume, this.pitch);
            }
            this.silent = false;
        }
    }

    /** Volume and pitch follow the engine; the pitch also shifts with the Doppler effect for bystanders. */
    private void followEngine(VehicleSpec info, boolean isRiding, boolean wasMoving) {
        this.isMoving = info.file().sound().volume() * this.aircraft.sound().volume() >= 0.01;
        if (this.isMoving) {
            this.volume = info.file().sound().volume() * this.aircraft.sound().volume();
            this.pitch = info.file().sound().pitch() * this.aircraft.sound().pitch();
            if (!isRiding) {
                this.pitch += updateDopplerShift();
            }
            this.pitch = Math.max(this.pitch, 0.0F);
        } else if (wasMoving) {
            this.volume = 0.0F;
            this.pitch = 0.0F;
        }
    }

    private void pushChanges(float prevPitch, float prevVolume) {
        if (this.pitch != prevPitch) {
            this.setEntitySoundPitch(this.pitch);
        }
        if (this.volume != prevVolume) {
            this.setEntitySoundVolume(this.volume);
        }
    }

    /** Raises the pitch while the aircraft approaches and lowers it while it moves away. */
    private float updateDopplerShift() {
        double dx = this.aircraft.getX() - this.player.getX();
        double dz = this.aircraft.getZ() - this.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double approach = this.prevDistance - distance;
        if (Math.abs(approach) > 0.3) {
            this.dopplerShift = Mth.clamp((float) (this.dopplerShift + approach / 40.0), -MAX_DOPPLER_SHIFT,
                    MAX_DOPPLER_SHIFT);
        }
        this.dopplerShift = (float) (this.dopplerShift * 0.9);
        this.prevDistance = distance;
        return this.dopplerShift;
    }

    private void updateSourcePosition(boolean isRiding, VehicleSpec info) {
        if (isRiding) {
            this.updateSoundLocation(this.aircraft);
            return;
        }
        double x = this.player.getX();
        double y = this.player.getY();
        double z = this.player.getZ();
        double scale = getHearingRange(info) / ATTENUATION_DISTANCE;
        this.updateSoundLocation(x + (this.aircraft.getX() - x) / scale, y + (this.aircraft.getY() - y) / scale,
                z + (this.aircraft.getZ() - z) / scale);
    }
}
