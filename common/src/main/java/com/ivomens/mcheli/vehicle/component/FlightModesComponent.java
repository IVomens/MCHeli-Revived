package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftCamera;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.util.EntityIds;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * The pilot's and crew's modes: hover (hold altitude), gunner mode (fly hands-off and aim through
 * the camera), free look, and the gunner seats that can switch between camera and free view. Hover
 * and gunner mode remember the throttle from before and restore it when they end.
 */
public final class FlightModesComponent implements VehicleComponent {

    private final AircraftEntity vehicle;
    private final StatusWord status;
    private final SeatsComponent seats;
    private final PartsComponent parts;
    private final EngineComponent engine;
    private final WeaponComponent weapons;
    private final AircraftCamera camera;
    private FlightModeRules rules = FlightModeRules.SHARED;
    private Runnable onHoverSwitch = () -> {
    };
    private boolean hovering;
    private boolean gunnerMode;
    /** Gunner seats that may switch are in camera view (client only; the server always treats them so). */
    private boolean otherSeatsInGunnerView;
    private boolean gunnerFreeLook;
    private double throttleBeforeMode;

    public FlightModesComponent(AircraftEntity vehicle, StatusWord status, SeatsComponent seats, PartsComponent parts,
            EngineComponent engine, WeaponComponent weapons, AircraftCamera camera) {
        this.vehicle = vehicle;
        this.status = status;
        this.seats = seats;
        this.parts = parts;
        this.engine = engine;
        this.weapons = weapons;
        this.camera = camera;
    }

    /** Runs whenever hover mode is switched on or off, before it is known whether it may switch. */
    public void whenHoverSwitched(Runnable listener) {
        this.onHoverSwitch = listener;
    }

    public void setRules(FlightModeRules rules) {
        this.rules = rules;
    }

    public boolean isHoveringMode() {
        return this.hovering;
    }

    /** The pilot is in gunner mode. */
    public boolean isGunnerMode() {
        return this.gunnerMode;
    }

    /** Hover or gunner mode: the vehicle holds its altitude on its own. */
    public boolean isHovering() {
        return this.gunnerMode || this.hovering;
    }

    @Override
    public void describe(StateSink state) {
        state.put("hovering", this.hovering);
        state.put("gunner mode", this.gunnerMode);
        state.put("gunner free look", this.gunnerFreeLook);
        state.put("throttle before mode", this.throttleBeforeMode);
    }

    /** Gunner mode: the pilot flies hands-off on the remembered throttle and aims through the camera. */
    public void switchGunnerMode(boolean on) {
        if (on && !canSwitchGunnerMode() || this.gunnerMode == on) {
            return;
        }
        Entity pilot = this.seats.riderOf(0);
        rememberOrRestoreThrottle(on);
        this.gunnerMode = on;
        this.camera.setCameraZoom(1.0F);
        this.weapons.current(pilot).onSelected(this.weapons.hasInfiniteAmmo(pilot));
    }

    public boolean canSwitchGunnerMode() {
        VehicleSpec info = this.vehicle.spec();
        if (info == null || !info.file().features().gunnerMode() || !this.parts.isCanopyClosed()) {
            return false;
        }
        boolean copilotAboard = this.seats.riderOf(1) instanceof Player;
        return (info.file().features().concurrentGunner() || !copilotAboard) && !this.hovering
                && this.rules.allowsGunnerMode();
    }

    /** Hover mode holds altitude; any rope repelling stops. */
    public void switchHoveringMode(boolean on) {
        this.onHoverSwitch.run();
        if (!canSwitchHoveringMode() || this.hovering == on) {
            return;
        }
        rememberOrRestoreThrottle(on);
        this.hovering = on;
        Entity pilot = this.vehicle.getRiddenByEntity();
        if (pilot != null) {
            pilot.setXRot(0.0F);
            pilot.xRotO = 0.0F;
        }
    }

