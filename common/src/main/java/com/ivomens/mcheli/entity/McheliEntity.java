package com.ivomens.mcheli.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Base of MCHeli's own entities. They take no damage unless a subclass handles it, and they are
 * culled by the size of their culling box times a render distance weight, so a subclass that is
 * larger than its collision box overrides {@link #getBoundingBoxForCulling()} instead of being culled
 * too early.
 */
public abstract class McheliEntity extends Entity {

    /** Vertical offset applied to everything that spawns or renders relative to an MCHeli entity. */
    public static final float GLOBAL_Y_OFFSET = 0.35F;
    /** Seat height correction so riders sit where the vehicle models expect them. */
    public static final double GLOBAL_SEAT_OFFSET = 0.22443D;
    private static final double BASE_RENDER_DISTANCE = 64.0;

    private double renderDistanceWeight = 1.0;

    public McheliEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    /** Scales how far away this entity is still rendered. */
    protected final double renderDistanceWeight() {
        return this.renderDistanceWeight;
    }

    protected final void setRenderDistanceWeight(double weight) {
        this.renderDistanceWeight = weight;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {}

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {}

    /**
     * The same passengers as vanilla's, in the same order, without building a stream unless a passenger
     * carries passengers of its own. The server asks this of every tracked entity for every player
     * whenever the entity crosses a chunk section ({@code ChunkMap.TrackedEntity#getEffectiveRange}); for
     * thousands of projectiles in a firefight the empty streams were most of what the server thread
     * allocated, and with hundreds of crewed vehicles the streams of their pilots and gunners were. The
     * empty list's iterator is a shared one, unlike that of {@code List.of()}, so the loop over no
     * passengers allocates nothing either.
     */
    @Override
    public @NotNull Iterable<Entity> getIndirectPassengers() {
        List<Entity> passengers = this.getPassengers();
        if (passengers.isEmpty()) {
            return Collections.emptyList();
        }
        for (int i = 0; i < passengers.size(); i++) {
            if (!passengers.get(i).getPassengers().isEmpty()) {
                return super.getIndirectPassengers();
            }
        }
        // nobody aboard carries anyone: the passengers themselves, which the vehicle never changes in place
        return passengers;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSq) {
        double size = this.getBoundingBoxForCulling().getSize();
        if (Double.isNaN(size)) {
            size = 1.0;
        }
        double maxDistance = size * BASE_RENDER_DISTANCE * this.renderDistanceWeight * getViewScale();
        return distanceSq < maxDistance * maxDistance;
    }
}
