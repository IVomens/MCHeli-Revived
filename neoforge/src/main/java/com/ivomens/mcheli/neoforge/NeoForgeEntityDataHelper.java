package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.platform.EntityDataHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/** NeoForge's persistent data, saved under {@code NeoForgeData} in the entity's NBT. */
public class NeoForgeEntityDataHelper implements EntityDataHelper {

    @Override
    public CompoundTag getPersistentData(Entity entity) {
        return entity.getPersistentData();
    }
}
