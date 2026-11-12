package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftPhysics;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import net.minecraft.util.Mth;

/**
 * Moving parts that only matter for rendering: wheels, crawler tracks, the search light hatch and
 * free-spinning parts. Only the light hatch also moves on the server; the rest is client animation.
 * The light hatch, wheels and tracks move before the physics step (they show last tick's keys), the
 * spinning parts after it.
 */
public final class RunningGearComponent implements VehicleComponent {

    private static final float TRACK_THROTTLE_LIMIT = 0.72F;

    private final AircraftEntity vehicle;
    private final EngineComponent engine;
    private final ControlKeysComponent keys;
    private final AircraftPhysics physics;
    private float wheelRoll;
    private float prevWheelRoll;
    private float wheelSteer;
    private float prevWheelSteer;
    private final float[] trackOffset = new float[2];
    private final float[] prevTrackOffset = new float[2];
    private final float[] trackThrottle = new float[2];
    private final float[] trackRoller = new float[2];
    private final float[] prevTrackRoller = new float[2];
    private float[] spinningParts = new float[0];
    private float[] prevSpinningParts = new float[0];

    public RunningGearComponent(AircraftEntity vehicle, EngineComponent engine, ControlKeysComponent keys,
            AircraftPhysics physics) {
        this.vehicle = vehicle;
        this.engine = engine;
        this.keys = keys;
        this.physics = physics;
    }

    public void rebuild(VehicleSpec info) {
        this.spinningParts = new float[info.parts().rotatingParts().size()];
        this.prevSpinningParts = new float[info.parts().rotatingParts().size()];
    }

    public float wheelRoll(float partialTicks) {
        return Mth.lerp(partialTicks, this.prevWheelRoll, this.wheelRoll);
    }

    /** Steering of the front wheels this tick, -1 to 1 of full lock. */
    public float wheelSteer() {
        return this.wheelSteer;
    }

    public float wheelSteer(float partialTicks) {
        return Mth.lerp(partialTicks, this.prevWheelSteer, this.wheelSteer);
    }

    public float trackRoller(int side, float partialTicks) {
        return Mth.lerp(partialTicks, this.prevTrackRoller[side], this.trackRoller[side]);
    }

    /** Texture offset of a crawler track, cycling through [0, 1). */
    public double trackOffset(int side, float partialTicks) {
        return Mth.lerp(partialTicks, this.prevTrackOffset[side], this.trackOffset[side]);
    }

    public int spinningPartCount() {
        return this.spinningParts.length;
    }

    /** Angle of a spinning part, unwrapped across the 360 degree seam. */
    public float spinningPart(int index, float partialTicks) {
        float rot = this.spinningParts[index];
        float prevRot = this.prevSpinningParts[index];
        if (prevRot > rot) {
            rot += 360.0F;
        }
        return Mth.lerp(partialTicks, prevRot, rot);
    }

    /** Client only: the spinning parts are animated where they are drawn. */
    public boolean hasSpinningParts() {
        VehicleSpec info = this.vehicle.spec();
        return this.vehicle.level().isClientSide && info != null && this.spinningParts.length > 0
                && this.spinningParts.length == info.parts().rotatingParts().size();
    }

    /** Client: wheel spin follows the throttle (backwards when reversing), steering eases in and out. */
    public void animateWheels() {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        if (info == null) {
            return;
        }
        this.prevWheelRoll = this.wheelRoll;
        this.prevWheelSteer = this.wheelSteer;

        double throttle = this.engine.throttle();
        float reverse = this.physics.reverseThrust();
        if (info.file().features().canReverse() && reverse > 0.01 && throttle <= 0.0) {
            throttle = -reverse * 15.0;
        }

        boolean left = this.keys.moveLeft();
        boolean right = this.keys.moveRight();
        if (left && !right) {
            this.wheelSteer = (float) Math.min(this.wheelSteer + 0.1, 1.0);
        } else if (!left && right) {
            this.wheelSteer = (float) Math.max(this.wheelSteer - 0.1, -1.0);
        } else {
            this.wheelSteer *= 0.9F;
        }

        this.wheelRoll += (float) (throttle * info.file().wheels().wheelRotation());
        if (this.wheelRoll >= 360.0F) {
            this.wheelRoll -= 360.0F;
            this.prevWheelRoll -= 360.0F;
        } else if (this.wheelRoll < 0.0F) {
            this.wheelRoll += 360.0F;
            this.prevWheelRoll += 360.0F;
        }
    }

