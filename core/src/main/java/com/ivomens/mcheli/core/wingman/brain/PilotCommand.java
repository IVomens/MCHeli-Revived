package com.ivomens.mcheli.core.wingman.brain;

import java.util.List;
import javax.annotation.Nullable;

/**
 * What the brain did to the controls during one decision, ready to be replayed onto the aircraft by
 * its scripted pilot. Untouched controls are null.
 *
 * <p>Throttle settings are kept in the order they were made rather than only the last one: the engine
 * wakes a resting vehicle on any opening of the throttle and a damaged engine never opens further,
 * so the sequence matters.
 *
 * @param throttleSettings every throttle setting, in order
 * @param motion           per axis, the motion last set; null axes keep the aircraft's own motion
 */
public record PilotCommand(List<Double> throttleSettings, @Nullable Boolean nozzleHover, @Nullable Float yaw,
                           @Nullable Float pitch, @Nullable Float roll, @Nullable Double motionX,
                           @Nullable Double motionY, @Nullable Double motionZ, @Nullable Point position,
                           @Nullable Float weaponYaw, @Nullable Float weaponPitch, @Nullable Float pilotYaw,
                           @Nullable Boolean gearFolded) {

    public PilotCommand {
        throttleSettings = List.copyOf(throttleSettings);
    }

    public boolean isEmpty() {
        return equals(RecordedControls.untouched());
    }

    /** Makes the same changes to the aircraft, in a fixed order; the controls are independent of each other. */
    public void replayOn(Controls aircraft) {
        for (double throttle : this.throttleSettings) {
            aircraft.setThrottle(throttle);
        }
        if (this.nozzleHover != null) {
            aircraft.switchNozzle(this.nozzleHover);
        }
        if (this.yaw != null) {
            aircraft.setYaw(this.yaw);
        }
        if (this.pitch != null) {
            aircraft.setPitch(this.pitch);
        }
        if (this.roll != null) {
            aircraft.setRoll(this.roll);
        }
        replayMotion(aircraft);
        if (this.position != null) {
            aircraft.setPosition(this.position.x(), this.position.y(), this.position.z());
        }
        if (this.weaponYaw != null) {
            aircraft.aimWeapons(this.weaponYaw);
        }
        if (this.weaponPitch != null) {
            aircraft.aimWeaponsPitch(this.weaponPitch);
        }
        if (this.pilotYaw != null) {
            aircraft.turnPilot(this.pilotYaw);
        }
        if (this.gearFolded != null) {
            aircraft.setGear(this.gearFolded);
        }
    }

    private void replayMotion(Controls aircraft) {
        if (this.motionX == null && this.motionY == null && this.motionZ == null) {
            return;
        }
        Point own = aircraft.motion();
        aircraft.setMotion(this.motionX != null ? this.motionX : own.x(), this.motionY != null ? this.motionY : own.y(),
                this.motionZ != null ? this.motionZ : own.z());
    }
}
