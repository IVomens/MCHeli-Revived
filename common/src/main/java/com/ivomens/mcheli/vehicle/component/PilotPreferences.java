package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.net.ClientSettingsMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.vehicle.settings.ClientPreferences;
import com.ivomens.mcheli.vehicle.settings.PilotSettings;

import java.util.function.Supplier;

/**
 * The pilot's own client settings, which change how the vehicle behaves (whether leaving takes the
 * crew out too, whether the throttle winds down by itself). A client sends its settings when it takes
 * a seat and keeps a copy for its own prediction; the server keeps what the pilot's client sent.
 */
public final class PilotPreferences implements VehicleComponent {

    private final CameraComponent cameras;
    private final Supplier<ClientPreferences> preferences;
    private PilotSettings pilotSettings = PilotSettings.DEFAULTS;

    /**
     * @param cameras     the cameras, told whether this client renders shaders
     * @param preferences this client's own preferences, from the vehicle's settings
     */
    public PilotPreferences(CameraComponent cameras, Supplier<ClientPreferences> preferences) {
        this.cameras = cameras;
        this.preferences = preferences;
    }

    /** The settings of whoever flies the vehicle; the defaults until their client sent its own. */
    public PilotSettings pilotSettings() {
        return this.pilotSettings;
    }

    /** Server: the pilot's client sent its settings. */
    public void setPilotSettings(PilotSettings settings) {
        this.pilotSettings = settings;
    }

    /** Client: sends this client's settings to the server, and keeps a copy for its own prediction. */
    public void send(int seatId) {
        ClientPreferences preferences = this.preferences.get();
        this.pilotSettings = preferences.pilot();
        this.cameras.camera().setShaderSupport(seatId, preferences.shaderSupport());
        Network.sendToServer(ClientSettingsMessage.of(preferences));
    }
}
