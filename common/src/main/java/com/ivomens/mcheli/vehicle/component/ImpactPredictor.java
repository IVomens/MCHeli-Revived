package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.weapon.Shooter;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;


/** Where the current weapon of a crew member would land, for the bombing and CCIP displays. */
public final class ImpactPredictor {

    private final AircraftEntity vehicle;
    private final WeaponComponent weapons;
    private final LifecycleComponent lifecycle;
    private int landInDistanceTick;
    private double landInDistance = -1.0;
    private int predictedImpactTick = -1;
    @Nullable
    private Vec3 predictedImpactPoint;
    private double prevLandInDistance = -1.0;
    @Nullable
    private Vec3 impactPos;
    @Nullable
    private Vec3 prevImpactPos;

    ImpactPredictor(AircraftEntity vehicle, WeaponComponent weapons, LifecycleComponent lifecycle) {
        this.vehicle = vehicle;
        this.weapons = weapons;
        this.lifecycle = lifecycle;
    }

    @Nullable
    public Vec3 impactPos() {
        return this.impactPos;
    }

    @Nullable
    public Vec3 prevImpactPos() {
        return this.prevImpactPos;
    }

    /** Horizontal distance to where the current weapon would land, recomputed every 5 ticks on the server. */
    public double getLandInDistance(@Nullable Entity user) {
        AircraftEntity ac = this.vehicle;
        int tick = this.lifecycle.age();
        if (this.landInDistanceTick == tick || !ac.level().isClientSide && tick % 5 != 0) {
            return this.landInDistance;
        }
        this.landInDistanceTick = tick;
        this.prevLandInDistance = this.landInDistance;
        this.prevImpactPos = this.impactPos;
        if (user == null) {
            return this.landInDistance;
        }

        WeaponGroup current = this.weapons.current(user);
        Shooter shooter = this.weapons.shooter(user);
        Vec3 shotPos = shooter.muzzleOffset(current.firstMount()).add(ac.getX(), ac.getY(), ac.getZ());
        float[] shotDir = this.weapons.shot().direction(user);
        this.impactPos = current.predictImpact(shooter, current.currentMount(), shotPos, Mth.wrapDegrees(shotDir[0]),
                Mth.wrapDegrees(shotDir[1]), 0.0F);
        if (this.impactPos != null) {
            double dx = this.impactPos.x - ac.getX();
            double dz = this.impactPos.z - ac.getZ();
            this.landInDistance = Math.sqrt(dx * dx + dz * dz);
        } else {
            this.landInDistance = -1.0;
        }
        if (this.prevLandInDistance < 0) {
            this.prevLandInDistance = this.landInDistance;
        }
        if (this.prevImpactPos == null) {
            this.prevImpactPos = this.impactPos;
        }
        return this.landInDistance;
    }

    /** CCIP impact point of the user's current weapon, computed at most once per tick. */
    @Nullable
    public Vec3 getPredictedImpactPoint(@Nullable Entity user) {
        AircraftEntity ac = this.vehicle;
        if (this.predictedImpactTick == this.lifecycle.age()) {
            return this.predictedImpactPoint;
        }
        this.predictedImpactTick = this.lifecycle.age();
        this.predictedImpactPoint = null;
        if (user != null) {
            WeaponGroup current = this.weapons.current(user);
            WeaponSpec info = current.spec();
            if (info != null && info.ccip() && WeaponSpec.isCCIPSupportedType(info.definition().type())) {
                this.predictedImpactPoint = current.predictImpact(this.weapons.shooter(user),
                        new Vec3(ac.getX(), ac.getY(), ac.getZ()), ac.roll());
            }
        }
        return this.predictedImpactPoint;
    }
}
