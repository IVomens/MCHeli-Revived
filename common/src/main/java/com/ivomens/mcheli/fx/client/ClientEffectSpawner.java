package com.ivomens.mcheli.fx.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Adds purely client side effect entities, such as explosion flares and spent cases, to the client level.
 * {@code addFreshEntity} does nothing on a client in 1.21, and these entities need ids of their own. One per
 * client; the class is never loaded on a dedicated server.
 */
public final class ClientEffectSpawner {

    /** Client effect entities use negative ids so they never collide with server entity ids. */
    private static final int FIRST_ID = -1_000_000;

    private int nextId = FIRST_ID;

    /** Adds {@code entity} to {@code level} if it is a client level; does nothing otherwise. */
    public void addEntity(Level level, Entity entity) {
        if (level instanceof ClientLevel clientLevel) {
            entity.setId(this.nextId--);
            if (this.nextId <= Integer.MIN_VALUE + 1) {
                this.nextId = FIRST_ID;
            }
            clientLevel.addEntity(entity);
        }
    }
}
