package com.ivomens.mcheli.setup.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.aircraft.client.SeatClientTickHandler;
import com.ivomens.mcheli.apiservice.client.ClientApiService;
import com.ivomens.mcheli.client.input.ClientTickHandler;
import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.combat.weapon.GPSPosition;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.equipment.client.ToolClientTickHandler;
import com.ivomens.mcheli.equipment.gltd.GltdEntity;
import com.ivomens.mcheli.equipment.gltd.client.GltdClientTickHandler;
import com.ivomens.mcheli.equipment.launcher.client.LauncherClientTickHandler;
import com.ivomens.mcheli.equipment.wrench.client.WrenchUse;
import com.ivomens.mcheli.helicopter.client.HelicopterClientTickHandler;
import com.ivomens.mcheli.hud.client.ConfiguredHudStyle;
import com.ivomens.mcheli.plane.client.PlaneClientTickHandler;
import com.ivomens.mcheli.ship.client.ShipClientTickHandler;
import com.ivomens.mcheli.tank.client.TankClientTickHandler;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import com.ivomens.mcheli.vehicle.client.VehicleClientTickHandler;
import com.ivomens.mcheli.vehicle.client.VehicleControlContext;
import com.ivomens.mcheli.vehicle.client.camera.DetachedViewRefresh;
import com.ivomens.mcheli.vehicle.client.camera.ThirdPersonCamera;
import com.ivomens.mcheli.vehicle.client.camera.VehicleCamera;
import com.ivomens.mcheli.vehicle.client.camera.ViewEntityDummy;
import com.ivomens.mcheli.vehicle.client.input.VehicleMouseControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * The client's tick and frame loop once the game is set up: drives the tick handlers of every vehicle
 * type and held item in a fixed order, the keyboard and mouse input, the camera entities and the
 * overlays. The client session creates it on the first tick after the config has loaded.
 */
final class ClientTicks {

    private final Minecraft mc;
    private final ClientSession session;
    private final ClientTickHandler[] handlers;
    private final GlobalKeys keys;
    private final Overlays overlays;
    private final VehicleMouseControl mouse;
    private final DetachedViewRefresh detachedViewRefresh = new DetachedViewRefresh();
    /** The aircraft the player rides as of the current frame. */
    @Nullable
    private AircraftEntity ridingAircraft;
    /** Camera entity swapped out while the GUI renders (see {@link #onRenderGuiStart}). */
    @Nullable
    private Entity swappedCamera;

    ClientTicks(Minecraft minecraft, KeySettings bindings, ClientSession session) {
        this.mc = minecraft;
        this.session = session;
        VehicleControlContext controls = session.vehicleControls();
        this.handlers = new ClientTickHandler[]{
                new HelicopterClientTickHandler(minecraft, bindings, controls),
                new PlaneClientTickHandler(minecraft, bindings, controls),
                new ShipClientTickHandler(minecraft, bindings, controls),
                new TankClientTickHandler(minecraft, bindings, controls),
                new GltdClientTickHandler(minecraft, bindings, controls),
                new VehicleClientTickHandler(minecraft, bindings, controls),
                new LauncherClientTickHandler(minecraft, bindings, session.launcher(), controls.camera()),
                new SeatClientTickHandler(minecraft, bindings, controls),
                new ToolClientTickHandler(minecraft, bindings, session.rangeFinder())};
        this.keys = new GlobalKeys(minecraft, this.handlers, controls.camera());
        this.mouse = new VehicleMouseControl(minecraft, controls.camera(),
                ridden -> ridden instanceof GltdEntity || ridden instanceof IUavStation, controls.pilot());
        this.overlays = new Overlays(minecraft, session.launcher(), session.rangeFinder(),
                new ConfiguredHudStyle(this.mouse.stick(), ClientApiService.widgets()), session.screenProjection(),
                session.teamMarkers(), this.keys::showsTeamList);

        this.keys.updateKeybind(bindings);
    }

    /** Rebinds every key from the settings, e.g. after the config screen saved them. */
    void updateKeybind(KeySettings bindings) {
        this.keys.updateKeybind(bindings);
    }

    /** Whether vanilla mouse look must be suppressed because MCHeli turns the player itself. */
    boolean isControllingPlayerTurn() {
        return this.mouse.isControllingMouse();
    }

    /** The aircraft the player rides as of the current frame. */
    @Nullable
    AircraftEntity ridingAircraft() {
        return this.ridingAircraft;
    }

    Overlays overlays() {
        return this.overlays;
    }

    void onTickStart() {
        this.keys.followConfig(McheliConfig.keys());
        if (this.mc.player == null || this.mc.level == null) {
            return;
        }
        this.session.vehicleControls().pitchLimit().clear();
        this.keys.onTick();
        LocalPlayer player = this.mc.player;
        boolean inGui = this.mc.screen != null;
        tickPilotControls(player, inGui);

        this.session.operatorReports().tick();
        for (ClientTickHandler handler : this.handlers) {
            handler.onTick(inGui);
        }
        this.overlays.onTick();
        followRiddenVehicle(player);
        updateThirdPersonCamera(player);
        if (player.getVehicle() == null) {
            GPSPosition.currentClientGPSPosition.setIsActive(false);
        }
        this.session.searchLights().refresh(this.mc.level);
    }

