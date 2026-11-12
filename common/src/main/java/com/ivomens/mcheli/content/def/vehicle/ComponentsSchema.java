package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.core.content.schema.EntrySchema;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Member;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.api.vehicle.VehicleKind;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * YAML {@code Components}: the drawn parts by type, in the order the emitter writes them. Every kind has the
 * common types; helicopters add {@code HeliRotor}, planes {@code PlaneRotor}, {@code Wing}, {@code Nozzle} and
 * {@code ExhaustFlame}, ships {@code ShipRotor}, {@code Wing} and {@code Nozzle}, ground vehicles
 * {@code Vpart}.
 */
final class ComponentsSchema {

    static final Field<Components, List<Parts.Camera>> CAMERAS = list("Camera", () -> PartSchemas.CAMERA,
            Components::cameras);
    static final Field<Components, List<Parts.Canopy>> CANOPIES = list("Canopy", () -> PartSchemas.CANOPY,
            Components::canopies);
    static final Field<Components, List<Parts.Hatch>> HATCHES = list("Hatch", () -> PartSchemas.HATCH,
            Components::hatches);
    static final Field<Components, List<Parts.Hatch>> LIGHT_HATCHES = list("LightHatch",
            () -> PartSchemas.LIGHT_HATCH, Components::lightHatches);
    static final Field<Components, List<Parts.WeaponBay>> WEAPON_BAYS = list("WeaponBay",
            () -> PartSchemas.WEAPON_BAY, Components::weaponBays);
    static final Field<Components, List<Parts.Rotating>> ROTATING_PARTS = list("Rotation",
            () -> PartSchemas.ROTATING, Components::rotatingParts);
    static final Field<Components, List<Parts.Wheel>> STEERING_WHEELS = list("SteeringWheel",
            () -> PartSchemas.STEERING_WHEEL, Components::steeringWheels);
    static final Field<Components, List<Parts.Wheel>> WHEELS = list("Wheel", () -> PartSchemas.WHEEL,
            Components::wheels);
    static final Field<Components, List<Parts.LandingGear>> LANDING_GEAR = list("LandingGear",
            () -> PartSchemas.LANDING_GEAR, Components::landingGear);
    static final Field<Components, List<Parts.Weapon>> WEAPONS = list("Weapon", () -> WeaponPartSchemas.WEAPON,
            Components::weapons);
    static final Field<Components, List<Parts.SearchLight>> SEARCH_LIGHTS = list("SearchLight",
            () -> PartSchemas.SEARCH_LIGHT, Components::searchLights);
    static final Field<Components, List<Parts.Plain>> TRACK_ROLLERS = list("TrackRoller",
            () -> PartSchemas.TRACK_ROLLER, Components::trackRollers);
    static final Field<Components, List<Parts.Throttle>> THROTTLES = list("Throttle", () -> PartSchemas.THROTTLE,
            Components::throttles);
    static final Field<Components, List<Parts.CrawlerTrack>> CRAWLER_TRACKS = list("CrawlerTrack",
            () -> PartSchemas.CRAWLER_TRACK, Components::crawlerTracks);
    static final Field<Components, List<Parts.HeliRotor>> HELI_ROTORS = list("HeliRotor",
            () -> KindPartSchemas.HELI_ROTOR, Components::heliRotors);
    static final Field<Components, List<Parts.RepellingHook>> REPELLING_HOOKS = list("RepelHook",
            () -> PartSchemas.REPELLING_HOOK, Components::repellingHooks);
    static final Field<Components, List<Parts.Propeller>> PLANE_ROTORS = list("PlaneRotor",
            () -> KindPartSchemas.PROPELLER, Components::propellers);
    static final Field<Components, List<Parts.Propeller>> SHIP_ROTORS = list("ShipRotor",
            () -> KindPartSchemas.PROPELLER, Components::propellers);
    static final Field<Components, List<Parts.Wing>> WINGS = list("Wing", () -> KindPartSchemas.WING,
            Components::wings);
    static final Field<Components, List<Parts.Plain>> NOZZLES = list("Nozzle", () -> KindPartSchemas.NOZZLE,
            Components::nozzles);
    static final Field<Components, List<Parts.ExhaustFlame>> EXHAUST_FLAMES = list("ExhaustFlame",
            () -> KindPartSchemas.EXHAUST_FLAME, Components::exhaustFlames);
    static final Field<Components, List<Parts.TurretPart>> TURRET_PARTS = list("Vpart",
            () -> KindPartSchemas.TURRET_PART, Components::turretParts);

    private ComponentsSchema() {
    }

    static Section<VehicleFile, Components> section(VehicleKind kind) {
        List<Member<Components>> members = new ArrayList<>(List.of(CAMERAS, CANOPIES, HATCHES, LIGHT_HATCHES,
                WEAPON_BAYS, ROTATING_PARTS, STEERING_WHEELS, WHEELS, LANDING_GEAR, WEAPONS, SEARCH_LIGHTS,
                TRACK_ROLLERS, THROTTLES, CRAWLER_TRACKS));
        if (kind == VehicleKind.HELICOPTER) {
            members.add(HELI_ROTORS);
        }
        members.add(REPELLING_HOOKS);
        switch (kind) {
            case PLANE -> members.addAll(List.of(PLANE_ROTORS, WINGS, NOZZLES, EXHAUST_FLAMES));
            case SHIP -> members.addAll(List.of(SHIP_ROTORS, WINGS, NOZZLES));
            case VEHICLE -> members.add(TURRET_PARTS);
            default -> {
            }
        }
        @SuppressWarnings("unchecked")
        Member<Components>[] array = members.toArray(Member[]::new);
        return Section.of("Components", VehicleFile::components, array)
                .unsupported("TurretWeaponBay", "TurretRotation");
    }

    /** The parts of every kind; those the file's kind does not have stay empty. */
    static Components build(Values v) {
        List<Parts.Propeller> propellers = v.isSet(SHIP_ROTORS) ? v.get(SHIP_ROTORS) : v.get(PLANE_ROTORS);
        return new Components(v.get(CAMERAS), v.get(CANOPIES), v.get(HATCHES), v.get(LIGHT_HATCHES),
                v.get(WEAPON_BAYS), v.get(ROTATING_PARTS), v.get(STEERING_WHEELS), v.get(WHEELS), v.get(LANDING_GEAR),
                v.get(WEAPONS), v.get(SEARCH_LIGHTS), v.get(TRACK_ROLLERS), v.get(THROTTLES), v.get(CRAWLER_TRACKS),
                v.get(HELI_ROTORS), v.get(REPELLING_HOOKS), propellers, v.get(WINGS), v.get(NOZZLES),
                v.get(EXHAUST_FLAMES), v.get(TURRET_PARTS));
    }

    private static <E> Field<Components, List<E>> list(String key, Supplier<EntrySchema<E>> entries,
                                                       Function<Components, List<E>> getter) {
        return Field.listOf(key, entries, getter);
    }
}
