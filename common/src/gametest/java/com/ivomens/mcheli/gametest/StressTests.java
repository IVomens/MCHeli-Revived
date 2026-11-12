package com.ivomens.mcheli.gametest;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Stress scenarios for profiling. Run one at a time with {@code ./gradlew runStressTest -Pscenario=<name>}
 * (see build.gradle); each logs tick-time statistics while the load runs. Scenarios:
 * <ul>
 *   <li>{@code parked}: a large parked fleet with nobody aboard, like a busy airbase;</li>
 *   <li>{@code flying}: manned helicopters and planes in the air at full throttle;</li>
 *   <li>{@code firefight}: two lines of manned vehicles firing every weapon at each other;</li>
 *   <li>{@code crowd}: 100 parked vehicles with a cow and an item next to each, and 200 more cows and
 *   items in a pasture beside them; every mob and item move asks for nearby aircraft part boxes.</li>
 * </ul>
 * The tests of the scenarios not asked for pass at once. The loader's game test glue calls the two
 * tick methods around every server tick (see {@code GameTestHooks}).
 */
public class StressTests {

    static final String SCENARIO = System.getProperty("mcheli.stress", "");
    private static final int WARMUP_TICKS = 100;
    private static final int MEASURE_TICKS = 600;

    private static long tickStart;
    private static long[] samples;
    private static int sampleCount;
    private static boolean measuring;

    /** One test per scenario, named and set up as the {@code @GameTest} methods they used to be. */
    public static List<TestFunction> scenarios() {
        return List.of(
                scenario("parked", StressTests::parked),
                scenario("flying", StressTests::flying),
                scenario("firefight", StressTests::firefight),
                scenario("crowd", StressTests::crowd));
    }

    private static TestFunction scenario(String name, java.util.function.Consumer<GameTestHelper> run) {
        return new TestFunction("mcheli_stress", name, "mcheli:empty", 2000, 0L, false, run);
    }

    static void onTickStart() {
        tickStart = System.nanoTime();
    }

    static void onTickEnd() {
        if (measuring && sampleCount < samples.length) {
            long took = System.nanoTime() - tickStart;
            samples[sampleCount++] = took;
            if (took > 30_000_000L) {
                ModLogger.get().info("[stress] slow tick {} ms ending at {}", took / 1_000_000, java.time.LocalTime.now());
            }
        }
    }

    private static void parked(GameTestHelper h) {
        if (!SCENARIO.equals("parked")) {
            h.succeed();
            return;
        }
        List<AircraftEntity> fleet = spawnGrid(h, 300, 14, 2, info -> true);
        measure(h, "parked", () -> {
            // what the vehicles that are still awake are waiting on
            Map<String, Integer> states = new TreeMap<>();
            for (AircraftEntity ac : fleet) {
                states.merge(ac.rest().status().split(" ")[0], 1, Integer::sum);
            }
            return fleet.size() + " vehicles, nobody aboard " + states;
        });
    }

    private static void flying(GameTestHelper h) {
        if (!SCENARIO.equals("flying")) {
            h.succeed();
            return;
        }
        List<AircraftEntity> fleet = spawnGrid(h, 60, 24, 40, info -> info.file().canRide()
                && (VehicleTypes.registry(VehicleKind.HELICOPTER).values().contains(info)
                        || VehicleTypes.registry(VehicleKind.PLANE).values().contains(info)));
        List<ServerPlayer> crew = board(h, fleet);
        h.onEachTick(() -> {
            for (AircraftEntity ac : fleet) {
                ac.engine().setThrottle(1.0);
                if (ac instanceof PlaneEntity && ac.getDeltaMovement().horizontalDistanceSqr() < 0.25) {
                    // planes need airspeed to stay up; give them a push along their heading
                    Vec3 look = Vec3.directionFromRotation(0, ac.getYRot());
                    ac.setDeltaMovement(look.scale(1.2));
                }
            }
        });
        measure(h, "flying", () -> fleet.size() + " aircraft, " + aboard(fleet, crew) + " crewed");
    }

