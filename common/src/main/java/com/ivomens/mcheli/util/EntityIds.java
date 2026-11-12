package com.ivomens.mcheli.util;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Entities compared by id. A client level replaces an entity's object when it tracks the entity
 * again, and the integrated server's entities are other objects than the client's, so references
 * kept across ticks or sides are compared by id rather than by identity.
 */
public final class EntityIds {

    /** The id of no entity. */
    public static final int NONE = -1;

    private EntityIds() {
    }

    /** The entity's id, or {@link #NONE}. */
    public static int of(@Nullable Entity entity) {
        return entity != null ? entity.getId() : NONE;
    }

    /** Whether both have the same id, or both are absent. */
    public static boolean same(@Nullable Entity a, @Nullable Entity b) {
        return of(a) == of(b);
    }
}
