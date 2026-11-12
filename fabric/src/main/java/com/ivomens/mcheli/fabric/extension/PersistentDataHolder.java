package com.ivomens.mcheli.fabric.extension;

import net.minecraft.nbt.CompoundTag;

/**
 * NeoForge's entity persistent data, added to {@code Entity} by a mixin and saved under the same
 * {@code NeoForgeData} key, so an entity saves the same NBT on both loaders.
 */
public interface PersistentDataHolder {

    /** The NBT key NeoForge saves the compound under. */
    String KEY = "NeoForgeData";

    CompoundTag mcheli$getPersistentData();
}
