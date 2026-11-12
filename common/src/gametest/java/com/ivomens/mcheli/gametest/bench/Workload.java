package com.ivomens.mcheli.gametest.bench;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.api.vehicle.VehicleKind;

import java.util.List;
import java.util.Locale;

/**
 * What the vehicles of a tier do.
 * <ul>
 *   <li>{@code parked}: every kind of vehicle, nobody aboard, 14 blocks apart (an airbase);</li>
 *   <li>{@code flying}: helicopters and planes 40 blocks up, each with a player at full throttle;</li>
 *   <li>{@code firefight}: manned vehicles in rows facing each other, each player firing every weapon in
 *   turn (rounds, missiles, rockets and bombs fill the level);</li>
 *   <li>{@code mixed}: helicopters, planes and tanks; of every ten, three parked, two flown by players,
 *   three manned by AI gunners (two tanks, one helicopter) and two helicopters flying as wingmen in
 *   formations of four behind leaders circling the field.</li>
 * </ul>
 */
enum Workload {
    PARKED(14), FLYING(24), FIREFIGHT(12), MIXED(16);

    /** Blocks between neighbouring vehicles of the grid. */
    private final int spacing;

    Workload(int spacing) {
        this.spacing = spacing;
    }

    /** Half the width of the square the vehicles of a tier stand in, in blocks. */
    double halfWidth(int count) {
        return (Math.ceil(Math.sqrt(count)) / 2.0 + 1) * this.spacing;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Workload byId(String id) {
        return valueOf(id.trim().toUpperCase(Locale.ROOT));
    }

    void populate(BenchScene scene, int count) {
        int side = (int) Math.ceil(Math.sqrt(count));
        switch (this) {
            case PARKED -> parked(scene, count, side, this.spacing);
            case FLYING -> flying(scene, count, side, this.spacing);
            case FIREFIGHT -> firefight(scene, count, side, this.spacing);
            case MIXED -> mixed(scene, count, side, this.spacing);
        }
    }

    private static void parked(BenchScene scene, int count, int side, int spacing) {
        List<VehicleSpec> specs = Fleets.all();
        for (int i = 0; i < count; i++) {
            scene.spawn(specs.get(i % specs.size()), i, side, spacing, 2, 0);
        }
    }

    private static void flying(BenchScene scene, int count, int side, int spacing) {
        List<VehicleSpec> specs = Fleets.flyable();
        for (int i = 0; i < count; i++) {
            AircraftEntity vehicle = scene.spawn(specs.get(i % specs.size()), i, side, spacing, 40, 0);
            if (vehicle != null) {
                scene.pilot(vehicle);
            }
        }
    }

    private static void firefight(BenchScene scene, int count, int side, int spacing) {
        List<VehicleSpec> specs = Fleets.manned();
        for (int i = 0; i < count; i++) {
            // rows facing each other
            AircraftEntity vehicle = scene.spawn(specs.get(i % specs.size()), i, side, spacing, 2, i % 2 == 0 ? 0 : 180);
            if (vehicle != null) {
                scene.shooter(vehicle);
            }
        }
    }

    private static void mixed(BenchScene scene, int count, int side, int spacing) {
        List<VehicleSpec> helis = Fleets.manned(VehicleKind.HELICOPTER);
        List<VehicleSpec> planes = Fleets.manned(VehicleKind.PLANE);
        List<VehicleSpec> tanks = Fleets.manned(VehicleKind.TANK);
        for (int i = 0; i < count; i++) {
            int n = i / 10;
            switch (i % 10) {
                case 0 -> scene.spawn(helis.get(n % helis.size()), i, side, spacing, 2, 0);
                case 1 -> scene.spawn(planes.get(n % planes.size()), i, side, spacing, 2, 0);
                case 2 -> scene.spawn(tanks.get(n % tanks.size()), i, side, spacing, 2, 0);
                case 3 -> pilot(scene, scene.spawn(helis.get((n + 3) % helis.size()), i, side, spacing, 40, 0));
                case 4 -> pilot(scene, scene.spawn(planes.get((n + 3) % planes.size()), i, side, spacing, 40, 0));
                case 5, 6 -> gunner(scene, scene.spawn(tanks.get((n * 2 + i) % tanks.size()), i, side, spacing, 2, 0));
                case 7 -> gunner(scene, scene.spawn(helis.get((n + 7) % helis.size()), i, side, spacing, 2, 0));
                default -> wingman(scene, scene.spawn(helis.get((n * 2 + i) % helis.size()), i, side, spacing, 2, 0));
            }
        }
    }

    private static void pilot(BenchScene scene, AircraftEntity vehicle) {
        if (vehicle != null) {
            scene.pilot(vehicle);
        }
    }

    private static void gunner(BenchScene scene, AircraftEntity vehicle) {
        if (vehicle != null) {
            scene.gunner(vehicle);
        }
    }

    private static void wingman(BenchScene scene, AircraftEntity vehicle) {
        if (vehicle != null) {
            scene.wingman(vehicle);
        }
    }
}
