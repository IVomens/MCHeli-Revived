package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.net.WeaponCounters;
import com.ivomens.mcheli.combat.weapon.net.WeaponStateMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import com.ivomens.mcheli.vehicle.uav.UavControl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Applies one tick of crew input to an aircraft. The control handlers of the vehicle types extend
 * it and override the steps that differ; the steps run in a fixed order, which the golden
 * trajectories depend on.
 */
abstract class ControlInputProcessor<A extends AircraftEntity> {

    protected void process(A aircraft, ControlInput input, VehicleExtras extras, Player player) {
        ControlChanges changes = input.changes();
        ControlSwitches switches = input.switches();
        if (appliesDetachedAim()) {
            handleDetachedAim(aircraft, input.aim().sanitized(), player);
        }
        handleUnmount(aircraft, changes.unmount());
        if (switches.ejectSeat()) aircraft.ejectionSeats().ejectSeat(player);
        handleBladeFold(aircraft, extras.blades());
        handleVtolSwitch(aircraft, changes.vtol());
        handleModeSwitch(aircraft, changes.mode());
        if (switches.switchSearchLight()) aircraft.searchLight().setOn(!aircraft.searchLight().isOn());
        handleCameraMode(aircraft, input.view().camera(), player);
        handleWeaponSwitch(aircraft, input.selection().switchWeapon(), player);
        handleUseWeapon(aircraft, input, player);
        handlePilotControls(aircraft, switches, player);
        handleFlare(aircraft, input.selection().flareType(), switches);
        handleChain(aircraft, extras.unhitchChainId(), player);
        if (switches.openGui()) aircraft.boarding().openGui(player);
        handleHatch(aircraft, changes.hatch());
        if (input.view().freeLook() > 0) aircraft.modes().setFreeLook(input.view().freeLook() == 1);
        if (appliesRadar() && input.view().radar() > 0) {
            aircraft.radar().setOn(input.view().radar() == 1);
        }
        handleGear(aircraft, changes.gear());
        handleRack(aircraft, changes.rack());
        if (switches.switchGunnerStatus()) aircraft.seats().setGunnersAllowed(!aircraft.seats().gunnersAllowed());
    }

    /** Whether this vehicle type takes the pilot's detached turret aim from the input. */
    protected boolean appliesDetachedAim() {
        return true;
    }

    /** Whether this vehicle type lets the crew switch its radar. */
    protected boolean appliesRadar() {
        return true;
    }

    /** The aircraft controlled by this player: ridden directly, through a seat, or through a UAV station. */
    @Nullable
    protected static <T extends AircraftEntity> T resolveControlled(ServerPlayer player, Class<T> type) {
        Entity riding = player.getVehicle();
        Entity aircraft = null;
        if (riding instanceof AircraftEntity) {
            aircraft = riding;
        } else if (riding instanceof SeatEntity seat) {
            aircraft = seat.getParent();
        } else if (riding instanceof IUavStation uavStation) {
            aircraft = uavStation.getControlled();
        }
        return type.isInstance(aircraft) ? type.cast(aircraft) : null;
    }

    /**
     * Unmount request of a UAV station operator whose link is already gone (UAV destroyed, unloaded
     * or out of range). The input then resolves to no aircraft and would be dropped, while the
     * operator's client may still show the frozen feed and keep asking to leave; disconnect the
     * station directly instead.
     */
    protected static void handleStationUnmount(Player player, ControlInput input) {
        if (input.changes().unmount() == ControlInput.UnmountAction.UNMOUNT_SELF
                && player.getVehicle() instanceof IUavStation station) {
            UavControl.disconnect(station);
        }
    }

    /** Detached turret aim belongs to the pilot; input from other seats neither applies nor clears it. */
    private static void handleDetachedAim(AircraftEntity aircraft, DetachedAim aim, Player player) {
        if (!aircraft.isPilot(player)) return;
        if (aim.active() && aircraft.aim().supportsDetachedTurret()) {
            aircraft.aim().setDetached(aim.yaw(), aim.pitch());
        } else {
            aircraft.aim().clearDetached();
        }
    }

    /** Releasing a tow chain; only helicopters and ground vehicles send a chain id. */
    protected void handleChain(A aircraft, int chainId, Player player) {
    }

    /** Rotor blade folding; only helicopters. */
    protected void handleBladeFold(A aircraft, VehicleExtras.BladeStatus blades) {
    }

    /** VTOL mode; only planes and ships. */
    protected void handleVtolSwitch(A aircraft, ControlInput.VtolSwitch vtol) {
    }

    private static void handleUnmount(AircraftEntity aircraft, ControlInput.UnmountAction unmount) {
        switch (unmount) {
            case UNMOUNT_SELF -> aircraft.pilotHandover().pilotLeaves();
            case UNMOUNT_CREW -> aircraft.crewExit().toggleCrewDrop();
            case UNMOUNT_AIRCRAFT -> aircraft.carrier().leave();
            case NONE -> {
            }
        }
    }

