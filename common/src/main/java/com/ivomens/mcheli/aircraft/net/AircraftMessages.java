package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.combat.weapon.net.AirburstDistanceMessage;
import com.ivomens.mcheli.combat.weapon.net.AmmoCountsRequestMessage;
import com.ivomens.mcheli.combat.weapon.net.BvrLockMessage;
import com.ivomens.mcheli.networking.MessageRegistry;

/**
 * Aircraft messages: the crew's control input for each vehicle type, the pilot's attitude report and
 * client settings, seats, weapon status, resupply, commands and content reloads.
 */
public final class AircraftMessages {

    private AircraftMessages() {
    }

    public static void register(MessageRegistry registry) {
        // Control input, sent every tick the crew presses something
        registry.serverbound(PlaneControlMessage.TYPE, new PlaneControlHandler());
        registry.serverbound(ShipControlMessage.TYPE, new ShipControlHandler());
        registry.serverbound(TankControlMessage.TYPE, new TankControlHandler());
        registry.serverbound(HelicopterControlMessage.TYPE, new HelicopterControlHandler());
        registry.serverbound(GroundVehicleControlMessage.TYPE, new GroundVehicleControlHandler());
        registry.serverbound(AttitudeReportMessage.TYPE, new AttitudeReportHandler());
        registry.serverbound(ClientSettingsMessage.TYPE, new ClientSettingsHandler());

        // Seats
        registry.serverbound(SwitchSeatMessage.TYPE, new SwitchSeatHandler());
        registry.serverbound(ParachuteMessage.TYPE, new ParachuteHandler());
        registry.serverbound(SeatListRequestMessage.TYPE, new SeatListRequestHandler());
        registry.clientbound(SeatListMessage.TYPE);

        // Weapons and the aircraft screen
        registry.serverbound(WeaponStatusRequestMessage.TYPE, new WeaponStatusRequestHandler());
        registry.clientbound(WeaponStatusMessage.TYPE);
        registry.serverbound(ResupplyMessage.TYPE, new ResupplyHandler());
        registry.serverbound(AircraftCommandMessage.TYPE, new AircraftCommandHandler());

        // The vehicle's side of combat messages (the messages are combat's)
        registry.serverbound(AmmoCountsRequestMessage.TYPE, new AmmoCountsRequestHandler());
        registry.serverbound(BvrLockMessage.TYPE, new BvrLockHandler());
        registry.serverbound(AirburstDistanceMessage.TYPE, new AirburstDistanceHandler());

        // Development aids
        registry.serverbound(ReloadVehicleContentMessage.TYPE, new ReloadVehicleContentHandler());
        registry.serverbound(ReloadWeaponContentMessage.TYPE, new ReloadWeaponContentHandler());
    }
}
