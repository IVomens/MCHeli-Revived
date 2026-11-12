package com.ivomens.mcheli.world.uavstation.net;

import com.ivomens.mcheli.networking.MessageRegistry;

/** The UAV station screen's messages. */
public final class UavMessages {

    private UavMessages() {
    }

    public static void register(MessageRegistry registry) {
        registry.serverbound(UavConnectMessage.TYPE, new UavConnectHandler());
        registry.serverbound(UavDropMessage.TYPE, new UavDropHandler());
        registry.serverbound(UavSpawnOffsetMessage.TYPE, new UavSpawnOffsetHandler());
    }
}
