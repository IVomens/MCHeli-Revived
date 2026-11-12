package com.ivomens.mcheli.example;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.event.SeatBoarded;
import com.ivomens.mcheli.api.vehicle.data.MutableVehicleData;
import com.ivomens.mcheli.api.vehicle.data.VehicleDataKey;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Keeps two values of its own on every vehicle, with the API's vehicle data: how many times a player took the
 * pilot's seat (synced, so a client could show it), and the name of the last such pilot (kept on the server).
 * Both are saved with the vehicle.
 */
public final class ExampleSorties {

    /** How many times a player took the pilot's seat; synced to the players who see the vehicle. */
    public static final VehicleDataKey<Integer> SORTIES = VehicleDataKey.of(id("sorties"), Codec.INT,
            ByteBufCodecs.VAR_INT, 0);
    /** The name of the last player who took the pilot's seat; the server keeps it to itself. */
    public static final VehicleDataKey<String> LAST_PILOT = VehicleDataKey.of(id("last_pilot"), Codec.STRING, "");

    private ExampleSorties() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ExampleAddon.MOD_ID, path);
    }

    /** Counts sorties from now on; call once, while the addon is constructed. */
    static void register(McheliApi api) {
        api.events().register(SeatBoarded.class, ExampleSorties::onSeatBoarded);
    }

    /**
     * Server thread: a player took the pilot's seat. The event also fires when a vehicle loads with its pilot
     * aboard, so this counts boardings rather than flights; good enough for an example.
     */
    private static void onSeatBoarded(SeatBoarded boarded) {
        if (boarded.seatId() == 0 && boarded.rider() instanceof Player pilot) {
            MutableVehicleData data = McheliApi.get().vehicleActions().control(boarded.vehicle()).data();
            data.update(SORTIES, sorties -> sorties + 1);
            data.set(LAST_PILOT, pilot.getGameProfile().getName());
        }
    }
}
