package com.ivomens.mcheli.fabric;

import com.ivomens.mcheli.platform.RegistryHelper;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fabric's registries are open while mods initialise, but MCHeli's entries refer to each other across
 * registries (block entity types name their blocks, buckets their fluid), and the common code creates
 * them in the order NeoForge opens its registries. So entries are collected here and created in
 * {@link #registerAll}, one registry at a time in the game's own registry order, which is the order
 * NeoForge's {@code RegisterEvent} follows.
 */
public class FabricRegistryHelper implements RegistryHelper {

    private final Map<ResourceKey<? extends Registry<?>>, List<Pending<?, ?>>> pending = new LinkedHashMap<>();
    private boolean registered;

    @Override
    public synchronized <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String name,
                                                              Supplier<T> factory) {
        if (this.registered) {
            throw new IllegalStateException("mcheli:" + name + " registered after the registries were filled");
        }
        Pending<R, T> entry = new Pending<>(registry, Ids.of(name), factory);
        this.pending.computeIfAbsent(registry, key -> new ArrayList<>()).add(entry);
        return entry;
    }

    @Override
    public CreativeModeTab createCreativeTab(Component title, Supplier<ItemStack> icon,
                                             CreativeModeTab.DisplayItemsGenerator items) {
        return new LiveIconCreativeTab(title, icon, items);
    }

    /**
     * Creates and registers every collected entry. For each registry, {@code afterEntries} runs once its
     * collected entries are in, which is where the common code adds the entries it registers directly
     * (blocks and items), as it does from NeoForge's register event.
     */
    synchronized void registerAll(Consumer<ResourceKey<? extends Registry<?>>> afterEntries) {
        this.registered = true;
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            ResourceKey<? extends Registry<?>> key = registry.key();
            for (Pending<?, ?> entry : this.pending.getOrDefault(key, List.of())) {
                entry.create();
            }
            afterEntries.accept(key);
        }
    }

    /** An entry waiting for its registry; reading it before {@link #registerAll} fails. */
    private static final class Pending<R, T extends R> implements Supplier<T> {

        private final ResourceKey<? extends Registry<R>> registry;
        private final ResourceLocation id;
        private final Supplier<T> factory;
        private T value;

        Pending(ResourceKey<? extends Registry<R>> registry, ResourceLocation id, Supplier<T> factory) {
            this.registry = registry;
            this.id = id;
            this.factory = factory;
        }

        @SuppressWarnings("unchecked")
        void create() {
            Registry<R> target = (Registry<R>) BuiltInRegistries.REGISTRY.getOrThrow((ResourceKey) this.registry);
            this.value = Registry.register(target, this.id, this.factory.get());
        }

        @Override
        public T get() {
            if (this.value == null) {
                throw new IllegalStateException(this.id + " is not registered yet");
            }
            return this.value;
        }
    }
}
