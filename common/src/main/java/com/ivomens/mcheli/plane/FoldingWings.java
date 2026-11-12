package com.ivomens.mcheli.plane;

import com.ivomens.mcheli.core.sim.model.FixedWingHandling;
import com.ivomens.mcheli.aircraft.AircraftPhysics;
import com.ivomens.mcheli.aircraft.Parts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.vehicle.VehicleMotion;
import com.ivomens.mcheli.vehicle.component.EngineComponent;
import com.ivomens.mcheli.vehicle.component.PartsComponent;
import com.ivomens.mcheli.vehicle.component.VehicleComponent;
import com.ivomens.mcheli.vehicle.definition.VehicleDefinition;

import javax.annotation.Nullable;

/**
 * Wings that fold for parking, or sweep back for speed. Folded wings ground the plane; swept wings
 * may stay back at speed and unfold by themselves once the plane is airborne and accelerating.
 */
public final class FoldingWings implements VehicleComponent {

    private final AbstractPlaneEntity plane;
    private final PartsComponent parts;
    private final EngineComponent engine;
    private final AircraftPhysics physics;
    @Nullable
    private Parts wing;

    FoldingWings(AbstractPlaneEntity plane, PartsComponent parts, EngineComponent engine, AircraftPhysics physics) {
        this.plane = plane;
        this.parts = parts;
        this.engine = engine;
        this.physics = physics;
    }

    void build(VehicleSpec info) {
        if (!info.kindParts().haveWing()) {
            this.wing = null;
            return;
        }
        Parts part = this.parts.newPart(PartsComponent.BIT_WING, "Wing");
        part.setRotationMax(90.0F);
        part.setRotationInv(2.5F);
        setSwitchSounds(part);
        this.wing = part;
    }

    static void setSwitchSounds(Parts part) {
        Parts.Sound click = new Parts.Sound("plane_cc", 1.0F, 0.5F);
        part.setOnSounds(click, click);
        part.setOffSounds(click, click);
    }

    public boolean isFitted() {
        return this.wing != null;
    }

    @Nullable
    public Parts part() {
        return this.wing;
    }

    private boolean sweeps() {
        return this.plane.planeSpec().file().kindFeatures().variableSweepWing();
    }

    /** Folded wings ground the plane; swept wings may stay folded at speed. */
    boolean canFly() {
        if (this.wing == null) {
            return true;
        }
        if (sweeps()) {
            return this.engine.throttle() >= 0.2 || this.wing.isOFF();
        }
        return this.wing.isOFF();
    }

    public boolean canFold() {
        AbstractPlaneEntity p = this.plane;
        if (this.wing == null || this.parts.modeSwitchCooldown() > 0) {
            return false;
        }
        double throttle = this.engine.throttle();
        if (sweeps()) {
            // sweep back only at speed in the air, or nearly stopped on the ground
            if (!p.onGround() && VehicleMotion.isClearBelow(p, 3, 20)) {
                if (throttle < 0.7F) {
                    return false;
                }
            } else if (throttle > 0.1F) {
                return false;
            }
        } else if (!p.onGround() && VehicleMotion.isClearBelow(p, 3, 3) || throttle > 0.01F) {
            // folding wings are for parking only
            return false;
        }
        return this.wing.isOFF();
    }

    public boolean canUnfold() {
        return this.wing != null && this.parts.modeSwitchCooldown() <= 0 && this.wing.isON();
    }

    public void fold(boolean fold) {
        if (this.wing != null && this.parts.modeSwitchCooldown() <= 0) {
            this.wing.setStatusServer(fold);
            this.parts.setModeSwitchCooldown(PartsComponent.MODE_SWITCH_COOLDOWN);
        }
    }

    public float angle() {
        return this.wing != null ? this.wing.rotation() : 0.0F;
    }

    public float prevAngle() {
        return this.wing != null ? this.wing.prevRotation() : 0.0F;
    }

    /** Server: swept wings unfold by themselves once the plane is airborne and accelerating. */
    void unsweepWhenAccelerating() {
        AbstractPlaneEntity p = this.plane;
        double throttle = this.engine.throttle();
        if (this.wing != null && sweeps() && this.wing.isON() && throttle >= 0.2F
                && (throttle < 0.5 || !VehicleMotion.isClearBelow(p, 1, 10))) {
            this.wing.setStatusServer(false);
        }
    }

    /** Swept wings trade speed for low-speed handling as they unfold. */
    float maxSpeed() {
        AbstractPlaneEntity p = this.plane;
        float speed = this.physics.handling(FixedWingHandling.class).maxSpeed();
        VehicleDefinition.WingSweepDef sweep = p.definition().wingSweep();
        Parts sweepPart = this.wing != null ? this.wing : this.parts.hatch();
        if (sweep == null || sweepPart == null) {
            return speed;
        }
        return sweep.maxSpeed(speed, sweepPart.getFactor());
    }
}
