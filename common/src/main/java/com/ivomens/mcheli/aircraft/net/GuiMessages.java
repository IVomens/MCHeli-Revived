package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.MessageRegistry;

/** Messages of the screens that have no feature of their own. */
public final class GuiMessages {

    private GuiMessages() {
    }

    public static void register(MessageRegistry registry) {
        registry.serverbound(OpenScreenMessage.TYPE, new OpenScreenHandler());
    }
}
