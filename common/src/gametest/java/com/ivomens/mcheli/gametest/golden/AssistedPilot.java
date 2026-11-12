package com.ivomens.mcheli.gametest.golden;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.assist.PilotAssist;
import com.ivomens.mcheli.config.HandlingSettings;
import com.ivomens.mcheli.config.McheliSettings;
import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.control.ThrottleCommand;
import com.ivomens.mcheli.gametest.golden.PilotInput.Key;
import com.ivomens.mcheli.vehicle.settings.AssistSettings;

/**
 * The handling assists of the pilot's client, played by the harness: each tick the scripted input
 * goes through {@link PilotAssist} as the client's {@code PilotControls} sends it through, so the
 * altitude hold presses the throttle keys and the attitude assists move the stick. The assists are
 * switched on here explicitly; the config is never read, so no other script is affected.
 * <p>
 * The client applies the attitude assists every frame; the harness applies them once per tick and
 * flies the frames of the tick with that stick.
 */
final class AssistedPilot implements Script.Assist {

    private final PilotAssist assist = new PilotAssist();
    private final AssistSettings settings;

    private AssistedPilot(AssistSettings settings) {
        this.settings = settings;
    }

    /** The helicopter assist: auto-level and altitude hold. */
    static AssistedPilot helicopter() {
        return new AssistedPilot(AssistSettings.of(
                McheliSettings.defaults().with(HandlingSettings.ASSISTED_HELI, true).handling()));
    }

    /** The planes' auto-rudder at its default gain. */
    static AssistedPilot autoRudder() {
        return new AssistedPilot(AssistSettings.of(
                McheliSettings.defaults().with(HandlingSettings.AUTO_RUDDER_PLANE, true).handling()));
    }

    @Override
    public PilotInput apply(AircraftEntity vehicle, PilotInput scripted) {
        boolean onThrottle = scripted.holds(Key.THROTTLE_UP) || scripted.holds(Key.THROTTLE_DOWN);
        ThrottleCommand throttle = this.assist.throttle(vehicle, this.settings.assistedHeli(), onThrottle);
        PilotInput input = scripted;
        if (throttle.up()) {
            input = input.withKey(Key.THROTTLE_UP);
        } else if (throttle.down()) {
            input = input.withKey(Key.THROTTLE_DOWN);
        }
        AttitudeInput stick = PilotAssist.stick(vehicle, new AttitudeInput(input.stickX(), input.stickY(),
                vehicle.modes().isFreeLook(), input.rudder()), this.settings);
        return input.withStickAndRudder(stick.stickX(), stick.stickY(), stick.rudder());
    }
}
