package com.ivomens.mcheli.vehicle.client.input;

import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.config.HandlingSettings;
import net.minecraft.client.Minecraft;

/** Reads the optional stick keys (see {@link HandlingSettings}) once per frame; none while a screen is open. */
final class StickKeyReader {

    private final Minecraft mc;

    StickKeyReader(Minecraft mc) {
        this.mc = mc;
    }

    PilotControls.StickKeys read(HandlingSettings handling) {
        if (!handling.hasStickKeys() || this.mc.screen != null) {
            return PilotControls.StickKeys.NONE;
        }
        return new PilotControls.StickKeys(InputKey.isKeyDown(handling.keyPitchUp()),
                InputKey.isKeyDown(handling.keyPitchDown()), InputKey.isKeyDown(handling.keyRollLeft()),
                InputKey.isKeyDown(handling.keyRollRight()), InputKey.isKeyDown(handling.keyYawLeft()),
                InputKey.isKeyDown(handling.keyYawRight()));
    }
}
