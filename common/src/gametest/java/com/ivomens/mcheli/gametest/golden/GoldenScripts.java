package com.ivomens.mcheli.gametest.golden;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.registry.ContentRegistry;
import com.ivomens.mcheli.gametest.golden.Script.Spawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.ivomens.mcheli.gametest.golden.PilotInput.Action.FOLD_BLADES;
import static com.ivomens.mcheli.gametest.golden.PilotInput.Action.GEAR_UP;
import static com.ivomens.mcheli.gametest.golden.PilotInput.Action.HOVER_ON;
import static com.ivomens.mcheli.gametest.golden.PilotInput.Action.VTOL_OFF;
import static com.ivomens.mcheli.gametest.golden.PilotInput.Action.VTOL_ON;
import static com.ivomens.mcheli.gametest.golden.PilotInput.Key.BRAKE;
import static com.ivomens.mcheli.gametest.golden.PilotInput.Key.LEFT;
import static com.ivomens.mcheli.gametest.golden.PilotInput.Key.RIGHT;
import static com.ivomens.mcheli.gametest.golden.PilotInput.Key.THROTTLE_DOWN;
import static com.ivomens.mcheli.gametest.golden.PilotInput.Key.THROTTLE_UP;

/**
 * Every golden case: which definitions are recorded and what is done with them.
 * <p>
 * Each type gets the scripts that exercise its own physics; the first few ticks of every manned
 * script go to the pilot climbing aboard. Positions are on the course in {@link TestTerrain}.
 */
final class GoldenScripts {

    private GoldenScripts() {
    }

    static List<GoldenCase> all() {
        List<GoldenCase> cases = new ArrayList<>();
        helicopter(cases, "ah-64", true);
        helicopter(cases, "uh-1c", false);
        plane(cases, "cessna172", true);
        plane(cases, "a-10", false);
        vtol(cases, "harrier");
        vtol(cases, "mv-22");
        ship(cases, "cb90", true);
        ship(cases, "zodiac", false);
        tank(cases, "m1a2", true);
        tank(cases, "t-90", false);
        tankCar(cases, "growler");
        groundVehicle(cases, "12.7");
        targetDrone(cases, "bqm_74e");
        cases.addAll(sixtyFps(cases));
        return cases;
    }

    /**
     * Cases that steer with the stick or keys, flown again by a 60 FPS client with fixed attitude
     * substeps. They record that mode's own baseline; they are not expected to match the 20 FPS files.
     */
    private static List<GoldenCase> sixtyFps(List<GoldenCase> twentyFps) {
        Set<String> chosen = Set.of("helicopter/ah-64.climb_bank", "helicopter/ah-64.strafe_hover",
                "helicopter/ah-64.assisted_hold",
                "plane/cessna172.roll", "plane/cessna172.takeoff", "vtol/harrier.liftoff_transition",
                "ship/cb90.drift_turn", "tank/m1a2.pivot", "tank/m1a2.ramp", "tank_car/growler.flat_steer",
                "vehicle/12.7.settle_aim");
        List<GoldenCase> cases = new ArrayList<>();
        for (GoldenCase golden : twentyFps) {
            if (chosen.contains(golden.type() + "/" + golden.definition() + "." + golden.script().name())) {
                cases.add(golden.atSixtyFps());
            }
        }
        return cases;
    }

