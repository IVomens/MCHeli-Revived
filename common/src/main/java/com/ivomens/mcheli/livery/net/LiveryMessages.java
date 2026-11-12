package com.ivomens.mcheli.livery.net;

import com.ivomens.mcheli.networking.MessageRegistry;

/** The livery screen's messages: listing, applying, deleting, uploading and downloading liveries. */
public final class LiveryMessages {

    private LiveryMessages() {
    }

    public static void register(MessageRegistry registry) {
        registry.serverbound(LiveryListRequestMessage.TYPE, new LiveryListRequestHandler());
        registry.serverbound(LiveryApplyMessage.TYPE, new LiveryApplyHandler());
        registry.serverbound(LiveryDeleteMessage.TYPE, new LiveryDeleteHandler());
        registry.serverbound(LiveryUploadBeginMessage.TYPE, new LiveryUploadBeginHandler());
        registry.serverbound(LiveryUploadChunkMessage.TYPE, new LiveryUploadChunkHandler());
        registry.serverbound(LiveryImageRequestMessage.TYPE, new LiveryImageRequestHandler());

        registry.clientbound(LiveryListMessage.TYPE);
        registry.clientbound(LiveryUploadStatusMessage.TYPE);
        registry.clientbound(LiveryImageChunkMessage.TYPE);
    }
}
