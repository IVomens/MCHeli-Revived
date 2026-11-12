package com.ivomens.mcheli.setup.client.config;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.aircraft.net.ReloadVehicleContentMessage;
import com.ivomens.mcheli.aircraft.net.ReloadWeaponContentMessage;
import com.ivomens.mcheli.combat.weapon.Weapons;
import com.ivomens.mcheli.config.DebugLog;
import com.ivomens.mcheli.hud.HudManager;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.vehicle.client.OnDemandModels;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The content reloads of the options screen's development page, for pack authors in single player:
 * the definitions are read again on both sides and applied to every loaded vehicle.
 */
final class DevelopmentReloads {

    private DevelopmentReloads() {
    }

    /** Reloads every weapon, then the definition of each vehicle type that is loaded. */
    static void reloadAllWeapons(Minecraft mc) {
        DebugLog.log(true, "Reloading all weapons.");
        Weapons.registry().reloadAll();
        Network.sendToServer(new ReloadWeaponContentMessage());
        Set<String> reloaded = new HashSet<>();
        for (AircraftEntity aircraft : loadedAircraft(mc)) {
            VehicleSpec info = aircraft.spec();
            if (info != null && reloaded.add(info.name())) {
                VehicleTypes.registry(info.kind()).reload(info.name());
                aircraft.changeType(info.name());
                aircraft.body().onDefinitionReloaded();
            }
        }
        reloadOnDemandModels();
    }

    /** Reloads every HUD, then the vehicle the player is in. */
    static void reloadHuds(Minecraft mc) {
        HudManager.registry().reloadAll();
        reloadRiddenAircraft(mc);
    }

    /** Reloads the definition of the vehicle the player is in and applies it to every loaded copy. */
    static void reloadRiddenAircraft(Minecraft mc) {
        AircraftEntity ridden = RiddenVehicle.of(mc.player);
        if (ridden != null && ridden.spec() != null) {
            String name = ridden.spec().name();
            DebugLog.log(true, "Reloading " + name);
            VehicleTypes.registry(ridden.spec().kind()).reload(name);
            for (AircraftEntity aircraft : loadedAircraft(mc)) {
                if (aircraft.spec() != null && aircraft.spec().name().equals(name)) {
                    aircraft.changeType(name);
                    aircraft.body().onDefinitionReloaded();
                }
            }
            Network.sendToServer(new ReloadVehicleContentMessage());
        }
        reloadOnDemandModels();
    }

    private static List<AircraftEntity> loadedAircraft(Minecraft mc) {
        List<AircraftEntity> aircraft = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof AircraftEntity vehicle) {
                aircraft.add(vehicle);
            }
        }
        return aircraft;
    }

    /**
     * Frees the vehicle models and re-arms the on-demand placeholders so each vehicle loads its model
     * again on its next render. Does nothing unless on-demand loading is on.
     */
    private static void reloadOnDemandModels() {
        OnDemandModels.onReloadAircraft(OnDemandModels::installAll);
    }
}
