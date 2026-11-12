package com.ivomens.mcheli.apiservice.client;

import com.ivomens.mcheli.api.client.McheliClientApi;
import com.ivomens.mcheli.hud.client.widget.HudWidgetRegistry;

/**
 * MCHeli's implementation of the client API, found by {@code McheliClientApi.get()} through
 * {@code META-INF/services}. One instance for the whole game; it owns the registry of HUD widgets, which the HUD
 * reaches through {@link #widgets()}.
 */
public final class ClientApiService implements McheliClientApi {

    private final HudWidgetRegistry widgets = new HudWidgetRegistry();

    @Override
    public HudWidgetRegistry hudWidgets() {
        return this.widgets;
    }

    /** The HUD widgets of the running game's client API. */
    public static HudWidgetRegistry widgets() {
        return (HudWidgetRegistry) McheliClientApi.get().hudWidgets();
    }
}
