package com.ivomens.mcheli.registry;

import com.ivomens.mcheli.util.Ids;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * An entry of a vanilla registry in MCHeli's namespace, looked up by its id the first time it is
 * read. The code that registers the entry names it with {@link #id()}; everyone else only reads it,
 * and must not before registration has finished.
 */
public final class RegistryRef<T> implements Supplier<T> {

    private final Registry<T> registry;
    private final ResourceLocation id;
    private volatile T value;

    private RegistryRef(Registry<T> registry, ResourceLocation id) {
        this.registry = registry;
        this.id = id;
    }

    public static RegistryRef<Item> item(String name) {
        return new RegistryRef<>(BuiltInRegistries.ITEM, Ids.of(name));
    }

    public static RegistryRef<Block> block(String name) {
        return new RegistryRef<>(BuiltInRegistries.BLOCK, Ids.of(name));
    }

    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public T get() {
        T found = this.value;
        if (found == null) {
            found = this.registry.getOptional(this.id)
                    .orElseThrow(() -> new IllegalStateException(this.id + " is not registered yet"));
            this.value = found;
        }
        return found;
    }
}