    private static void helicopter(List<GoldenCase> cases, String name, boolean full) {
        Supplier<VehicleSpec> info = lookup(() -> VehicleTypes.registry(VehicleKind.HELICOPTER), name);
        Script hover = Script.builder("hover", 400)
                .spawn(Spawn.at(TestTerrain.RUNWAY_MIDDLE))
                .hold(0, 70, THROTTLE_UP)
                .build();
        Script climbBank = Script.builder("climb_bank", 400)
                .spawn(Spawn.at(TestTerrain.RUNWAY_MIDDLE))
                .hold(0, 110, THROTTLE_UP)
                .stick(110, 150, 20.0F, 0.0F)
                .stick(170, 190, 0.0F, -10.0F)
                .stick(250, 290, -20.0F, 0.0F)
                .build();
        cases.add(new GoldenCase("helicopter", name, info, hover));
        cases.add(new GoldenCase("helicopter", name, info, climbBank));
        if (!full) {
            return;
        }
        // the helicopter assist: the climb and bank above, then hands off; it levels out and holds altitude
        cases.add(new GoldenCase("helicopter", name, info, Script.builder("assisted_hold", 400)
                .spawn(Spawn.at(TestTerrain.RUNWAY_MIDDLE))
                .hold(0, 110, THROTTLE_UP)
                .stick(110, 150, 20.0F, 0.0F)
                .stick(170, 190, 0.0F, -10.0F)
                .assisted(AssistedPilot::helicopter)
                .build()));
        cases.add(new GoldenCase("helicopter", name, info, Script.builder("strafe_hover", 360)
                .spawn(Spawn.at(TestTerrain.RUNWAY_MIDDLE))
                .hold(0, 60, THROTTLE_UP)
                .press(100, HOVER_ON)
                .hold(120, 180, LEFT)
                .hold(200, 260, RIGHT)
                .hold(280, 320, THROTTLE_UP)
                .build()));
        cases.add(new GoldenCase("helicopter", name, info, Script.builder("autorotation", 500)
                .spawn(Spawn.at(TestTerrain.RUNWAY_MIDDLE))
                .hold(0, 120, THROTTLE_UP)
                .hold(140, 500, THROTTLE_DOWN)
                .build()));
        cases.add(new GoldenCase("helicopter", name, info, Script.builder("water_landing", 400)
                .spawn(Spawn.at(TestTerrain.POOL_CENTRE).raised(8.0).flying(0.5, 0.0))
                .hold(20, 120, THROTTLE_DOWN)
                .build()));
        cases.add(new GoldenCase("helicopter", name, info, Script.builder("folded_taxi", 300)
                .spawn(Spawn.at(TestTerrain.RUNWAY_START))
                .press(20, FOLD_BLADES)
                .hold(80, 180, THROTTLE_UP)
                .hold(180, 240, RIGHT)
                .build()));
        cases.add(new GoldenCase("helicopter", name, info, Script.builder("rest_and_wake", 360)
                .spawn(Spawn.at(TestTerrain.RUNWAY_MIDDLE))
                .boardAt(300)
                .event(150, "hurt", vehicle -> vehicle.hurt(vehicle.damageSources().generic(), 0.5F))
                .build()));
        cases.add(new GoldenCase("helicopter", name, info, hover.atNegativeCoordinates()));
    }

    private static void plane(List<GoldenCase> cases, String name, boolean full) {
        Supplier<VehicleSpec> info = lookup(() -> VehicleTypes.registry(VehicleKind.PLANE), name);
        cases.add(new GoldenCase("plane", name, info, Script.builder("takeoff", 500)
                .spawn(Spawn.at(TestTerrain.RUNWAY_START))
                .hold(0, 500, THROTTLE_UP)
                .stick(180, 230, 0.0F, 10.0F)
                .press(300, GEAR_UP)
                .build()));
        cases.add(new GoldenCase("plane", name, info, Script.builder("roll", 240)
                .spawn(Spawn.at(TestTerrain.RUNWAY_MIDDLE).raised(30.0).flying(1.0, 1.0))
                .hold(0, 240, THROTTLE_UP)
                .stick(40, 80, 20.0F, 0.0F)
                .stick(120, 160, -20.0F, 0.0F)
                .build()));
        if (!full) {
            return;
        }
        // auto-rudder: bank, centre the stick, and the plane turns the way it leans
        cases.add(new GoldenCase("plane", name, info, Script.builder("coordinated_turn", 240)
                .spawn(Spawn.at(TestTerrain.RUNWAY_MIDDLE).raised(30.0).flying(1.0, 1.0))
                .hold(0, 240, THROTTLE_UP)
                .stick(40, 60, 20.0F, 0.0F)
                .stick(140, 160, -20.0F, 0.0F)
                .assisted(AssistedPilot::autoRudder)
                .build()));
        cases.add(new GoldenCase("plane", name, info, Script.builder("glide", 300)
                .spawn(Spawn.at(TestTerrain.RUNWAY_MIDDLE).raised(40.0).flying(0.6, 1.0))
                .hold(10, 100, THROTTLE_DOWN)
                .build()));
        cases.add(new GoldenCase("plane", name, info, Script.builder("taxi_brake", 300)
                .spawn(Spawn.at(TestTerrain.RUNWAY_START))
                .hold(0, 80, THROTTLE_UP)
                .hold(80, 300, THROTTLE_DOWN)
                .build()));
    }

    private static void vtol(List<GoldenCase> cases, String name) {
        Supplier<VehicleSpec> info = lookup(() -> VehicleTypes.registry(VehicleKind.PLANE), name);
        cases.add(new GoldenCase("vtol", name, info, Script.builder("liftoff_transition", 500)
                .spawn(Spawn.at(TestTerrain.RUNWAY_START))
                .press(40, VTOL_ON)
                .hold(80, 200, THROTTLE_UP)
                .press(300, VTOL_OFF)
                .hold(300, 380, THROTTLE_UP)
                .build()));
    }

    private static void ship(List<GoldenCase> cases, String name, boolean full) {
        Supplier<VehicleSpec> info = lookup(() -> VehicleTypes.registry(VehicleKind.SHIP), name);
        Script cruiseBeach = Script.builder("cruise_beach", 400)
                .spawn(Spawn.at(TestTerrain.POOL_CENTRE))
                .hold(0, 400, THROTTLE_UP)
                .build();
        cases.add(new GoldenCase("ship", name, info, cruiseBeach));
        if (full) {
            cases.add(new GoldenCase("ship", name, info, cruiseBeach.atNegativeCoordinates()));
            cases.add(new GoldenCase("ship", name, info, Script.builder("drift_turn", 300)
                    .spawn(Spawn.at(TestTerrain.POOL_CENTRE))
                    .hold(20, 60, THROTTLE_UP)
                    .stick(60, 140, 20.0F, 0.0F)
                    .hold(140, 200, LEFT)
                    .build()));
        }
    }

