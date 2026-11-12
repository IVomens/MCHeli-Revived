package com.ivomens.mcheli.equipment.launcher.net;

import com.ivomens.mcheli.networking.MessageRegistry;

/** The handheld launchers' messages. */
public final class LightWeaponMessages {

    private LightWeaponMessages() {
    }

    public static void register(MessageRegistry registry) {
        registry.serverbound(LauncherFireMessage.TYPE, new LauncherFireHandler());
        registry.serverbound(LauncherReloadMessage.TYPE, new LauncherReloadHandler());
        registry.serverbound(NightVisionMessage.TYPE, new NightVisionHandler());
    }
}
