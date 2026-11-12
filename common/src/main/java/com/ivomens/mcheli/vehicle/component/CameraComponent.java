package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftCamera;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.combat.weapon.MissileCamera;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.util.EntityIds;
import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import javax.annotation.Nullable;

/**
 * The crew's cameras: which camera position the pilot picked, zoom and night vision modes per seat,
 * the gimbal parts that follow the gunner, and the view from a TV-guided missile.
 */
public final class CameraComponent implements VehicleComponent, MissileCamera {

    private final AircraftEntity vehicle;
    private final AircraftCamera camera;
    private final SeatsComponent seats;
    private final FlightModesComponent modes;
    private int cameraId;
    private float thirdPersonDistance = 4.0F;
    /** Client: the pilot's free look relative to the cockpit; the eye looks along orientation * offset. */
    @Nullable
    private Quaternionf freeLookOffset;
    /** Ground vehicle sights step through every zoom level instead of doubling. */
    private boolean linearZoom;
    @Nullable
    private ProjectileEntity tvMissile;

    public CameraComponent(AircraftEntity vehicle, AircraftCamera camera, SeatsComponent seats,
            FlightModesComponent modes) {
        this.vehicle = vehicle;
        this.camera = camera;
        this.seats = seats;
        this.modes = modes;
    }

    public AircraftCamera camera() {
        return this.camera;
    }

    public float thirdPersonDistance() {
        return this.thirdPersonDistance;
    }

    public void setThirdPersonDistance(float distance) {
        this.thirdPersonDistance = distance;
    }

    @Nullable
    public Quaternionf freeLookOffset() {
        return this.freeLookOffset;
    }

    public void setFreeLookOffset(@Nullable Quaternionf offset) {
        this.freeLookOffset = offset;
    }

    /** The pilot looks through their own eyes, unless the definition always puts them behind the camera. */
    public boolean pilotUsesOwnEyes() {
        VehicleSpec info = this.vehicle.spec();
        return info == null || !info.alwaysCameraView();
    }

    public void useLinearZoom() {
        this.linearZoom = true;
    }

    public int cameraId() {
        return this.cameraId;
    }

    public void setCameraId(int cameraId) {
        this.cameraId = cameraId;
    }

    public int positionCount() {
        VehicleSpec info = this.vehicle.spec();
        return info != null ? info.crew().cameraPositions().size() : 1;
    }

    public boolean canSwitchPosition() {
        return positionCount() >= 2;
    }

    /** Camera position for the player on this client. */
    @Nullable
    public VehicleParts.CameraPosition positionForLocalPlayer() {
        return position(this.seats.seatIdOf(LocalClient.get().player()));
    }

    /** Camera position for a seat: the selected pilot camera, or the seat's own camera. */
    @Nullable
    public VehicleParts.CameraPosition position(int seatId) {
        VehicleSpec info = this.vehicle.spec();
        if (info == null) {
            return null;
        }
        if (seatId == 0 && positionCount() >= 2 && this.cameraId > 0
                && this.cameraId < info.crew().cameraPositions().size()) {
            return info.crew().cameraPositions().get(this.cameraId);
        }
        SeatInfo[] seatInfos = this.seats.infos();
        if (seatId > 0 && seatId < seatInfos.length && seatInfos[seatId].invCamPos) {
            return seatInfos[seatId].getCamPos();
        }
        return info.crew().cameraPositions().getFirst();
    }

    public int zoomMax() {
        VehicleSpec info = this.vehicle.spec();
        return info != null ? info.file().camera().zoom() : 1;
    }

    public boolean canZoom() {
        return zoomMax() > 1;
    }

    /** Doubles the zoom (or adds one level, see {@link #useLinearZoom}) up to the maximum, then wraps to 1x. */
    public void zoom() {
        if (!canZoom()) {
            return;
        }
        float maxZoom = zoomMax();
        float zoom = this.camera.getCameraZoom();
        if (this.linearZoom) {
            zoom += 1;
        } else {
            zoom = zoom >= maxZoom - 0.01 ? 1.0F : Math.min(zoom * 2.0F, maxZoom);
        }
        this.camera.setCameraZoom(zoom <= maxZoom + 0.01 ? zoom : 1.0F);
    }

