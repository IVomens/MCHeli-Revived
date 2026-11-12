package com.ivomens.mcheli.multiplay.client;

import net.minecraft.network.chat.Component;

/** Where this client shows the titles the server sends ({@code /mcheli title}). */
@FunctionalInterface
public interface TitleDisplay {

    /**
     * @param showTime how long to show it, in ticks
     * @param position the screen row (see {@link TitleOverlay})
     */
    void show(Component text, int showTime, int position);
}
