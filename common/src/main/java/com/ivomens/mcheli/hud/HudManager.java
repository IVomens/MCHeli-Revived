package com.ivomens.mcheli.hud;

import com.ivomens.mcheli.core.content.def.hud.HudProgram;
import com.ivomens.mcheli.content.registry.ContentRegistries;
import com.ivomens.mcheli.content.registry.ContentRegistry;
import com.ivomens.mcheli.content.registry.ContentType;

import java.util.Optional;

/** The loaded HUD scripts by name. Only a client reads HUD files; on a server the registry stays empty. */
public final class HudManager {

    private static final ContentRegistry<HudProgram, Hud> HUDS =
            new ContentRegistry<>(ContentType.HUD, HudProgram.class, Hud::of);

    private HudManager() {
    }

    /** Hands the HUD registry to the content pipeline; call once at start-up, before content loads. */
    public static void registerContent() {
        ContentRegistries.register(HUDS);
    }

    public static ContentRegistry<HudProgram, Hud> registry() {
        return HUDS;
    }

    public static Optional<Hud> get(String name) {
        return HUDS.get(name);
    }
}
