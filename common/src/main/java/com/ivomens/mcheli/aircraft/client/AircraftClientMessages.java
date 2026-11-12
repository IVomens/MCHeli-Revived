package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.net.SeatListMessage;
import com.ivomens.mcheli.aircraft.net.WeaponStatusMessage;
import com.ivomens.mcheli.networking.MessageRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.List;

/** Client handlers of the aircraft messages: seat entities and the weapons each seat has selected. */
public final class AircraftClientMessages {

    private AircraftClientMessages() {
    }

    public static void register(MessageRegistry registry) {
        registry.onClient(SeatListMessage.TYPE, AircraftClientMessages::linkSeats);
        registry.onClient(WeaponStatusMessage.TYPE, AircraftClientMessages::updateWeapons);
    }

    @Nullable
    private static Entity entity(int id) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && id >= 0 ? level.getEntity(id) : null;
    }

    /** Links the seat entities to their aircraft; a list that does not match the aircraft's seats is ignored. */
    private static void linkSeats(SeatListMessage message) {
        List<Integer> seatIds = message.seatIds();
        if (!(entity(message.aircraftId()) instanceof AircraftEntity aircraft) || seatIds.isEmpty()
                || aircraft.seats().all().length != seatIds.size()) {
            return;
        }
        for (int i = 0; i < seatIds.size(); i++) {
            if (entity(seatIds.get(i)) instanceof SeatEntity seat) {
                seat.setSeatID(i);
                seat.setParentUniqueID(aircraft.identity().uniqueId());
                aircraft.seats().setSeat(i, seat);
                seat.setParent(aircraft);
            }
        }
    }

    private static void updateWeapons(WeaponStatusMessage message) {
        if (entity(message.aircraftId()) instanceof AircraftEntity aircraft) {
            List<Byte> weaponIds = message.weaponIds();
            for (int seatId = 0; seatId < weaponIds.size(); seatId++) {
                aircraft.weapons().applySelection(seatId, weaponIds.get(seatId));
            }
        }
    }
}
