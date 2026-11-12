package com.ivomens.mcheli.vehicle.component;

import net.minecraft.world.entity.player.Player;

/**
 * Switches the crew's stations off when the vehicle is destroyed: the search light goes dark, hover
 * and gunner mode end, and every player aboard is put back to the normal camera view.
 */
public final class StationShutdown implements VehicleComponent {

    private final SeatsComponent seats;
    private final CameraComponent cameras;
    private final FlightModesComponent modes;
    private final SearchLightComponent searchLight;

    public StationShutdown(SeatsComponent seats, CameraComponent cameras, FlightModesComponent modes,
            SearchLightComponent searchLight) {
        this.seats = seats;
        this.cameras = cameras;
        this.modes = modes;
        this.searchLight = searchLight;
    }

    /** Both sides: the vehicle was destroyed (a client sees it by the vehicle's health). */
    public void shutDown() {
        this.searchLight.setOn(false);
        this.modes.switchHoveringMode(false);
        this.modes.switchGunnerMode(false);
        for (int seatId = 0; seatId < this.seats.count() + 1; seatId++) {
            if (this.seats.riderOf(seatId) instanceof Player player) {
                this.cameras.switchMode(player, 0);
            }
        }
    }
}
