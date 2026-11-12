package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.core.math.Degrees;
import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The search lights: the switch (synced and saved), where a steerable light points (it follows its
 * operator as the model is drawn), and how brightly the beam lights an entity.
 */
public final class SearchLightComponent implements VehicleComponent {

    /** Light level an entity in the centre of a beam gets, before the cap. */
    private static final double BEAM_CENTRE_LIGHT = 1440.0;
    private static final float MAX_LIGHT = 240.0F;

    private final AircraftEntity vehicle;
    private final StatusWord status;
    private final SeatsComponent seats;
    private final BodyComponent body;
    private final RunningGearComponent runningGear;
    private float aimYaw;
    private float aimPitch;
    /** How far the hatches in front of the lights are open, 0 to 1. */
    private float hatch;
    private float prevHatch;

    public SearchLightComponent(AircraftEntity vehicle, StatusWord status, SeatsComponent seats, BodyComponent body,
            RunningGearComponent runningGear) {
        this.vehicle = vehicle;
        this.status = status;
        this.seats = seats;
        this.body = body;
        this.runningGear = runningGear;
    }

    public boolean isFitted() {
        VehicleSpec info = this.vehicle.spec();
        return info != null && !info.parts().searchLights().isEmpty();
    }

    /** The pilot and the copilot work the light. */
    public boolean canSwitch(Entity entity) {
        return isFitted() && this.seats.seatIdOf(entity) <= 1;
    }

    public boolean isOn() {
        return this.status.get(StatusWord.SEARCH_LIGHT);
    }

    /** Server: switches the light. */
    public void setOn(boolean on) {
        this.status.set(StatusWord.SEARCH_LIGHT, on);
    }

    public float aimYaw() {
        return this.aimYaw;
    }

    public float aimPitch() {
        return this.aimPitch;
    }

    /** Points the steerable lights; the model renderer does this as it draws them. */
    public void aimAt(float yaw, float pitch) {
        this.aimYaw = yaw;
        this.aimPitch = pitch;
    }

    /** The hatches in front of the lights open while the light is on and close when it goes off. */
    public void animateHatch() {
        this.prevHatch = this.hatch;
        float step = isOn() ? 0.5F : -0.5F;
        this.hatch = Mth.clamp(this.hatch + step, 0.0F, 1.0F);
    }

    public float hatch(float partialTicks) {
        return Mth.lerp(partialTicks, this.prevHatch, this.hatch);
    }

    /** Block light the beams give the target, 0 outside every beam. */
    public float lightAt(Entity target) {
        AircraftEntity v = this.vehicle;
        VehicleSpec info = v.spec();
        if (!isFitted() || !isOn() || info == null) {
            return 0.0F;
        }
        for (VehicleParts.SearchLight light : info.parts().searchLights()) {
            Vec3 origin = this.body.toWorld(light.pos);
            double dx = target.getX() - origin.x;
            double dy = target.getY() - origin.y;
            double dz = target.getZ() - origin.z;
            double distanceSqr = dx * dx + dy * dy + dz * dz;
            if (distanceSqr <= 2.0 || distanceSqr >= light.height * light.height + 20.0) {
                continue;
            }

            double elevation = Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
            double horizontalAngle;
            double verticalAngle;
            if (!light.fixDir) {
                Vec3 beam = Rotations.rotate(0.0, 0.0, 1.0, -this.aimYaw + light.yaw, -this.aimPitch + light.pitch,
                        -v.roll());
                horizontalAngle = Degrees.between(beam.x, beam.z, dx, dz);
                verticalAngle = Math.abs(Math.toDegrees(elevation) + this.aimPitch + light.pitch);
            } else {
                float steering = light.steering ? this.runningGear.wheelSteer() * light.stRot : 0.0F;
                Vec3 beam = Rotations.rotate(0.0, 0.0, 1.0, -v.getYRot() + light.yaw + steering,
                        -v.getXRot() + light.pitch, -v.roll());
                horizontalAngle = Degrees.between(beam.x, beam.z, dx, dz);
                verticalAngle = Math.abs(Math.toDegrees(elevation) + v.getXRot() + light.pitch);
            }

            float coneAngle = light.angle * 3.0F;
            if (horizontalAngle < coneAngle && verticalAngle < coneAngle) {
                float lightLevel = 0.0F;
                if (horizontalAngle + verticalAngle < coneAngle) {
                    lightLevel = (float) (BEAM_CENTRE_LIGHT * (1.0 - (horizontalAngle + verticalAngle) / coneAngle));
                }
                return Math.min(lightLevel, MAX_LIGHT);
            }
        }
        return 0.0F;
    }

    public void save(CompoundTag tag) {
        tag.putBoolean(VehicleNbtCodec.SEARCH_LIGHT, isOn());
    }

    public void load(CompoundTag tag) {
        if (isFitted() && tag.contains(VehicleNbtCodec.SEARCH_LIGHT)) {
            setOn(tag.getBoolean(VehicleNbtCodec.SEARCH_LIGHT));
        }
    }
}
