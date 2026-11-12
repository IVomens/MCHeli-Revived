package com.ivomens.mcheli.livery.client;

import com.ivomens.mcheli.livery.net.LiveryImageChunkMessage;
import com.ivomens.mcheli.livery.net.LiveryListMessage;
import com.ivomens.mcheli.livery.net.LiveryUploadStatusMessage;
import com.ivomens.mcheli.networking.MessageRegistry;

/** Client handlers of the livery messages. */
public final class LiveryClientMessages {

    private LiveryClientMessages() {
    }

    /** @param client the client's livery state, which the replies update */
    public static void register(MessageRegistry registry, LiveryClient client) {
        registry.onClient(LiveryListMessage.TYPE, client::onList);
        registry.onClient(LiveryUploadStatusMessage.TYPE, client::onUploadStatus);
        registry.onClient(LiveryImageChunkMessage.TYPE, LiveryTextures::onChunk);
    }
}
