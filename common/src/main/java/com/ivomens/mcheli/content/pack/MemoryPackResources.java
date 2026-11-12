package com.ivomens.mcheli.content.pack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only pack whose files are held in memory (generated recipe JSON).
 */
public class MemoryPackResources implements PackResources {

    private final PackLocationInfo location;
    private final PackType type;
    private final Map<ResourceLocation, byte[]> files;

    public MemoryPackResources(PackLocationInfo location, PackType type, Map<ResourceLocation, byte[]> files) {
        this.location = location;
        this.type = type;
        this.files = files;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        if (packType != this.type) {
            return null;
        }
        byte[] data = this.files.get(location);
        return data == null ? null : () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, ResourceOutput resourceOutput) {
        if (packType != this.type) {
            return;
        }
        String prefix = path.endsWith("/") ? path : path + "/";
        for (Map.Entry<ResourceLocation, byte[]> e : this.files.entrySet()) {
            ResourceLocation rl = e.getKey();
            if (rl.getNamespace().equals(namespace) && rl.getPath().startsWith(prefix)) {
                byte[] data = e.getValue();
                resourceOutput.accept(rl, () -> new ByteArrayInputStream(data));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType packType) {
        Set<String> set = new HashSet<>();
        if (packType == this.type) {
            for (ResourceLocation rl : this.files.keySet()) {
                set.add(rl.getNamespace());
            }
        }
        return set;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        if (deserializer == PackMetadataSection.TYPE) {
            return (T) new PackMetadataSection(Component.literal("MCHeli generated data"), 48, Optional.empty());
        }
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }

    @Override
    public void close() {}
}
