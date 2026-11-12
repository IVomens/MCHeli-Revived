package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.CrewEntries.CameraSpec;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * One entry of YAML {@code Seats}.
 *
 * @param unmountPosition where the rider is put when leaving, or null for beside the vehicle
 * @param switchGunner    the rider can switch to gunner view
 * @param fixedRotation   the view is locked to {@code fixedYaw} and {@code fixedPitch}
 * @param rotating        the seat turns with the rider's view
 * @param invertCamera    the gunner camera looks the other way
 * @param camera          where a gunner's camera sits, or null for the rider's eyes
 * @param hud             the HUD shown in this seat, or null for none
 * @param excludeWith     seats and racks that cannot be taken while this one is
 */
public record Seat(Vec3 position, @Nullable Vec3 unmountPosition, boolean gunner, boolean switchGunner,
                   boolean fixedRotation, float fixedYaw, float fixedPitch, float minPitch, float maxPitch,
                   boolean rotating, boolean invertCamera, @Nullable CameraSpec camera, @Nullable String hud,
                   SeatExclusions excludeWith) {

    Seat withHud(String newHud) {
        return new Seat(this.position, this.unmountPosition, this.gunner, this.switchGunner, this.fixedRotation,
                this.fixedYaw, this.fixedPitch, this.minPitch, this.maxPitch, this.rotating, this.invertCamera,
                this.camera, newHud, this.excludeWith);
    }

    Seat withExclusions(SeatExclusions exclusions) {
        return new Seat(this.position, this.unmountPosition, this.gunner, this.switchGunner, this.fixedRotation,
                this.fixedYaw, this.fixedPitch, this.minPitch, this.maxPitch, this.rotating, this.invertCamera,
                this.camera, this.hud, exclusions);
    }
}
