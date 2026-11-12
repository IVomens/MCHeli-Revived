package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.config.McheliConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * The mouse wheel while flying: it switches weapons instead of the hotbar slot. The scroll hook offers
 * each scroll with {@link #offer}; the vehicle's tick handler takes the pending scroll on its next tick.
 *
 * <p>One per client, owned by the client session; only the client thread touches it.
 */
public final class WeaponWheel {

    private int pending;

    /**
     * Takes the scroll over when the player's vehicle has another weapon to switch to.
     *
     * @param wheel scroll amount, one notch is +-120
     * @return true if the vanilla scroll handling should be cancelled
     */
    public boolean offer(int wheel) {
        if (wheel == 0 || !McheliConfig.controls().switchWeaponWithMouseWheel()) return false;
        this.pending = 0;

        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        AircraftEntity ac = RiddenVehicle.of(player);
        if (ac == null) return false;

        int currentWeapon = ac.weapons().idForSeat(ac.seats().seatIdOf(player));
        if (currentWeapon == ac.weapons().nextId(player, 1)) return false;
        this.pending = wheel;
        return true;
    }

    /** The scroll waiting to switch the weapon, 0 for none. */
    public int pending() {
        return this.pending;
    }

    /** The pending scroll switched the weapon. */
    public void consume() {
        this.pending = 0;
    }
}
