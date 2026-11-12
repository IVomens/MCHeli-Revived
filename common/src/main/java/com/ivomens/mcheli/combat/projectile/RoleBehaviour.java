package com.ivomens.mcheli.combat.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;

/**
 * What a projectile of a {@link ProjectileKind.Role} does beyond flying and exploding. Each projectile
 * owns one, holding the role's state.
 */
interface RoleBehaviour {

    /** No role: fly, steer, explode. */
    RoleBehaviour NONE = new RoleBehaviour() {
    };

    static RoleBehaviour of(ProjectileKind kind, ProjectileEntity projectile) {
        return switch (kind.role()) {
            case PROJECTILE -> NONE;
            case MINE -> new LandMine(projectile);
            case MARKER -> new MarkerSmoke(projectile);
            case DISPENSER -> new ItemDispenser(projectile);
        };
    }

    /** Runs a tick of its own instead of the projectile's flight; true when it did. */
    default boolean tickInstead() {
        return false;
    }

    /** Handles an impact instead of the warhead; true when it did. */
    default boolean onImpact(HitResult hit) {
        return false;
    }

    /** A player uses an item on the projectile; null when the role does not care. */
    @Nullable
    default InteractionResult interact(Player player, InteractionHand hand) {
        return null;
    }

    /**
     * Whether the projectile is kept beyond its flight: saved with the world, and never removed for waiting
     * in a chunk that ticks no entities (see {@link ProjectileLifetime}).
     */
    default boolean persists() {
        return false;
    }

    default void save(CompoundTag tag) {
    }

    /** Restores the role from a save; false when the projectile is not restored and goes away. */
    default boolean load(CompoundTag tag) {
        return false;
    }
}