    /**
     * Client: drives the two crawler tracks. Steering pivots the vehicle (one track forward, the
     * other slower or backwards); reversing swaps which track leads.
     */
    public void animateTracks() {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        if (info == null) {
            return;
        }
        System.arraycopy(this.trackRoller, 0, this.prevTrackRoller, 0, 2);
        System.arraycopy(this.trackOffset, 0, this.prevTrackOffset, 0, 2);

        double throttle = this.engine.throttle();
        double pivotFactor = info.file().physics().pivotTurnThrottle() <= 0.0 ? 1.0
                : info.file().physics().pivotTurnThrottle() * 0.1;
        boolean left = this.keys.moveLeft();
        boolean right = this.keys.moveRight();
        int direction = 1;
        if (info.file().features().canReverse() && this.physics.reverseThrust() > 0.0F && throttle <= 0.0) {
            throttle = -this.physics.reverseThrust() * 5.0F;
            if (left != right) {
                left = !left;
                right = !right;
                direction = -1;
            }
        }

        double steer = 0.2 * direction;
        if (left && !right) {
            this.trackThrottle[0] += (float) steer;
            this.trackThrottle[1] -= (float) (pivotFactor * steer);
        } else if (!left && right) {
            this.trackThrottle[0] -= (float) (pivotFactor * steer);
            this.trackThrottle[1] += (float) steer;
        } else {
            throttle = Mth.clamp(throttle, -0.2, 0.2);
            this.trackThrottle[0] += (float) throttle;
            this.trackThrottle[1] += (float) throttle;
        }
        for (int i = 0; i < 2; i++) {
            moveTrack(i, info);
        }
    }

    private void moveTrack(int i, VehicleSpec info) {
        float throttle = Mth.clamp(this.trackThrottle[i], -TRACK_THROTTLE_LIMIT, TRACK_THROTTLE_LIMIT);

        float roller = this.trackRoller[i] + throttle * info.file().wheels().trackRotation();
        float prevRoller = this.prevTrackRoller[i];
        if (roller >= 360.0F) {
            roller -= 360.0F;
            prevRoller -= 360.0F;
        } else if (roller < 0.0F) {
            roller += 360.0F;
            prevRoller += 360.0F;
        }
        this.trackRoller[i] = roller;
        this.prevTrackRoller[i] = prevRoller;

        float track = this.trackOffset[i] - throttle;
        float prevTrack = this.prevTrackOffset[i];
        while (track >= 1.0F) {
            track--;
            prevTrack--;
        }
        while (track < 0.0F) {
            track++;
        }
        while (prevTrack < 0.0F) {
            prevTrack++;
        }
        this.trackOffset[i] = track;
        this.prevTrackOffset[i] = prevTrack;

        this.trackThrottle[i] = throttle * 0.75F;
    }

    /** Client: spins the always-rotating parts (radars, fans), and all spinning parts while crewed. */
    public void animateSpinningParts() {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        if (!hasSpinningParts()) {
            return;
        }
        for (int i = 0; i < this.spinningParts.length; i++) {
            this.prevSpinningParts[i] = this.spinningParts[i];
            VehicleParts.RotPart part = info.parts().rotatingParts().get(i);
            if (!ac.isDestroyed() && part.rotAlways || ac.getRiddenByEntity() != null) {
                float rotation = this.spinningParts[i] + part.rotSpeed;
                if (rotation < 0.0F) {
                    rotation += 360.0F;
                }
                if (rotation >= 360.0F) {
                    rotation -= 360.0F;
                }
                this.spinningParts[i] = rotation;
            }
        }
    }
}
