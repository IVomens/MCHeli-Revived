package com.ivomens.mcheli.apiservice;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.content.ContentLookup;
import com.ivomens.mcheli.api.content.VehicleInfo;
import com.ivomens.mcheli.api.content.WeaponInfo;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.Weapons;
import com.ivomens.mcheli.content.registry.ContentEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** The API's view of the content registries: small snapshots of the loaded types, built on each call. */
final class LoadedContentLookup implements ContentLookup {

    @Override
    public List<VehicleInfo> vehicles() {
        List<VehicleInfo> vehicles = new ArrayList<>();
        for (VehicleKind kind : VehicleKind.values()) {
            VehicleTypes.registry(kind).values().stream()
                    .sorted(Comparator.comparing(VehicleSpec::name))
                    .map(LoadedContentLookup::vehicleInfo)
                    .forEach(vehicles::add);
        }
        return List.copyOf(vehicles);
    }

    @Override
    public Optional<VehicleInfo> vehicle(VehicleKind kind, String id) {
        return VehicleTypes.get(kind, id).map(LoadedContentLookup::vehicleInfo);
    }

    @Override
    public Optional<VehicleInfo> vehicleOf(Vehicle vehicle) {
        return vehicle.entity() instanceof AircraftEntity aircraft && aircraft.spec() != null
                ? Optional.of(vehicleInfo(aircraft.spec()))
                : Optional.empty();
    }

    @Override
    public List<WeaponInfo> weapons() {
        return Weapons.registry().values().stream()
                .sorted(Comparator.comparing(WeaponSpec::name))
                .map(LoadedContentLookup::weaponInfo)
                .toList();
    }

    @Override
    public Optional<WeaponInfo> weapon(String id) {
        return Weapons.get(id).map(LoadedContentLookup::weaponInfo);
    }

    private static VehicleInfo vehicleInfo(VehicleSpec spec) {
        String pack = VehicleTypes.entry(spec).map(LoadedContentLookup::packOf).orElse("");
        return new LoadedVehicle(spec.kind(), spec.name(), spec.file().displayName().name(), pack);
    }

    private static WeaponInfo weaponInfo(WeaponSpec spec) {
        String pack = Weapons.registry().entry(spec.name()).map(LoadedContentLookup::packOf).orElse("");
        return new LoadedWeapon(spec.name(), spec.definition().displayName(), spec.definition().type(), pack);
    }

    private static String packOf(ContentEntry<?> entry) {
        return entry.location().getAddonDomain();
    }

    private record LoadedVehicle(VehicleKind kind, String id, String displayName, String packId)
            implements VehicleInfo {
    }

    private record LoadedWeapon(String id, String displayName, String type, String packId) implements WeaponInfo {
    }
}
