package com.ivomens.mcheli.gametest.golden;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.net.ControlInput;
import com.ivomens.mcheli.aircraft.net.ControlInput.GearSwitch;
import com.ivomens.mcheli.aircraft.net.ControlInput.HatchSwitch;
import com.ivomens.mcheli.aircraft.net.ControlInput.ModeSwitch;
import com.ivomens.mcheli.aircraft.net.ControlInput.VtolSwitch;
import com.ivomens.mcheli.aircraft.net.ControlInputBuilder;
import com.ivomens.mcheli.aircraft.net.GroundVehicleControlHandler;
import com.ivomens.mcheli.aircraft.net.GroundVehicleControlMessage;
import com.ivomens.mcheli.aircraft.net.HelicopterControlHandler;
import com.ivomens.mcheli.aircraft.net.HelicopterControlMessage;
import com.ivomens.mcheli.aircraft.net.PlaneControlHandler;
import com.ivomens.mcheli.aircraft.net.PlaneControlMessage;
import com.ivomens.mcheli.aircraft.net.ShipControlHandler;
import com.ivomens.mcheli.aircraft.net.ShipControlMessage;
import com.ivomens.mcheli.aircraft.net.TankControlHandler;
import com.ivomens.mcheli.aircraft.net.TankControlMessage;
import com.ivomens.mcheli.aircraft.net.VehicleControlInputBuilder;
import com.ivomens.mcheli.aircraft.net.VehicleExtras.BladeStatus;
import com.ivomens.mcheli.gametest.golden.PilotInput.Action;
import com.ivomens.mcheli.gametest.golden.PilotInput.Key;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.ship.ShipEntity;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds the control message the pilot's client would send for a vehicle type, filled the way
 * {@code AircraftClientTickHandler} and the per-type client handlers fill it, and hands it to the
 * server handler of that type.
 */
final class ControlMessages {

    private ControlMessages() {
    }

    static void deliver(AircraftEntity vehicle, PilotInput input, ServerPlayer pilot) {
        switch (vehicle) {
            case HelicopterEntity heli -> {
                VehicleControlInputBuilder data = vehicleData(vehicle, input);
                HelicopterControlMessage message = new HelicopterControlMessage(data.build(), data.buildExtras());
                new HelicopterControlHandler().handle(message, pilot);
            }
            case ShipEntity ship ->
                    new ShipControlHandler().handle(new ShipControlMessage(input(vehicle, input)), pilot);
            case PlaneEntity plane ->
                    new PlaneControlHandler().handle(new PlaneControlMessage(input(vehicle, input)), pilot);
            case TankEntity tank ->
                    new TankControlHandler().handle(new TankControlMessage(input(vehicle, input)), pilot);
            case GroundVehicleEntity ground -> {
                VehicleControlInputBuilder data = vehicleData(vehicle, input);
                GroundVehicleControlMessage message = new GroundVehicleControlMessage(data.build(), data.buildExtras());
                new GroundVehicleControlHandler().handle(message, pilot);
            }
            default -> throw new IllegalArgumentException("no control message for " + vehicle);
        }
    }

    private static VehicleControlInputBuilder vehicleData(AircraftEntity vehicle, PilotInput input) {
        VehicleControlInputBuilder data = fill(vehicle, input, new VehicleControlInputBuilder());
        if (input.actions().contains(Action.FOLD_BLADES)) {
            data.setBladeStatus(BladeStatus.FOLD);
        } else if (input.actions().contains(Action.UNFOLD_BLADES)) {
            data.setBladeStatus(BladeStatus.UNFOLD);
        }
        return data;
    }

    private static ControlInput input(AircraftEntity vehicle, PilotInput input) {
        return fill(vehicle, input, new ControlInputBuilder()).build();
    }

    private static <T extends ControlInputBuilder> T fill(AircraftEntity vehicle, PilotInput input, T data) {
        // with brakes, the brake key releases the throttle keys, as handleMovementAndBraking does
        boolean brake = vehicle.keys().hasBrake() && input.holds(Key.BRAKE);
        data.setThrottleUp(!brake && input.holds(Key.THROTTLE_UP));
        data.setThrottleDown(!brake && input.holds(Key.THROTTLE_DOWN));
        data.setMoveLeft(input.holds(Key.LEFT));
        data.setMoveRight(input.holds(Key.RIGHT));
        data.setUseBrake(brake);
        for (Action action : input.actions()) {
            switch (action) {
                case GEAR_UP -> data.setSwitchGear(GearSwitch.FOLD);
                case GEAR_DOWN -> data.setSwitchGear(GearSwitch.UNFOLD);
                case VTOL_ON -> data.setSwitchVtol(VtolSwitch.VTOL_ON);
                case VTOL_OFF -> data.setSwitchVtol(VtolSwitch.VTOL_OFF);
                case HOVER_ON -> data.setSwitchMode(ModeSwitch.HOVERING_ON);
                case HOVER_OFF -> data.setSwitchMode(ModeSwitch.HOVERING_OFF);
                // the client asks for UNFOLD to fold the wings (see AbstractPlaneClientTickHandler)
                case FOLD_WINGS -> data.setSwitchHatch(HatchSwitch.UNFOLD);
                case UNFOLD_WINGS -> data.setSwitchHatch(HatchSwitch.FOLD);
                case FOLD_BLADES, UNFOLD_BLADES -> {
                    // helicopter only, see vehicleData
                }
            }
        }
        return data;
    }
}
