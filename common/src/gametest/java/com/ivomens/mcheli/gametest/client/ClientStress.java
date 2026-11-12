package com.ivomens.mcheli.gametest.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.bench.BenchmarkLayout;
import com.ivomens.mcheli.bench.BenchmarkScene;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.vehicle.client.render.VehicleSnapshots;
import com.ivomens.mcheli.vehicle.client.render.flywheel.FlywheelVehicleStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Client render load test, active with -Dmcheli.clientStress=N: parks N vehicles in rows from 15 to
 * about 180 blocks in front of the player (an airbase seen from the tower) and logs frame times. With
 * -Dmcheli.clientStress.moving=M another M tanks and helicopters drive back and forth in a lane in
 * front of the parked rows, so their pose changes every frame.
 * <p>
 * With -Dmcheli.clientStress.orbit=true it builds the scene of the client benchmark instead (N
 * vehicles, one in ten moving with rotors running, damaged helicopters smoking) and circles the camera
 * around it, one turn in 20 s as the benchmark does; it measures the first turn (what the benchmark
 * measures) and two more, when everything has been seen.
 * <p>
 * Each measured window logs {@code [clientstress]} with frame time percentiles, the 1 % low and the
 * spikes (frames over twice the median of the 101 before them), and with Flywheel installed what the
 * backend made in the window and the spikes {@link FlywheelProbe} explained. The loader's game test
 * layer calls the hooks through {@link ClientSceneHooks}.
 */
public final class ClientStress {
    private static final int COUNT = Integer.getInteger("mcheli.clientStress", 0);
    private static final int MOVING = Integer.getInteger("mcheli.clientStress.moving", 0);
    private static final boolean ORBIT = Boolean.getBoolean("mcheli.clientStress.orbit");
    /** Half the length of the lane the moving vehicles drive along, in blocks. */
    private static final double LANE_HALF_LENGTH = 40.0;
    private static final double LANE_SPEED = 0.01;
    /** One orbit, as long as the benchmark's orbit step. */
    private static final int ORBIT_TICKS = 400;
    /** Client ticks from the spawn until the orbit starts: the vehicles arrive, then the camera settles. */
    private static final int ORBIT_ARRIVE = 100;
    private static final int ORBIT_SETTLE = 60;
    private static final List<Window> WINDOWS = ORBIT
            ? List.of(orbitWindow(1), orbitWindow(2), orbitWindow(3))
            : List.of(new Window("parked", 300, 600));
    private static final int MEDIAN_OF = 101;

    /**
     * At least 12 to a row, and more for big counts: the back row has to stay within the 16 chunks
     * the client asks the server for, or its vehicles are never sent.
     */
    private static final int MAX_ROWS = 10;
    private static final int PER_ROW = Math.max(12, (COUNT + MAX_ROWS - 1) / MAX_ROWS);
    private static volatile boolean clientReady;
    private static volatile boolean spawned;
    private static int clientTicks;
    private static long lastFrameStart;
    private static final long[] frames = new long[20000];
    private static int frameCount;
    private static boolean recording;
    private static final List<AircraftEntity> moving = new ArrayList<>();
    private static Vec3 laneOrigin;
    private static int serverTicks;
    private static volatile BenchmarkScene scene;
    private static volatile BenchmarkLayout layout;
    private static float orbitYaw;
    /** The Flywheel backend's counters when the window started. */
    private static FlywheelVehicleStats.Counts measureStart = FlywheelVehicleStats.counts();

    private record Window(String name, int from, int to) {
    }

    /** The {@code turn}th turn of the orbit (from 1). */
    private static Window orbitWindow(int turn) {
        int start = ORBIT_ARRIVE + ORBIT_SETTLE + (turn - 1) * ORBIT_TICKS;
        return new Window("orbit" + turn, start, start + ORBIT_TICKS);
    }

    private ClientStress() {
    }

