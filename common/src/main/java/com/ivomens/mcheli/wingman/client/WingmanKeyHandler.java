package com.ivomens.mcheli.wingman.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.config.client.KeyNames;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.wingman.net.OpenWingmanPanelMessage;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** Wingman panel key binding and its HUD hint, shown while the player sits in an aircraft. */
public class WingmanKeyHandler {

    public static final KeyMapping KEY_PANEL = new KeyMapping(
            "key.wingman.panel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_0,
            "key.categories.wingman");

    /** Screen Y of the hint, roughly below MCHeli's own key hints on the right edge. */
    private static final int HINT_Y = 150;
    private static final String HINT_TEXT = " Wingman Panel";

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        if (!(mc.player.getVehicle() instanceof AircraftEntity)) {
            // Drop presses made outside an aircraft so they do not all fire on boarding.
            while (KEY_PANEL.consumeClick()) {
                // discard
            }
            return;
        }
        if (KEY_PANEL.consumeClick()) {
            Network.sendToServer(new OpenWingmanPanelMessage());
        }
    }

    public static void onRenderOverlay(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        if (!(mc.player.getVehicle() instanceof AircraftEntity)) return;
        drawKeyHint(mc, graphics);
    }

    /** "[KEY] Wingman Panel", right-aligned in the style of MCHeli's key hints. */
    private static void drawKeyHint(Minecraft mc, GuiGraphics graphics) {
        Font font = mc.font;
        String key = "[" + keyName() + "]";
        int keyWidth = font.width(key);
        int totalWidth = keyWidth + font.width(HINT_TEXT);
        int x = mc.getWindow().getGuiScaledWidth() - totalWidth - 4;

        RenderSystem.enableBlend();
        graphics.fill(x - 2, HINT_Y - 1, x + totalWidth + 2, HINT_Y + font.lineHeight + 1, 0x88000000);
        graphics.drawString(font, key, x, HINT_Y, 0xFFFFAA00, true);
        graphics.drawString(font, HINT_TEXT, x + keyWidth, HINT_Y, 0xFFFFFFFF, true);
        RenderSystem.disableBlend();
    }

    private static String keyName() {
        String name = KeyNames.getDescOrName(KEY_PANEL);
        if (name != null && !name.isEmpty()) return name;
        return KEY_PANEL.getTranslatedKeyMessage().getString();
    }
}
