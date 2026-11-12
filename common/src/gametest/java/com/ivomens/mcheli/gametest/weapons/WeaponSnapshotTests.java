package com.ivomens.mcheli.gametest.weapons;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.gametest.golden.GoldenMode;
import com.ivomens.mcheli.gametest.golden.PinnedSettings;
import com.ivomens.mcheli.gametest.snapshot.TextGolden;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Weapon firing snapshots (docs/ARCHITECTURE.md Part 2, §5.4): every bundled weapon is fired once on
 * the {@link WeaponRange}, from the first vehicle that carries it (or held by a soldier when none
 * does), and the shot is compared with {@code golden/weapons/shots/<weapon>.shot}. Guided kinds get
 * an armor stand downrange as their lock target, floating for air-to-air missiles.
 * <p>
 * Countermeasure snapshots fire a heat seeker and a radar missile at a parked vehicle that uses
 * nothing, a flare, chaff, its APS or the iron curtain five ticks after launch
 * ({@code golden/weapons/countermeasures}). Besides matching the golden, flares must decoy the heat seeker
 * and not the radar missile, and chaff the radar missile and not the heat seeker.
 * <p>
 * Every case is its own batch, so the shots run one after another and cannot see each other.
 */
public class WeaponSnapshotTests {

    /**
     * Ticks allowed per run for boarding and waiting for the weapon (at most 400, see {@link FiringRun}),
     * with room to spare. Loading the range takes none: it happens between two ticks.
     */
    private static final int SETUP_TICKS = 1000;

    /** The infrared AIM-9 Sidewinder and the active radar AIM-120 AMRAAM. */
    private static final String HEAT_SEEKER = "aim9";
    private static final String RADAR_MISSILE = "aim120";

    /** Whether the missile of a countermeasure case must be decoyed, by golden path; cases not listed hit. */
    private static final Map<String, Boolean> DECOYED = Map.of(
            "weapons/countermeasures/heat_seeker_vs_flare.shot", true,
            "weapons/countermeasures/radar_vs_chaff.shot", true);
    /** The cases checked against {@link #DECOYED}: the decoys against both kinds of seeker. */
    private static final Set<String> DECOY_CASES = Set.of("_vs_flare.shot", "_vs_chaff.shot");

    private static FiringRun current;

    public static List<TestFunction> weaponShots() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return List.of();
        }
        List<FiringScenario> scenarios = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (WeaponSpec weapon : Arsenal.weapons()) {
            String testName = Arsenal.testName("weapon", weapon.name());
            if (!names.add(testName)) {
                throw new IllegalStateException("two weapons map to the test name " + testName);
            }
            scenarios.add(new FiringScenario(testName, "weapons/shots/" + weapon.name() + ".shot", weapon.name(),
                    Arsenal.lookup(Arsenal.carrierOf(weapon.name())),
                    Arsenal.targetFor(weapon),
                    FiringScenario.none(), FiringScenario.Countermeasure.NONE));
        }
        return tests(scenarios);
    }

    public static List<TestFunction> countermeasures() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return List.of();
        }
        // the AIM-9 is a heat seeker and the AIM-120 a radar missile as loaded
        List<FiringScenario> scenarios = new ArrayList<>();
        addMissile(scenarios, "heat_seeker", Arsenal.firstWeapon(w -> w.name().equals(HEAT_SEEKER)));
        addMissile(scenarios, "radar", Arsenal.firstWeapon(w -> w.name().equals(RADAR_MISSILE)));
        return tests(scenarios);
    }

    /** One missile against the same flare-carrying target, using each countermeasure in turn. */
    private static void addMissile(List<FiringScenario> scenarios, String kind, WeaponSpec missile) {
        if (missile == null) {
            throw new IllegalStateException("no bundled " + kind + " missile");
        }
        VehicleSpec target = Arsenal.firstVehicle(info -> info.armament().haveFlare());
        for (FiringScenario.Countermeasure countermeasure : FiringScenario.Countermeasure.values()) {
            String name = kind + "_vs_" + countermeasure.name().toLowerCase(Locale.ROOT);
            scenarios.add(new FiringScenario(Arsenal.testName("countermeasure", name),
                    "weapons/countermeasures/" + name + ".shot", missile.name(),
                    Arsenal.lookup(Arsenal.carrierOf(missile.name())), FiringScenario.Target.VEHICLE,
                    Arsenal.lookup(target), countermeasure));
        }
    }

    private static List<TestFunction> tests(List<FiringScenario> scenarios) {
        int repetitions = TextGolden.repetitions();
        List<TestFunction> tests = new ArrayList<>();
        for (FiringScenario scenario : scenarios) {
            int timeout = repetitions * (SETUP_TICKS + FiringScenario.FLIGHT_TICKS);
            tests.add(new TestFunction(scenario.testName(), scenario.testName(), "mcheli:empty", timeout, 0, true,
                    helper -> run(helper, scenario, repetitions)));
        }
        return tests;
    }

    private static void run(GameTestHelper helper, FiringScenario scenario, int repetitions) {
        if (current != null) {
            current.abort();
        }
        FiringRun run = new FiringRun(scenario, helper, repetitions);
        current = run;
        run.start();
        helper.onEachTick(() -> {
            run.tick();
            if (!run.isFinished()) {
                return;
            }
            if (current == run) {
                current = null;
            }
            String problem = run.failure() != null ? run.failure() : decoyProblem(scenario, run.recordings());
            if (problem == null) {
                problem = TextGolden.check(scenario.goldenPath(), header(scenario), run.recordings());
            }
            if (problem != null) {
                throw new GameTestAssertException(scenario.goldenPath() + ": " + problem);
            }
            helper.succeed();
        });
    }

    /**
     * For a flare or chaff case, a problem when the missile was decoyed though it should hit or the other way
     * round; a decoyed missile is removed without exploding.
     */
    @Nullable
    private static String decoyProblem(FiringScenario scenario, List<String> recordings) {
        String path = scenario.goldenPath();
        if (DECOY_CASES.stream().noneMatch(path::endsWith)) {
            return null;
        }
        boolean expected = DECOYED.getOrDefault(path, false);
        for (String recording : recordings) {
            if (!recording.contains("spawned #0")) {
                return "the missile was not fired";
            }
            boolean decoyed = !recording.contains("explosion by #0");
            if (decoyed != expected) {
                return expected ? "the missile was not decoyed" : "the missile was decoyed";
            }
        }
        return null;
    }

    private static String header(FiringScenario scenario) {
        return "{\"schema\":1,\"weapon\":\"" + scenario.weapon() + "\",\"seed\":" + FiringScenario.SEED
                + ",\"flightTicks\":" + FiringScenario.FLIGHT_TICKS + ",\"range\":" + WeaponRange.describe()
                + ",\"pinned\":" + PinnedSettings.describe() + ",\"commit\":\"" + GoldenMode.commit() + "\"}";
    }
}
