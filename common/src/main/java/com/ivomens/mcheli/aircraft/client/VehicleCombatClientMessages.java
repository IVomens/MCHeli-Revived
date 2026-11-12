package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.countermeasure.net.ChaffUseMessage;
import com.ivomens.mcheli.combat.countermeasure.net.IronCurtainUseMessage;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.net.AllAmmoCountsMessage;
import com.ivomens.mcheli.combat.weapon.net.AmmoCount;
import com.ivomens.mcheli.combat.weapon.net.AmmoCountMessage;
import com.ivomens.mcheli.combat.weapon.net.HitMarkerMessage;
import com.ivomens.mcheli.combat.weapon.net.ReloadAcceptedMessage;
import com.ivomens.mcheli.combat.weapon.net.RwrThreat;
import com.ivomens.mcheli.combat.weapon.net.RwrThreatsMessage;
import com.ivomens.mcheli.combat.weapon.net.TvMissileMessage;
import com.ivomens.mcheli.combat.weapon.net.WeaponCounters;
import com.ivomens.mcheli.combat.weapon.net.WeaponStateMessage;
import com.ivomens.mcheli.networking.MessageRegistry;
import com.ivomens.mcheli.vehicle.sensor.RWRThreatEvent;
import com.ivomens.mcheli.vehicle.sensor.RWRThreatTable;
import com.ivomens.mcheli.vehicle.sensor.client.RWRThreatClientTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Client handlers of the combat messages about a vehicle: its countermeasures, the ammunition and selection
 * of its weapons, hits it scored, the TV missile its crew steers, and the radar threats it receives.
 */
public final class VehicleCombatClientMessages {

    private VehicleCombatClientMessages() {
    }

    /** @param showHitMarker flashes the local player's own hit marker */
    public static void register(MessageRegistry registry, Runnable showHitMarker) {
        registry.onClient(ChaffUseMessage.TYPE, message -> {
            if (aircraft(message.aircraftId()) instanceof AircraftEntity aircraft) {
                aircraft.countermeasures().announceChaff(message.time());
            }
        });
        registry.onClient(IronCurtainUseMessage.TYPE, message -> {
            if (aircraft(message.aircraftId()) instanceof AircraftEntity aircraft) {
                aircraft.countermeasures().ironCurtain().showRun(message.time());
            }
        });

        registry.onClient(AmmoCountMessage.TYPE, VehicleCombatClientMessages::setAmmo);
        registry.onClient(AllAmmoCountsMessage.TYPE, VehicleCombatClientMessages::setAllAmmo);
        registry.onClient(WeaponStateMessage.TYPE, VehicleCombatClientMessages::setWeaponState);
        registry.onClient(ReloadAcceptedMessage.TYPE, message -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (aircraft(message.aircraftId()) instanceof AircraftEntity aircraft && player != null) {
                aircraft.ammoSupply().manualReload(player);
            }
        });

        registry.onClient(HitMarkerMessage.TYPE, message -> showHit(message, showHitMarker));
        registry.onClient(TvMissileMessage.TYPE, VehicleCombatClientMessages::takeTvMissile);
        registry.onClient(RwrThreatsMessage.TYPE, VehicleCombatClientMessages::updateRwr);
    }

    @Nullable
    private static Entity entity(int id) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && id >= 0 ? level.getEntity(id) : null;
    }

    @Nullable
    private static AircraftEntity aircraft(int id) {
        return entity(id) instanceof AircraftEntity aircraft ? aircraft : null;
    }

    private static void setAmmo(AmmoCountMessage message) {
        AircraftEntity aircraft = aircraft(message.aircraftId());
        if (aircraft != null && message.weaponId() >= 0 && message.weaponId() < aircraft.weapons().count()) {
            setAmmo(aircraft.weapons().get(message.weaponId()), message.count().ammo(), message.count().reserveAmmo());
        }
    }

    private static void setAllAmmo(AllAmmoCountsMessage message) {
        AircraftEntity aircraft = aircraft(message.aircraftId());
        if (aircraft == null) {
            return;
        }
        List<AmmoCount> counts = message.counts();
        int weapons = Math.min(aircraft.weapons().count(), counts.size());
        for (int i = 0; i < weapons; i++) {
            setAmmo(aircraft.weapons().get(i), counts.get(i).ammo(), counts.get(i).reserveAmmo());
        }
    }

    private static void setAmmo(WeaponGroup weapon, int ammo, int reserveAmmo) {
        weapon.setAmmo(ammo);
        weapon.setReserveAmmo(reserveAmmo);
    }

    private static void setWeaponState(WeaponStateMessage message) {
        AircraftEntity aircraft = aircraft(message.aircraftId());
        int seatId = message.seatId();
        int weaponId = message.weaponId();
        if (aircraft == null || !aircraft.seats().isValidSeatId(seatId)
                || weaponId < 0 || weaponId >= aircraft.weapons().count()) {
            return;
        }
        WeaponGroup weapon = aircraft.weapons().get(weaponId);
        WeaponCounters counters = message.counters();
        setAmmo(weapon, counters.ammo(), counters.reserveAmmo());
        weapon.syncTimers(counters.cooldown(), counters.reloadCooldown());
        // The local player's own weapon selection is driven by its input, not by the server echo.
        Entity rider = aircraft.seats().riderOf(seatId);
        if (rider == null || rider != Minecraft.getInstance().player) {
            aircraft.weapons().applySelection(seatId, weaponId);
        }
    }

    private static void showHit(HitMarkerMessage message, Runnable showHitMarker) {
        if (message.aircraftId() <= 0) {
            showHitMarker.run();
        } else if (aircraft(message.aircraftId()) instanceof AircraftEntity aircraft) {
            aircraft.weapons().markHit();
        }
    }

    private static void takeTvMissile(TvMissileMessage message) {
        AircraftEntity aircraft = aircraft(message.aircraftId());
        if (aircraft != null && entity(message.missileId()) instanceof ProjectileEntity missile) {
            missile.setShooter(Minecraft.getInstance().player, missile.platform());
            aircraft.cameras().watchTvMissile(missile);
        }
    }

    private static void updateRwr(RwrThreatsMessage message) {
        List<RWRThreatEvent> events = message.threats().stream().map(VehicleCombatClientMessages::event).toList();
        RWRThreatClientTracker.updateTable(new RWRThreatTable(message.receiverId(), message.snapshotSeq(),
                new ArrayList<>(events)));
    }

    private static RWRThreatEvent event(RwrThreat threat) {
        return new RWRThreatEvent(threat.emitterId(), threat.kind().emitterKind(), threat.kind().threatMode(),
                threat.kind().seeker(), threat.sourceName(), threat.angleDeg(), threat.signal().strength(),
                threat.signal().confidence());
    }
}
