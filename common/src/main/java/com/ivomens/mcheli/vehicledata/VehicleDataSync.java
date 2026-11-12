package com.ivomens.mcheli.vehicledata;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The vehicles whose synced addon values changed this tick. At the end of the server tick each sends its
 * changes once, to the players that see it, so many changes in one tick make one message. One per server
 * ({@link #of}); with no addon values changed, a tick costs one empty check.
 */
public final class VehicleDataSync {

    private final Set<VehicleDataStore> pending = new LinkedHashSet<>();

    /** Implemented on {@link MinecraftServer} by a mixin, so every server syncs its own vehicles. */
    public interface Holder {
        VehicleDataSync mcheli$vehicleDataSync();
    }

    public static VehicleDataSync of(MinecraftServer server) {
        return ((Holder) server).mcheli$vehicleDataSync();
    }

    void changed(VehicleDataStore store) {
        this.pending.add(store);
    }

    /** End of a server tick: sends what changed. */
    public void flush() {
        if (this.pending.isEmpty()) {
            return;
        }
        List<VehicleDataStore> stores = new ArrayList<>(this.pending);
        this.pending.clear();
        stores.forEach(VehicleDataStore::sendChanges);
    }

    /** The server stopped: forgets what was not sent. */
    public void clear() {
        this.pending.clear();
    }
}
