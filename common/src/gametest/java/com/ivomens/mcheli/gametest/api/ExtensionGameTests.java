package com.ivomens.mcheli.gametest.api;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.api.vehicle.extension.VehicleExtensions;
import com.ivomens.mcheli.content.bind.YamlReader;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.content.def.vehicle.VehicleSchema;
import com.ivomens.mcheli.content.report.ContentReport;
import com.ivomens.mcheli.content.report.Diagnostic;
import com.ivomens.mcheli.content.report.ReportCollector;
import com.ivomens.mcheli.extension.ConfiguredBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Vehicle extensions through the public API: the behaviours and the movement model {@link TestVehicleExtensions}
 * registers, used by the test pack's {@code api_test_hover}. The hover vehicle flies as the test model says, its
 * behaviours tick before and after the move and hear of its removal, and a file that names extensions nobody
 * registers, or settings an extension rejects, still loads with warnings in the content report.
 */
public final class ExtensionGameTests {

    private static final String BATCH = "mcheli_api";
    private static final String HOVER_VEHICLE = "api_test_hover";
    /** {@code HoverHeight} and {@code Speed} in the test pack's file. */
    private static final double HOVER_HEIGHT = 1.5;
    private static final double SPEED = 0.1;
    private static final int FORWARD_FROM = 40;
    private static final int FORWARD_TICKS = 20;

    private ExtensionGameTests() {
    }

