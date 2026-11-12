package com.ivomens.mcheli.gametest.fixtures;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.registry.ContentRegistry;
import com.ivomens.mcheli.gametest.golden.GoldenMode;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Save fixtures (docs/ARCHITECTURE.md, section 4): vehicles saved in several states by today's code
 * and committed as SNBT. Every fixture must load into a fresh entity with the same observable state
 * and save again with every original key unchanged. Record mode (re)writes the files first.
 */
public class SaveFixtureTests {

    private static final String BATCH = "mcheli_fixtures";
    /** Where fixture vehicles are created; they are never added to the level. */
    private static final double X = 0.5;
    private static final double Y = 100.0;
    private static final double Z = 0.5;

    private record Subject(String type, String definition,
                           Supplier<? extends ContentRegistry<?, ? extends VehicleSpec>> registry) {
        VehicleSpec info() {
            return this.registry.get().get(this.definition).orElse(null);
        }
    }

    private static final List<Subject> SUBJECTS = List.of(
            new Subject("helicopter", "ah-64", () -> VehicleTypes.registry(VehicleKind.HELICOPTER)),
            new Subject("helicopter", "ch47", () -> VehicleTypes.registry(VehicleKind.HELICOPTER)),
            new Subject("plane", "a6m2", () -> VehicleTypes.registry(VehicleKind.PLANE)),
            new Subject("vtol", "harrier", () -> VehicleTypes.registry(VehicleKind.PLANE)),
            new Subject("uav", "mq-9", () -> VehicleTypes.registry(VehicleKind.PLANE)),
            new Subject("ship", "cb90", () -> VehicleTypes.registry(VehicleKind.SHIP)),
            new Subject("tank", "m1a2", () -> VehicleTypes.registry(VehicleKind.TANK)),
            new Subject("tank_car", "growler", () -> VehicleTypes.registry(VehicleKind.TANK)),
            new Subject("vehicle", "12.7", () -> VehicleTypes.registry(VehicleKind.VEHICLE)),
            new Subject("target_drone", "bqm_74e", () -> VehicleTypes.registry(VehicleKind.PLANE)));

    public static List<TestFunction> saveFixtures() {
        List<TestFunction> tests = new ArrayList<>();
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return tests;
        }
        for (Subject subject : SUBJECTS) {
            String name = ("fixtures_" + subject.type() + "_" + subject.definition()).replaceAll("[^a-z0-9_]", "_");
            tests.add(new TestFunction(BATCH, name, "mcheli:empty", 20, 0, true, helper -> run(helper, subject)));
        }
        return tests;
    }

    private static void run(GameTestHelper helper, Subject subject) {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (FixtureState state : FixtureState.values()) {
            Path file = GoldenMode.fixtureDir().resolve(subject.type())
                    .resolve(subject.definition() + "." + state.fileName() + ".snbt");
            if (GoldenMode.CURRENT.records()) {
                record(helper.getLevel(), subject, state, file);
            }
            if (!Files.isRegularFile(file)) {
                continue;
            }
            String problem = check(helper.getLevel(), subject, state, file);
            if (problem != null) {
                problems.add(file.getFileName() + ": " + problem);
            }
            checked++;
        }
        if (checked == 0) {
            problems.add("no fixtures for " + subject.definition() + "; record them with -Pgolden=record");
        }
        if (!problems.isEmpty()) {
            throw new GameTestAssertException(String.join("; ", problems));
        }
        helper.succeed();
    }

    /** Saves a new vehicle in the given state, if the definition can be in it. */
    private static void record(ServerLevel level, Subject subject, FixtureState state, Path file) {
        AircraftEntity vehicle = create(level, subject);
        if (!state.appliesTo(vehicle)) {
            return;
        }
        state.apply(vehicle);
        CompoundTag tag = new CompoundTag();
        vehicle.saveWithoutId(tag);
        tag.putString("id", EntityType.getKey(vehicle.getType()).toString());
        vehicle.discard();
        try {
            SnbtFiles.writeIfChanged(file, tag);
        } catch (IOException e) {
            throw new GameTestAssertException("could not write " + file + ": " + e);
        }
    }

    private static String check(ServerLevel level, Subject subject, FixtureState state, Path file) {
        CompoundTag saved;
        try {
            saved = SnbtFiles.read(file);
        } catch (IOException e) {
            return "unreadable: " + e;
        }
        if (!(EntityType.create(saved, level).orElse(null) instanceof AircraftEntity loaded)) {
            return "does not load as a vehicle";
        }
        try {
            if (!subject.definition().equals(loaded.identity().typeName())) {
                return "loaded as " + loaded.identity().typeName();
            }
            String problem = state.verify(loaded, saved);
            if (problem != null) {
                return problem;
            }
            CompoundTag resaved = new CompoundTag();
            loaded.saveWithoutId(resaved);
            return SnbtFiles.missingOrChanged(saved, resaved, "id");
        } finally {
            loaded.discard();
        }
    }

    private static AircraftEntity create(ServerLevel level, Subject subject) {
        VehicleSpec info = subject.info();
        if (info == null || !(VehicleTypes.item(info).orElse(null) instanceof AircraftItem item)) {
            throw new GameTestAssertException(subject.definition() + " is not loaded");
        }
        AircraftEntity vehicle = item.createAircraft(level, X, Y, Z, new ItemStack(item));
        if (vehicle == null) {
            throw new GameTestAssertException("could not create " + subject.definition());
        }
        // a fixed UUID keeps re-recorded fixtures byte for byte the same
        vehicle.setUUID(UUID.nameUUIDFromBytes((subject.type() + "/" + subject.definition()).getBytes()));
        return vehicle;
    }
}
