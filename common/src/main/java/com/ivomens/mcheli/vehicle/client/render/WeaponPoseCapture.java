package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.combat.weapon.RecoilBuffer;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.MountPose;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.WeaponPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Reads where each weapon part of a vehicle points into {@link WeaponPose}s. */
final class WeaponPoseCapture {

    private WeaponPoseCapture() {
    }

    static List<WeaponPose> capture(AircraftEntity ac, VehicleSpec info, float tickTime) {
        if (info.parts().weapons().isEmpty()) {
            return List.of();
        }
        List<WeaponPose> poses = new ArrayList<>(info.parts().weapons().size());
        WeaponGroup lastWeaponSet = null;
        Entity rider = ac.getRiddenByEntity();
        int weaponIndex = 0;
        for (VehicleParts.PartWeapon part : info.parts().weapons()) {
            WeaponGroup weaponSet = ac.weapons().byName(part.name[0]);
            if (weaponSet != lastWeaponSet) {
                weaponIndex = 0;
                lastWeaponSet = weaponSet;
            }
            // hidden parts do not count towards the index of the next missile
            boolean hidden = part.hideGM && LocalClient.get().isFirstPerson() && isUsedByClientPlayer(ac, part,
                    weaponSet);
            boolean loaded = !hidden && (!part.isMissile || ac.weapons().isOnCooldown(weaponSet, weaponIndex));
            poses.add(pose(ac, part, weaponSet, rider, hidden, loaded, tickTime));
            if (!hidden) {
                weaponIndex++;
            }
        }
        return List.copyOf(poses);
    }

    private static WeaponPose pose(AircraftEntity ac, VehicleParts.PartWeapon part, @Nullable WeaponGroup weaponSet,
                                   @Nullable Entity rider, boolean hidden, boolean loaded, float tickTime) {
        float turretYaw = part.turret
                ? Mth.lerp(tickTime, ac.aim().prevRiderYaw() - ac.yRotO, ac.aim().riderYaw() - ac.getYRot())
                : 0.0F;
        MountPose mount = null;
        if (weaponSet != null) {
            float[] childRecoil = new float[part.child.size()];
            for (int i = 0; i < childRecoil.length; i++) {
                VehicleParts.PartWeaponChild child = part.child.get(i);
                childRecoil[i] = child.recoilBuf != 0.0F ? recoil(ac, weaponSet, child.name, tickTime) : 0.0F;
            }
            mount = new MountPose(weaponSet.turret().defaultYaw(), turretYaw - weaponSet.turret().turretYaw(),
                    part.recoilBuf != 0.0F ? recoil(ac, weaponSet, part.name, tickTime) : 0.0F,
                    part.rotBarrel ? Mth.lerp(tickTime, weaponSet.barrels().prevAngle(), weaponSet.barrels().angle())
                            : 0.0F,
                    Angles.wrap(childRecoil));
        }
        return new WeaponPose(hidden, loaded, turretYaw, weaponYaw(ac, weaponSet, rider, tickTime),
                weaponPitch(ac, weaponSet, rider, tickTime), mount);
    }

    private static boolean isUsedByClientPlayer(AircraftEntity ac, VehicleParts.PartWeapon part,
                                                @Nullable WeaponGroup weaponSet) {
        if (weaponSet == null) {
            return ac.seats().isAboard(LocalClient.get().player());
        }
        for (String weaponName : part.name) {
            if (LocalClient.get().isLocalPlayer(ac.weapons().userOf(weaponName))) {
                return true;
            }
        }
        return false;
    }

    /** Interpolated weapon yaw relative to the airframe: the weapon's own, the rider's, or the last rider's. */
    private static float weaponYaw(AircraftEntity ac, @Nullable WeaponGroup weaponSet, @Nullable Entity rider,
                                   float tickTime) {
        float yaw;
        float prevYaw;
        if (weaponSet != null) {
            yaw = weaponSet.yaw() - weaponSet.turret().defaultYaw();
            prevYaw = weaponSet.turret().prevYaw() - weaponSet.turret().defaultYaw();
        } else if (rider != null) {
            yaw = rider.getYRot() - ac.getYRot();
            prevYaw = rider.yRotO - ac.yRotO;
        } else {
            yaw = ac.aim().riderYaw() - ac.getYRot();
            prevYaw = ac.aim().prevRiderYaw() - ac.yRotO;
        }
        if (yaw - prevYaw > 180.0F) {
            prevYaw += 360.0F;
        } else if (yaw - prevYaw < -180.0F) {
            prevYaw -= 360.0F;
        }
        return Mth.lerp(tickTime, prevYaw, yaw);
    }

    private static float weaponPitch(AircraftEntity ac, @Nullable WeaponGroup weaponSet, @Nullable Entity rider,
                                     float tickTime) {
        float pitch;
        float prevPitch;
        if (weaponSet != null) {
            pitch = weaponSet.pitch();
            prevPitch = weaponSet.turret().prevPitch();
        } else if (rider != null) {
            pitch = rider.getXRot();
            prevPitch = rider.xRotO;
        } else {
            pitch = ac.aim().riderPitch();
            prevPitch = ac.aim().prevRiderPitch();
        }
        return Mth.lerp(tickTime, prevPitch, pitch);
    }

    /** Interpolated recoil; a part linked to several weapons kicks back with the strongest of them. */
    private static float recoil(AircraftEntity ac, WeaponGroup weaponSet, String[] weaponNames, float tickTime) {
        RecoilBuffer strongest = weaponSet.recoil(0);
        if (weaponNames.length > 1) {
            for (String name : weaponNames) {
                WeaponGroup linked = ac.weapons().byName(name);
                if (linked != null && linked.recoil(0).value() > strongest.value()) {
                    strongest = linked.recoil(0);
                }
            }
        }
        return Mth.lerp(tickTime, strongest.previous(), strongest.value());
    }
}