    private static void handleModeSwitch(AircraftEntity aircraft, ControlInput.ModeSwitch mode) {
        switch (mode) {
            case GUNNER_OFF -> aircraft.modes().switchGunnerMode(false);
            case GUNNER_ON -> aircraft.modes().switchGunnerMode(true);
            case HOVERING_OFF -> aircraft.modes().switchHoveringMode(false);
            case HOVERING_ON -> aircraft.modes().switchHoveringMode(true);
            case NONE -> {
            }
        }
    }

    private static void handleCameraMode(AircraftEntity aircraft, ControlInput.CameraMode camera, Player player) {
        if (camera != ControlInput.CameraMode.NORMAL) {
            aircraft.cameras().switchMode(player, camera.ordinal());
        }
    }

    private static void handleWeaponSwitch(AircraftEntity aircraft, byte weaponId, Player player) {
        if (weaponId >= 0 && weaponId < aircraft.weapons().count()) {
            aircraft.weapons().select(player, weaponId);
        }
    }

    private static void handleUseWeapon(AircraftEntity aircraft, ControlInput input, Player player) {
        if (input.switches().reload()) {
            aircraft.ammoSupply().manualReload(player);
        }
        if (!input.switches().useWeapon()) {
            return;
        }
        // A malformed or malicious client cannot fire weapons from an arbitrary position.
        WeaponUse use = input.weaponUse().sanitizedFor(aircraft.position());
        aircraft.aim().setPacketAim(player, use.userYaw(), use.userPitch());
        boolean fired;
        try {
            aircraft.weapons().aiming().updateRotations();
            fired = aircraft.weapons().fire(player, use.pos(), use.request());
        } finally {
            aircraft.aim().clearPacketAim();
        }
        if (!fired) {
            resyncCurrentWeapon(aircraft, player);
        }
    }

    /** A shot the client predicted was refused: send the real weapon state back so the HUD corrects itself. */
    private static void resyncCurrentWeapon(AircraftEntity aircraft, Player player) {
        int seatId = aircraft.seats().seatIdOf(player);
        int weaponId = aircraft.weapons().currentId(player);
        if (player instanceof ServerPlayer serverPlayer && aircraft.seats().isValidSeatId(seatId) && weaponId >= 0) {
            WeaponGroup weapon = aircraft.weapons().get(weaponId);
            WeaponCounters counters = new WeaponCounters((short) weapon.ammo(), (short) weapon.reserveAmmo(),
                    (short) weapon.cooldown(), (short) weapon.reloadTicks());
            Network.sendToPlayer(serverPlayer, new WeaponStateMessage(aircraft.getId(), seatId, weaponId, counters));
        }
    }

    protected void handlePilotControls(A aircraft, ControlSwitches switches, Player player) {
        if (!aircraft.isPilot(player)) return;
        aircraft.keys().holdThrottleUp(switches.throttleUp());
        aircraft.keys().holdThrottleDown(switches.throttleDown());
        aircraft.keys().holdMoveLeft(switches.moveLeft());
        aircraft.keys().holdMoveRight(switches.moveRight());
    }

    private static void handleFlare(AircraftEntity aircraft, byte flareType, ControlSwitches switches) {
        if (flareType > 0) aircraft.countermeasures().useFlare(flareType);
        if (switches.useChaff()) aircraft.countermeasures().useChaff();
        if (switches.useAPS()) aircraft.countermeasures().useAPS();
        if (switches.useECMJammer()) aircraft.countermeasures().useECMJammer();
    }

    protected void handleHatch(A aircraft, ControlInput.HatchSwitch hatch) {
        if (hatch != ControlInput.HatchSwitch.NONE && hasHatch(aircraft)) {
            aircraft.parts().foldHatch(hatch == ControlInput.HatchSwitch.UNFOLD);
        }
    }

    protected static boolean hasHatch(AircraftEntity aircraft) {
        return aircraft.spec() != null && aircraft.spec().parts().haveHatch();
    }

    private static void handleGear(AircraftEntity aircraft, ControlInput.GearSwitch gear) {
        switch (gear) {
            case FOLD -> aircraft.parts().foldLandingGear();
            case UNFOLD -> aircraft.parts().unfoldLandingGear();
            case NONE -> {
            }
        }
    }

    private static void handleRack(AircraftEntity aircraft, ControlInput.RackAction rack) {
        switch (rack) {
            case MOUNT -> aircraft.racks().loadRacks();
            case UNMOUNT -> aircraft.racks().unloadRack();
            case RIDE -> aircraft.racks().rideNearbyRack();
            case NONE -> {
            }
        }
    }
}
