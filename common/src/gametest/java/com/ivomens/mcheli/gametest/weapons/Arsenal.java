package com.ivomens.mcheli.gametest.weapons;

import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.Weapons;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.pack.AddonManager;
import com.ivomens.mcheli.content.registry.ContentRegistry;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Looks up what the snapshots need in the loaded content: every weapon, the first vehicle that
 * carries it, and vehicles with each kind of countermeasure. Vehicles are searched in a fixed order
 * (helicopters, planes, ships, tanks, ground vehicles, each sorted by name), so the choice is stable.
 */
final class Arsenal {

    private static final Set<String> LOCKING_TYPES = Set.of("aamissile", "atmissile");
    /** Close air support is called on the floor this far downrange. */
    private static final double CAS_AIM_DISTANCE = 48.0;

    private Arsenal() {
    }

    /** The bundled weapons; packs that other mods or the tests add have no recorded shots. */
    static List<WeaponSpec> weapons() {
        List<WeaponSpec> weapons = new ArrayList<>(Weapons.registry().values());
        weapons.removeIf(weapon -> !Weapons.registry().entry(weapon.name())
                .map(entry -> AddonManager.BUILTIN_ADDON_DOMAIN.equals(entry.location().getAddonDomain()))
                .orElse(false));
        weapons.sort(Comparator.comparing(info -> info.name()));
        return weapons;
    }

    /** Every vehicle definition in search order. */
    static List<VehicleSpec> vehicles() {
        List<VehicleSpec> vehicles = new ArrayList<>();
        addSorted(vehicles, VehicleTypes.registry(VehicleKind.HELICOPTER));
        addSorted(vehicles, VehicleTypes.registry(VehicleKind.PLANE));
        addSorted(vehicles, VehicleTypes.registry(VehicleKind.SHIP));
        addSorted(vehicles, VehicleTypes.registry(VehicleKind.TANK));
        addSorted(vehicles, VehicleTypes.registry(VehicleKind.VEHICLE));
        return vehicles;
    }

    private static void addSorted(List<VehicleSpec> into, ContentRegistry<?, ? extends VehicleSpec> registry) {
        List<VehicleSpec> infos = new ArrayList<>(registry.values());
        infos.sort(Comparator.comparing(info -> info.name()));
        into.addAll(infos);
    }

    /** The first rideable vehicle whose weapon list names the weapon, or null. */
    @Nullable
    static VehicleSpec carrierOf(String weapon) {
        for (VehicleSpec vehicle : vehicles()) {
            if (vehicle.file().canRide() && !vehicle.isUAV() && carries(vehicle, weapon)) {
                return vehicle;
            }
        }
        return null;
    }

    private static boolean carries(VehicleSpec vehicle, String weapon) {
        for (VehicleParts.WeaponSet set : vehicle.armament().weaponSets()) {
            if (set.type.equalsIgnoreCase(weapon)) {
                return true;
            }
        }
        return false;
    }

    /** A lookup that runs when the test runs, by name, so a reload in between cannot leave a stale info. */
    static Supplier<VehicleSpec> lookup(@Nullable VehicleSpec info) {
        if (info == null) {
            return () -> null;
        }
        String name = info.name();
        return () -> vehicles().stream().filter(vehicle -> vehicle.name().equals(name)).findFirst().orElse(null);
    }

    /** The first vehicle matching the filter, or null. */
    @Nullable
    static VehicleSpec firstVehicle(Predicate<VehicleSpec> filter) {
        return vehicles().stream().filter(filter).findFirst().orElse(null);
    }

    /** The first weapon of a guided kind matching the filter, or null. */
    @Nullable
    static WeaponSpec firstWeapon(Predicate<WeaponSpec> filter) {
        return weapons().stream().filter(filter).findFirst().orElse(null);
    }

    /**
     * Whether the weapon takes a lock target with the shot: the types whose classes extend
     * lock-on seeker (air-to-air and anti-tank missiles). The others ignore the target.
     */
    static boolean isGuided(WeaponSpec info) {
        return LOCKING_TYPES.contains(info.definition().type());
    }

    /** The lock target a weapon gets: in the air for air-to-air missiles, on the ground for anti-tank. */
    static FiringScenario.Target targetFor(WeaponSpec info) {
        if (!isGuided(info)) {
            return FiringScenario.Target.NONE;
        }
        return "aamissile".equals(info.definition().type()) ? FiringScenario.Target.AIR_DUMMY
                : FiringScenario.Target.GROUND_DUMMY;
    }

    /**
     * Where the shooter looks when firing close air support, which calls the strike on the block it is aimed
     * at; null for any other weapon, which is fired straight ahead. A guided torpedo would need the seabed
     * next to water within 150 blocks, which a vehicle on the range floor cannot see, so it stays unfired.
     */
    @Nullable
    static Vec3 surfaceAimedAt(String weapon) {
        return Weapons.get(weapon).filter(info -> "cas".equals(info.definition().type()))
                .map(info -> WeaponRange.at(0.0, 1.0, CAS_AIM_DISTANCE)).orElse(null);
    }

    /** A game test name: lower case letters, digits and underscores. */
    static String testName(String prefix, String name) {
        return (prefix + "_" + name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }
}
