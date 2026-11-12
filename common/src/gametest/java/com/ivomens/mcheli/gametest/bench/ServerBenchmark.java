package com.ivomens.mcheli.gametest.bench;

import com.ivomens.mcheli.bench.SystemInfo;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The server benchmark: one game test that runs every tier of {@link BenchSettings} in turn on the same
 * field, 1,200 blocks from the test structures, and writes {@code server-<mode>.json} and
 * {@code .md} to {@code mcheli.bench.out}. In {@code gate} mode the run is compared with the committed
 * baseline and the test fails on a regression; a tier that fails is measured once more and the
 * better of the two runs counts (time and allocation each), since CI machines are noisy. {@code record} writes the
 * baseline instead. See docs/BENCHMARKS.md.
 * <p>
 * Before the first tier, every chunk the observer will see is generated, and the chunks the vehicles
 * stand on stay loaded for the whole run, so no tier pays for world generation. Mob griefing is off,
 * so explosions leave the ground as it was for the next tier.
 * <p>
 * Only registered when {@code mcheli.bench} is set, and then alone (see {@code McheliGameTests}).
 */
public final class ServerBenchmark {

    private static final int MAX_TICKS = 2_000_000;
    private static final double FIELD_DISTANCE = 1200.0;
    /** Generated before the first tier: the observer's view distance and one more chunk. */
    private static final int PRELOAD_CHUNKS = TierRun.OBSERVER_VIEW + 1;
    private static final int MAX_PRELOAD_TICKS = 2400;
    /** Ticks between tiers, for the removed entities to go and the collector to run. */
    private static final int PAUSE_TICKS = 40;
    private static final BenchSettings SETTINGS = BenchSettings.fromSystemProperties();

    private static Run current;

    private ServerBenchmark() {
    }

    public static boolean enabled() {
        return !SETTINGS.mode().isEmpty();
    }

    public static List<TestFunction> tests() {
        return List.of(new TestFunction("mcheli_bench", "server_benchmark", "mcheli:empty", MAX_TICKS, 0L, true,
                helper -> current = new Run(helper)));
    }

    /**
     * The start of a level tick, inside the measured server tick. The run steps from here rather than
     * from {@code GameTestHelper.onEachTick}, which schedules one task per tick up to the test's
     * timeout and walks all of them every tick (tens of milliseconds for a long test).
     */
    public static void onLevelTickStart(Level level) {
        if (current != null && level == current.level) {
            current.step();
        }
    }

    public static void onTickStart() {
        if (current != null) {
            current.tickStart();
        }
    }

    public static void onTickEnd() {
        if (current != null) {
            current.tickEnd();
        }
    }

    public static void onEntityJoin(Entity entity, Level level) {
        if (current != null && !level.isClientSide()) {
            current.joined(entity);
        }
    }

    /** The whole run: calibration, the tiers, the retries and the report. */
    private static final class Run {

        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Vec3 origin;
        private final Set<ChunkPos> forced = new HashSet<>();
        private final boolean mobGriefing;
        private boolean preloading = true;
        private final Deque<BenchSettings.Tier> queue = new ArrayDeque<>(SETTINGS.tiers());
        private final Map<String, TierResult> results = new LinkedHashMap<>();
        private final List<String> retried = new ArrayList<>();
        private final double calibrationStart;
        private final int oldViewDistance;
        private TierRun tier;
        private int tiersStarted;
        private int preloadTicks;
        private int pause;
        private boolean finished;

        Run(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.origin = helper.absoluteVec(new Vec3(16, 1, 16)).add(FIELD_DISTANCE, 0, 0);
            this.mobGriefing = this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
            this.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, this.level.getServer());
            forceChunks(PRELOAD_CHUNKS * 16);
            this.oldViewDistance = this.level.getServer().getPlayerList().getViewDistance();
            this.level.getServer().getPlayerList().setViewDistance(TierRun.OBSERVER_VIEW);
            this.calibrationStart = Calibration.measure();
            ModLogger.get().info("[bench] mode {}, {} tiers, calibration {} ms", SETTINGS.mode(), this.queue.size(),
                    BenchReport.fmt(this.calibrationStart));
        }

        void step() {
            if (this.finished) {
                return;
            }
            if (this.preloading) {
                preload();
                return;
            }
            if (this.tier != null) {
                this.tier.step();
                if (this.tier.done()) {
                    keep(this.tier.result());
                    this.tier = null;
                    this.pause = PAUSE_TICKS;
                    System.gc();
                }
                return;
            }
            if (this.pause-- > 0) {
                return;
            }
            if (this.queue.isEmpty() && !queueRetries()) {
                finish();
                return;
            }
            BenchSettings.Tier next = this.queue.poll();
            this.tiersStarted++;
            this.tier = new TierRun(next, this.tiersStarted, SETTINGS, this.level, this.origin);
        }

        /** Waits for the field to generate, then keeps only the vehicles' chunks loaded. */
        private void preload() {
            this.preloadTicks++;
            boolean loaded = this.forced.stream().allMatch(c -> this.level.isPositionEntityTicking(c.getWorldPosition()));
            if (!loaded && this.preloadTicks < MAX_PRELOAD_TICKS) {
                return;
            }
            double keep = SETTINGS.tiers().stream().mapToDouble(t -> t.workload().halfWidth(t.vehicles())).max()
                    .orElse(0);
            releaseChunks();
            forceChunks(keep);
            this.preloading = false;
            this.pause = PAUSE_TICKS;
            ModLogger.get().info("[bench] field ready after {} ticks", this.preloadTicks);
        }

