package com.ivomens.mcheli.setup.client;

import com.ivomens.mcheli.client.input.ClientTickHandler;
import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.multiplay.client.MultiplayManagerScreen;
import com.ivomens.mcheli.vehicle.client.camera.VehicleCamera;
import net.minecraft.client.Minecraft;

/**
 * Keys that work regardless of the vehicle: the third person camera distance, the team list and the
 * multiplayer menu. It also passes new key settings on to every tick handler.
 */
final class GlobalKeys {

    private static final int CAM_DIST_STEP = 4;
    private static final int CAM_DIST_MIN = 4;
    private static final int CAM_DIST_MAX = 72;

    private final Minecraft mc;
    private final ClientTickHandler[] handlers;
    private final VehicleCamera camera;
    private InputKey[] keys = new InputKey[0];
    private InputKey keyCamDistUp;
    private InputKey keyCamDistDown;
    private InputKey keyScoreboard;
    private InputKey keyMultiplayManager;
    private KeySettings bindings;
    private boolean showsTeamList;

    GlobalKeys(Minecraft mc, ClientTickHandler[] handlers, VehicleCamera camera) {
        this.mc = mc;
        this.handlers = handlers;
        this.camera = camera;
    }

    /** Rebinds the keys when the config's bindings changed, for example after {@code /mcheli reconfig}. */
    void followConfig(KeySettings bindings) {
        if (!bindings.equals(this.bindings)) {
            updateKeybind(bindings);
        }
    }

    /** Rebinds these keys and those of every tick handler from the config. */
    void updateKeybind(KeySettings bindings) {
        this.bindings = bindings;
        this.keyCamDistUp = new InputKey(bindings.cameraDistUp());
        this.keyCamDistDown = new InputKey(bindings.cameraDistDown());
        this.keyScoreboard = new InputKey(bindings.scoreboard());
        this.keyMultiplayManager = new InputKey(bindings.multiplayManager());
        this.keys = new InputKey[]{this.keyCamDistUp, this.keyCamDistDown, this.keyScoreboard, this.keyMultiplayManager};

        for (ClientTickHandler handler : this.handlers) {
            handler.updateKeybind(bindings);
        }
    }

    /** Whether the team list key is held, so the list is drawn. */
    boolean showsTeamList() {
        return this.showsTeamList;
    }

    void onTick() {
        for (InputKey key : this.keys) {
            key.update();
        }
        if (this.mc.player != null && this.mc.screen == null) {
            handleCameraDistance();
            handleScoreboardAndMultiplayer();
        }
    }

    private void handleScoreboardAndMultiplayer() {
        // Team play needs a server with other players; in single player only for testing.
        if (this.mc.hasSingleplayerServer() && !McheliConfig.debug().debugLog()) return;

        this.showsTeamList = this.keyScoreboard.isKeyPress();
        if (!this.showsTeamList && this.keyMultiplayManager.isKeyDown()) {
            this.mc.setScreen(new MultiplayManagerScreen());
        }
    }

    private void handleCameraDistance() {
        if (!McheliConfig.server().enableCamDistChange()) return;

        boolean up = this.keyCamDistUp.isKeyDown();
        boolean down = this.keyCamDistDown.isKeyDown();
        if (!up && !down) return;

        int distance = (int) this.camera.thirdPersonDistance();
        if (up) {
            distance = Math.min(distance + CAM_DIST_STEP, CAM_DIST_MAX);
        } else {
            distance = Math.max(distance - CAM_DIST_STEP, CAM_DIST_MIN);
        }
        this.camera.setThirdPersonDistance(distance);
    }
}
