package com.ivomens.mcheli.vehicle.component;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;

/**
 * One synced entity value handed to the component that owns it. The keys stay defined on the
 * entity class, in their original order, so the synced data ids do not change.
 * <p>
 * A value made with {@link #serverOwned} is written by the server only: on a client {@link #set}
 * does nothing and {@link #isWritable} is false, so the client keeps showing what the server sent.
 */
public final class SyncedValue<T> {

    private final SynchedEntityData data;
    private final EntityDataAccessor<T> key;
    private final boolean writable;

    public SyncedValue(SynchedEntityData data, EntityDataAccessor<T> key) {
        this(data, key, true);
    }

    private SyncedValue(SynchedEntityData data, EntityDataAccessor<T> key, boolean writable) {
        this.data = data;
        this.key = key;
        this.writable = writable;
    }

    /** A value only the server writes; {@code clientSide} is the side of the entity that holds it. */
    public static <T> SyncedValue<T> serverOwned(SynchedEntityData data, EntityDataAccessor<T> key,
            boolean clientSide) {
        return new SyncedValue<>(data, key, !clientSide);
    }

    public T get() {
        return this.data.get(this.key);
    }

    public void set(T value) {
        if (this.writable) {
            this.data.set(this.key, value);
        }
    }

    /** False on a client for a {@link #serverOwned} value. */
    public boolean isWritable() {
        return this.writable;
    }
}
