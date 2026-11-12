package com.ivomens.mcheli.vehicle.definition;

import com.ivomens.mcheli.content.def.vehicle.Components;
import com.ivomens.mcheli.content.def.vehicle.Parts;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.content.def.vehicle.VehicleRules;
import com.ivomens.mcheli.vehicle.definition.PartDef.PartKind;

import java.util.ArrayList;
import java.util.List;

/** Reads the drawn parts out of a vehicle file: the kind's own parts first, then the shared ones. */
final class PartDefs {

    /** A crawler track needs this many points to be drawn. */
    private static final int MIN_TRACK_POINTS = 4;

    private PartDefs() {
    }

    static List<PartDef> of(VehicleFile file) {
        Components parts = file.components();
        List<PartDef> defs = new ArrayList<>();
        switch (file.kind()) {
            case HELICOPTER -> defs.addAll(leaves(PartKind.ROTOR, parts.heliRotors()));
            case PLANE, SHIP -> addPlaneParts(defs, file);
            case VEHICLE -> parts.turretParts().forEach(part -> defs.add(turretPart(part)));
            case TANK -> {
            }
        }
        defs.addAll(leaves(PartKind.HATCH, VehicleRules.hatchesAndWingsClash(file) ? List.of() : parts.hatches()));
        defs.addAll(leaves(PartKind.CAMERA, parts.cameras()));
        defs.addAll(leaves(PartKind.THROTTLE, parts.throttles()));
        defs.addAll(leaves(PartKind.ROTATING_PART, parts.rotatingParts()));
        parts.weapons().forEach(weapon -> defs.add(weapon(weapon)));
        defs.addAll(leaves(PartKind.CANOPY, parts.canopies()));
        defs.addAll(leaves(PartKind.LANDING_GEAR, parts.landingGear()));
        defs.addAll(leaves(PartKind.WEAPON_BAY, VehicleRules.weaponBays(file).stream()
                .map(VehicleRules.WeaponBayLink::bay).toList()));
        defs.addAll(leaves(PartKind.CRAWLER_TRACK, parts.crawlerTracks().stream()
                .filter(track -> track.points().size() >= MIN_TRACK_POINTS).toList()));
        defs.addAll(leaves(PartKind.TRACK_ROLLER, parts.trackRollers()));
        defs.addAll(leaves(PartKind.WHEEL, parts.wheels()));
        defs.addAll(leaves(PartKind.STEERING_WHEEL, parts.steeringWheels()));
        defs.addAll(leaves(PartKind.LIGHT_HATCH, parts.lightHatches()));
        return defs;
    }

    /** Nozzles, propellers with their blades, then wings with their pylons; a plane with hatches has no wings. */
    private static void addPlaneParts(List<PartDef> defs, VehicleFile file) {
        Components parts = file.components();
        defs.addAll(leaves(PartKind.NOZZLE, parts.nozzles()));
        for (Parts.Propeller rotor : parts.propellers()) {
            defs.add(part(PartKind.ROTOR, rotor, leaves(PartKind.BLADE, rotor.blades())));
        }
        if (!VehicleRules.hatchesAndWingsClash(file)) {
            for (Parts.Wing wing : parts.wings()) {
                defs.add(part(PartKind.WING, wing, leaves(PartKind.PYLON, wing.pylons())));
            }
        }
    }

    /** A weapon part with its barrels, which are named after it and their place. */
    private static PartDef weapon(Parts.Weapon weapon) {
        List<PartDef> barrels = new ArrayList<>();
        for (Parts.WeaponChild child : weapon.children()) {
            barrels.add(new PartDef(PartKind.WEAPON_CHILD, weapon.name() + "_" + barrels.size(), child.position(),
                    child.rotation(), List.of()));
        }
        return part(PartKind.WEAPON, weapon, barrels);
    }

    private static PartDef turretPart(Parts.TurretPart part) {
        List<PartDef> children = new ArrayList<>();
        part.children().forEach(child -> children.add(turretPart(child)));
        return part(PartKind.TURRET_PART, part, children);
    }

    private static List<PartDef> leaves(PartKind kind, List<? extends Parts.Placed> parts) {
        List<PartDef> defs = new ArrayList<>(parts.size());
        parts.forEach(part -> defs.add(part(kind, part, List.of())));
        return defs;
    }

    private static PartDef part(PartKind kind, Parts.Placed part, List<PartDef> children) {
        return new PartDef(kind, part.name(), part.position(), part.rotation(), children);
    }
}
