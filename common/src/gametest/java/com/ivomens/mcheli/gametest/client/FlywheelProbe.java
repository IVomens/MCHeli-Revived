package com.ivomens.mcheli.gametest.client;

import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.vehicle.client.render.flywheel.FlywheelVehicleStats;
import net.minecraft.client.Minecraft;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Logs what the Flywheel backend did in a development client run with Flywheel ({@code -Pflywheel}).
 * <p>
 * Every second: the {@link FlywheelVehicleStats} counters, the frames drawn and the number of
 * instancers Flywheel holds (each one a draw call on its instancing backend), as {@code [flywheelstats]}.
 * <p>
 * Every frame that took more than twice the median of the frames before it: what happened in that
 * frame, as {@code [flywheelspike]}: the counters, the instancers Flywheel made or dropped, the bytes
 * the JVM allocated (all threads, Flywheel's workers included) and the garbage collections. A spike
 * with nothing in its line is not ours. {@link #window()} sums the spikes since {@link #startWindow()}.
 * Silent while no vehicle visual runs.
 */
final class FlywheelProbe {

    private static final boolean INSTALLED = Services.PLATFORM.isModLoaded("flywheel");
    private static final long SECOND = 1_000_000_000L;
    /** The frames the median is taken over. */
    private static final int HISTORY = 101;
    /** Frames a spike is judged against, at least, before any is reported. */
    private static final int MIN_HISTORY = 30;
    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final List<GarbageCollectorMXBean> COLLECTORS = ManagementFactory.getGarbageCollectorMXBeans();

    private static FlywheelVehicleStats.Counts last = FlywheelVehicleStats.counts();
    private static long lastTime = System.nanoTime();
    private static int frames;

    private static FlywheelVehicleStats.Counts lastFrame = FlywheelVehicleStats.counts();
    private static long lastFrameEnd;
    private static long lastAllocated = THREADS.getTotalThreadAllocatedBytes();
    private static long lastGcCount = gcCount();
    private static long lastGcMillis = gcMillis();
    private static int lastInstancers = -1;
    private static final long[] history = new long[HISTORY];
    private static final long[] sorted = new long[HISTORY];
    private static int historySize;
    private static int historyNext;

    private static Window window = new Window();

    private FlywheelProbe() {
    }

    /** Spikes of the frames since {@link #startWindow()}. */
    static final class Window {
        int frames;
        int spikes;
        /** Spikes in frames in which the backend made, deleted or uploaded something. */
        int spikesWithCreation;
        int spikesWithGc;
        /** Spikes with nothing of ours and no garbage collection. */
        int spikesUnexplained;
        long allocated;
        long gcCount;
        /** Frames in which the backend made, deleted or uploaded something, spike or not. */
        int framesWithCreation;

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "frames=%d spikes=%d (with creation %d, with GC %d, neither %d) "
                            + "framesWithCreation=%d allocatedMB/frame=%.2f gcs=%d", this.frames, this.spikes,
                    this.spikesWithCreation, this.spikesWithGc, this.spikesUnexplained, this.framesWithCreation,
                    this.frames == 0 ? 0 : this.allocated / 1048576.0 / this.frames, this.gcCount);
        }
    }

    static void startWindow() {
        window = new Window();
    }

    static Window window() {
        return window;
    }

    static void onFrameEnd() {
        if (!INSTALLED) {
            return;
        }
        long now = System.nanoTime();
        perFrame(now);
        frames++;
        if (now - lastTime < SECOND) {
            return;
        }
        FlywheelVehicleStats.Counts counts = FlywheelVehicleStats.counts();
        FlywheelVehicleStats.Counts delta = counts.since(last);
        if (delta.frames() > 0) {
            ModLogger.info("[flywheelstats] fps={} backend={} instancers={} {}",
                    Math.round(frames * 1e9 / (now - lastTime)), backend(), instancers(), delta);
        }
        last = counts;
        lastTime = now;
        frames = 0;
    }

    /** Takes this frame's counters and reports it if it was a spike. */
    private static void perFrame(long now) {
        FlywheelVehicleStats.Counts counts = FlywheelVehicleStats.counts();
        FlywheelVehicleStats.Counts delta = counts.since(lastFrame);
        lastFrame = counts;
        long allocated = THREADS.getTotalThreadAllocatedBytes();
        long gcs = gcCount();
        long gcTime = gcMillis();
        int instancers = delta.frames() > 0 ? instancers() : lastInstancers;
        long frameNanos = lastFrameEnd == 0 ? 0 : now - lastFrameEnd;
        long frameAllocated = allocated - lastAllocated;
        long frameGcs = gcs - lastGcCount;
        long frameGcMillis = gcTime - lastGcMillis;
        int instancerChange = lastInstancers < 0 || instancers < 0 ? 0 : instancers - lastInstancers;
        lastFrameEnd = now;
        lastAllocated = allocated;
        lastGcCount = gcs;
        lastGcMillis = gcTime;
        lastInstancers = instancers;
        if (frameNanos == 0 || delta.frames() == 0) {
            return;
        }
        boolean creation = !delta.createsNothing() || instancerChange != 0;
        Window w = window;
        w.frames++;
        w.allocated += frameAllocated;
        w.gcCount += frameGcs;
        if (creation) {
            w.framesWithCreation++;
        }
        long median = median();
        if (historySize >= MIN_HISTORY && frameNanos > 2 * median) {
            w.spikes++;
            if (creation) {
                w.spikesWithCreation++;
            }
            if (frameGcs > 0) {
                w.spikesWithGc++;
            }
            if (!creation && frameGcs == 0) {
                w.spikesUnexplained++;
            }
            ModLogger.info(String.format(Locale.ROOT, "[flywheelspike] ms=%.1f median=%.1f instancers=%d (%+d) "
                            + "allocKB=%d gc=%d (%d ms) %s", frameNanos / 1e6, median / 1e6, instancers,
                    instancerChange, frameAllocated / 1024, frameGcs, frameGcMillis, delta));
        }
        history[historyNext] = frameNanos;
        historyNext = (historyNext + 1) % HISTORY;
        historySize = Math.min(HISTORY, historySize + 1);
    }

    private static long median() {
        if (historySize == 0) {
            return Long.MAX_VALUE;
        }
        System.arraycopy(history, 0, sorted, 0, historySize);
        Arrays.sort(sorted, 0, historySize);
        return sorted[historySize / 2];
    }

    private static long gcCount() {
        long count = 0;
        for (GarbageCollectorMXBean collector : COLLECTORS) {
            count += Math.max(0, collector.getCollectionCount());
        }
        return count;
    }

    private static long gcMillis() {
        long millis = 0;
        for (GarbageCollectorMXBean collector : COLLECTORS) {
            millis += Math.max(0, collector.getCollectionTime());
        }
        return millis;
    }

    private static String backend() {
        dev.engine_room.flywheel.api.backend.Backend backend =
                dev.engine_room.flywheel.api.backend.BackendManager.currentBackend();
        Object id = dev.engine_room.flywheel.api.backend.Backend.REGISTRY.getId(backend);
        return String.valueOf(id);
    }

    /** Flywheel's own count of instancers, read from its backend by reflection; -1 when unknown. */
    static int instancers() {
        try {
            Minecraft mc = Minecraft.getInstance();
            Class<?> managers = Class.forName("dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl");
            Object manager = managers.getMethod("get", net.minecraft.world.level.LevelAccessor.class).invoke(null, mc.level);
            if (manager == null) {
                return -1;
            }
            Object engine = manager.getClass().getMethod("getEngineImpl").invoke(manager);
            Object drawManager = engine.getClass().getMethod("drawManager").invoke(engine);
            Object instancers = drawManager.getClass().getMethod("instancers").invoke(drawManager);
            return ((java.util.Map<?, ?>) instancers).size();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return -1;
        }
    }
}
