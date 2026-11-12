package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Watches the pilot seat from tick to tick: a new pilot's first tick (they look where the last pilot
 * looked, and their client sends its settings) and the pilot leaving (the controls reset, and the
 * pilot is put at the exit, with the rest of the crew if they asked for it).
 */
public final class PilotHandover implements VehicleComponent {

    private final AircraftEntity vehicle;
    private final SeatsComponent seats;
    private final CrewAimComponent aim;
    private final ControlKeysComponent keys;
    private final FlightModesComponent modes;
    private final CameraComponent cameras;
    private final CrewExitComponent crewExit;
    private final PilotPreferences preferences;
    /** The pilot as of the end of the last tick. */
    @Nullable
    private Entity lastPilot;
    private Runnable onPilotFirstTick = () -> {
    };

    public PilotHandover(AircraftEntity vehicle, SeatsComponent seats, CrewAimComponent aim, ControlKeysComponent keys,
            FlightModesComponent modes, CameraComponent cameras, CrewExitComponent crewExit,
            PilotPreferences preferences) {
        this.vehicle = vehicle;
        this.seats = seats;
        this.aim = aim;
        this.keys = keys;
        this.modes = modes;
        this.cameras = cameras;
        this.crewExit = crewExit;
        this.preferences = preferences;
    }

    /** What the vehicle type does on a new pilot's first tick aboard. */
    public void onPilotFirstTick(Runnable hook) {
        this.onPilotFirstTick = hook;
    }

    /** True on the tick a pilot took the controls. */
    public boolean pilotJustBoarded() {
        return this.lastPilot == null && this.vehicle.getRiddenByEntity() != null;
    }

    /** A pilot is boarding: their first tick is a new pilot's, whoever flew before. */
    void forgetPilot() {
        this.lastPilot = null;
    }

    /** A new pilot's first tick: they look where the last pilot looked, and the client sends its settings. */
    public void tickPilotBoarded() {
        if (!pilotJustBoarded()) {
            return;
        }
        AircraftEntity ac = this.vehicle;
        Entity pilot = ac.getRiddenByEntity();
        if (ac.level().isClientSide && LocalClient.get().isLocalPlayer(pilot)) {
            this.preferences.send(0);
        }
        pilot.setYRot(this.aim.riderYaw());
        pilot.setXRot(this.aim.riderPitch());
        if (ac.spec() != null) {
            this.modes.predictFreeLook(ac.spec().file().camera().defaultFreeLook());
        }
        this.onPilotFirstTick.run();
    }

    public void tickPilotLeft() {
        if (this.vehicle.getRiddenByEntity() == null && this.lastPilot != null) {
            pilotLeaves();
        }
    }

    /** End of the tick: whoever flies now is the pilot the next tick compares against. */
    public void rememberPilot() {
        this.lastPilot = this.vehicle.getRiddenByEntity();
    }

    /** The pilot left (or is being removed): reset the controls and put them at the exit. */
    public void pilotLeaves() {
        AircraftEntity ac = this.vehicle;
        if (!this.seats.hasPlayerAboard()) {
            this.modes.switchHoveringMode(false);
        }
        this.keys.releaseAll();

        Entity pilot = ac.getRiddenByEntity();
        if (pilot != null) {
            this.cameras.camera().initCamera(0, pilot);
            if (!ac.level().isClientSide) {
                pilot.stopRiding();
            }
        } else if (this.lastPilot != null) {
            pilot = this.lastPilot;
            if (pilot instanceof Player) {
                this.cameras.camera().initCamera(0, pilot);
            }
        }

        if (!this.seats.hasPlayerAboard()) {
            this.modes.switchGunnerMode(false);
        }
        this.modes.setFreeLook(false);
        if (!ac.isUAV()) {
            this.crewExit.setUnmountPosition(pilot, this.seats.info(0));
        } else if (pilot != null && pilot.getVehicle() instanceof IUavStation) {
            pilot.stopRiding();
        }

        this.lastPilot = null;
        if (this.preferences.pilotSettings().dismountAll()) {
            this.crewExit.unmountCrew(false);
        }
    }
}
