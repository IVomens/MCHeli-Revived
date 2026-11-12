package com.ivomens.mcheli.sound;

import com.ivomens.mcheli.platform.Services;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * What the sound registry asks of the client while content loads: whether a sound file exists in the
 * resource packs, and to define the sounds content files declare so that events no {@code sounds.json}
 * covers still resolve. A dedicated server has no sounds and answers with the defaults.
 */
public interface ClientSounds {

    ClientSounds INSTANCE = Services.sided(ClientSounds.class, new ClientSounds() {
    });

    /** Whether {@code assets/<ns>/sounds/<path>.ogg} exists in the loaded resource packs. */
    default boolean hasOgg(ResourceLocation sound) {
        return false;
    }

    /** Defines a sound declared as a YAML entry of a content file. */
    default void define(ResourceLocation id, Map<String, Object> ymlEntry) {
    }

    /** Defines a sound declared by name in a content file. */
    default void define(ResourceLocation id, String location) {
    }
}
