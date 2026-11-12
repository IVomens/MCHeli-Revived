package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.api.target.SeekerType;
import com.ivomens.mcheli.combat.guidance.ExternalTargets;
import com.ivomens.mcheli.combat.guidance.LockOnSeeker;
import com.ivomens.mcheli.vehicle.sensor.ThreatBoard;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.net.BvrLockMessage;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Locks the current weapon of the aircraft the sender rides or controls onto a radar contact, and
 * reports the radar track to the target's warning receiver (or, for an addon's target, its profile).
 */
public final class BvrLockHandler implements ServerMessageHandler<BvrLockMessage> {

    @Override
    public void handle(BvrLockMessage message, ServerPlayer sender) {
        AircraftEntity aircraft = RiddenVehicle.of(sender);
        if (aircraft == null) {
            return;
        }
        int targetId = message.targetId();
        ThreatBoard.of(sender.server).reportRadarTracking(sender, aircraft.getId(), targetId);
        WeaponGroup weaponSet = aircraft.weapons().current(sender);
        if (weaponSet == null || weaponSet.currentMount() == null) {
            return;
        }
        LockOnSeeker guidance = weaponSet.currentMount().seeker();
        if (guidance == null) {
            return;
        }
        if (targetId <= 0) {
            guidance.designate(null);
        } else {
            Entity target = ServerMessageHandler.entityNear(sender, targetId);
            if (target != null) {
                if (target != guidance.target()) {
                    ExternalTargets.get().lockedOn(sender, target, aircraft, SeekerType.RADAR);
                }
                guidance.designate(target);
            }
        }
    }
}
