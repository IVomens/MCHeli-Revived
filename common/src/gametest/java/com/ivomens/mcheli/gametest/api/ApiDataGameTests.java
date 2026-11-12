package com.ivomens.mcheli.gametest.api;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.api.vehicle.data.MutableVehicleData;
import com.ivomens.mcheli.api.vehicle.data.VehicleDataKey;
import com.ivomens.mcheli.gametest.golden.GoldenMode;
import com.ivomens.mcheli.vehicledata.VehicleDataMessage;
import com.ivomens.mcheli.vehicledata.VehicleDataStore;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static com.ivomens.mcheli.gametest.api.ApiActionGameTests.HELICOPTER;
import static com.ivomens.mcheli.gametest.api.ApiActionGameTests.expect;

/**
 * Addon data on vehicles: values are saved and loaded with the vehicle, values of keys nobody declares survive
 * a save, synced keys travel in one batch per tick to a client copy and unsynced ones never do, and a vehicle
 * without addon data saves exactly as before.
 */
public final class ApiDataGameTests {

    private static final String BATCH = "mcheli_api_data";
    private static final String NAMESPACE = "mcheli_gametest";

    static final VehicleDataKey<Integer> SORTIES = VehicleDataKey.of(id("sorties"), Codec.INT, ByteBufCodecs.VAR_INT,
            0);
    static final VehicleDataKey<String> CALLSIGN = VehicleDataKey.of(id("callsign"), Codec.STRING,
            ByteBufCodecs.STRING_UTF8, "");
    static final VehicleDataKey<String> NOTE = VehicleDataKey.of(id("note"), Codec.STRING, "");

