package com.ivomens.mcheli.livery.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** The key that opens the livery screen. Unbound by default; players pick one in Controls. */
public final class LiveryKeys {

    public static final KeyMapping OPEN_SCREEN = new KeyMapping("key.mcheli.livery", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "key.categories.mcheli");

    private LiveryKeys() {
    }

    static void handlePresses(LiveryClient client) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_SCREEN.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                openScreen(mc, client);
            }
        }
    }

    /** Opens the livery screen for the vehicle the player is in, or explains why it cannot. */
    public static void openScreen(Minecraft mc, LiveryClient client) {
        AircraftEntity aircraft = RiddenVehicle.of(mc.player);
        if (aircraft == null || aircraft.spec() == null) {
            mc.player.displayClientMessage(Component.translatable("mcheli.livery.not_in_vehicle"), true);
            return;
        }
        mc.setScreen(new LiveryScreen(aircraft, client));
    }
}