    private static void tank(List<GoldenCase> cases, String name, boolean full) {
        Supplier<VehicleSpec> info = lookup(() -> VehicleTypes.registry(VehicleKind.TANK), name);
        Script flat = Script.builder("flat", 300)
                .spawn(Spawn.at(TestTerrain.RUNWAY_START))
                .hold(0, 120, THROTTLE_UP)
                .hold(200, 240, BRAKE)
                .build();
        cases.add(new GoldenCase("tank", name, info, flat));
        cases.add(new GoldenCase("tank", name, info, Script.builder("step", 200)
                .spawn(Spawn.at(TestTerrain.TRACK_START))
                .hold(0, 90, THROTTLE_UP)
                .hold(120, 160, BRAKE)
                .build()));
        if (!full) {
            return;
        }
        cases.add(new GoldenCase("tank", name, info, Script.builder("pivot", 200)
                .spawn(Spawn.at(TestTerrain.TRACK_START))
                .hold(0, 10, THROTTLE_UP)
                .hold(10, 120, LEFT)
                .build()));
        cases.add(new GoldenCase("tank", name, info, Script.builder("ramp", 240)
                .spawn(Spawn.at(TestTerrain.RAMP_FOOT))
                .hold(0, 80, THROTTLE_UP)
                .hold(120, 160, BRAKE)
                .build()));
        Script ledge = Script.builder("ledge", 200)
                .spawn(Spawn.at(TestTerrain.PLATEAU))
                .hold(0, 70, THROTTLE_UP)
                .hold(110, 150, BRAKE)
                .build();
        cases.add(new GoldenCase("tank", name, info, ledge));
        cases.add(new GoldenCase("tank", name, info, Script.builder("reverse", 200)
                .spawn(Spawn.at(TestTerrain.TRACK_START).facing(180.0F))
                .hold(0, 150, THROTTLE_DOWN)
                .build()));
        cases.add(new GoldenCase("tank", name, info, Script.builder("wall", 200)
                .spawn(Spawn.at(TestTerrain.WALL_APPROACH))
                .hold(0, 200, THROTTLE_UP)
                .build()));
        cases.add(new GoldenCase("tank", name, info, flat.atNegativeCoordinates()));
        // driving off the ledge is where truncated and floored block lookups disagree
        cases.add(new GoldenCase("tank", name, info, ledge.atNegativeCoordinates()));
    }

    private static void tankCar(List<GoldenCase> cases, String name) {
        Supplier<VehicleSpec> info = lookup(() -> VehicleTypes.registry(VehicleKind.TANK), name);
        cases.add(new GoldenCase("tank_car", name, info, Script.builder("flat_steer", 300)
                .spawn(Spawn.at(TestTerrain.RUNWAY_START))
                .hold(0, 120, THROTTLE_UP)
                .hold(60, 100, RIGHT)
                .hold(100, 140, LEFT)
                .hold(180, 220, BRAKE)
                .build()));
        cases.add(new GoldenCase("tank_car", name, info, Script.builder("step", 200)
                .spawn(Spawn.at(TestTerrain.TRACK_START))
                .hold(0, 70, THROTTLE_UP)
                .hold(110, 150, BRAKE)
                .build()));
    }

    private static void groundVehicle(List<GoldenCase> cases, String name) {
        Supplier<VehicleSpec> info = lookup(() -> VehicleTypes.registry(VehicleKind.VEHICLE), name);
        cases.add(new GoldenCase("vehicle", name, info, Script.builder("settle_aim", 200)
                .spawn(Spawn.at(TestTerrain.TRACK_START).raised(1.0))
                .hold(40, 80, THROTTLE_UP)
                .stick(80, 160, 20.0F, 5.0F)
                .build()));
    }

    private static void targetDrone(List<GoldenCase> cases, String name) {
        Supplier<VehicleSpec> info = lookup(() -> VehicleTypes.registry(VehicleKind.PLANE), name);
        cases.add(new GoldenCase("target_drone", name, info, Script.builder("fuel_out", 500)
                .spawn(Spawn.at(TestTerrain.RUNWAY_MIDDLE).raised(30.0).flying(1.0, 1.0).withFuel(8))
                .unmanned()
                .build()));
    }

    private static Supplier<VehicleSpec> lookup(Supplier<? extends ContentRegistry<?, ? extends VehicleSpec>> registry,
                                                 String name) {
        return () -> registry.get().get(name).orElse(null);
    }
}
