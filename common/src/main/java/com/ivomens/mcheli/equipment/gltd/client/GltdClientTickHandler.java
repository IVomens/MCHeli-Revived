package com.ivomens.mcheli.equipment.gltd.client;

import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.equipment.gltd.GltdEntity;
import com.ivomens.mcheli.equipment.gltd.net.GltdCameraModeMessage;
import com.ivomens.mcheli.equipment.gltd.net.GltdDismountMessage;
import com.ivomens.mcheli.equipment.gltd.net.GltdFireMessage;
import com.ivomens.mcheli.client.input.ClientTickHandler;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.vehicle.client.VehicleControlContext;
import com.ivomens.mcheli.vehicle.client.camera.ViewEntityDummy;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Controls for the laser designator: aim with the mouse, zoom with the zoom and weapon-mode keys. */
public class GltdClientTickHandler extends ClientTickHandler {

    /** How far the operator can look away from the tripod's facing, in degrees. */
    private static final float MAX_LOOK_ANGLE = 70.0F;
    /** Zoom change per tick while a zoom key is held, as a fraction of the current zoom. */
    private static final float ZOOM_RATE = 0.1F;
    private static final int LEFT_MOUSE_BUTTON = -100;

    private final VehicleControlContext controls;
    private InputKey keyUseWeapon;
    private InputKey keySwitchWeapon1;
    private InputKey keySwitchWeapon2;
    private InputKey keySwWeaponMode;
    private InputKey keyZoom;
    private InputKey keyCameraMode;
    private InputKey keyUnmount;
    private InputKey[] trackedKeys;
    private boolean isRiding;

    public GltdClientTickHandler(Minecraft minecraft, KeySettings bindings, VehicleControlContext controls) {
        super(minecraft);
        this.controls = controls;
        updateKeybind(bindings);
    }

    @Override
    public void updateKeybind(KeySettings bindings) {
        keyUseWeapon = new InputKey(LEFT_MOUSE_BUTTON);
        keySwitchWeapon1 = new InputKey(bindings.switchWeapon1());
        keySwitchWeapon2 = new InputKey(bindings.switchWeapon2());
        keySwWeaponMode = new InputKey(bindings.switchWeaponMode());
        keyZoom = new InputKey(bindings.zoom());
        keyCameraMode = new InputKey(bindings.cameraMode());
        keyUnmount = new InputKey(bindings.unmount());
        trackedKeys = new InputKey[] {keyUseWeapon, keySwWeaponMode, keySwitchWeapon1, keySwitchWeapon2, keyZoom,
                keyCameraMode, keyUnmount};
    }

    private static void limitViewAndAimCamera(Player player, GltdEntity gltd) {
        player.setXRot(Mth.clamp(player.getXRot(), -MAX_LOOK_ANGLE, MAX_LOOK_ANGLE));
        float yaw = gltd.getYRot();
        if (player.getYRot() < yaw - MAX_LOOK_ANGLE) {
            player.setYRot(yaw - MAX_LOOK_ANGLE);
        }
        if (player.getYRot() > yaw + MAX_LOOK_ANGLE) {
            player.setYRot(yaw + MAX_LOOK_ANGLE);
        }

        gltd.camera.setRotationYaw(player.getYRot());
        gltd.camera.setRotationPitch(player.getXRot());
    }

    @Override
    public void onTick(boolean inGUI) {
        for (InputKey key : trackedKeys) {
            key.update();
        }

        boolean wasRiding = isRiding;
        Player player = mc.player;
        ViewEntityDummy view = null;
        isRiding = player != null && player.getVehicle() instanceof GltdEntity;
        if (isRiding) {
            GltdEntity gltd = (GltdEntity) player.getVehicle();
            limitViewAndAimCamera(player, gltd);
            this.controls.view().hideHeldItem(player.getMainHandItem());
            this.controls.camera().followCamera(mc.level, gltd.camera);
            view = this.controls.camera().viewDummy(mc.level);
            if (!inGUI) {
                playerControl(gltd);
            }
            this.controls.view().useIfConfigured(view);
        }

        if (wasRiding == isRiding) {
            return;
        }
        if (isRiding) {
            // no interpolation from wherever the camera was before
            view.xo = view.getX();
            view.yo = view.getY();
            view.zo = view.getZ();
        } else {
            this.controls.view().showHeldItem();
            this.controls.view().useIfConfigured(player);
        }
    }

    private void playerControl(GltdEntity gltd) {
        // Leaving the designator ends the tick: nothing else is sent with it.
        boolean dismount = keyUnmount.isKeyDown();
        boolean fired = fireLaser(gltd);
        updateZoom(gltd);
        int cameraMode = switchCameraMode(gltd);
        if (dismount) {
            Network.sendToServer(new GltdDismountMessage());
            return;
        }
        if (cameraMode >= 0) {
            Network.sendToServer(new GltdCameraModeMessage(cameraMode));
        }
        if (fired) {
            Network.sendToServer(new GltdFireMessage());
        }
    }

    private boolean fireLaser(GltdEntity gltd) {
        boolean switching = keySwitchWeapon1.isKeyDown() || keySwitchWeapon2.isKeyDown();
        if (switching || !keyUseWeapon.isKeyPress()) {
            return false;
        }
        if (gltd.useCurrentWeapon(0, 0)) {
            return true;
        }
        if (keyUseWeapon.isKeyDown()) {
            playSoundNG();
        }
        return false;
    }

    /** The zoom key zooms in and the weapon-mode key zooms out, smoothly while held. */
    private void updateZoom(GltdEntity gltd) {
        float prevZoom = gltd.camera.getCameraZoom();
        boolean zoomIn = keyZoom.isKeyPress();
        boolean zoomOut = keySwWeaponMode.isKeyPress();
        if (zoomIn && !zoomOut) {
            gltd.zoomCamera(ZOOM_RATE * prevZoom);
        } else if (zoomOut && !zoomIn) {
            gltd.zoomCamera(-ZOOM_RATE * prevZoom);
        }

        float newZoom = gltd.camera.getCameraZoom();
        if (prevZoom != newZoom) {
            playSound("zoom", 0.1F, prevZoom < newZoom ? 1.0F : 0.85F);
        }
    }

    /** @return the camera mode switched to, or -1 when it did not change */
    private int switchCameraMode(GltdEntity gltd) {
        if (!keyCameraMode.isKeyDown()) {
            return -1;
        }

        int before = gltd.camera.getMode(0);
        gltd.camera.setMode(0, before + 1);
        int after = gltd.camera.getMode(0);
        if (after == before) {
            return -1;
        }
        playSoundOK();
        return after;
    }
}