    public boolean canSwitchHoveringMode() {
        return this.vehicle.spec() != null && !this.gunnerMode && this.rules.allowsHovering();
    }

    /** A UAV that lost its link stops holding its hover. */
    public void stopHoveringForLostLink() {
        this.hovering = false;
    }

    private void rememberOrRestoreThrottle(boolean modeStarts) {
        if (modeStarts) {
            this.throttleBeforeMode = this.engine.throttle();
        } else {
            this.engine.setThrottle(this.throttleBeforeMode);
        }
    }

    /** Whether the entity looks through the gunner camera: the pilot in gunner mode, or a gunner seat. */
    public boolean usesGunnerView(@Nullable Entity entity) {
        VehicleSpec info = this.vehicle.spec();
        int seatId = this.seats.seatIdOf(entity);
        if (info == null || seatId < 0) {
            return false;
        }
        if (seatId == 0 && info.file().features().gunnerMode()) {
            return this.gunnerMode;
        }
        SeatInfo[] seatInfos = this.seats.infos();
        if (seatId >= seatInfos.length || !seatInfos[seatId].gunner) {
            return false;
        }
        boolean decidedOnThisClient = this.vehicle.level().isClientSide;
        return !decidedOnThisClient || !seatInfos[seatId].switchgunner || this.otherSeatsInGunnerView;
    }

    /** The entity looks through a camera: a gunner view, every seat of a UAV, or a camera-only vehicle. */
    public boolean usesCameraView(@Nullable Entity entity) {
        return this.rules.alwaysCameraView() || usesGunnerView(entity) || this.vehicle.isUAV();
    }

    public boolean canSwitchGunnerModeOtherSeat(Player player) {
        int seatId = this.seats.seatIdOf(player);
        SeatInfo seatInfo = seatId > 0 ? this.seats.info(seatId) : null;
        return seatInfo != null && seatInfo.gunner && seatInfo.switchgunner;
    }

    public void toggleGunnerModeOtherSeat() {
        this.otherSeatsInGunnerView = !this.otherSeatsInGunnerView;
    }

    /** A crew member took a seat that can switch: it starts in camera view. */
    public void enterGunnerViewOtherSeat() {
        this.otherSeatsInGunnerView = true;
    }

    public boolean canSwitchFreeLook() {
        return this.rules.allowsFreeLook();
    }

    public boolean isFreeLook() {
        // the crew exit owns the repelling bit; the pilot looks around freely while crew slide down
        return this.status.get(StatusWord.FREE_LOOK) || this.status.get(StatusWord.REPELLING);
    }

    /** Server: free look on or off. */
    public void setFreeLook(boolean freeLook) {
        this.status.set(StatusWord.FREE_LOOK, freeLook);
    }

    /** The pilot's client predicts its own free look switch. */
    public void predictFreeLook(boolean freeLook) {
        this.status.set(StatusWord.FREE_LOOK, freeLook, true);
    }

    /** The airframe turns the pilot's view with it, unless the pilot looks around freely. */
    public boolean viewFollowsYaw() {
        return !isFreeLook() && !(this.rules.hoverFreesYaw() && isHovering());
    }

    public boolean viewFollowsPitch() {
        return !isFreeLook() && !(this.rules.hoverFreesPitch() && isHovering());
    }

    public boolean canSwitchGunnerFreeLook(Player player) {
        SeatInfo seatInfo = this.seats.info(player);
        return seatInfo != null && seatInfo.fixRot && usesGunnerView(player);
    }

    /** The gunner's view follows the gun; the pilot's always does. */
    public boolean isGunnerLookMode(Player player) {
        return EntityIds.same(this.vehicle.getRiddenByEntity(), player) || !this.gunnerFreeLook;
    }

    public void setGunnerFreeLook(boolean freeLook) {
        this.gunnerFreeLook = freeLook;
    }

    public void toggleGunnerFreeLook() {
        this.gunnerFreeLook = !this.gunnerFreeLook;
    }
}
