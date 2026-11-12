package com.ivomens.mcheli.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * Free-form NBT attached to any entity, ours or not, and saved with it. MCHeli and other vehicle and
 * gun mods signal each other through a few flags in it:
 * <ul>
 *     <li>{@code FlareUsing}, {@code ChaffUsing} and {@code APSUsing} on an aircraft using that
 *     countermeasure; our missiles look for {@code FlareUsing} on any entity near their target,</li>
 *     <li>{@code Tracking} and {@code LockOn}, set on an aircraft by guided weapons aimed at it,</li>
 *     <li>{@code CanMountEntity} on any entity that may be loaded onto a cargo rack.</li>
 * </ul>
 */
public interface EntityDataHelper {

    /** The entity's own compound; changes to it are kept and saved with the entity. */
    CompoundTag getPersistentData(Entity entity);
}