    public static List<TestFunction> extensions() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return List.of();
        }
        return List.of(
                new TestFunction(BATCH, "api_hover_model_moves_and_behaviours_tick", "mcheli:empty", 200, 0, true,
                        ExtensionGameTests::hoverMovesAndBehavioursTick),
                new TestFunction(BATCH, "api_unknown_extensions_warn", "mcheli:empty", 20, 0, true,
                        ExtensionGameTests::unknownExtensionsWarn));
    }

    /**
     * The hover vehicle rises to its hover height over a stone floor, flies {@code Speed} per tick while the
     * forward key is held and stops when it is let go; the early probe sees the vehicle where the tick starts and
     * the late one where it ends.
     */
    private static void hoverMovesAndBehavioursTick(GameTestHelper h) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        ServerLevel level = h.getLevel();
        if (!(VehicleTypes.get(VehicleKind.VEHICLE, HOVER_VEHICLE).flatMap(VehicleTypes::item).orElse(null)
                instanceof AircraftItem item)) {
            h.fail(HOVER_VEHICLE + " has no item");
            return;
        }
        Vec3 start = h.absoluteVec(new Vec3(8, 2, 4));
        double groundTop = h.absoluteVec(new Vec3(0, 1, 0)).y;
        AircraftEntity ac = item.createAircraft(level, start.x, start.y, start.z, new ItemStack(item));
        ac.initRotationYaw(0.0F);
        level.addFreshEntity(ac);

        VehicleExtensions extensions = McheliApi.get().vehicleExtensions();
        Vec3[] forwardStart = new Vec3[1];
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == FORWARD_FROM) {
                check(Math.abs(ac.getY() - groundTop - HOVER_HEIGHT) < 0.01,
                        "hovers " + (ac.getY() - groundTop) + " above the ground, not " + HOVER_HEIGHT);
                check(ac.getDeltaMovement().horizontalDistance() < 1.0E-9, "drifts without a key held");
                forwardStart[0] = ac.position();
                ac.keys().holdThrottleUp(true);
            } else if (tick[0] == FORWARD_FROM + FORWARD_TICKS) {
                ac.keys().holdThrottleUp(false);
            }
        });
        h.runAfterDelay(FORWARD_FROM + FORWARD_TICKS + 20, () -> {
            TestVehicleExtensions.Probe probe = TestVehicleExtensions.PROBES.get(ac);
            check(probe != null, "the behaviours never ran");
            check(extensions.movementModelOf(ac).equals(Optional.of(TestVehicleExtensions.HOVER)),
                    "movementModelOf " + extensions.movementModelOf(ac));
            check(extensions.behavioursOf(ac).equals(List.of(TestVehicleExtensions.PROBE_EARLY,
                    TestVehicleExtensions.PROBE_LATE)), "behavioursOf " + extensions.behavioursOf(ac));
            check(probe.earlyAdded == 1 && probe.lateAdded == 1, "added " + probe.earlyAdded + "/" + probe.lateAdded);
            check("late".equals(probe.label), "the late probe's settings read as " + probe.label);
            check(probe.earlyTicks >= FORWARD_FROM && probe.lateTicks == probe.earlyTicks,
                    "ticks " + probe.earlyTicks + "/" + probe.lateTicks);
            check(probe.sawForward, "the behaviour never saw the forward key");
            double flown = ac.position().subtract(forwardStart[0]).horizontalDistance();
            check(Math.abs(flown - SPEED * FORWARD_TICKS) < 1.0E-6,
                    "flew " + flown + " with the key held for " + FORWARD_TICKS + " ticks, not " + SPEED * FORWARD_TICKS);
            check(Math.abs(ac.getZ() - forwardStart[0].z - flown) < 1.0E-6, "did not fly south (yaw 0)");
            check(probe.moved.lengthSqr() < 1.0E-12, "still moving after the key was let go: " + probe.moved);
            ac.discard();
        });
        h.succeedWhen(() -> {
            TestVehicleExtensions.Probe probe = TestVehicleExtensions.PROBES.get(ac);
            check(probe != null && probe.earlyRemoved == 1 && probe.lateRemoved == 1,
                    "removed not heard: " + (probe == null ? "no probe" : probe.earlyRemoved + "/" + probe.lateRemoved));
            TestVehicleExtensions.PROBES.remove(ac);
        });
    }

    /** Unknown ids, a malformed id and rejected settings are warnings; the file loads with what is left. */
    private static void unknownExtensionsWarn(GameTestHelper h) {
        List<String> lines = List.of(
                "DisplayName: Broken extensions",
                "Seats:",
                "- Position: [0.0, 1.0, 0.0]",
                "Behaviours:",
                "  mcheli_test:probe_late: { Label: kept }",
                "  mcheli_test:nobody_registers_this: {}",
                "  no_namespace: {}",
                "  mcheli_test:strict: { Required: maybe }",
                "Movement: mcheli_test:no_such_model");
        VehicleFile file;
        ContentReport report;
        try (ReportCollector collector = ReportCollector.open("extension test")) {
            try (ReportCollector.FileScope scope = ReportCollector.enterFile("test", "vehicles/broken.yml")) {
                file = YamlReader.read(VehicleSchema.forKind(VehicleKind.VEHICLE), "broken", "vehicles/broken.yml",
                        lines);
                scope.loaded();
            }
            report = collector.finish();
        }
        List<String> warnings = report.diagnostics().stream()
                .filter(d -> d.severity() == Diagnostic.Severity.WARNING).map(Diagnostic::message).toList();
        expectWarning(warnings, "unknown behaviour 'mcheli_test:nobody_registers_this'");
        expectWarning(warnings, "'no_namespace' is not a behaviour id");
        expectWarning(warnings, "behaviour 'mcheli_test:strict' rejected its settings (Required must be a whole number)");
        expectWarning(warnings, "unknown movement model 'mcheli_test:no_such_model'");
        check(file.addons().movement() == null, "an unknown movement model was kept");
        check(file.addons().behaviours().stream().map(ConfiguredBehaviour::id).toList()
                .equals(List.of(TestVehicleExtensions.PROBE_LATE)), "behaviours " + file.addons().behaviours());
        h.succeed();
    }

    private static void expectWarning(List<String> warnings, String part) {
        check(warnings.stream().anyMatch(w -> w.contains(part)), "no warning with \"" + part + "\" in " + warnings);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
