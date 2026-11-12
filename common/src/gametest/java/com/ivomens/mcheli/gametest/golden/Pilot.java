package com.ivomens.mcheli.gametest.golden;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.net.ClientSettingsHandler;
import com.ivomens.mcheli.aircraft.net.ClientSettingsMessage;
import com.ivomens.mcheli.config.McheliConfig;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * A mock player who boards the vehicle like a player clicking on it, then drives it only through
 * the packets a client sends.
 * <p>
 * Control packets are edge triggered, as on a client: one goes out when a key changes or a switch
 * is pressed. The client's keep-alive resend repeats the same keys and changes nothing.
 */
final class Pilot {

    /** A pilot who still is not aboard after this many clicks makes the script invalid. */
    private static final int MAX_BOARDING_CLICKS = 40;

    private final ServerPlayer player;
    private int clicks;
    private boolean settingsSent;
    private int lastKeys;

    private Pilot(ServerPlayer player) {
        this.player = player;
    }

    static Pilot spawn(GameTestHelper helper) {
        return new Pilot(helper.makeMockServerPlayerInLevel());
    }

    boolean isAboard(AircraftEntity vehicle) {
        return this.player.getVehicle() == vehicle;
    }

    /** One click on the vehicle from right beside it; the first click may only open the canopy. */
    void clickToBoard(AircraftEntity vehicle) {
        if (++this.clicks > MAX_BOARDING_CLICKS) {
            throw new IllegalStateException("pilot could not board after " + MAX_BOARDING_CLICKS + " clicks");
        }
        this.player.moveTo(vehicle.getX() + 1.0, vehicle.getY(), vehicle.getZ());
        vehicle.boarding().interact(this.player, InteractionHand.MAIN_HAND);
    }

    /** Sends this tick's input the way the pilot's client would, then turns the vehicle. */
    void fly(AircraftEntity vehicle, PilotInput input, AttitudeDriver.ClientFrames frames) {
        if (!this.settingsSent) {
            sendClientSettings();
            this.settingsSent = true;
        }
        if (input.keys() != this.lastKeys || !input.actions().isEmpty()) {
            ControlMessages.deliver(vehicle, input, this.player);
            this.lastKeys = input.keys();
        }
        AttitudeDriver.apply(vehicle, this.player, input, frames);
    }

    /** What the client reports in ClientSettingsMessage on its first tick aboard, from the pinned config. */
    private void sendClientSettings() {
        ClientSettingsMessage settings = new ClientSettingsMessage(McheliConfig.vehicles().dismountAll(),
                McheliConfig.controls().autoThrottleDownHeli(), McheliConfig.controls().autoThrottleDownPlane(),
                McheliConfig.controls().autoThrottleDownTank(), false);
        new ClientSettingsHandler().handle(settings, this.player);
    }

    void leave() {
        this.player.stopRiding();
        this.player.server.getPlayerList().remove(this.player);
    }
}
