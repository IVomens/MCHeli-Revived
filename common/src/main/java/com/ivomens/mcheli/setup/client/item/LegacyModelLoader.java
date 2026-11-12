package com.ivomens.mcheli.setup.client.item;

import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.registry.ContentBindings;
import com.ivomens.mcheli.content.registry.ContentId;
import com.ivomens.mcheli.equipment.throwable.Throwables;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.state.StaticCache;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Generated item models for content items (aircraft, vehicles, throwables) defined by the content
 * packs at runtime: every such item gets {@code models/item/<name>.json} =
 * {@code item/generated} with {@code mcheli:items/<name>} as its texture.
 * <p>
 * Served from a built-in resource pack at the bottom of the pack list, so a real model JSON in the
 * mod or a resource pack always wins. The loader adds {@link #packSource()} to the client resource
 * packs.
 */
public enum LegacyModelLoader implements PackResources {

    INSTANCE;

    private static final String TEMPLATE = "{'parent':'item/generated','textures':{'layer0':'__item__'}}".replaceAll("'", "\"");
    private static final String NAMESPACE = "mcheli";
    private static final String TITLE = "MCHeli content item models";
    private static final PackLocationInfo LOCATION = new PackLocationInfo("mcheli_legacy_item_models",
            Component.literal(TITLE), PackSource.BUILT_IN, Optional.empty());

    /** Always-on, hidden client resource pack at the bottom of the list. */
    public static RepositorySource packSource() {
        return consumer -> consumer.accept(new Pack(LOCATION, new Pack.ResourcesSupplier() {
            @Override
            public @NotNull PackResources openPrimary(@NotNull PackLocationInfo location) {
                return INSTANCE;
            }

            @Override
            public @NotNull PackResources openFull(@NotNull PackLocationInfo location, Pack.@NotNull Metadata metadata) {
                return INSTANCE;
            }
        }, Services.PACKS.hiddenPackMetadata(Component.literal(TITLE)),
                new PackSelectionConfig(true, Pack.Position.BOTTOM, true)));
    }

    @StaticCache(reset = "reset")
    private static volatile Map<String, String> cache;

    /** Forgets the listed models; the next lookup lists them again. */
    public static void reset() {
        cache = null;
    }

    /** Cached for one resource reload; a listing (start of a model reload) refreshes it. */
    private static Map<String, String> models() {
        Map<String, String> m = cache;
        if (m == null) {
            m = collectModels();
            cache = m;
        }
        return m;
    }

    /** Model path ({@code models/item/<name>.json}) to its JSON. */
    private static Map<String, String> collectModels() {
        Map<String, String> out = new HashMap<>();
        try {
            VehicleTypes.registry(VehicleKind.HELICOPTER).values().forEach(c -> add(out, c.id()));
            VehicleTypes.registry(VehicleKind.PLANE).values().forEach(c -> add(out, c.id()));
            VehicleTypes.registry(VehicleKind.TANK).values().forEach(c -> add(out, c.id()));
            VehicleTypes.registry(VehicleKind.VEHICLE).values().forEach(c -> add(out, c.id()));
            VehicleTypes.registry(VehicleKind.SHIP).values().forEach(c -> add(out, c.id()));
            Throwables.registry().values().forEach(c -> add(out, Throwables.id(c)));
        } catch (RuntimeException ignored) {
            // content not loaded yet
        }
        return out;
    }

    private static void add(Map<String, String> out, ContentId content) {
        Item item = ContentBindings.item(content).orElse(null);
        if (item == null) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (!NAMESPACE.equals(id.getNamespace())) {
            return;
        }
        out.put("models/item/" + id.getPath() + ".json", TEMPLATE.replace("__item__", id.getNamespace() + ":items/" + id.getPath()));
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String @NotNull ... elements) {
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(@NotNull PackType packType, @NotNull ResourceLocation location) {
        if (packType != PackType.CLIENT_RESOURCES || !NAMESPACE.equals(location.getNamespace())
                || !location.getPath().startsWith("models/item/")) {
            return null;
        }
        String json = models().get(location.getPath());
        if (json == null) {
            return null;
        }
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void listResources(@NotNull PackType packType, @NotNull String namespace, @NotNull String path, @NotNull ResourceOutput resourceOutput) {
        if (packType != PackType.CLIENT_RESOURCES || !NAMESPACE.equals(namespace) || !"models/item/".startsWith(path.endsWith("/") ? path : path + "/")) {
            return;
        }
        Map<String, String> fresh = collectModels();
        cache = fresh;
        for (Map.Entry<String, String> e : fresh.entrySet()) {
            byte[] bytes = e.getValue().getBytes(StandardCharsets.UTF_8);
            resourceOutput.accept(ResourceLocation.fromNamespaceAndPath(NAMESPACE, e.getKey()), () -> new ByteArrayInputStream(bytes));
        }
    }

    @Override
    public @NotNull Set<String> getNamespaces(@NotNull PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(NAMESPACE) : Set.of();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T getMetadataSection(@NotNull MetadataSectionSerializer<T> deserializer) {
        if (deserializer == PackMetadataSection.TYPE) {
            return (T) new PackMetadataSection(Component.literal(TITLE),
                    SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES), Optional.empty());
        }
        return null;
    }

    @Override
    public @NotNull PackLocationInfo location() {
        return LOCATION;
    }

    @Override
    public void close() {
    }
}
