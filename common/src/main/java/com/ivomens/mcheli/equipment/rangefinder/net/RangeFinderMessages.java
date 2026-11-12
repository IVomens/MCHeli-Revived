package com.ivomens.mcheli.equipment.rangefinder.net;

import com.ivomens.mcheli.networking.MessageRegistry;

/** The range finder's message: a spot request through the raised scope. */
public final class RangeFinderMessages {

    private RangeFinderMessages() {
    }

    public static void register(MessageRegistry registry) {
        registry.serverbound(SpotRequestMessage.TYPE, new SpotRequestHandler());
    }
}
