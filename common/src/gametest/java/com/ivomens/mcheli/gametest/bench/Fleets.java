package com.ivomens.mcheli.gametest.bench;

import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** The bundled vehicle types a workload draws from, sorted by name so every run spawns the same ones. */
public final class Fleets {

    private Fleets() {
    }

    static List<VehicleSpec> of(Predicate<VehicleSpec> filter, VehicleKind... kinds) {
        List<VehicleSpec> specs = new ArrayList<>();
        for (VehicleKind kind : kinds) {
            List<VehicleSpec> ofKind = new ArrayList<>();
            for (VehicleSpec spec : VehicleTypes.registry(kind).values()) {
                if (filter.test(spec) && VehicleTypes.item(spec).orElse(null) instanceof AircraftItem) {
                    ofKind.add(spec);
                }
            }
            ofKind.sort(Comparator.comparing(VehicleSpec::name));
            specs.addAll(ofKind);
        }
        if (specs.isEmpty()) {
            throw new IllegalStateException("No bundled vehicles of " + List.of(kinds));
        }
        return specs;
    }

    /** Every kind, parked or not, crewed or not. */
    static List<VehicleSpec> all() {
        return of(spec -> true, VehicleKind.HELICOPTER, VehicleKind.PLANE, VehicleKind.TANK, VehicleKind.VEHICLE,
                VehicleKind.SHIP);
    }

    /** Helicopters and planes a player can fly. */
    static List<VehicleSpec> flyable() {
        return of(Fleets::crewable, VehicleKind.HELICOPTER, VehicleKind.PLANE);
    }

    /** Anything a player can sit in, without drones. */
    static List<VehicleSpec> manned() {
        return of(Fleets::crewable, VehicleKind.HELICOPTER, VehicleKind.PLANE, VehicleKind.TANK, VehicleKind.VEHICLE,
                VehicleKind.SHIP);
    }

    public static List<VehicleSpec> manned(VehicleKind kind) {
        return of(Fleets::crewable, kind);
    }

    private static boolean crewable(VehicleSpec spec) {
        return spec.file().canRide() && !spec.isUAV();
    }
}
