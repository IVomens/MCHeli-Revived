package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.platform.Services;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Ejects the spent case of a shot. Cases are a client-side effect: the physical client spawns a
 * {@link CartridgeEntity}; a dedicated server does nothing.
 */
public interface SpentCases {

    SpentCases INSTANCE = Services.sided(SpentCases.class, new SpentCases() {
    });

    /**
     * Ejects a case from the muzzle of a gun moving with {@code platformMotion}, the gun turned by
     * {@code yaw} and {@code pitch}. Only ever called on a client level.
     */
    default void eject(Level level, Cartridge cartridge, Vec3 muzzle, Vec3 platformMotion, float yaw, float pitch) {
    }
}
