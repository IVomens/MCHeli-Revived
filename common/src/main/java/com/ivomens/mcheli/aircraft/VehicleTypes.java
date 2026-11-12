package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.def.vehicle.VehicleKinds;
import com.ivomens.mcheli.content.registry.ContentBindings;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.content.registry.ContentId;
import com.ivomens.mcheli.content.registry.ContentRegistries;
import com.ivomens.mcheli.content.registry.ContentRegistry;
import com.ivomens.mcheli.vehicle.definition.VehicleDefinitions;
import net.minecraft.world.item.Item;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** The loaded vehicle types, one registry per kind; names are unique within a kind. */
public final class VehicleTypes {

    private static final Map<VehicleKind, ContentRegistry<VehicleFile, VehicleSpec>> REGISTRIES = registries();
    /** Which texture {@link #nextTexture} hands out next, by vehicle. */
    private static final Map<ContentId, Integer> NEXT_TEXTURE = new ConcurrentHashMap<>();

    private VehicleTypes() {
    }

    private static Map<VehicleKind, ContentRegistry<VehicleFile, VehicleSpec>> registries() {
        Map<VehicleKind, ContentRegistry<VehicleFile, VehicleSpec>> registries = new EnumMap<>(VehicleKind.class);
        for (VehicleKind kind : VehicleKind.values()) {
            ContentRegistry<VehicleFile, VehicleSpec> registry = new ContentRegistry<>(VehicleKinds.contentType(kind),
                    VehicleFile.class, VehicleTypes::load, VehicleTypes::reloaded);
            registry.addChangeListener(VehicleDefinitions::changed);
            registries.put(kind, registry);
        }
        return registries;
    }

    /** Builds a vehicle file that was just read; a file without a seat is rejected. */
    private static VehicleSpec load(ContentEntry<VehicleFile> entry) throws Exception {
        VehicleSpec vehicle = VehicleSpec.of(entry);
        if (vehicle.crew().riderSeats() < 1) {
            throw new Exception("At least one seat must be set.");
        }
        return vehicle;
    }

    /** A vehicle read again in game starts its textures over and loads its models again. */
    private static void reloaded(VehicleSpec vehicle) {
        NEXT_TEXTURE.remove(vehicle.id());
        VehicleClientHooks.INSTANCE.reloadModels(vehicle);
    }

    /** Hands the vehicle registries to the content pipeline; call once at start-up, before content loads. */
    public static void registerContent() {
        REGISTRIES.values().forEach(ContentRegistries::register);
    }

    public static ContentRegistry<VehicleFile, VehicleSpec> registry(VehicleKind kind) {
        return REGISTRIES.get(kind);
    }

    public static Optional<VehicleSpec> get(VehicleKind kind, String name) {
        return registry(kind).get(name);
    }

    /** The vehicle of {@code kind} an item was registered for. */
    public static Optional<VehicleSpec> fromItem(VehicleKind kind, Item item) {
        return ContentBindings.contentOf(VehicleKinds.contentType(kind), item).map(ContentId::name)
                .flatMap(name -> get(kind, name));
    }

    /** The vehicle's file as read. */
    public static Optional<ContentEntry<VehicleFile>> entry(VehicleSpec vehicle) {
        return registry(vehicle.kind()).entry(vehicle.name());
    }

    /** Who made the vehicle's file; an override from another pack that names nobody keeps the original author. */
    public static String author(VehicleSpec vehicle) {
        return entry(vehicle).map(ContentEntry::author).orElse("");
    }

    /** Hands out the vehicle's textures in turn, for vehicles placed one after another. */
    public static synchronized String nextTexture(VehicleSpec vehicle) {
        List<String> textures = vehicle.textures();
        int index = NEXT_TEXTURE.getOrDefault(vehicle.id(), 0) % textures.size();
        NEXT_TEXTURE.put(vehicle.id(), (index + 1) % textures.size());
        return textures.get(index);
    }

    /** The item registered for the vehicle, if items are registered. */
    public static Optional<Item> item(VehicleSpec vehicle) {
        return ContentBindings.item(vehicle.id());
    }
}
