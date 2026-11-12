package com.ivomens.mcheli.vehicledata;

import com.ivomens.mcheli.api.vehicle.data.MutableVehicleData;
import com.ivomens.mcheli.api.vehicle.data.VehicleDataKey;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The values addons keep on one vehicle. Until an addon stores something it holds nothing at all, and a
 * vehicle without values saves and syncs exactly as before addon data existed.
 * <p>
 * Saved values are read with their key's codec when the vehicle loads. Values no declared key reads (their addon
 * is gone, or not loaded yet) stay as saved tags and are saved again unchanged; a key declared later still
 * reads its value from them. Changes to synced keys are collected and sent once per server tick by
 * {@link VehicleDataSync}.
 */
public final class VehicleDataStore implements MutableVehicleData {

    /** The compound in the vehicle's save that holds every value, by key id. */
    public static final String NBT_KEY = "McheliAddonData";

    private final Entity vehicle;
    /** The values read or set, by key id; null until there is one. */
    @Nullable
    private Map<ResourceLocation, Object> values;
    /** Saved values no declared key has read, as they were saved; null when there are none. */
    @Nullable
    private CompoundTag unread;
    /** Synced keys changed since the last sync; null or empty when nothing waits. */
    @Nullable
    private Set<ResourceLocation> changed;