    public boolean hasNightVision() {
        VehicleSpec info = this.vehicle.spec();
        return !this.vehicle.isDestroyed() && info != null && info.file().features().nightVision();
    }

    public boolean canSwitchMode(int seatId) {
        return !this.vehicle.isDestroyed() && hasNightVision() && this.camera.isValidUid(seatId);
    }

    /** The viewer's camera mode: normal, night vision or thermal. */
    public int mode(@Nullable Entity viewer) {
        return this.camera.getMode(this.seats.seatIdOf(viewer));
    }

    public void switchMode(Entity viewer) {
        switchMode(viewer, mode(viewer) + 1);
    }

    public void switchMode(Entity viewer, int mode) {
        this.camera.setMode(this.seats.seatIdOf(viewer), mode);
    }

    public void updateViewers() {
        for (int i = 0; i < this.seats.count() + 1; i++) {
            this.camera.updateViewer(i, this.seats.riderOf(i));
        }
    }

    @Override
    @Nullable
    public ProjectileEntity tvMissile() {
        return this.tvMissile != null && !this.tvMissile.isRemoved() ? this.tvMissile : null;
    }

    /** The missile is drawn unless the rider is guiding it from inside its own camera. */
    @Override
    public boolean showsMissile(Entity missile, Entity rider) {
        ProjectileEntity guided = tvMissile();
        return !this.modes.usesCameraView(rider) || !EntityIds.same(guided, missile)
                || guided != null && !EntityIds.same(guided.shooter(), rider);
    }

    /** The entity watches through the TV missile it is guiding. */
    public boolean usesMissileCamera(@Nullable Entity entity) {
        return tvMissile() != null && this.modes.usesCameraView(entity);
    }

    @Override
    public void watchTvMissile(@Nullable ProjectileEntity missile) {
        this.tvMissile = missile;
    }

    /** Client: moves the camera to the TV missile being guided, or to the selected camera position. */
    public void follow(double x, double y, double z) {
        AircraftEntity ac = this.vehicle;
        ProjectileEntity missile = tvMissile();
        if (missile != null) {
            this.camera.setPosition(missile.getX(), missile.getY(), missile.getZ());
            this.camera.setCameraZoom(1.0F);
            missile.setTrailHidden(usesMissileCamera(missile.shooter()));
            return;
        }
        this.tvMissile = null;
        VehicleParts.CameraPosition cameraPos = positionForLocalPlayer();
        Vec3 offset = Rotations.rotate(cameraPos != null ? cameraPos.pos() : Vec3.ZERO, -ac.getYRot(), -ac.getXRot(),
                -ac.roll());
        this.camera.setPosition(x + offset.x, y + offset.y, z + offset.z);
    }

    public void rotate(float yaw, float pitch) {
        this.camera.setPrevRotationYaw(this.camera.rotationYaw());
        this.camera.setPrevRotationPitch(this.camera.rotationPitch());
        this.camera.setRotationYaw(yaw);
        this.camera.setRotationPitch(pitch);
    }

    /** Client: camera parts (gimbals) follow the copilot's view, or the pilot's without a copilot. */
    public void turnGimbals() {
        Entity viewer = this.seats.riderOf(1);
        if (viewer == null) {
            viewer = this.vehicle.getRiddenByEntity();
        }
        if (viewer != null) {
            this.camera.setPrevPartRotationYaw(this.camera.partRotationYaw());
            this.camera.setPrevPartRotationPitch(this.camera.partRotationPitch());
            this.camera.setPartRotationYaw(viewer.getYRot());
            this.camera.setPartRotationPitch(viewer.getXRot());
        }
    }

    /** Points the gimbals straight ahead at the given attitude. */
    public void resetGimbals(float yaw, float pitch) {
        this.camera.setPartRotationYaw(yaw);
        this.camera.setPrevPartRotationYaw(yaw);
        this.camera.setPartRotationPitch(pitch);
        this.camera.setPrevPartRotationPitch(pitch);
    }
}
