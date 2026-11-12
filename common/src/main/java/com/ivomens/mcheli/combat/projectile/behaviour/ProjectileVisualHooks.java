package com.ivomens.mcheli.combat.projectile.behaviour;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.util.state.RegistryHolder;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;

/**
 * Where a client's copy of a projectile finds the visuals addons register for its weapon's behaviours
 * (the API's {@code ProjectileVisual}). The client's set-up code installs the client side; on a dedicated server
 * nothing is installed and projectiles get no visuals.
 */
public final class ProjectileVisualHooks {

    /** Finds the visuals of a client's projectile. */
    @FunctionalInterface
    public interface Hook {

        /** The visuals of the projectile, whose weapon names addon behaviours; null when no addon draws any. */
        @Nullable
        Visuals attach(ProjectileEntity projectile, WeaponSpec weapon);
    }

    /** The visuals of one projectile on a client. */
    public interface Visuals {

        void tick();

        void impact(HitResult hit);

        void removed();
    }

    /** Handed in by the client's set-up, while the mod is constructed. */
    @RegistryHolder
    private static Hook hook = (projectile, weapon) -> null;

    private ProjectileVisualHooks() {
    }

    public static void install(Hook clientHook) {
        hook = clientHook;
    }

    @Nullable
    public static Visuals attach(ProjectileEntity projectile, WeaponSpec weapon) {
        return hook.attach(projectile, weapon);
    }
}