    public VehicleDataStore(Entity vehicle) {
        this.vehicle = vehicle;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(VehicleDataKey<T> key) {
        DataKey<T> own = DataKeyRegistry.declared(key);
        if (this.values != null) {
            Object value = this.values.get(own.id());
            if (value != null) {
                return (T) value;
            }
        }
        if (this.unread != null && this.unread.contains(own.id().toString())) {
            T value = readSaved(own);
            if (value != null) {
                return value;
            }
        }
        return own.defaultValue();
    }

    @Override
    public boolean has(VehicleDataKey<?> key) {
        String id = key.id().toString();
        return this.values != null && this.values.containsKey(key.id()) || this.unread != null && this.unread.contains(id);
    }

    @Override
    public <T> void set(VehicleDataKey<T> key, T value) {
        DataKey<T> own = DataKeyRegistry.declared(key);
        Objects.requireNonNull(value, "value");
        requireServer();
        if (this.values == null) {
            this.values = new LinkedHashMap<>();
        }
        this.values.put(own.id(), value);
        if (this.unread != null) {
            this.unread.remove(own.id().toString());
        }
        changed(own);
    }

    @Override
    public void remove(VehicleDataKey<?> key) {
        DataKey<?> own = DataKeyRegistry.declared(key);
        requireServer();
        boolean had = this.values != null && this.values.remove(own.id()) != null;
        if (this.unread != null && this.unread.contains(own.id().toString())) {
            this.unread.remove(own.id().toString());
            had = true;
        }
        if (had) {
            changed(own);
        }
    }

    private void requireServer() {
        if (this.vehicle.level().isClientSide) {
            throw new IllegalStateException("Vehicle data is changed on the server only");
        }
    }

    private void changed(DataKey<?> key) {
        if (!key.isSynced()) {
            return;
        }
        if (this.changed == null) {
            this.changed = new LinkedHashSet<>();
        }
        boolean first = this.changed.isEmpty();
        this.changed.add(key.id());
        MinecraftServer server = this.vehicle.level().getServer();
        if (first && server != null) {
            VehicleDataSync.of(server).changed(this);
        }
    }

    /** Reads a saved value with its key, now that the key is known; null when it cannot be read. */
    @Nullable
    private <T> T readSaved(DataKey<T> key) {
        Tag saved = Objects.requireNonNull(this.unread).get(key.id().toString());
        T value = key.codec().parse(ops(), saved).resultOrPartial(error -> ModLogger.warn(
                "Could not read the saved value of {} on {}, keeping it as saved: {}", key.id(), this.vehicle, error))
                .orElse(null);
        if (value != null) {
            this.unread.remove(key.id().toString());
            if (this.values == null) {
                this.values = new LinkedHashMap<>();
            }
            this.values.put(key.id(), value);
        }
        return value;
    }

    private RegistryOps<Tag> ops() {
        return this.vehicle.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    // Saving

    /** Writes every value, read or unread, under {@link #NBT_KEY}; writes nothing when there is none. */
    public void save(CompoundTag tag) {
        boolean noValues = this.values == null || this.values.isEmpty();
        if (noValues && (this.unread == null || this.unread.isEmpty())) {
            return;
        }
        CompoundTag data = this.unread != null ? this.unread.copy() : new CompoundTag();
        if (!noValues) {
            DataKeyRegistry registry = DataKeyRegistry.get();
            this.values.forEach((id, value) -> {
                DataKey<?> key = registry.find(id);
                Tag encoded = key != null ? encode(key, value) : null;
                if (encoded != null) {
                    data.put(id.toString(), encoded);
                }
            });
        }
        tag.put(NBT_KEY, data);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> Tag encode(DataKey<T> key, Object value) {
        return key.codec().encodeStart(ops(), (T) value).resultOrPartial(error -> ModLogger.error(
                "Could not save the value of {} on {}: {}", key.id(), this.vehicle, error)).orElse(null);
    }

    /** Reads the values saved under {@link #NBT_KEY}, replacing any the store holds. */
    public void load(CompoundTag tag) {
        this.values = null;
        this.unread = null;
        if (!tag.contains(NBT_KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        this.unread = tag.getCompound(NBT_KEY).copy();
        DataKeyRegistry registry = DataKeyRegistry.get();
        for (String name : List.copyOf(this.unread.getAllKeys())) {
            ResourceLocation id = ResourceLocation.tryParse(name);
            DataKey<?> key = id != null ? registry.find(id) : null;
            if (key != null) {
                readSaved(key);
            }
        }
    }

    // Syncing

    /** Server: sends the synced keys changed since the last call to the players that see the vehicle. */
    void sendChanges() {
        changeMessages().forEach(message -> Network.sendToTracking(this.vehicle, message));
    }

    /** Server: sends every synced value to a player who just started seeing the vehicle; nothing if none. */
    public void sendAll(ServerPlayer player) {
        if (this.values != null && !this.values.isEmpty() && DataKeyRegistry.get().anySynced()) {
            allValueMessages().forEach(message -> Network.sendToPlayer(player, message));
        }
    }

    /**
     * Server: the messages that carry the synced keys changed since the last call, which forgets them; none when
     * nothing changed or the vehicle is gone.
     */
    public List<VehicleDataMessage> changeMessages() {
        if (this.changed == null || this.changed.isEmpty()) {
            return List.of();
        }
        List<ResourceLocation> ids = List.copyOf(this.changed);
        this.changed.clear();
        if (this.vehicle.isRemoved()) {
            return List.of();
        }
        List<VehicleDataMessage.Entry> entries = new ArrayList<>(ids.size());
        DataKeyRegistry registry = DataKeyRegistry.get();
        for (ResourceLocation id : ids) {
            DataKey<?> key = registry.find(id);
            Object value = this.values != null ? this.values.get(id) : null;
            VehicleDataMessage.Entry entry = key == null ? null : value == null
                    ? new VehicleDataMessage.Entry(id, true, new byte[0]) : SyncedValues.encode(this.vehicle, key, value);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return SyncedValues.messages(this.vehicle, entries);
    }

    /** Server: the messages that carry every synced value, for a player who starts seeing the vehicle. */
    public List<VehicleDataMessage> allValueMessages() {
        if (this.values == null || this.values.isEmpty()) {
            return List.of();
        }
        List<VehicleDataMessage.Entry> entries = new ArrayList<>();
        DataKeyRegistry registry = DataKeyRegistry.get();
        this.values.forEach((id, value) -> {
            DataKey<?> key = registry.find(id);
            VehicleDataMessage.Entry entry = key != null && key.isSynced()
                    ? SyncedValues.encode(this.vehicle, key, value) : null;
            if (entry != null) {
                entries.add(entry);
            }
        });
        return SyncedValues.messages(this.vehicle, entries);
    }

    /** Client: takes the values the server sent; values of keys this client does not know are skipped. */
    public void receive(List<VehicleDataMessage.Entry> entries) {
        DataKeyRegistry registry = DataKeyRegistry.get();
        for (VehicleDataMessage.Entry entry : entries) {
            DataKey<?> key = registry.find(entry.id());
            if (key == null || !key.isSynced()) {
                continue;
            }
            if (entry.removed()) {
                if (this.values != null) {
                    this.values.remove(entry.id());
                }
                continue;
            }
            Object value = SyncedValues.decode(this.vehicle, key, entry.value());
            if (value != null) {
                if (this.values == null) {
                    this.values = new LinkedHashMap<>();
                }
                this.values.put(entry.id(), value);
            }
        }
    }
}
