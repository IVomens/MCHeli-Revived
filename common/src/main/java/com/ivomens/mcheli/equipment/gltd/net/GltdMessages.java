package com.ivomens.mcheli.equipment.gltd.net;

import com.ivomens.mcheli.networking.MessageRegistry;

/** The laser designator operator's messages. */
public final class GltdMessages {

    private GltdMessages() {
    }

    public static void register(MessageRegistry registry) {
        registry.serverbound(GltdCameraModeMessage.TYPE, new GltdCameraModeHandler());
        registry.serverbound(GltdFireMessage.TYPE, new GltdFireHandler());
        registry.serverbound(GltdDismountMessage.TYPE, new GltdDismountHandler());
    }
}