    static void onServerTick(MinecraftServer server) {
        if (spawned) {
            if (scene != null) {
                scene.tick();
            } else {
                driveMovingVehicles();
            }
            return;
        }
        // wait until the client holds the chunks of the back row, or those vehicles are never sent
        if (COUNT <= 0 || !clientReady) return;
        ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
        if (ORBIT) {
            spawnBenchmarkScene(p);
            spawned = true;
            return;
        }
        spawned = true;
        ServerLevel level = p.serverLevel();
        List<VehicleSpec> infos = new ArrayList<>();
        infos.addAll(VehicleTypes.registry(VehicleKind.PLANE).values());
        infos.addAll(VehicleTypes.registry(VehicleKind.HELICOPTER).values());
        infos.addAll(VehicleTypes.registry(VehicleKind.TANK).values());
        for (int i = 0; i < COUNT; i++) {
            VehicleSpec info = infos.get(i % infos.size());
            if (!(VehicleTypes.item(info).orElse(null) instanceof AircraftItem item)) continue;
            double x = p.getX() + (i % PER_ROW - PER_ROW / 2.0) * 16;
            double z = p.getZ() + 15 + (i / PER_ROW) * 18;
            level.setChunkForced((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4, true);
            AircraftEntity ac = item.createAircraft(level, x, p.getY(), z, new ItemStack(item));
            if (ac != null) level.addFreshEntity(ac);
        }
        spawnMovingVehicles(level, p);
        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.teleportTo(level, p.getX(), p.getY() + 12, p.getZ() - 10, java.util.Set.of(), 0F, 18F);
    }

    /** The client benchmark's scene around the player, with the camera at the start of its orbit. */
    private static void spawnBenchmarkScene(ServerPlayer p) {
        int x = Mth.floor(p.getX());
        int z = Mth.floor(p.getZ());
        int ground = p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        Vec3 start = new Vec3(x + 0.5, ground, z + 0.5);
        BenchmarkScene created = BenchmarkScene.begin(p, start);
        created.populate(COUNT);
        BenchmarkLayout planned = new BenchmarkLayout(start, COUNT);
        Vec3 at = planned.orbitPosition(0);
        created.moveCamera(p, at, 0.0F, 30.0F);
        layout = planned;
        scene = created;
    }

    /** Tanks and helicopters, alternately, side by side in a lane 8 blocks in front of the parked rows. */
    private static void spawnMovingVehicles(ServerLevel level, ServerPlayer p) {
        List<VehicleSpec> tanks = new ArrayList<>(VehicleTypes.registry(VehicleKind.TANK).values());
        List<VehicleSpec> helis = new ArrayList<>(VehicleTypes.registry(VehicleKind.HELICOPTER).values());
        laneOrigin = new Vec3(p.getX(), p.getY(), p.getZ() + 7);
        for (int i = 0; i < MOVING && !tanks.isEmpty() && !helis.isEmpty(); i++) {
            VehicleSpec info = i % 2 == 0 ? tanks.get(i / 2 % tanks.size()) : helis.get(i / 2 % helis.size());
            if (!(VehicleTypes.item(info).orElse(null) instanceof AircraftItem item)) continue;
            AircraftEntity ac = item.createAircraft(level, laneOrigin.x, laneOrigin.y, laneOrigin.z, new ItemStack(item));
            if (ac != null && level.addFreshEntity(ac)) moving.add(ac);
        }
    }

    /** Moves every lane vehicle along its own phase of a back-and-forth path, facing where it goes. */
    private static void driveMovingVehicles() {
        serverTicks++;
        for (int i = 0; i < moving.size(); i++) {
            AircraftEntity ac = moving.get(i);
            double phase = serverTicks * LANE_SPEED * 2 * Math.PI + i * 0.7;
            double x = laneOrigin.x + Math.sin(phase) * LANE_HALF_LENGTH;
            double z = laneOrigin.z + (i - moving.size() / 2.0) * 1.5;
            double y = laneOrigin.y + (i % 2 == 0 ? 0.0 : 4.0);
            double velocity = Math.cos(phase) * LANE_HALF_LENGTH * LANE_SPEED * 2 * Math.PI;
            ac.setPos(x, y, z);
            ac.setDeltaMovement(velocity, 0.0, 0.0);
            ac.setYRot(velocity >= 0 ? -90.0F : 90.0F);
        }
    }

    /** Frame time is the time between the starts of two frames, as in the client benchmark. */
    static void onFrameStart() {
        long now = System.nanoTime();
        if (recording && lastFrameStart != 0 && frameCount < frames.length) {
            frames[frameCount++] = now - lastFrameStart;
        }
        lastFrameStart = now;
    }

    static void onFrameEnd() {
    }

    static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (COUNT <= 0 || mc.player == null) return;
        if (!clientReady) {
            // far enough to see the back rows, and uncapped so the frame time is the real cost
            mc.options.renderDistance().set(ORBIT ? 12 : 16);
            mc.options.framerateLimit().set(260);
            mc.options.enableVsync().set(false);
            if (mc.player.tickCount == 1) {
                // the server caps the view at what the client asks for, and that is only sent here
                mc.options.broadcastOptions();
            }
            double backRow = ORBIT ? mc.player.getZ() : mc.player.getZ() + 15 + (COUNT / PER_ROW) * 18;
            clientReady = mc.level.getChunkSource().hasChunk((int) Math.floor(mc.player.getX()) >> 4, (int) Math.floor(backRow) >> 4);
            return;
        }
        if (!spawned) return;
        clientTicks++;
        if (ORBIT && layout != null && clientTicks >= ORBIT_ARRIVE) {
            int orbitTick = Math.max(0, clientTicks - ORBIT_ARRIVE - ORBIT_SETTLE);
            placeOnOrbit(mc.player, layout, (orbitTick % ORBIT_TICKS) / (double) ORBIT_TICKS);
        }
        for (Window window : WINDOWS) {
            if (clientTicks == window.from()) {
                startWindow();
            }
            if (clientTicks == window.to()) {
                endWindow(mc, window);
            }
        }
        Window lastWindow = WINDOWS.getLast();
        if (clientTicks == lastWindow.from() + 80) {
            Screenshot.grab(mc.gameDirectory, "clientstress.png", mc.getMainRenderTarget(), c -> {});
        }
        if (clientTicks == lastWindow.to()) {
            List<AircraftEntity> vehicles = vehicles(mc);
            RenderEquivalenceCheck.run("clientstress", vehicles, mc.gameRenderer.getMainCamera().getPosition(),
                    VehicleSnapshots.pixelsAtOneBlock());
            mc.stop();
        }
    }

