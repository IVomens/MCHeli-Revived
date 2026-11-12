package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.platform.LocalClient;
import net.minecraft.world.entity.Entity;

/**
 * Crew taking and leaving the seats behind the pilot's: a crew member who sits down gets their weapon
 * (and takes it over from the pilot), the gunner view switches, and one who gets up is put at the
 * seat's exit.
 */
public final class SeatChanges implements VehicleComponent {

    private final AircraftEntity vehicle;
    private final SeatsComponent seats;
    private final FlightModesComponent modes;
    private final WeaponComponent weapons;
    private final CameraComponent cameras;
    private final CrewExitComponent crewExit;
    private final PilotPreferences preferences;

    public SeatChanges(AircraftEntity vehicle, SeatsComponent seats, FlightModesComponent modes,
            WeaponComponent weapons, CameraComponent cameras, CrewExitComponent crewExit,
            PilotPreferences preferences) {
        this.vehicle = vehicle;
        this.seats = seats;
        this.modes = modes;
        this.weapons = weapons;
        this.cameras = cameras;
        this.crewExit = crewExit;
        this.preferences = preferences;
    }

    /**
     * Someone took a seat: select their weapon, and if they took over the weapon the pilot had
     * selected, move the pilot on to the next one.
     */
    public void onSeatTaken(SeatEntity seat, Entity entity) {
        if (seat == null) {
            return;
        }
        AircraftEntity ac = this.vehicle;
        boolean onClient = ac.level().isClientSide;
        if (onClient && LocalClient.get().player() == entity) {
            this.modes.setGunnerFreeLook(false);
        }
        WeaponComponent weapons = this.weapons;
        weapons.selectFirst(entity);

        Entity pilot = ac.getRiddenByEntity();
        VehicleSpec info = ac.spec();
        int seatId = this.seats.seatIdOf(entity);
        if (seatId == 1 && (info == null || !info.file().features().concurrentGunner())) {
            this.modes.switchGunnerMode(false);
        }
        if (seatId > 0) {
            this.modes.enterGunnerViewOtherSeat();
        }

        if (pilot != null && info != null) {
            int pilotWeaponId = weapons.currentId(pilot);
            VehicleParts.Weapon mount = info.armament().getWeaponById(pilotWeaponId);
            if (mount != null && weapons.seatOf(weapons.infoById(pilotWeaponId), mount) == seatId) {
                int next = weapons.nextId(pilot, 1);
                if (next >= 0) {
                    weapons.select(pilot, next);
                }
            }
        }

        if (onClient) {
            this.preferences.send(seatId);
        }
    }

    public void onSeatLeft(SeatEntity seat, Entity entity) {
        this.cameras.camera().initCamera(this.seats.seatIdOf(entity), entity);
        SeatInfo seatInfo = this.seats.info(seat.seatID() + 1);
        if (seatInfo != null) {
            this.crewExit.setUnmountPosition(entity, seatInfo);
        }
        if (!this.seats.hasPlayerAboard()) {
            this.modes.switchGunnerMode(false);
            this.modes.switchHoveringMode(false);
        }
    }
}
