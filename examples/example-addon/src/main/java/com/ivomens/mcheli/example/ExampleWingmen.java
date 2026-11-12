package com.ivomens.mcheli.example;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.wingman.WingmanOrderResult;
import com.ivomens.mcheli.api.wingman.WingmanState;
import com.ivomens.mcheli.api.wingman.Wingmen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Wingman orders through the API: send every wingman of the player's formation after one target, as a
 * "attack my target" key or a scripted mission of an addon would.
 */
public final class ExampleWingmen {

    private ExampleWingmen() {
    }

    /**
     * Server thread. Orders every wingman following the aircraft the player flies to attack the target.
     *
     * @return how many wingmen took the order
     */
    public static int attackWithFormation(ServerPlayer player, Entity target) {
        Wingmen wingmen = McheliApi.get().wingmen();
        int ordered = 0;
        for (WingmanState wingman : wingmen.following(player.getVehicle())) {
            if (wingmen.attack(player, wingman.aircraftId(), target) == WingmanOrderResult.DONE) {
                ordered++;
            }
        }
        return ordered;
    }
}
