package com.ivomens.mcheli.block.net;

import com.ivomens.mcheli.networking.MessageRegistry;

/** The drafting table screen's messages. */
public final class DraftingTableMessages {

    private DraftingTableMessages() {
    }

    public static void register(MessageRegistry registry) {
        registry.serverbound(DraftingTableCraftMessage.TYPE, new DraftingTableCraftHandler());
    }
}