    private static void firefight(GameTestHelper h) {
        if (!SCENARIO.equals("firefight")) {
            h.succeed();
            return;
        }
        List<AircraftEntity> fleet = spawnGrid(h, 40, 12, 2, info -> info.file().canRide() && !info.isUAV());
        for (int i = 0; i < fleet.size(); i++) {
            // two facing lines
            fleet.get(i).setYRot(i % 2 == 0 ? 0 : 180);
        }
        List<ServerPlayer> crew = board(h, fleet);
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            for (int i = 0; i < fleet.size(); i++) {
                AircraftEntity ac = fleet.get(i);
                ServerPlayer p = crew.get(i);
                if (ac.isRemoved() || ac.seats().seatIdOf(p) < 0 || ac.weapons().count() == 0) continue;
                if (tick[0] % 40 == 0) ac.weapons().select(p, (tick[0] / 40) % ac.weapons().count());
                ac.weapons().fire(p);
            }
        });
        measure(h, "firefight", () -> {
            int alive = 0;
            for (AircraftEntity ac : fleet) if (!ac.isRemoved()) alive++;
            return fleet.size() + " vehicles (" + alive + " left), entities in level: " + countEntities(h.getLevel());
        });
    }

    private static void crowd(GameTestHelper h) {
        if (!SCENARIO.equals("crowd")) {
            h.succeed();
            return;
        }
        List<AircraftEntity> fleet = spawnGrid(h, 100, 14, 2, info -> true);
        ServerLevel level = h.getLevel();
        for (AircraftEntity ac : fleet) {
            spawnCowAndItem(level, ac.getX() + 4, ac.getY(), ac.getZ());
        }
        // a pasture beside the airfield, in loaded chunks but away from every vehicle
        Vec3 origin = h.absoluteVec(new Vec3(16, 1, 16));
        for (int i = 0; i < 200; i++) {
            double x = origin.x + 100 + (i % 10) * 6;
            double z = origin.z - 60 + (i / 10) * 6;
            level.setChunkForced(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4, true);
            spawnCowAndItem(level, x, origin.y + 2, z);
        }
        measure(h, "crowd", () -> fleet.size() + " vehicles, entities in level: " + countEntities(level));
    }

    private static void spawnCowAndItem(ServerLevel level, double x, double y, double z) {
        Cow cow = EntityType.COW.create(level);
        if (cow != null) {
            cow.moveTo(x, y, z, 0, 0);
            level.addFreshEntity(cow);
        }
        ItemEntity item = new ItemEntity(level, x, y, z + 2, new ItemStack(Items.IRON_INGOT));
        item.setUnlimitedLifetime();
        level.addFreshEntity(item);
    }

    // ------------------------------------------------------------------ helpers

    private static List<AircraftEntity> spawnGrid(GameTestHelper h, int count, int spacing, int height,
                                                  Predicate<VehicleSpec> filter) {
        ServerLevel level = h.getLevel();
        List<VehicleSpec> infos = new ArrayList<>();
        for (var registry : List.of(VehicleTypes.registry(VehicleKind.HELICOPTER),
                VehicleTypes.registry(VehicleKind.PLANE), VehicleTypes.registry(VehicleKind.TANK),
                VehicleTypes.registry(VehicleKind.VEHICLE), VehicleTypes.registry(VehicleKind.SHIP))) {
            for (VehicleSpec info : registry.values()) {
                if (filter.test(info) && VehicleTypes.item(info).orElse(null) instanceof AircraftItem) infos.add(info);
            }
        }
        List<AircraftEntity> spawned = new ArrayList<>();
        Vec3 origin = h.absoluteVec(new Vec3(16, 1, 16));
        int side = (int) Math.ceil(Math.sqrt(count));
        for (int i = 0; i < count && !infos.isEmpty(); i++) {
            double x = origin.x + (i % side - side / 2.0) * spacing;
            double z = origin.z + (i / side - side / 2.0) * spacing;
            level.setChunkForced(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4, true);
            VehicleSpec info = infos.get(i % infos.size());
            AircraftItem item = (AircraftItem) VehicleTypes.item(info).orElse(null);
            AircraftEntity ac = item.createAircraft(level, x, origin.y + height, z, new ItemStack(item));
            if (ac != null && level.addFreshEntity(ac)) spawned.add(ac);
        }
        return spawned;
    }

    private static List<ServerPlayer> board(GameTestHelper h, List<AircraftEntity> fleet) {
        List<ServerPlayer> crew = new ArrayList<>();
        for (AircraftEntity ac : fleet) {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            p.moveTo(ac.getX() + 1, ac.getY(), ac.getZ());
            crew.add(p);
        }
        for (int t = 5; t <= 65; t += 10) {
            h.runAfterDelay(t, () -> {
                for (int i = 0; i < fleet.size(); i++) {
                    if (fleet.get(i).seats().seatIdOf(crew.get(i)) < 0) {
                        fleet.get(i).boarding().interact(crew.get(i), InteractionHand.MAIN_HAND);
                    }
                }
            });
        }
        return crew;
    }

    private static int aboard(List<AircraftEntity> fleet, List<ServerPlayer> crew) {
        int n = 0;
        for (int i = 0; i < fleet.size(); i++) if (fleet.get(i).seats().seatIdOf(crew.get(i)) >= 0) n++;
        return n;
    }

    private static int countEntities(ServerLevel level) {
        int n = 0;
        for (Entity ignored : level.getAllEntities()) n++;
        return n;
    }

    private static void measure(GameTestHelper h, String name, java.util.function.Supplier<String> describe) {
        h.runAfterDelay(WARMUP_TICKS, () -> {
            samples = new long[MEASURE_TICKS];
            sampleCount = 0;
            measuring = true;
        });
        h.runAfterDelay(WARMUP_TICKS + MEASURE_TICKS + 5, () -> {
            measuring = false;
            long[] s = Arrays.copyOf(samples, sampleCount);
            Arrays.sort(s);
            double mean = Arrays.stream(s).average().orElse(0) / 1e6;
            ModLogger.get().info("[stress] {}: {} | ticks={} mean={} p50={} p95={} p99={} max={} ms", name, describe.get(), s.length,
                    fmt(mean), fmt(pct(s, 50)), fmt(pct(s, 95)), fmt(pct(s, 99)), fmt(s.length == 0 ? 0 : s[s.length - 1] / 1e6));
            h.succeed();
        });
    }

    private static double pct(long[] sorted, int p) {
        if (sorted.length == 0) return 0;
        return sorted[Math.min(sorted.length - 1, sorted.length * p / 100)] / 1e6;
    }

    private static String fmt(double ms) {
        return String.format(Locale.ROOT, "%.2f", ms);
    }
}
