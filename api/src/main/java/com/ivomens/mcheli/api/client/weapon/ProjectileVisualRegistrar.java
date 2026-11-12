package com.ivomens.mcheli.api.client.weapon;

import net.minecraft.resources.ResourceLocation;

/**
 * What a {@link ProjectileVisualProvider} registers its visuals with.
 *
 * <p>Thread: only valid during {@link ProjectileVisualProvider#registerVisuals}. Stability: since API 1.1;
 * implemented by MCHeli only, so a minor version may add methods.
 */
public interface ProjectileVisualRegistrar {

    /**
     * Registers what clients draw for the projectiles of weapons that name {@code behaviourId}, either as their
     * {@code Guidance} or among their {@code ProjectileEffects}. The factory gets the settings written next to the
     * id in the weapon file, as the server's factory does.
     *
     * @throws IllegalArgumentException when a visual for this id is already registered
     */
    void visual(ResourceLocation behaviourId, ProjectileVisual.Factory factory);
}
