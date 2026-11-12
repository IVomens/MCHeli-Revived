package com.ivomens.mcheli.extension;

import com.ivomens.mcheli.api.vehicle.extension.BehaviourFactory;
import com.ivomens.mcheli.api.vehicle.extension.BehaviourTiming;
import com.ivomens.mcheli.api.vehicle.extension.MovementModelFactory;
import com.ivomens.mcheli.api.vehicle.extension.SettingsDecoder;
import com.ivomens.mcheli.api.vehicle.extension.VehicleExtensionProvider;
import com.ivomens.mcheli.api.vehicle.extension.VehicleExtensionRegistrar;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * The behaviour types and movement models other mods register through the API's
 * {@link VehicleExtensionProvider}. The providers are asked once, the first time anything needs the registry (the
 * first vehicle file read, or the first vehicle made), and the result never changes afterwards.
 * <p>
 * With no provider installed both maps are empty, and the vehicles add nothing to their tick for behaviours and
 * keep their kind's movement without a check per tick.
 */
public final class ExtensionRegistry {

    private final Map<ResourceLocation, BehaviourType<?>> behaviours;
    private final Map<ResourceLocation, MovementType<?>> movements;

    ExtensionRegistry(Map<ResourceLocation, BehaviourType<?>> behaviours,
                      Map<ResourceLocation, MovementType<?>> movements) {
        this.behaviours = Collections.unmodifiableMap(new LinkedHashMap<>(behaviours));
        this.movements = Collections.unmodifiableMap(new LinkedHashMap<>(movements));
    }

    /** The class is initialised on first use, which asks the providers once, on whichever thread gets there first. */
    private static final class Loaded {
        private static final ExtensionRegistry INSTANCE = load();
    }

    /** What the installed mods registered. */
    public static ExtensionRegistry get() {
        return Loaded.INSTANCE;
    }

    public boolean hasBehaviours() {
        return !this.behaviours.isEmpty();
    }

    public boolean hasMovementModels() {
        return !this.movements.isEmpty();
    }

    public Optional<BehaviourType<?>> behaviour(ResourceLocation id) {
        return Optional.ofNullable(this.behaviours.get(id));
    }

    public Optional<MovementType<?>> movement(ResourceLocation id) {
        return Optional.ofNullable(this.movements.get(id));
    }

    public Set<ResourceLocation> behaviourIds() {
        return this.behaviours.keySet();
    }

    public Set<ResourceLocation> movementIds() {
        return this.movements.keySet();
    }

    private static ExtensionRegistry load() {
        Collector collector = new Collector();
        Iterator<VehicleExtensionProvider> providers = ServiceLoader.load(VehicleExtensionProvider.class,
                ExtensionRegistry.class.getClassLoader()).iterator();
        while (true) {
            VehicleExtensionProvider provider;
            try {
                if (!providers.hasNext()) {
                    break;
                }
                provider = providers.next();
            } catch (ServiceConfigurationError e) {
                ModLogger.error("A vehicle extension provider could not be created", e);
                continue;
            }
            try {
                provider.register(collector);
            } catch (RuntimeException e) {
                ModLogger.error("Vehicle extension provider " + provider.getClass().getName() + " failed", e);
            }
        }
        if (!collector.behaviours.isEmpty() || !collector.movements.isEmpty()) {
            ModLogger.info("Vehicle extensions from other mods: behaviours {}, movement models {}",
                    collector.behaviours.keySet(), collector.movements.keySet());
        }
        return new ExtensionRegistry(collector.behaviours, collector.movements);
    }

    /** What the providers register into, in registration order. */
    static final class Collector implements VehicleExtensionRegistrar {

        final Map<ResourceLocation, BehaviourType<?>> behaviours = new LinkedHashMap<>();
        final Map<ResourceLocation, MovementType<?>> movements = new LinkedHashMap<>();

        @Override
        public <S> void behaviour(ResourceLocation id, BehaviourTiming timing, SettingsDecoder<S> settings,
                                  BehaviourFactory<S> factory) {
            BehaviourType<S> type = new BehaviourType<>(required(id, "id"), required(timing, "timing"),
                    required(settings, "settings decoder"), required(factory, "factory"));
            if (this.behaviours.putIfAbsent(id, type) != null) {
                throw new IllegalArgumentException("behaviour type " + id + " is already registered");
            }
        }

        @Override
        public <S> void movementModel(ResourceLocation id, SettingsDecoder<S> settings,
                                      MovementModelFactory<S> factory) {
            MovementType<S> type = new MovementType<>(required(id, "id"), required(settings, "settings decoder"),
                    required(factory, "factory"));
            if (this.movements.putIfAbsent(id, type) != null) {
                throw new IllegalArgumentException("movement model " + id + " is already registered");
            }
        }

        private static <T> T required(T value, String what) {
            if (value == null) {
                throw new IllegalArgumentException("the " + what + " is null");
            }
            return value;
        }
    }
}