    /** Puts the player on the orbit at {@code turn} (0 to 1), looking at the middle of the rows. */
    private static void placeOnOrbit(LocalPlayer player, BenchmarkLayout planned, double turn) {
        Vec3 at = planned.orbitPosition(turn);
        Vec3 look = planned.centre().subtract(at);
        float rawYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        orbitYaw += Mth.wrapDegrees(rawYaw - orbitYaw);
        player.getAbilities().flying = true;
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(at.x, at.y, at.z);
        player.setYRot(orbitYaw);
        player.setYHeadRot(orbitYaw);
        player.setXRot((float) Math.toDegrees(Math.atan2(-look.y, look.horizontalDistance())));
    }

    private static void startWindow() {
        frameCount = 0;
        lastFrameStart = 0;
        recording = true;
        measureStart = FlywheelVehicleStats.counts();
        FlywheelProbe.startWindow();
    }

    private static void endWindow(Minecraft mc, Window window) {
        recording = false;
        long[] f = Arrays.copyOf(frames, frameCount);
        long[] sorted = f.clone();
        Arrays.sort(sorted);
        double mean = Arrays.stream(f).average().orElse(0) / 1e6;
        int worst = Math.max(1, sorted.length / 100);
        double worstMean = 0;
        for (int i = sorted.length - worst; i < sorted.length; i++) worstMean += sorted[i] / 1e6 / worst;
        ModLogger.get().info("[clientstress] window={} vehicles={} moving={} frames={} mean={} ms p50={} p95={} "
                        + "p99={} max={} fps~{} low1%={} spikes={}", window.name(), vehicles(mc).size(),
                ORBIT ? new BenchmarkLayout(Vec3.ZERO, COUNT).moving() : MOVING, f.length, fmt(mean),
                fmt(pct(sorted, 50)), fmt(pct(sorted, 95)), fmt(pct(sorted, 99)),
                fmt(sorted.length == 0 ? 0 : sorted[sorted.length - 1] / 1e6), fmt(mean > 0 ? 1000 / mean : 0),
                fmt(worstMean > 0 ? 1000 / worstMean : 0), spikes(f));
        checkFlywheelSteadyState(window);
    }

    /** Frames over twice the median of the {@value #MEDIAN_OF} frames before them (from the 30th on). */
    private static int spikes(long[] f) {
        int spikes = 0;
        for (int i = 30; i < f.length; i++) {
            long[] before = Arrays.copyOfRange(f, Math.max(0, i - MEDIAN_OF), i);
            Arrays.sort(before);
            if (f[i] > 2 * before[before.length / 2]) spikes++;
        }
        return spikes;
    }

    private static List<AircraftEntity> vehicles(Minecraft mc) {
        List<AircraftEntity> vehicles = new ArrayList<>();
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof AircraftEntity ac) vehicles.add(ac);
        return vehicles;
    }

    /**
     * With Flywheel drawing, the measured frames of a parked scene must not make meshes, models or
     * instances, nor make Flywheel upload its mesh pool: only moving parts may be rewritten. The
     * moving and orbiting scenes are logged for information.
     */
    private static void checkFlywheelSteadyState(Window window) {
        FlywheelVehicleStats.Counts measured = FlywheelVehicleStats.counts().since(measureStart);
        if (measured.frames() == 0) {
            return;
        }
        String verdict = MOVING > 0 || ORBIT ? "INFO" : measured.createsNothing() ? "PASS" : "FAIL";
        ModLogger.get().info("[flywheelsteady] {} window={} {}", verdict, window.name(), measured);
        ModLogger.get().info("[flywheelspikes] window={} {}", window.name(), FlywheelProbe.window());
    }

    private static double pct(long[] sorted, int p) {
        if (sorted.length == 0) return 0;
        int rank = (int) Math.ceil(p / 100.0 * sorted.length);
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))] / 1e6;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
