package com.ivomens.mcheli.vehicle.definition;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.def.vehicle.Physics;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.content.def.vehicle.VehicleRules;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The current vehicle definitions, by kind and name. Each vehicle file is frozen into its definition once,
 * when it loads; a reload builds a new one instead of changing the old, so a reader holding a reference sees a
 * consistent vehicle.
 */
public final class VehicleDefinitions {

    private static final AtomicLong GENERATION = new AtomicLong();

    private VehicleDefinitions() {
    }

    /** Freezes one vehicle file, as linked to the weapons it mounts. */
    public static VehicleDefinition build(VehicleFile file) {
        boolean sweeps = (file.kind() == VehicleKind.PLANE || file.kind() == VehicleKind.SHIP)
                && file.kindFeatures().variableSweepWing();
        VehicleDefinition.WingSweepDef wingSweep = sweeps
                ? new VehicleDefinition.WingSweepDef(VehicleRules.sweptSpeed(file)) : null;
        VehicleDefinition.TargetDroneDef targetDrone = file.uav().targetDrone()
                ? new VehicleDefinition.TargetDroneDef(file.physics().rotationSnap()) : null;
        Physics.BodySize body = file.physics().bodySize();
        return new VehicleDefinition(file.kind(), file.name(), HandlingProfiles.of(file),
                new VehicleDefinition.BodyDef(body.width(), body.height(), file.physics().stepHeight()), wingSweep,
                targetDrone, CrewDefs.seats(file), CrewDefs.mounts(file), PartDefs.of(file));
    }

    /** Called whenever vehicles were loaded or read again: readers see a new generation. */
    public static void changed() {
        GENERATION.incrementAndGet();
    }

    @Nullable
    public static VehicleDefinition get(VehicleKind kind, String name) {
        return VehicleTypes.get(kind, name).map(VehicleSpec::definition).orElse(null);
    }

    /** Counts the loads and reloads of vehicles. */
    public static long generation() {
        return GENERATION.get();
    }

    /** Every loaded definition, by kind and then name. */
    public static List<VehicleDefinition> all() {
        List<VehicleDefinition> all = new ArrayList<>();
        for (VehicleKind kind : VehicleKind.values()) {
            VehicleTypes.registry(kind).values().forEach(vehicle -> all.add(vehicle.definition()));
        }
        all.sort(Comparator.comparing(VehicleDefinition::kind).thenComparing(VehicleDefinition::name));
        return all;
    }
}
