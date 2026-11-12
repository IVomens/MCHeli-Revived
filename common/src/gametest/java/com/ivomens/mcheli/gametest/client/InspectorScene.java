package com.ivomens.mcheli.gametest.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.setup.client.McheliClient;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * The vehicle inspector in the dev scene, after the other screens: each tab of the inspector on the boarded
 * AH-64, the export, and the compact overlay over the flight view, each photographed.
 */
final class InspectorScene {

    static final int FIRST_TICK = ScreensScene.LAST_SCREEN_TICK + 4;
    static final int LAST_TICK = FIRST_TICK + 76;

    private InspectorScene() {
    }

    static void onClientTick(Minecraft mc, int tick, AircraftEntity boarded) {
        if (boarded == null) {
            return;
        }
        switch (tick - FIRST_TICK) {
            case 0 -> McheliClient.openInspector(mc);
            case 8 -> capture(mc, "inspector_overview.png");
            case 10 -> press(mc, "Components");
            case 12 -> clickListRow(mc, 4);
            case 18 -> capture(mc, "inspector_components.png");
            case 20 -> press(mc, "Weapons");
            case 26 -> capture(mc, "inspector_weapons.png");
            case 28 -> press(mc, "Crew");
            case 34 -> capture(mc, "inspector_crew.png");
            case 36 -> press(mc, "Definition");
            case 42 -> capture(mc, "inspector_definition.png");
            case 44 -> press(mc, "Export JSON");
            case 48 -> capture(mc, "inspector_export.png");
            case 50 -> press(mc, "Overlay");
            case 60 -> capture(mc, "inspector_overlay.png");
            case 62 -> McheliClient.showInspectorOverlay(false);
            default -> {
            }
        }
    }

    /** Selects a row of the screen's list, the way a click does. */
    private static void clickListRow(Minecraft mc, int row) {
        if (mc.screen == null) {
            return;
        }
        for (var child : mc.screen.children()) {
            if (child instanceof com.ivomens.mcheli.ui.client.ListView<?> list) {
                list.mouseClicked(list.getX() + 4, list.getY() + row * 12 + 4, 0);
            }
        }
    }

    private static void press(Minecraft mc, String label) {
        if (mc.screen == null) {
            ModLogger.get().info("[inspector] no screen to press {} on", label);
            return;
        }
        for (var child : mc.screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && widget.active
                    && widget.getMessage().getString().equals(label)) {
                widget.onClick(widget.getX() + 1, widget.getY() + 1);
                ModLogger.get().info("[inspector] pressed {}", label);
                return;
            }
        }
        ModLogger.get().info("[inspector] no {} button on {}", label, mc.screen.getClass().getSimpleName());
    }

    private static void capture(Minecraft mc, String file) {
        ModLogger.get().info("[inspector] screen {} for {}", mc.screen == null ? null : mc.screen.getClass().getName(),
                file);
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), c -> {
        });
    }
}
