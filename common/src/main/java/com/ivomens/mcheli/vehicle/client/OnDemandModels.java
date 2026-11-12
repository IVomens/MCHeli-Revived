package com.ivomens.mcheli.vehicle.client;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Optional on-demand model residency: a vehicle's models are parsed in the background the first
 * time it is rendered (a {@link LazyModel} stands in until then) and dropped again after a
 * configurable idle time.
 */
public final class OnDemandModels {

    private enum State {UNLOADED, LOADING, LOADED}

    private static final class Entry {

        final VehicleKind kind;
        final String name;
        /** {@link ModelCache} key of the body model. */
        final String mapKey;
        /** Parses and bakes the vehicle's models; the argument is the force-reload flag. */
        final Consumer<Boolean> loader;
        volatile State state = State.UNLOADED;
        volatile long lastUsedMs;
        int idleSweeps;

        Entry(VehicleKind kind, String name, Consumer<Boolean> loader) {
            this.kind = kind;
            this.name = name;
            this.mapKey = kind.directory() + "/" + name;
            this.loader = loader;
        }
    }

    /** By kind, then definition name; the outer map is filled once and only read afterwards. */
    private static final Map<VehicleKind, Map<String, Entry>> ENTRIES = createEntryMaps();
    /** Parsing is CPU bound but short; one virtual thread per model keeps loads from queueing up. */
    private static final ExecutorService LOADER = Executors.newVirtualThreadPerTaskExecutor();
    private static final int SWEEP_INTERVAL_TICKS = 200;
    private static final long SWEEP_INTERVAL_MS = SWEEP_INTERVAL_TICKS * 50L;

    private static Map<VehicleKind, Map<String, Entry>> createEntryMaps() {
        Map<VehicleKind, Map<String, Entry>> maps = new EnumMap<>(VehicleKind.class);
        for (VehicleKind kind : VehicleKind.values()) {
            maps.put(kind, new ConcurrentHashMap<>());
        }
        return Collections.unmodifiableMap(maps);
    }

    public static boolean isEnabled() {
        return McheliConfig.loading().asyncOnDemandModelLoading();
    }

    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    /** Idle time before a model is dropped, or -1 to keep models forever. */
    private static long lifetimeMs() {
        int seconds = McheliConfig.loading().onDemandModelLifetimeSeconds();
        return seconds < 0 ? -1L : seconds * 1000L;
    }

    /** Registers a vehicle for on-demand loading and puts a placeholder in place of its models. */
    public static void install(VehicleSpec info, Consumer<Boolean> loader) {
        if (info == null) {
            return;
        }
        VehicleKind kind = info.kind();
        ENTRIES.get(kind).put(info.name(), new Entry(kind, info.name(), loader));
        putPlaceholder(kind, info.name());
    }

    private static void putPlaceholder(VehicleKind kind, String name) {
        VehicleModelRegistry.put(kind, name, new VehicleModels(new LazyModel(kind, name), Map.of()));
    }

    /** Marks the vehicle's models as used this frame and starts loading them if needed. */
    public static void notifyRendered(VehicleSpec info) {
        if (info != null) {
            notifyRendered(info.kind(), info.name());
        }
    }

    public static void notifyRendered(VehicleKind kind, String name) {
        Entry entry = name == null ? null : ENTRIES.get(kind).get(name);
        if (entry == null) {
            return;
        }
        entry.lastUsedMs = now();
        entry.idleSweeps = 0;
        if (entry.state == State.UNLOADED) {
            entry.state = State.LOADING;
            submitLoad(entry);
        }
    }

    private static void submitLoad(Entry entry) {
        LOADER.execute(() -> {
            boolean loaded = true;
            try {
                entry.loader.accept(true);
            } catch (Throwable t) {
                loaded = false;
                ModLogger.error("[OnDemandModels] load failed for " + entry.mapKey, t);
            }
            State result = loaded ? State.LOADED : State.UNLOADED;
            Minecraft.getInstance().execute(() -> entry.state = result);
        });
    }

    /** Sweeps out unused models every {@value #SWEEP_INTERVAL_TICKS} client ticks. One per client. */
    public static final class Sweeper {

        private int tickCounter;

        /** Once per client tick. */
        public void onClientTick() {
            if (!isEnabled() || ++this.tickCounter < SWEEP_INTERVAL_TICKS) {
                return;
            }
            this.tickCounter = 0;
            sweep();
        }
    }

    /** Evicts models that have not been rendered for their whole lifetime. */
    private static void sweep() {
        long lifetime = lifetimeMs();
        if (lifetime < 0) {
            return;
        }
        int maxIdleSweeps = (int) Math.max(1, Math.ceil((double) lifetime / SWEEP_INTERVAL_MS));
        long now = now();
        for (Map<String, Entry> entries : ENTRIES.values()) {
            for (Entry entry : entries.values()) {
                if (entry.state != State.LOADED) {
                    continue;
                }
                if (now - entry.lastUsedMs < SWEEP_INTERVAL_MS) {
                    entry.idleSweeps = 0;
                } else if (++entry.idleSweeps >= maxIdleSweeps) {
                    evict(entry);
                }
            }
        }
    }

    private static void evict(Entry entry) {
        entry.state = State.UNLOADED;
        entry.idleSweeps = 0;
        ModelCache.remove(entry.mapKey);
        putPlaceholder(entry.kind, entry.name);
        if (McheliConfig.debug().debugLog()) {
            ModLogger.debug("[OnDemandModels] evicted {}", entry.mapKey);
        }
    }

    /** After a content reload: forgets every entry and lets {@code reinstaller} register them again. */
    public static void onReloadAircraft(Runnable reinstaller) {
        if (!isEnabled()) {
            return;
        }
        Minecraft.getInstance().execute(() -> {
            ENTRIES.values().forEach(Map::clear);
            if (reinstaller != null) {
                reinstaller.run();
            }
        });
    }

    /**
     * Installs (or re-installs, on reload) a lazy placeholder for every vehicle model. The placeholder
     * loads the vehicle's models on the on-demand background worker.
     */
    public static void installAll() {
        VehicleTypes.registry(VehicleKind.HELICOPTER).values().forEach(OnDemandModels::installLazyModels);
        VehicleTypes.registry(VehicleKind.PLANE).values().forEach(OnDemandModels::installLazyModels);
        VehicleTypes.registry(VehicleKind.SHIP).values().forEach(OnDemandModels::installLazyModels);
        VehicleTypes.registry(VehicleKind.TANK).values().forEach(OnDemandModels::installLazyModels);
        VehicleTypes.registry(VehicleKind.VEHICLE).values().forEach(OnDemandModels::installLazyModels);
    }

    private static void installLazyModels(VehicleSpec info) {
        install(info, reload -> VehicleModelLoader.load(info, reload));
    }
}
