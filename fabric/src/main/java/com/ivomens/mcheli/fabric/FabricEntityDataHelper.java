package com.ivomens.mcheli.fabric;

import com.ivomens.mcheli.fabric.extension.PersistentDataHolder;
import com.ivomens.mcheli.platform.EntityDataHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * Fabric has no shared free-form entity NBT. MCHeli adds one with a mixin and saves it under
 * NeoForge's own key, {@code NeoForgeData}: entities save identical NBT on both loaders, so worlds and
 * structure files move between them, and the flags MCHeli sets for other vehicle mods (countermeasures,
 * lock-on, cargo) are where those mods' NeoForge versions look. Fabric mods only see them if they read
 * the same compound.
 */
public class FabricEntityDataHelper implements EntityDataHelper {

    @Override
    public CompoundTag getPersistentData(Entity entity) {
        return ((PersistentDataHolder) entity).mcheli$getPersistentData();
    }
}
