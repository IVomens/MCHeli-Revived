package com.ivomens.mcheli.gametest.fabric;

import com.ivomens.mcheli.gametest.ExposedStorage;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Fabric's Transfer API has item and fluid storages for blocks and items but none that other mods look
 * up on entities, so MCHeli exposes its entity inventories and fuel tanks to nobody there.
 */
public class FabricExposedStorage implements ExposedStorage {

    @Nullable
    @Override
    public Slots items(Entity entity) {
        return null;
    }

    @Nullable
    @Override
    public Tank fluids(Entity entity) {
        return null;
    }
}
