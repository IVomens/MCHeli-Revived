package com.ivomens.mcheli.vehicle.client.camera;

import com.ivomens.mcheli.core.sim.math.Orientation;
import com.ivomens.mcheli.aircraft.AircraftCamera;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.client.camera.CameraAngles;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * The local player's camera while riding a vehicle: third person distance, view roll, the airframe-locked
 * (quaternion) camera, zoom, and the two camera stand-ins the view switches to (the gunner/UAV/TV missile
 * view and the smoothed third person anchor). It also says when the vanilla HUD elements that clash with
 * the vehicle HUD are hidden.
 *
 * <p>One per client, owned by the client session; only the client thread touches it.
 */
public final class VehicleCamera {

    private static final float DEF_THIRD_CAMERA_DIST = 4.0F;
    private static final float MAX_THIRD_CAMERA_DIST = 60.0F;
    private static final float MIN_REQUESTED_THIRD_CAMERA_DIST = 0.1F;
    private static final Quaternionf ROT_Y_PI = new Quaternionf().rotateY((float) Math.PI);
    private static final Quaternionf ROT_Y_PI_INVERSE = new Quaternionf(ROT_Y_PI).conjugate();

    /** Scratch values for the per-frame camera maths. */
    private final Quaternionf view = new Quaternionf();
    private final Quaternionf camera = new Quaternionf();
    private final Vector3f euler = new Vector3f();
    private float roll;
    private float distance = DEF_THIRD_CAMERA_DIST;
    private float zoom = 1.0F;
    @Nullable
    private AircraftEntity ridingAircraft;
    @Nullable
    private ViewEntityDummy viewDummy;
    @Nullable
    private ThirdPersonCamera thirdPerson;

    @Nullable
    private static AircraftEntity riddenAircraft(Player player) {
        Entity riding = player.getVehicle();
        return switch (riding) {
            case AircraftEntity aircraft -> aircraft;
            case SeatEntity seat -> seat.getParent();
            case IUavStation station -> station.getControlled();
            case null, default -> null;
        };
    }

    /** The third person camera distance to use instead of {@code vanillaDistance}. */
    public float adjustDistance(float vanillaDistance) {
        if (this.ridingAircraft != null) {
            return vanillaDistance + (this.distance - DEF_THIRD_CAMERA_DIST);
        }
        return vanillaDistance;
    }

    /** Adjusts the camera angles about to be applied, in place. */
    public void adjustAngles(CameraAngles angles) {
        Minecraft mc = Minecraft.getInstance();
        AircraftEntity aircraft = this.ridingAircraft;
        if (aircraft == null || mc.player == null || !aircraft.modes().canSwitchFreeLook()
                || !aircraft.isPilot(mc.player)) {
            return;
        }
        Orientation airframe = aircraft.attitudeControl().cameraOrientation();
        boolean quaternionCamera = McheliConfig.flight().quaternionRotation()
                && airframe != null
                && !aircraft.weapons().aiming().hasIndependentAim(mc.player);

        if (quaternionCamera && aircraft.modes().viewFollowsPitch()) {
            applyAirframeCamera(angles, setView(airframe));
        } else if (quaternionCamera && aircraft.modes().isFreeLook() && aircraft.cameras().freeLookOffset() != null) {
            // cockpit-relative free look: the eye is orientation * freeLookOffset, so the view stays bolted
            // to the airframe and the mouse pans in the screen plane (no roll skew, no gimbal lock)
            applyAirframeCamera(angles, setView(airframe).mul(aircraft.cameras().freeLookOffset()));
        } else {
            // airframe-coupled view: the player's rotation follows the aircraft, only the roll is added
            angles.addRoll(this.roll);
        }
    }

    private Quaternionf setView(Orientation orientation) {
        return this.view.set(orientation.x(), orientation.y(), orientation.z(), orientation.w());
    }

    /**
     * Sets the camera angles so the view equals the body-to-world orientation {@code view}.
     * <p>
     * The camera's view rotation is {@code Rz(roll) * Rx(pitch) * Ry(yaw + 180)}, so
     * {@code Ry(180) * view * Ry(-180) = Ry(-yaw) * Rx(-pitch) * Rz(-roll)}: decompose that
     * conjugated orientation in YXZ order and negate each angle.
     */
    private void applyAirframeCamera(CameraAngles angles, Quaternionf view) {
        Quaternionf conjugated = this.camera.set(ROT_Y_PI).mul(view).mul(ROT_Y_PI_INVERSE);
        if (!Float.isFinite(conjugated.x) || !Float.isFinite(conjugated.w)) {
            return;
        }
        conjugated.getEulerAnglesYXZ(this.euler);
        angles.set(Mth.wrapDegrees((float) -Math.toDegrees(this.euler.y)), (float) -Math.toDegrees(this.euler.x),
                (float) -Math.toDegrees(this.euler.z));
    }

