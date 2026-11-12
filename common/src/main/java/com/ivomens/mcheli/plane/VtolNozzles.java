package com.ivomens.mcheli.plane;

import com.ivomens.mcheli.aircraft.Parts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.vehicle.component.PartsComponent;
import com.ivomens.mcheli.vehicle.component.VehicleComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * The swivelling nozzles (or tilting rotors) of a VTOL plane: horizontal for flight, vertical to
 * hover. Planes that rest on their nozzles start vertical and cannot leave VTOL on the ground.
 */
public final class VtolNozzles implements VehicleComponent {

    /** Nozzle angle of full VTOL. */
    private static final float VERTICAL = 90.0F;
    private static final float MAX_SWITCH_ROLL = 30.0F;

    private final AbstractPlaneEntity plane;
    private final PartsComponent parts;
    @Nullable
    private Parts nozzle;

    VtolNozzles(AbstractPlaneEntity plane, PartsComponent parts) {
        this.plane = plane;
        this.parts = parts;
    }

    /** Builds the nozzle part for the definition, or none for planes without nozzles, rotors or VTOL. */
    void build(VehicleSpec info) {
        if (info.kindParts().nozzles().isEmpty() && info.kindParts().propellers().isEmpty()
                && !info.file().kindFeatures().vtol().enabled()) {
            this.nozzle = null;
            return;
        }
        Parts part = this.parts.newPart(PartsComponent.BIT_NOZZLE, "Nozzle");
        part.setRotationMax(VERTICAL);
        part.setRotationInv(1.5F);
        FoldingWings.setSwitchSounds(part);
        part.setSwitchingSound(new Parts.Sound("plane_cv", 1.0F, 0.5F));
        if (info.file().kindFeatures().vtol().byDefault()) {
            part.forceSwitch(true);
        }
        this.nozzle = part;
    }

    public boolean isFitted() {
        return this.nozzle != null;
    }

    @Nullable
    public Parts part() {
        return this.nozzle;
    }

    /** True while the nozzles are commanded vertical (they may still be rotating). */
    public boolean isVertical() {
        return this.nozzle != null && this.nozzle.getStatus();
    }

    /** 0 = horizontal, 1 = rotating, 2 = fully vertical. */
    public int mode() {
        if (!isVertical()) {
            return angle() <= 0.005F ? 0 : 1;
        }
        return angle() >= 89.995F ? 2 : 1;
    }

    public float angle() {
        return this.nozzle != null ? this.nozzle.rotation() : 0.0F;
    }

    public float prevAngle() {
        return this.nozzle != null ? this.nozzle.prevRotation() : 0.0F;
    }

    /**
     * Whether the pilot may toggle VTOL right now. A positive answer starts the switch cooldown, so call
     * this only when the switch is actually about to happen.
     */
    public boolean canSwitch() {
        AbstractPlaneEntity p = this.plane;
        VehicleSpec info = p.planeSpec();
        if (info == null || !info.file().kindFeatures().vtol().enabled() || this.parts.modeSwitchCooldown() > 0
                || mode() == 1 || Mth.abs(p.roll()) > MAX_SWITCH_ROLL
                || p.onGround() && info.file().kindFeatures().vtol().byDefault()) {
            return false;
        }
        this.parts.setModeSwitchCooldown(PartsComponent.MODE_SWITCH_COOLDOWN);
        return true;
    }

    /** Swivels the nozzles (the server decides); the pilot's view levels out. */
    public void switchTo(boolean vertical) {
        AbstractPlaneEntity p = this.plane;
        if (this.nozzle == null) {
            return;
        }
        // planes that rest on their nozzles cannot leave VTOL on the ground
        if (p.planeSpec().file().kindFeatures().vtol().byDefault() && p.onGround() && !vertical) {
            return;
        }
        this.nozzle.setStatusServer(vertical);
        Entity rider = p.getRiddenByEntity();
        if (rider != null && !rider.isRemoved()) {
            rider.setXRot(0.0F);
            rider.xRotO = 0.0F;
        }
    }

    /** Planes that rest on their nozzles go back to VTOL once they are on the ground. */
    void restOnNozzlesWhenLanded() {
        if (this.plane.onGround() && mode() == 0
                && this.plane.planeSpec().file().kindFeatures().vtol().byDefault()) {
            switchTo(true);
        }
    }
}