    private ApiDataGameTests() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static List<TestFunction> data() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return List.of();
        }
        return List.of(
                new TestFunction(BATCH, "api_data_saved_and_loaded", "mcheli:empty", 20, 0, true,
                        ApiDataGameTests::savedAndLoaded),
                new TestFunction(BATCH, "api_data_synced_in_batches", "mcheli:empty", 20, 0, true,
                        ApiDataGameTests::syncedInBatches),
                new TestFunction(BATCH, "api_data_old_save_unchanged", "mcheli:empty", 20, 0, true,
                        ApiDataGameTests::oldSaveUnchanged));
    }

    private static Vehicle spawn(GameTestHelper h) {
        ApiActionGameTests.floor(h);
        return McheliApi.get().vehicleActions()
                .spawner(h.getLevel(), VehicleKind.HELICOPTER, HELICOPTER, h.absoluteVec(new Vec3(8, 1, 8)))
                .spawn().vehicle().orElseThrow();
    }

    /** Values survive a save; a saved value of a key nobody declares is saved again untouched. */
    private static void savedAndLoaded(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vehicle vehicle = spawn(h);
        expect(vehicle.data().get(SORTIES) == 0 && !vehicle.data().has(SORTIES), "a new vehicle has sorties");
        expect(McheliApi.get().vehicleData().key(SORTIES.id()).orElse(null) == SORTIES, "the key is not declared");
        MutableVehicleData data = McheliApi.get().vehicleActions().control(vehicle).data();
        data.set(SORTIES, 3);
        expect(data.update(SORTIES, n -> n + 1) == 4, "update");
        data.set(NOTE, "server only");

        CompoundTag saved = new CompoundTag();
        vehicle.entity().saveWithoutId(saved);
        CompoundTag addonData = saved.getCompound(VehicleDataStore.NBT_KEY);
        expect(addonData.getInt(SORTIES.id().toString()) == 4, "saved as " + addonData);
        addonData.putString("removed_addon:value", "kept");
        saved.putString("id", EntityType.getKey(vehicle.entity().getType()).toString());

        Vehicle loaded = (Vehicle) EntityType.create(saved, level).orElseThrow();
        expect(loaded.data().get(SORTIES) == 4 && "server only".equals(loaded.data().get(NOTE)),
                "loaded " + loaded.data().get(SORTIES) + ", " + loaded.data().get(NOTE));
        expect(!loaded.data().has(CALLSIGN), "a value appeared from nowhere");
        CompoundTag resaved = new CompoundTag();
        loaded.entity().saveWithoutId(resaved);
        expect("kept".equals(resaved.getCompound(VehicleDataStore.NBT_KEY).getString("removed_addon:value")),
                "the unknown value was dropped: " + resaved.getCompound(VehicleDataStore.NBT_KEY));

        McheliApi.get().vehicleActions().control(loaded).data().remove(SORTIES);
        expect(loaded.data().get(SORTIES) == 0 && !loaded.data().has(SORTIES), "remove");
        loaded.entity().discard();
        vehicle.entity().discard();
        h.succeed();
    }

    /**
     * The changes of a tick go out together, only for synced keys, and a client copy reads them; a removed value
     * goes back to the default there too.
     */
    private static void syncedInBatches(GameTestHelper h) {
        Vehicle vehicle = spawn(h);
        VehicleDataStore store = (VehicleDataStore) vehicle.data();
        expect(store.changeMessages().isEmpty() && store.allValueMessages().isEmpty(), "messages without data");
        MutableVehicleData data = McheliApi.get().vehicleActions().control(vehicle).data();
        data.set(NOTE, "not for clients");
        expect(store.changeMessages().isEmpty(), "an unsynced value was going to be sent");

        data.set(SORTIES, 1);
        data.set(SORTIES, 2);
        data.set(CALLSIGN, "Viper");
        List<VehicleDataMessage> changes = store.changeMessages();
        expect(changes.size() == 1 && changes.getFirst().entries().size() == 2, "not one batch: " + changes);
        expect(store.changeMessages().isEmpty(), "the changes were not forgotten once taken");

        Vehicle client = (Vehicle) vehicle.entity().getType().create(h.getLevel());
        VehicleDataStore clientStore = (VehicleDataStore) client.data();
        changes.forEach(message -> clientStore.receive(message.entries()));
        expect(client.data().get(SORTIES) == 2 && "Viper".equals(client.data().get(CALLSIGN)),
                "the client read " + client.data().get(SORTIES) + ", " + client.data().get(CALLSIGN));
        expect(!client.data().has(NOTE), "an unsynced value reached the client");

        data.remove(CALLSIGN);
        store.changeMessages().forEach(message -> clientStore.receive(message.entries()));
        expect(client.data().get(CALLSIGN).isEmpty(), "the removed value stayed on the client");

        List<VehicleDataMessage> all = store.allValueMessages();
        expect(all.size() == 1 && all.getFirst().entries().size() == 1
                && all.getFirst().entries().getFirst().id().equals(SORTIES.id()), "a new watcher gets " + all);
        client.entity().discard();
        vehicle.entity().discard();
        h.succeed();
    }

    /** The committed save fixture loads and saves without the addon data compound. */
    private static void oldSaveUnchanged(GameTestHelper h) {
        Vehicle loaded = (Vehicle) EntityType.create(fixture(), h.getLevel()).orElseThrow();
        CompoundTag resaved = new CompoundTag();
        loaded.entity().saveWithoutId(resaved);
        expect(!resaved.contains(VehicleDataStore.NBT_KEY), "a vehicle without addon data saved the compound");
        expect(!loaded.data().has(SORTIES), "an old save has addon data");
        loaded.entity().discard();
        h.succeed();
    }

    private static CompoundTag fixture() {
        try {
            return TagParser.parseTag(Files.readString(GoldenMode.fixtureDir().resolve("helicopter")
                    .resolve(HELICOPTER + ".fresh.snbt"), StandardCharsets.UTF_8));
        } catch (IOException | CommandSyntaxException e) {
            throw new AssertionError("could not read the save fixture: " + e);
        }
    }
}
