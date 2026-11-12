package com.ivomens.mcheli.sound.client;

import com.ivomens.mcheli.sound.ClientSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Map;

/** {@link ClientSounds} on a physical client: the client's resource packs and {@link ClientSoundRegistry}. */
public final class ClientSoundsService implements ClientSounds {

    /**
     * Content loads while the mod initialises, before the first resource reload: NeoForge has an empty
     * resource manager by then and Fabric none at all. Either way no sound file is found yet.
     */
    @Override
    public boolean hasOgg(ResourceLocation sound) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(sound.getNamespace(),
                "sounds/" + sound.getPath() + ".ogg");
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        return resources != null && resources.getResource(file).isPresent();
    }

    @Override
    public void define(ResourceLocation id, Map<String, Object> ymlEntry) {
        ClientSoundRegistry.INSTANCE.buildFromYML(id, ymlEntry);
    }

    @Override
    public void define(ResourceLocation id, String location) {
        ClientSoundRegistry.INSTANCE.buildFromString(id, location);
    }
}
