package com.ivomens.mcheli.wingman.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Client side of the wingman subsystem: the panel key mapping and the client tick and HUD hooks. */
public final class WingmanClientReg {

    private static final ClientAutopilotHandler AUTOPILOT = new ClientAutopilotHandler();

    private WingmanClientReg() {}

    public static List<KeyMapping> keyMappings() {
        return List.of(WingmanKeyHandler.KEY_PANEL);
    }

    /** The client keeps one autopilot follower for the whole session. */
    public static ClientAutopilotHandler autopilot() {
        return AUTOPILOT;
    }

    public static void onLoggingOut() {
        AUTOPILOT.clearOverrides();
    }

    public static void onClientTick() {
        WingmanKeyHandler.onClientTick();
        AUTOPILOT.onClientTick();
    }

    public static void onRenderGuiPost(GuiGraphics graphics) {
        WingmanKeyHandler.onRenderOverlay(graphics);
    }
}
