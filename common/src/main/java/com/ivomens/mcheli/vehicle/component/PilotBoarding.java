package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.registry.McheliCriteria;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;

/**
 * A player taking the pilot's seat: the canopy closes, the radar, camera and pilot weapon start over,
 * the server seats nearby mobs as crew, the client sends its settings, and the vehicle type reacts
 * in its own way ({@link #onBoard}).
 */
public final class PilotBoarding implements VehicleComponent {

    private final AircraftEntity vehicle;
    private final PartsComponent parts;
    private final RadarComponent radar;
    private final CameraComponent cameras;
    private final WeaponComponent weapons;
    private final CrewExitComponent crewExit;
    private final PilotPreferences preferences;
    private final PilotHandover handover;
    private Consumer<Player> onBoard = player -> {
    };

    public PilotBoarding(AircraftEntity vehicle, PartsComponent parts, RadarComponent radar, CameraComponent cameras,
            WeaponComponent weapons, CrewExitComponent crewExit, PilotPreferences preferences,
            PilotHandover handover) {
        this.vehicle = vehicle;
        this.parts = parts;
        this.radar = radar;
        this.cameras = cameras;
        this.weapons = weapons;
        this.crewExit = crewExit;
        this.preferences = preferences;
        this.handover = handover;
    }

    /** What the vehicle type does when a player has just boarded as pilot. */
    public void onBoard(Consumer<Player> hook) {
        this.onBoard = hook;
    }

    /** The player takes the pilot's seat (the server seats them; the client predicts it). */
    public void board(Player player, VehicleSpec info) {
        AircraftEntity ac = this.vehicle;
        this.parts.closeCanopy();
        this.handover.forgetPilot();
        this.radar.reset();
        if (ac.level().isClientSide) {
            this.preferences.send(0);
        } else {
            player.startRiding(ac);
            this.crewExit.mountNearbyMobs();
        }
        this.cameras.setCameraId(0);
        this.weapons.clearPilotSelection();
        if ("uh-1c".equalsIgnoreCase(info.name()) && player instanceof ServerPlayer serverPlayer) {
            McheliCriteria.RIDING_VALKYRIES.trigger(serverPlayer);
        }
        boarded(player);
    }

    /** The vehicle type's own reaction to a new pilot; the client's mouse handler calls it too. */
    public void boarded(Player player) {
        this.onBoard.accept(player);
    }
}
