package com.ivomens.mcheli.combat.projectile;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Where projectiles that may keep chunks loaded announce their path each tick. Whoever loads chunks for
 * them (the wingman module's chunk loader) listens here, so the weapons never depend on it.
 */
public final class ProjectilePaths {

    private static final List<Consumer<ProjectileEntity>> LISTENERS = new CopyOnWriteArrayList<>();

    private ProjectilePaths() {
    }

    public static void addListener(Consumer<ProjectileEntity> listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Consumer<ProjectileEntity> listener) {
        LISTENERS.remove(listener);
    }

    /** Server: the projectile flies on and wants the chunks along its path kept loaded. */
    static void request(ProjectileEntity projectile) {
        for (Consumer<ProjectileEntity> listener : LISTENERS) {
            listener.accept(projectile);
        }
    }
}
