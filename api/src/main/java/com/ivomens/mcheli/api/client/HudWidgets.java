package com.ivomens.mcheli.api.client;

import com.ivomens.mcheli.api.event.Subscription;
import net.minecraft.resources.ResourceLocation;

/**
 * The widgets addons add to the in-vehicle HUD. Widgets are drawn after MCHeli's own seat HUD, in the modern HUD
 * and in the legacy (scripted) one, whenever that HUD itself is drawn: not with the GUI hidden (F1), not in
 * third person unless the player shows the HUD there, and not in a vehicle that is a wreck.
 *
 * <p>With no widget registered the HUD does exactly what it did before, and costs nothing more.
 *
 * <p><b>Side and thread.</b> Physical client only. {@link #register} and {@link Subscription#unsubscribe()}
 * work from any thread; a widget registered while a frame is drawn shows from the next frame on.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface HudWidgets {

    /**
     * Adds a widget to the HUD. Widgets are usually registered once, in the addon's client set-up; the returned
     * subscription removes the widget again.
     *
     * @param id     names the widget, with the addon's mod id as namespace. It orders widgets of the same
     *               {@link HudWidget#order() order} in one anchor, and names the widget in the log when it fails.
     * @param widget what to draw
     * @return removes the widget; it is not drawn in any frame that starts afterwards
     * @throws IllegalArgumentException when a widget with the same id is registered already
     */
    Subscription register(ResourceLocation id, HudWidget widget);
}
