package com.ivomens.mcheli.entityview;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * A chain that tows one entity (a vehicle, a cargo container) behind or below another. The vehicle at
 * either end keeps a reference to the chain; projectiles and beams pass through it.
 */
public interface TowChain {

    /** The chain as the entity it is. */
    Entity entity();

    /** The entity pulling the chain, or null while the chain hangs loose. */
    @Nullable
    Entity puller();

    /** The entity hanging on the chain, or null while nothing is hooked. */
    @Nullable
    Entity towed();

    /** Whether the towed entity is a cargo container, whose air drop earns the relief supplies advancement. */
    boolean towsCargoContainer();
}
