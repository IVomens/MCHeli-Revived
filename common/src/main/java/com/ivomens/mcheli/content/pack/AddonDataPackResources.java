package com.ivomens.mcheli.content.pack;

import com.ivomens.mcheli.content.recipe.LegacyItemIds;
import com.ivomens.mcheli.content.recipe.LegacyRecipePacks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server data view of an addon pack: the addon's own {@code data/} folder plus its legacy 1.12 recipes
 * ({@code assets/<ns>/recipes/*.json}) converted to {@code data/<ns>/recipe/*.json}. A recipe file the
 * addon ships in the 1.21 location takes precedence over the converted legacy one.
 */
public class AddonDataPackResources implements PackResources {

    private final PackResources base;
    private final String sourceName;
    private Map<ResourceLocation, byte[]> converted;

    public AddonDataPackResources(PackResources base, String sourceName) {
        this.base = base;
        this.sourceName = sourceName;
    }

    private Map<ResourceLocation, byte[]> converted() {
        if (this.converted == null) {
            this.converted = LegacyRecipePacks.convert(this.base, this.sourceName, LegacyItemIds::isRegistered);
        }
        return this.converted;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return this.base.getRootResource(elements);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        if (packType != PackType.SERVER_DATA) {
            return null;
        }
        IoSupplier<InputStream> own = this.base.getResource(packType, location);
        if (own != null) {
            return own;
        }
        byte[] data = this.converted().get(location);
        return data == null ? null : () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, ResourceOutput resourceOutput) {
        if (packType != PackType.SERVER_DATA) {
            return;
        }
        Set<ResourceLocation> seen = new HashSet<>();
        this.base.listResources(packType, namespace, path, (rl, sup) -> {
            seen.add(rl);
            resourceOutput.accept(rl, sup);
        });
        String prefix = path.endsWith("/") ? path : path + "/";
        for (Map.Entry<ResourceLocation, byte[]> e : this.converted().entrySet()) {
            ResourceLocation rl = e.getKey();
            if (rl.getNamespace().equals(namespace) && rl.getPath().startsWith(prefix) && !seen.contains(rl)) {
                byte[] data = e.getValue();
                resourceOutput.accept(rl, () -> new ByteArrayInputStream(data));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType packType) {
        if (packType != PackType.SERVER_DATA) {
            return Set.of();
        }
        Set<String> set = new HashSet<>(this.base.getNamespaces(PackType.SERVER_DATA));
        for (ResourceLocation rl : this.converted().keySet()) {
            set.add(rl.getNamespace());
        }
        return set;
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) throws IOException {
        try {
            return this.base.getMetadataSection(deserializer);
        } catch (Exception e) {
            // legacy pack.mcmeta files are not always valid for 1.21; the pack metadata is supplied separately
            return null;
        }
    }

    @Override
    public PackLocationInfo location() {
        return this.base.location();
    }

    @Override
    public void close() {
        this.base.close();
    }

    /**
     * Wraps a resources supplier so every opened instance is an {@link AddonDataPackResources}.
     */
    public record Supplier(Pack.ResourcesSupplier base, String sourceName) implements Pack.ResourcesSupplier {
        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            return new AddonDataPackResources(this.base.openPrimary(location), this.sourceName);
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            return new AddonDataPackResources(this.base.openPrimary(location), this.sourceName);
        }
    }
}
