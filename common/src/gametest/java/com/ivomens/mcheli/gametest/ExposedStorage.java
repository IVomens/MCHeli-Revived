package com.ivomens.mcheli.gametest;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import javax.annotation.Nullable;
import java.util.ServiceLoader;

/**
 * How other mods see an entity's inventory and fuel tank through the loader's own storage API. Each
 * loader's game test layer provides one, listed in its {@code META-INF/services}. A loader without an
 * entity storage API returns null, and the tests skip those checks there.
 */
public interface ExposedStorage {

    static ExposedStorage get() {
        return ServiceLoader.load(ExposedStorage.class, ExposedStorage.class.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No ExposedStorage for this loader"));
    }

    /** The entity's inventory as an item storage, or null when the loader has no entity item storage. */
    @Nullable
    Slots items(Entity entity);

    /** The entity's fluid tank, or null when the loader has no entity fluid storage. */
    @Nullable
    Tank fluids(Entity entity);

    interface Slots {

        int size();

        ItemStack get(int slot);

        /** @return what did not fit */
        ItemStack insert(int slot, ItemStack stack);
    }

    interface Tank {

        /** Millibuckets in the first tank. */
        int amount();

        /** @return the millibuckets taken in, or that would be when simulating */
        int fill(Fluid fluid, int millibuckets, boolean simulate);
    }
}
