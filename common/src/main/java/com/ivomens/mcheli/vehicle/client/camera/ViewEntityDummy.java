package com.ivomens.mcheli.vehicle.client.camera;

import com.ivomens.mcheli.aircraft.AircraftCamera;
import com.ivomens.mcheli.client.camera.CameraStandIn;
import net.minecraft.world.level.Level;

/**
 * Camera stand-in for gunner cameras, UAV views and TV missiles. Being a non-player entity, the
 * HUD layer temporarily hands the real player back to the GUI so the hotbar keeps showing the
 * player's inventory.
 */
public class ViewEntityDummy extends CameraStandIn {

    ViewEntityDummy(Level level) {
        super(level);
    }

    /** Moves the camera without interpolation. */
    void place(double x, double y, double z) {
        setPosRaw(x, y, z);
        this.xo = this.xOld = x;
        this.yo = this.yOld = y;
        this.zo = this.zOld = z;
    }

    /** Takes the vehicle camera's position and rotation, interpolating from the last ones. */
    void follow(AircraftCamera camera) {

        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        this.setYRot(camera.rotationYaw());
        this.setXRot(camera.rotationPitch());
        this.setYHeadRot(camera.rotationYaw());
        this.setPosRaw(camera.posX(), camera.posY(), camera.posZ());

        this.xOld = this.xo;
        this.yOld = this.yo;
        this.zOld = this.zo;
    }

    @Override
    public void tick() {
    }
}