    /**
     * The joystick throttle and the altitude hold decide this tick's throttle keys before the vehicle
     * handlers read them; the player's own throttle keys take precedence.
     */
    private void tickPilotControls(Player player, boolean inGui) {
        AircraftEntity ridden = RiddenVehicle.of(player);
        AircraftEntity piloted = ridden != null && ridden.isPilot(player) ? ridden : null;
        KeySettings keySettings = McheliConfig.keys();
        boolean ownThrottle = !inGui && (InputKey.isKeyDown(keySettings.up()) || InputKey.isKeyDown(keySettings.down()));
        this.session.vehicleControls().pilot().tick(piloted, ownThrottle, McheliConfig.handling());
    }

    /** The lock warning tone and the camera follow the vehicle the player rides. */
    private void followRiddenVehicle(Player player) {
        AircraftEntity ac = RiddenVehicle.of(player);
        this.session.lockWarningTone().tick(ac != null && !ac.isDestroyed());
        this.session.vehicleControls().camera().setRidingAircraft(ac);
    }

    private void updateThirdPersonCamera(LocalPlayer player) {
        VehicleCamera camera = this.session.vehicleControls().camera();
        if (player.getVehicle() instanceof AircraftEntity aircraft
                && !this.mc.options.getCameraType().isFirstPerson() && !aircraft.isDestroyed()) {
            ThirdPersonCamera anchor = camera.thirdPerson(this.mc.level, aircraft);
            camera.followWithThirdPerson(anchor, aircraft, player);
            this.session.vehicleControls().view().switchTo(anchor);
            return;
        }

        if (this.mc.getCameraEntity() instanceof ThirdPersonCamera) {
            this.session.vehicleControls().view().switchTo(player);
        }
        camera.clearThirdPerson();
    }

    void onTickEnd() {
        if (this.mc.player != null && this.mc.level != null) {
            this.session.teamMarkers().onClientTick();
            clearMountedArmSwing(this.mc.player);
        }
        this.session.viewRecoil().onUpdate();
    }

    void onFrameStart(float partialTicks) {
        this.session.teamMarkers().startFrame();
        if (!McheliConfig.server().enableDebugBoundingBox()) {
            this.mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
        }

        Player player = this.mc.player;
        if (this.mc.isPaused() || player == null) return;

        this.ridingAircraft = RiddenVehicle.of(player);
        WrenchUse.holdStill(player);
        this.session.thermalVision().setCameraMode(cameraMode(player));
        this.mouse.onFrame(player, partialTicks);
    }

    /** The camera mode the player looks through: 0 normal, 1 night vision, 2 thermal. */
    private int cameraMode(Player player) {
        if (this.ridingAircraft != null) {
            return this.ridingAircraft.cameras().mode(player);
        }
        if (player.getVehicle() instanceof GltdEntity gltd) {
            return gltd.camera.getMode(0);
        }
        return 0;
    }

    void onFrameEnd() {
        if (this.mc.player != null) {
            clearMountedArmSwing(this.mc.player);
            this.session.vehicleControls().pitchLimit().apply(this.mc.player);
            if (this.ridingAircraft != null) {
                Entity view = this.session.vehicleControls().camera().viewDummy(this.mc.player.level());
                if (view != null) {
                    view.setXRot(this.mc.player.getXRot());
                    view.setYRot(this.mc.player.getYRot());
                    view.xRotO = this.mc.player.xRotO;
                    view.yRotO = this.mc.player.yRotO;
                }
            }
        }
        this.detachedViewRefresh.onRenderTickEnd();
    }

    void onPlayerTickStart(Player player) {
        WrenchUse.keepRepairing(player);
    }

    /**
     * While one of the camera stand-ins is the camera entity, the vanilla GUI would read the hotbar, health
     * and experience from it (and show an empty inventory). Hands the real player back for the GUI pass;
     * {@link #onRenderGuiEnd} restores the camera afterwards.
     */
    void onRenderGuiStart() {
        if (this.mc.player == null) return;
        Entity camera = this.mc.getCameraEntity();
        if (camera instanceof ViewEntityDummy || camera instanceof ThirdPersonCamera) {
            this.swappedCamera = camera;
            this.mc.cameraEntity = this.mc.player;
        }
    }

    void onRenderGuiEnd() {
        if (this.swappedCamera != null) {
            if (this.mc.cameraEntity == this.mc.player) this.mc.cameraEntity = this.swappedCamera;
            this.swappedCamera = null;
        }
    }

    /** The main MCHeli HUD layer. */
    void renderHud(GuiGraphics graphics, float partialTicks) {
        this.overlays.render(graphics, partialTicks);
    }

    /** The top MCHeli HUD layer. */
    void renderHudTop(GuiGraphics graphics, float partialTicks) {
        this.overlays.renderTop(graphics, partialTicks);
    }

    private static void clearMountedArmSwing(Player player) {
        if (RiddenVehicle.of(player) == null) {
            return;
        }
        player.attackAnim = 0.0F;
        player.oAttackAnim = 0.0F;
        player.swingTime = 0;
    }
}