    /** The field of view to use instead of {@code fov}: zoomed while the view comes from a vehicle camera. */
    public double adjustFov(double fov, Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        if (this.ridingAircraft != null && mc.level != null) {
            Entity viewEntity = camera.getEntity();
            if (viewDummy(mc.level) == viewEntity || viewEntity instanceof ThirdPersonCamera) {
                return zoomed(fov);
            }
        }
        return fov;
    }

    /** The field of view narrowed by the current zoom. */
    public double zoomed(double fov) {
        return fov * (1.0F / this.zoom);
    }

    /**
     * True while the player rides an aircraft: the vanilla health, armor, food, air, crosshair, boss bar
     * and camera overlay HUD elements are then hidden (the hotbar stays visible).
     */
    public boolean hidesVanillaHud() {
        Player player = Minecraft.getInstance().player;
        return player != null && riddenAircraft(player) != null;
    }

    public void setRoll(float degrees) {
        this.roll = Mth.wrapDegrees(degrees);
    }

    public void setZoom(float zoom) {
        this.zoom = zoom;
    }

    public float thirdPersonDistance() {
        return this.distance;
    }

    public void setThirdPersonDistance(float distance) {
        this.distance = Mth.clamp(distance, DEF_THIRD_CAMERA_DIST, MAX_THIRD_CAMERA_DIST);
    }

    /** The third person distance a vehicle asks for; distances below 0.1 mean it has none. */
    public void useVehicleDistance(float distance) {
        if (!(distance < MIN_REQUESTED_THIRD_CAMERA_DIST)) {
            setThirdPersonDistance(distance);
        }
    }

    /** Back to vanilla's third person distance. */
    public void restoreThirdPersonDistance() {
        setThirdPersonDistance(DEF_THIRD_CAMERA_DIST);
    }

    /** The aircraft the player rides as of this tick; leaving it resets distance, zoom and roll. */
    public void setRidingAircraft(@Nullable AircraftEntity aircraft) {
        if (aircraft == null && this.ridingAircraft != null) {
            this.distance = DEF_THIRD_CAMERA_DIST;
            this.zoom = 1.0F;
            this.roll = 0.0F;
        }
        this.ridingAircraft = aircraft;
    }

    /**
     * The stand-in for gunner, UAV and TV missile views, recreated for a new client level; for a server
     * level (or none) the last one, which may be null.
     */
    public ViewEntityDummy viewDummy(@Nullable Level level) {
        if (level == null || !level.isClientSide()) {
            return this.viewDummy;
        }
        if (this.viewDummy == null || this.viewDummy.isRemoved() || this.viewDummy.level() != level) {
            this.viewDummy = new ViewEntityDummy(level);
            this.viewDummy.setPos(0.0, -4.0, 0.0);
        }
        return this.viewDummy;
    }

    /** Moves the view stand-in without interpolation, if there is one. */
    public void placeViewDummy(double x, double y, double z) {
        if (this.viewDummy != null) {
            this.viewDummy.place(x, y, z);
        }
    }

    /** Points the view stand-in along the vehicle camera and takes over its zoom. */
    public void followCamera(@Nullable Level level, @Nullable AircraftCamera vehicleCamera) {
        ViewEntityDummy dummy = viewDummy(level);
        if (vehicleCamera != null) {
            dummy.follow(vehicleCamera);
            this.zoom = vehicleCamera.getCameraZoom();
        }
    }

    /** The smoothed third person anchor for the aircraft, created above it when there is none. */
    public ThirdPersonCamera thirdPerson(Level level, AircraftEntity aircraft) {
        if (this.thirdPerson != null && this.thirdPerson.level() != level) {
            clearThirdPerson();
        }
        if ((this.thirdPerson == null || this.thirdPerson.isRemoved()) && level.isClientSide()) {
            this.thirdPerson = ThirdPersonCamera.above(level, aircraft);
        }
        return this.thirdPerson;
    }

    /** Moves the third person anchor and takes over the vehicle camera's zoom. */
    public void followWithThirdPerson(ThirdPersonCamera anchor, AircraftEntity aircraft, Player player) {
        if (anchor.follow(aircraft, player)) {
            this.zoom = aircraft.cameras().camera().getCameraZoom();
        }
    }

    /** Drops the third person anchor. */
    public void clearThirdPerson() {
        if (this.thirdPerson != null) {
            this.thirdPerson.release();
            this.thirdPerson = null;
        }
    }
}
