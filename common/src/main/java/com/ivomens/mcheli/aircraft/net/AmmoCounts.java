package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.net.AllAmmoCountsMessage;
import com.ivomens.mcheli.combat.weapon.net.AmmoCount;
import com.ivomens.mcheli.combat.weapon.net.AmmoCountMessage;
import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Network;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Tells clients how much ammunition an aircraft's weapons have. */
public final class AmmoCounts {

    private AmmoCounts() {
    }

    /** Every weapon of the aircraft, to {@code target}, or to every player aboard when it is null. */
    public static void sendAll(AircraftEntity aircraft, @Nullable Player target) {
        int count = Math.min(aircraft.weapons().count(), AllAmmoCountsMessage.MAX_WEAPONS);
        List<AmmoCount> counts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            counts.add(of(aircraft.weapons().get(i)));
        }
        send(aircraft, target, new AllAmmoCountsMessage(aircraft.getId(), counts));
    }

    /** One weapon of the aircraft, to {@code target}, or to every player aboard when it is null. */
    public static void send(AircraftEntity aircraft, @Nullable Player target, int weaponId) {
        AmmoCount count = of(aircraft.weapons().get(weaponId));
        send(aircraft, target, new AmmoCountMessage(aircraft.getId(), weaponId, count));
    }

    /** The counts as the client shows them; larger numbers wrap the way they always have. */
    private static AmmoCount of(WeaponGroup weapon) {
        return new AmmoCount((short) weapon.ammo(), (short) weapon.reserveAmmo());
    }

    private static void send(AircraftEntity aircraft, @Nullable Player target, ClientboundMessage message) {
        if (target != null) {
            Network.sendToPlayer(target, message);
            return;
        }
        for (int seatId = 0; seatId <= aircraft.seats().count(); seatId++) {
            if (aircraft.seats().riderOf(seatId) instanceof ServerPlayer player) {
                Network.sendToPlayer(player, message);
            }
        }
    }
}
