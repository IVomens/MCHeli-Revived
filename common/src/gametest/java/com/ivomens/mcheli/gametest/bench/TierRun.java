package com.ivomens.mcheli.gametest.bench;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.gametest.golden.SeededRandoms;
import com.ivomens.mcheli.gametest.snapshot.EntitySeeding;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.ModLogger;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One tier from start to finish: spawn the workload on the benchmark field and an observing player 30 blocks above its centre, let it run for the warm-up ticks, measure the next
 * ticks, then remove everything it spawned. {@link #step} runs once per server tick from the test;
 * {@link #tickStart} and {@link #tickEnd} wrap every server tick.
 */
final class TierRun {

    /** Chunks the observer asks for: the default client render distance. */
    static final int OBSERVER_VIEW = 12;
    private static final double OBSERVER_HEIGHT = 30.0;
    /** Ticks between the log lines that count the projectiles during the warm-up (not timed, so not measured). */
    static final int CENSUS_INTERVAL = 400;

    private enum Phase { LOADING, RUNNING, DONE }

    private final BenchSettings.Tier tier;
    private final int index;
    private final BenchSettings settings;
    private final ServerLevel level;
    private final BenchScene scene;
    private final List<Entity> joined = new ArrayList<>();
    private final IntOpenHashSet ours = new IntOpenHashSet();
    private Phase phase = Phase.LOADING;
    private int phaseTicks;
    private MockPlayer observer;
    private PacketMeter packets;
    @Nullable
    private MockPlayer pilot;
    private PacketMeter pilotPackets;
    private TickMeter meter;
    @Nullable
    private ProjectileCensus census;
    private double trackedAtStart;
    private TierResult result;

    TierRun(BenchSettings.Tier tier, int index, BenchSettings settings, ServerLevel level, Vec3 origin) {
        this.tier = tier;
        this.index = index;
        this.settings = settings;
        this.level = level;
        this.scene = new BenchScene(level, origin, index, 1000L * tier.workload().ordinal() + tier.vehicles());
    }

    /** Null until the tier is done. */
    TierResult result() {
        return this.result;
    }

    boolean done() {
        return this.phase == Phase.DONE;
    }

    void step() {
        this.phaseTicks++;
        switch (this.phase) {
            case LOADING -> start();
            case RUNNING -> run();
            case DONE -> {
            }
        }
    }

    private void start() {
        // the same random numbers every run: weapons, explosions, and every entity that joins
        long seed = 7919L * this.tier.workload().ordinal() + this.tier.vehicles();
        SeededRandoms.seedGlobal(this.level, seed);
        EntitySeeding.start(seed);
        this.tier.workload().populate(this.scene, this.tier.vehicles());
        Vec3 origin = this.scene.origin();
        this.observer = MockPlayer.join(this.level, "bench_observer" + this.index, OBSERVER_VIEW);
        this.observer.player().moveTo(origin.x, origin.y + OBSERVER_HEIGHT, origin.z);
        this.level.getChunkSource().move(this.observer.player());
        this.observer.openModChannels();
        this.packets = new PacketMeter(this.level, this.ours::contains);
        this.phase = Phase.RUNNING;
        this.phaseTicks = 0;
        ModLogger.get().info("[bench] {}: {} vehicles spawned", this.tier.key(), this.scene.vehicles().size());
    }

    private void run() {
        this.scene.tick();
        if (this.meter == null && this.phaseTicks % CENSUS_INTERVAL == 0) {
            ModLogger.get().info("[bench] {}: after {} s, {}", this.tier.key(), this.phaseTicks / 20,
                    ProjectileCensus.snapshot(this.level));
        }
        if (this.phaseTicks == this.settings.warmupTicks()) {
            this.meter = new TickMeter(this.settings.measureTicks());
            this.census = new ProjectileCensus(this.level);
            this.packets.setCounting(true);
            this.pilot = this.scene.firstCrew();
            if (this.pilot != null) {
                // a real pilot's client has the mod, so the pilot gets MCHeli's messages too
                this.pilot.openModChannels();
                this.pilotPackets = new PacketMeter(this.level, this.ours::contains);
                this.pilotPackets.setCounting(true);
            }
            this.trackedAtStart = tracked();
        } else if (this.meter != null && this.meter.count() >= this.settings.measureTicks()) {
            this.result = summarize();
            ModLogger.get().info("[bench] {}: mean {} ms, p50 {} ms, p99 {} ms, {} KiB/tick, {} B/tick/vehicle",
                    this.tier.key(), BenchReport.fmt(this.result.msMean()), BenchReport.fmt(this.result.msP50()),
                    BenchReport.fmt(this.result.msP99()), BenchReport.fmt(this.result.allocMean() / 1024),
                    BenchReport.fmt(this.result.netBytesPerVehicle()));
            tearDown();
            this.phase = Phase.DONE;
        }
    }

    void tickStart() {
        if (this.meter != null && this.meter.count() < this.settings.measureTicks()) {
            this.meter.tickStart();
        }
    }

    /** After the tick was timed: count what the observer was sent, throw away what the crew was sent. */
    void tickEnd() {
        if (this.meter != null) {
            int measured = this.meter.count();
            this.meter.tickEnd();
            if (this.meter.count() > measured) {
                this.census.afterTick();
            }
        }
        if (this.observer != null) {
            this.observer.acknowledgeChunks();
            // a real player moves all the time, which makes the server look again at what it can see;
            // without this, vehicles in chunks that arrived after the observer stopped are never tracked
            this.level.getChunkSource().move(this.observer.player());
            this.observer.drain(this.packets::accept);
            if (this.meter != null && this.meter.count() >= this.settings.measureTicks()) {
                this.packets.setCounting(false);
                if (this.pilotPackets != null) {
                    this.pilotPackets.setCounting(false);
                }
            }
        }
        this.scene.forEachPlayer(player -> {
            player.acknowledgeChunks();
            if (player == this.pilot) {
                player.drain(this.pilotPackets::accept);
            } else {
                player.discardSent();
            }
        });
    }

    void joined(Entity entity) {
        if (entity instanceof Player) {
            return;
        }
        this.joined.add(entity);
        if (this.census != null) {
            this.census.joined(entity);
        }
        if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals("mcheli")) {
            this.ours.add(entity.getId());
        }
    }

    private double tracked() {
        ServerPlayer player = this.observer.player();
        int n = 0;
        for (AircraftEntity vehicle : this.scene.vehicles()) {
            if (!vehicle.isRemoved() && Services.NETWORK.getPlayersTracking(vehicle).contains(player)) {
                n++;
            }
        }
        return n;
    }

    private TierResult summarize() {
        TickMeter.Stats time = this.meter.time();
        TickMeter.Stats alloc = this.meter.allocation();
        int ticks = Math.max(1, time.n());
        double tracked = Math.max(1, (this.trackedAtStart + tracked()) / 2);
        double mod = this.packets.modBytes() / (double) ticks;
        double vanilla = this.packets.vanillaBytes() / (double) ticks;
        Map<String, long[]> groups = new LinkedHashMap<>();
        this.packets.byKind().forEach((kind, entry) -> {
            long[] group = groups.computeIfAbsent(PacketMeter.group(kind), k -> new long[2]);
            group[0] += entry[0];
            group[1] += entry[1];
        });
        double pilotBytes = 0;
        Map<String, Double> pilotKinds = Map.of();
        if (this.pilotPackets != null) {
            pilotBytes = round((this.pilotPackets.modBytes() + this.pilotPackets.vanillaBytes()) / (double) ticks);
            pilotKinds = perTick(this.pilotPackets.byKind(), 0, ticks);
        }
        int entities = 0;
        for (Entity ignored : this.level.getAllEntities()) {
            entities++;
        }
        return new TierResult(this.tier.workload().id(), this.tier.vehicles(), this.scene.vehicles().size(),
                this.scene.alive(), this.scene.crewAboard(), this.scene.gunnerCount(), this.scene.wingmanCount(),
                entities, time.n(), round(time.mean()), round(time.p50()), round(time.p95()), round(time.p99()),
                round(time.max()), Math.round(alloc.mean()), Math.round(alloc.p50()), tracked, round(mod),
                round(vanilla), round((mod + vanilla) / tracked), perTick(this.packets.byKind(), 0, ticks),
                perTick(this.packets.byKind(), 1, ticks), perTick(groups, 0, ticks), perTick(groups, 1, ticks),
                perTick(this.packets.entityData(), 0, ticks), perTick(this.packets.byEntityType(), 0, ticks),
                pilotBytes, pilotKinds, this.census.result());
    }

    /** Column {@code column} (bytes or count) of each entry per tick, largest first. */
    private static Map<String, Double> perTick(Map<String, long[]> entries, int column, int ticks) {
        return entries.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[column], a.getValue()[column]))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> round(e.getValue()[column] / (double) ticks),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private void tearDown() {
        EntitySeeding.stop();
        this.scene.tearDown();
        this.observer.leave();
        for (Entity entity : this.joined) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        this.joined.clear();
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