        private void forceChunks(double halfWidth) {
            int minX = (int) Math.floor(this.origin.x - halfWidth) >> 4;
            int maxX = (int) Math.floor(this.origin.x + halfWidth) >> 4;
            int minZ = (int) Math.floor(this.origin.z - halfWidth) >> 4;
            int maxZ = (int) Math.floor(this.origin.z + halfWidth) >> 4;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (this.forced.add(new ChunkPos(x, z))) {
                        this.level.setChunkForced(x, z, true);
                    }
                }
            }
        }

        private void releaseChunks() {
            this.forced.forEach(c -> this.level.setChunkForced(c.x, c.z, false));
            this.forced.clear();
        }

        /** A retried tier keeps the better of its two runs, for time and allocation each. */
        private void keep(TierResult result) {
            this.results.merge(result.key(), result, TierResult::bestOf);
        }

        /** In gate mode, queues once every tier that failed. @return whether any was queued */
        private boolean queueRetries() {
            if (!SETTINGS.gate() || !this.retried.isEmpty()) {
                return false;
            }
            BenchReport baseline = readBaseline();
            if (baseline == null) {
                return false;
            }
            BenchReport report = report(this.calibrationStart);
            for (String problem : report.regressionsAgainst(baseline, SETTINGS.allocTolerance(),
                    SETTINGS.timeTolerance(), SETTINGS.netTolerance())) {
                String key = problem.substring(0, problem.indexOf(':'));
                if (!this.retried.contains(key)) {
                    SETTINGS.tiers().stream().filter(t -> t.key().equals(key)).findFirst().ifPresent(t -> {
                        this.queue.add(t);
                        this.retried.add(key);
                    });
                }
            }
            if (!this.retried.isEmpty()) {
                ModLogger.get().info("[bench] measuring again: {}", this.retried);
            }
            return !this.retried.isEmpty();
        }

        void tickStart() {
            if (this.tier != null) {
                this.tier.tickStart();
            }
        }

        void tickEnd() {
            if (this.tier != null) {
                this.tier.tickEnd();
            }
        }

        void joined(Entity entity) {
            if (this.tier != null && entity.level() == this.level) {
                this.tier.joined(entity);
            }
        }

        private void finish() {
            this.finished = true;
            releaseChunks();
            this.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(this.mobGriefing, this.level.getServer());
            this.level.getServer().getPlayerList().setViewDistance(Math.max(2, this.oldViewDistance));
            double calibration = (this.calibrationStart + Calibration.measure()) / 2;
            BenchReport report = report(calibration);
            try {
                Path out = SETTINGS.outDir();
                report.writeJson(out.resolve("server-" + SETTINGS.mode() + ".json"));
                report.writeMarkdown(out.resolve("server-" + SETTINGS.mode() + ".md"));
                ModLogger.get().info("[bench] results written to {}", out.toAbsolutePath());
                if (SETTINGS.record()) {
                    report.writeJson(SETTINGS.baseline());
                    ModLogger.get().info("[bench] baseline recorded to {}", SETTINGS.baseline().toAbsolutePath());
                }
            } catch (IOException e) {
                this.helper.fail("Could not write the benchmark results: " + e);
                return;
            }
            verdict(report);
        }

        private void verdict(BenchReport report) {
            if (!SETTINGS.gate()) {
                this.helper.succeed();
                return;
            }
            BenchReport baseline = readBaseline();
            if (baseline == null) {
                this.helper.fail("No benchmark baseline at " + SETTINGS.baseline().toAbsolutePath()
                        + "; record one with -Pbench=record");
                return;
            }
            report.comparisonLines(baseline).forEach(line -> ModLogger.get().info("[bench] {}", line));
            List<String> problems = report.regressionsAgainst(baseline, SETTINGS.allocTolerance(),
                    SETTINGS.timeTolerance(), SETTINGS.netTolerance());
            if (problems.isEmpty()) {
                ModLogger.get().info("[bench] gate passed");
                this.helper.succeed();
            } else {
                problems.forEach(problem -> ModLogger.get().error("[bench] regression: {}", problem));
                this.helper.fail("Benchmark regression: " + String.join("; ", problems));
            }
        }

        private BenchReport readBaseline() {
            if (!Files.isRegularFile(SETTINGS.baseline())) {
                return null;
            }
            try {
                return BenchReport.read(SETTINGS.baseline());
            } catch (IOException | RuntimeException e) {
                ModLogger.get().error("[bench] cannot read the baseline {}", SETTINGS.baseline(), e);
                return null;
            }
        }

        private BenchReport report(double calibration) {
            String date = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
            return new BenchReport(BenchReport.SCHEMA, SETTINGS.mode(), Services.PLATFORM.getLoaderName(),
                    System.getProperty("mcheli.golden.commit", "unknown"), date, SystemInfo.describe(),
                    Math.round(calibration * 1000) / 1000.0, SETTINGS.warmupTicks(), SETTINGS.measureTicks(),
                    new ArrayList<>(this.results.values()));
        }
    }
}
