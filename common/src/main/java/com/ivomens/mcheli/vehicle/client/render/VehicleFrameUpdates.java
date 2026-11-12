package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.plane.ExhaustFlames;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import com.ivomens.mcheli.vehicle.client.ExhaustFlameTextures;
import net.minecraft.world.entity.Entity;

/**
 * The client-side vehicle state that has always moved on with every drawn frame rather than every
 * tick: where a ground vehicle's parts and the search lights aim, and the flame flip-books. It runs
 * on the render thread, once per frame the vehicle is drawn, before the snapshot is taken; the
 * snapshot itself never changes the entity.
 */
final class VehicleFrameUpdates {

    private VehicleFrameUpdates() {
    }

    /**
     * Ground vehicle parts aim where the gunner (or the detached aim) looks; with nobody aboard they
     * keep the last aim. The aim is kept on the entity because gameplay code reads it back.
     */
    static void aimGroundParts(GroundVehicleEntity vehicle) {
        Entity rider = vehicle.getRiddenByEntity();
        if (vehicle.aim().isDetachedActive()) {
            vehicle.aim().setRiderYaw(vehicle.aim().detachedYaw());
            vehicle.aim().setRiderPitch(vehicle.aim().detachedPitch());
        } else if (rider != null && !vehicle.isDestroyed()) {
            vehicle.aim().markAimed();
            vehicle.aim().setRiderYaw(rider.getYRot());
            vehicle.aim().setRiderPitch(rider.getXRot());
        } else if (!vehicle.aim().hasBeenAimed()) {
            vehicle.aim().setRiderYaw(vehicle.getYRot());
            vehicle.aim().setRiderPitch(vehicle.getXRot());
        }
    }

    /** The steerable search lights follow the gunner's look, or the pilot's without a gunner. */
    static void aimSearchLights(AircraftEntity ac) {
        Entity operator = ac.seats().riderOf(1);
        if (operator == null) {
            operator = ac.seats().riderOf(0);
        }
        if (operator != null) {
            ac.searchLight().aimAt(operator.getYRot(), operator.getXRot());
        }
    }

    /** Steps a flame's flip-book every {@code delay} drawn frames and returns the frame to show. */
    static int advanceFlameFrame(ExhaustFlames.Animation animation, int index, VehicleParts.ExhaustFlame flame) {
        int delay = Math.max(flame.delay, 1);
        int frame = animation.frame[index];
        int tick = animation.tick[index] + 1;
        if (tick >= delay) {
            tick = 0;
            frame++;
            if (frame >= ExhaustFlameTextures.frames(flame.texturePrefix)) {
                frame = 0;
            }
        }
        animation.frame[index] = frame;
        animation.tick[index] = tick;
        return frame;
    }
}
