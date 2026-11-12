package com.ivomens.mcheli.admin.perf;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * What MCHeli's entities cost one level's server tick, for {@code /mcheli perf}: the time spent ticking
 * vehicles and projectiles in each of the last {@link #WINDOW} ticks, and the cost of each vehicle.
 * <p>
 * Timing is diagnostics, never gameplay: nothing reads it back into the simulation, so the wall clock is
 * allowed here (and only here, outside the client). The level's entity tick is timed around each MCHeli
 * vehicle and projectile, which costs two clock reads per such entity and tick.
 */
public final class TickCost {

    /** Ticks kept: 30 seconds at 20 ticks per second. */
    public static final int WINDOW = 600;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final double P95 = 0.95;

    /** Implemented on {@code ServerLevel} by a mixin, so every level keeps its own. */
    public interface Holder {
        TickCost mcheli$tickCost();
    }

    /** Which of MCHeli's costs an entity's tick counts towards. */
    public enum Kind { VEHICLE, PROJECTILE }

    /** One vehicle's time over the current and the previous window. */
    private static final class Cost {
        private long nanos;
        private int ticks;
    }

    /** Average and 95th percentile milliseconds per tick over the ticks kept. */
    public record Stats(double meanMs, double p95Ms, int ticks) {
    }

    /** A vehicle and its average milliseconds per tick it ticked. */
    public record EntityCost(Entity entity, double meanMs) {
    }

    private final long[] vehicleNanos = new long[WINDOW];
    private final long[] projectileNanos = new long[WINDOW];
    private int head;
    private int filled;
    private long currentTick = Long.MIN_VALUE;
    private long currentVehicleNanos;
    private long currentProjectileNanos;
    private int currentProjectiles;
    private int lastProjectiles;
    private long windowStart = Long.MIN_VALUE;
    private Map<Entity, Cost> current = new Reference2ObjectOpenHashMap<>();
    private Map<Entity, Cost> previous = new Reference2ObjectOpenHashMap<>();

    public static TickCost of(Level level) {
        return ((Holder) level).mcheli$tickCost();
    }

    /** The clock the costs are measured with. */
    public static long now() {
        return System.nanoTime();
    }

    /** An entity of {@code kind} that started ticking at {@code startNanos} has finished its tick. */
    public void record(Entity entity, Kind kind, long gameTime, long startNanos) {
        long spent = now() - startNanos;
        advanceTo(gameTime);
        if (kind == Kind.PROJECTILE) {
            this.currentProjectileNanos += spent;
            this.currentProjectiles++;
            return;
        }
        this.currentVehicleNanos += spent;
        Cost cost = this.current.computeIfAbsent(entity, key -> new Cost());
        cost.nanos += spent;
        cost.ticks++;
    }

    /** Closes the ticks before {@code gameTime}; ticks nothing of ours ran in count as zero. */
    private void advanceTo(long gameTime) {
        if (this.currentTick == gameTime) {
            return;
        }
        if (this.currentTick != Long.MIN_VALUE && gameTime > this.currentTick) {
            push(this.currentVehicleNanos, this.currentProjectileNanos);
            long idle = Math.min(gameTime - this.currentTick - 1, WINDOW);
            for (long i = 0; i < idle; i++) {
                push(0L, 0L);
            }
            this.lastProjectiles = gameTime - this.currentTick == 1 ? this.currentProjectiles : 0;
        }
        this.currentTick = gameTime;
        this.currentVehicleNanos = 0L;
        this.currentProjectileNanos = 0L;
        this.currentProjectiles = 0;
        if (this.windowStart == Long.MIN_VALUE || gameTime - this.windowStart >= WINDOW || gameTime < this.windowStart) {
            Map<Entity, Cost> recycled = this.previous;
            recycled.clear();
            this.previous = this.current;
            this.current = recycled;
            this.windowStart = gameTime;
        }
    }

    private void push(long vehicles, long projectiles) {
        this.vehicleNanos[this.head] = vehicles;
        this.projectileNanos[this.head] = projectiles;
        this.head = (this.head + 1) % WINDOW;
        this.filled = Math.min(this.filled + 1, WINDOW);
    }

    /** Closes the ticks up to and including {@code gameTime}, the last tick the level ran. */
    public void closeTicksUpTo(long gameTime) {
        advanceTo(gameTime + 1);
    }

    public Stats vehicles() {
        return stats(this.vehicleNanos);
    }

    public Stats projectiles() {
        return stats(this.projectileNanos);
    }

    /** Projectiles that ticked in the last closed tick. */
    public int projectilesLastTick() {
        return this.lastProjectiles;
    }

    private Stats stats(long[] ring) {
        if (this.filled == 0) {
            return new Stats(0.0, 0.0, 0);
        }
        long[] samples = new long[this.filled];
        for (int i = 0; i < this.filled; i++) {
            samples[i] = ring[Math.floorMod(this.head - 1 - i, WINDOW)];
        }
        long sum = 0L;
        for (long sample : samples) {
            sum += sample;
        }
        Arrays.sort(samples);
        int index = Math.min(samples.length - 1, (int) Math.ceil(P95 * samples.length) - 1);
        return new Stats(sum / NANOS_PER_MILLI / samples.length, samples[Math.max(index, 0)] / NANOS_PER_MILLI,
                samples.length);
    }

    /** The vehicles that cost the most per tick over the last one to two windows, most expensive first. */
    public List<EntityCost> mostExpensive(int count) {
        Map<Entity, Cost> merged = new Reference2ObjectOpenHashMap<>();
        for (Map<Entity, Cost> window : List.of(this.previous, this.current)) {
            window.forEach((entity, cost) -> {
                Cost total = merged.computeIfAbsent(entity, key -> new Cost());
                total.nanos += cost.nanos;
                total.ticks += cost.ticks;
            });
        }
        List<EntityCost> costs = new ArrayList<>();
        merged.forEach((entity, cost) -> {
            if (!entity.isRemoved() && cost.ticks > 0) {
                costs.add(new EntityCost(entity, cost.nanos / NANOS_PER_MILLI / cost.ticks));
            }
        });
        costs.sort(Comparator.comparingDouble(EntityCost::meanMs).reversed());
        return costs.subList(0, Math.min(count, costs.size()));
    }
}
