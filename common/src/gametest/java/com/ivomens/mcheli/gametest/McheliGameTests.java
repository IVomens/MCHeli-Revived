package com.ivomens.mcheli.gametest;

import com.ivomens.mcheli.gametest.api.ApiGameTests;
import com.ivomens.mcheli.gametest.api.HudWidgetGameTests;
import com.ivomens.mcheli.gametest.admin.AdminCommandGameTests;
import com.ivomens.mcheli.gametest.admin.LimitGameTests;
import com.ivomens.mcheli.gametest.admin.OwnershipGameTests;
import com.ivomens.mcheli.gametest.bench.ServerBenchmark;
import com.ivomens.mcheli.gametest.controls.JoystickControlGameTests;
import com.ivomens.mcheli.gametest.definitions.DefinitionGoldenTests;
import com.ivomens.mcheli.gametest.fixtures.SaveFixtureTests;
import com.ivomens.mcheli.gametest.golden.GoldenTrajectoryTests;
import com.ivomens.mcheli.gametest.network.MissileWarningGameTests;
import com.ivomens.mcheli.gametest.network.NetworkSyncGameTests;
import com.ivomens.mcheli.gametest.network.ExplosionEffectGameTests;
import com.ivomens.mcheli.gametest.network.ProjectileSyncGameTests;
import com.ivomens.mcheli.gametest.network.RadarContactKindGameTests;
import com.ivomens.mcheli.gametest.weapons.FrozenProjectileTests;
import com.ivomens.mcheli.gametest.weapons.LockOnTests;
import com.ivomens.mcheli.gametest.weapons.WeaponSnapshotTests;
import com.ivomens.mcheli.gametest.wingman.WingmanGearGameTests;
import com.ivomens.mcheli.gametest.wingman.WingmanScenarioTests;
import net.minecraft.gametest.framework.TestFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * Every MCHeli game test, for the loaders to register: each loader's game test layer hands this list to
 * its game test framework, together with the tick and join hooks of {@link GameTestHooks}. All tests use
 * the {@code mcheli:empty} structure and set their own batch, so their names, batches and order are the
 * same on every loader.
 */
public final class McheliGameTests {

    private McheliGameTests() {
    }

    public static List<TestFunction> all() {
        if (ServerBenchmark.enabled()) {
            return ServerBenchmark.tests();
        }
        List<TestFunction> tests = new ArrayList<>();
        tests.addAll(VehicleGameTests.vehicles());
        tests.addAll(VehiclePhysicsGameTests.physics());
        tests.addAll(StressTests.scenarios());
        tests.addAll(GoldenTrajectoryTests.goldenTrajectories());
        tests.addAll(SaveFixtureTests.saveFixtures());
        tests.addAll(DefinitionGoldenTests.definitions());
        tests.addAll(WeaponSnapshotTests.weaponShots());
        tests.addAll(WeaponSnapshotTests.countermeasures());
        tests.addAll(LockOnTests.lockOn());
        tests.addAll(WingmanScenarioTests.scenarios());
        tests.addAll(WingmanGearGameTests.gear());
        tests.addAll(WingmanOwnershipGameTests.ownership());
        tests.addAll(WingmanServerStopGameTests.serverStop());
        tests.addAll(LiveryGameTests.liveries());
        tests.addAll(MultiplayGameTests.multiplay());
        tests.addAll(ApiGameTests.api());
        tests.addAll(HudWidgetGameTests.widgets());
        tests.addAll(DispenserGameTests.dispensers());
        tests.addAll(OwnershipGameTests.ownership());
        tests.addAll(LimitGameTests.limits());
        tests.addAll(AdminCommandGameTests.commands());
        tests.addAll(InspectorGameTests.inspector());
        tests.addAll(JoystickControlGameTests.controls());
        if (StressTests.SCENARIO.isEmpty()) {
            tests.addAll(NetworkSyncGameTests.network());
            tests.addAll(ProjectileSyncGameTests.projectiles());
            tests.addAll(FrozenProjectileTests.lifetime());
            tests.addAll(RadarContactKindGameTests.kinds());
            tests.addAll(ExplosionEffectGameTests.effects());
            tests.addAll(MissileWarningGameTests.seekers());
        }
        // -Dmcheli.gametest.batch=<batch> runs one batch alone, for working on it
        String batch = System.getProperty("mcheli.gametest.batch", "");
        if (!batch.isEmpty()) {
            tests.removeIf(test -> !test.batchName().equals(batch));
        }
        return tests;
    }
}
