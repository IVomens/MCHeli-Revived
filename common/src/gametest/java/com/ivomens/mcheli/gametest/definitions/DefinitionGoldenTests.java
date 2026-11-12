package com.ivomens.mcheli.gametest.definitions;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.pack.AddonManager;
import com.ivomens.mcheli.gametest.golden.GoldenMode;
import com.ivomens.mcheli.vehicle.definition.VehicleDefinition;
import com.ivomens.mcheli.vehicle.definition.VehicleDefinitions;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The content safety net: every built-in vehicle definition, dumped as text, must match the committed
 * {@code golden/definitions/<kind>.txt}. A change to the parsers or the definition records that moves
 * any value shows up here as a one-line diff. Re-record with {@code ./gradlew runGameTestServer -Pgolden=record}.
 * <p>
 * Also checks that a content reload publishes new definitions and that entities pick them up.
 */
public class DefinitionGoldenTests {

    /** Not used by the golden trajectories, so reloading it cannot disturb them. */
    private static final String RELOADED_HELICOPTER = "bell47gf";

    public static List<TestFunction> definitions() {
        List<TestFunction> tests = new ArrayList<>();
        // stress runs (-Dmcheli.stress=...) run nothing else
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return tests;
        }
        for (VehicleKind kind : VehicleKind.values()) {
            String name = "definitions_" + kind.name().toLowerCase(Locale.ROOT);
            tests.add(new TestFunction("mcheli_definitions", name, "mcheli:empty", 20, 0, true,
                    helper -> compare(helper, kind)));
        }
        tests.add(new TestFunction("mcheli_definitions", "reload_swaps_definitions", "mcheli:empty", 40, 0, true,
                DefinitionGoldenTests::reloadSwapsDefinitions));
        return tests;
    }

    private static void compare(GameTestHelper helper, VehicleKind kind) {
        List<String> actual = dump(kind);
        Path file = GoldenMode.goldenDir().resolve("definitions").resolve(kind.directory() + ".txt");
        try {
            if (GoldenMode.CURRENT.records()) {
                write(file, actual);
                helper.succeed();
                return;
            }
            if (!Files.isRegularFile(file)) {
                // a new kind's first dump is written straight away, but still fails until it is committed
                write(file, actual);
                helper.fail("there was no golden file; wrote " + file + ", review and commit it");
                return;
            }
            List<String> expected = Files.readAllLines(file, StandardCharsets.UTF_8);
            String difference = firstDifference(expected, actual);
            if (difference != null) {
                helper.fail(file.getFileName() + " " + difference);
                return;
            }
            helper.succeed();
        } catch (IOException e) {
            helper.fail("could not access " + file + ": " + e);
        }
    }

    private static void write(Path file, List<String> lines) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /** The speed multiplier is baked into the definitions, so the header records it. */
    private static List<String> dump(VehicleKind kind) {
        List<String> lines = new ArrayList<>();
        lines.add("# Built-in " + kind.directory() + ", as the physics and gameplay see them. "
                + speedMultiplier(kind));
        for (VehicleDefinition definition : VehicleDefinitions.all()) {
            if (definition.kind() == kind && isBuiltIn(kind, definition.name())) {
                DefinitionDump.write(definition, lines);
            }
        }
        return lines;
    }

    private static String speedMultiplier(VehicleKind kind) {
        return switch (kind) {
            case HELICOPTER -> "AllHeliSpeed=" + McheliConfig.flight().heliSpeed();
            case PLANE -> "AllPlaneSpeed=" + McheliConfig.flight().planeSpeed();
            case SHIP -> "AllShipSpeed=" + McheliConfig.flight().shipSpeed();
            case TANK -> "AllTankSpeed=" + McheliConfig.flight().tankSpeed();
            case VEHICLE -> "No speed multiplier.";
        };
    }

    private static boolean isBuiltIn(VehicleKind kind, String name) {
        return VehicleTypes.registry(kind).entry(name)
                .map(entry -> AddonManager.BUILTIN_ADDON_DOMAIN.equals(entry.location().getAddonDomain()))
                .orElse(false);
    }

    private static String firstDifference(List<String> expected, List<String> actual) {
        int lines = Math.min(expected.size(), actual.size());
        for (int i = 0; i < lines; i++) {
            if (!expected.get(i).equals(actual.get(i))) {
                return "differs at line " + (i + 1) + ":\n  expected " + expected.get(i) + "\n  actual   "
                        + actual.get(i);
            }
        }
        if (expected.size() != actual.size()) {
            return "has " + expected.size() + " lines, the dump " + actual.size();
        }
        return null;
    }

    /**
     * Reloading a definition publishes a whole new set; a vehicle keeps its old, unchanged definition
     * until it takes the new one, the way the reload packet applies it.
     */
    private static void reloadSwapsDefinitions(GameTestHelper helper) {
        VehicleSpec info = VehicleTypes.registry(VehicleKind.HELICOPTER).get(RELOADED_HELICOPTER).orElse(null);
        if (info == null || !(VehicleTypes.item(info).orElse(null) instanceof AircraftItem item)) {
            helper.fail(RELOADED_HELICOPTER + " missing");
            return;
        }
        Vec3 pos = helper.absoluteVec(new Vec3(4, 2, 4));
        AircraftEntity aircraft = item.createAircraft(helper.getLevel(), pos.x, pos.y, pos.z, new ItemStack(item));
        VehicleDefinition before = aircraft.definition();
        long generation = VehicleDefinitions.generation();

        if (!VehicleTypes.registry(VehicleKind.HELICOPTER).reload(RELOADED_HELICOPTER)) {
            helper.fail("reload of " + RELOADED_HELICOPTER + " failed");
            return;
        }
        VehicleDefinition published = VehicleDefinitions.get(VehicleKind.HELICOPTER, RELOADED_HELICOPTER);
        expect(helper, VehicleDefinitions.generation() > generation, "the reload published no new set");
        expect(helper, published != before && before.equals(published),
                "the reloaded definition should be a new, equal object");
        expect(helper, aircraft.definition() == before, "the vehicle changed definition before applying the reload");

        aircraft.changeType(RELOADED_HELICOPTER);
        aircraft.body().onDefinitionReloaded();
        expect(helper, aircraft.definition() == published, "the vehicle did not take the new definition");
        expect(helper, aircraft.spec() == VehicleTypes.registry(VehicleKind.HELICOPTER).get(RELOADED_HELICOPTER).orElse(null),
                "the vehicle did not take the reloaded info");
        aircraft.discard();
        helper.succeed();
    }

    private static void expect(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
