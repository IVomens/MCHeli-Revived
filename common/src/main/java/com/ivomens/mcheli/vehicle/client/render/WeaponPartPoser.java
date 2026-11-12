package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.MountPose;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.WeaponPose;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/** Lays out the weapon parts: turrets, guns that follow the gunner, recoiling and spinning barrels, missiles. */
final class WeaponPartPoser {

    private WeaponPartPoser() {
    }

    static void pose(VehicleSpec info, List<WeaponPose> poses, PartDrawer drawer) {
        for (int i = 0; i < poses.size(); i++) {
            VehicleParts.PartWeapon part = info.parts().weapons().get(i);
            WeaponPose pose = poses.get(i);
            if (!pose.hidden() && (drawer.isVisible(part) || isAnyChildVisible(part, drawer))) {
                poseWeapon(info, part, pose, drawer);
            }
        }
    }

    private static boolean isAnyChildVisible(VehicleParts.PartWeapon part, PartDrawer drawer) {
        for (VehicleParts.PartWeaponChild child : part.child) {
            if (drawer.isVisible(child)) {
                return true;
            }
        }
        return false;
    }

    private static void poseWeapon(VehicleSpec info, VehicleParts.PartWeapon part, WeaponPose pose,
                                   PartDrawer drawer) {
        PartSink sink = drawer.sink();
        MountPose mount = pose.mount();
        sink.pushPose();
        if (part.turret) {
            Vec3 turret = info.file().turretPosition();
            sink.rotateAround(pose.turretYaw(), 0.0F, -1.0F, 0.0F, turret.x, turret.y, turret.z);
        }
        sink.translate(part.pos.x, part.pos.y, part.pos.z);
        if (part.yaw) {
            sink.rotate(pose.yaw(), 0.0F, -1.0F, 0.0F);
        }
        if (part.turret && mount != null) {
            sink.rotate(-mount.turretRelativeYaw(), 0.0F, -1.0F, 0.0F);
        }
        boolean invertPitch = undoDefaultYaw(mount, sink);
        if (part.pitch) {
            sink.rotate(invertPitch ? -pose.pitch() : pose.pitch(), 1.0F, 0.0F, 0.0F);
        }
        if (mount != null && part.recoilBuf != 0.0F) {
            sink.translate(0.0, 0.0, part.recoilBuf * mount.recoil());
        }
        if (mount != null) {
            sink.rotate(mount.defaultYaw(), 0.0F, -1.0F, 0.0F);
            if (part.rotBarrel) {
                sink.rotate(mount.barrel(), (float) part.rot.x, (float) part.rot.y, (float) part.rot.z);
            }
        }
        sink.translate(-part.pos.x, -part.pos.y, -part.pos.z);

        // a missile is drawn on its rail only while it is not fired (reloading)
        if (pose.loaded()) {
            drawer.drawIfVisible(part);
            for (int i = 0; i < part.child.size(); i++) {
                VehicleParts.PartWeaponChild child = part.child.get(i);
                if (drawer.isVisible(child)) {
                    sink.pushPose();
                    poseChild(child, pose, mount == null ? 0.0F : mount.childRecoil().get(i), drawer);
                    sink.popPose();
                }
            }
        }
        sink.popPose();
    }

    private static void poseChild(VehicleParts.PartWeaponChild child, WeaponPose pose, float recoil,
                                  PartDrawer drawer) {
        PartSink sink = drawer.sink();
        MountPose mount = pose.mount();
        sink.translate(child.pos.x, child.pos.y, child.pos.z);
        if (child.yaw) {
            sink.rotate(pose.yaw(), 0.0F, -1.0F, 0.0F);
        }
        boolean invertPitch = undoDefaultYaw(mount, sink);
        if (child.pitch) {
            sink.rotate(invertPitch ? -pose.pitch() : pose.pitch(), 1.0F, 0.0F, 0.0F);
        }
        if (mount != null && child.recoilBuf != 0.0F) {
            sink.translate(0.0, 0.0, -child.recoilBuf * recoil);
        }
        if (mount != null) {
            sink.rotate(mount.defaultYaw(), 0.0F, -1.0F, 0.0F);
        }
        sink.translate(-child.pos.x, -child.pos.y, -child.pos.z);
        drawer.draw(child);
    }

    /**
     * Turns back a weapon's default yaw so its pitch axis lines up with the airframe. Returns
     * whether the weapon points sideways (45 to 135 degrees either way), which flips its pitch.
     */
    private static boolean undoDefaultYaw(@Nullable MountPose mount, PartSink sink) {
        if (mount == null || (int) mount.defaultYaw() == 0) {
            return false;
        }
        float defaultYaw = Math.abs(Mth.wrapDegrees(mount.defaultYaw()));
        sink.rotate(-mount.defaultYaw(), 0.0F, -1.0F, 0.0F);
        return defaultYaw >= 45.0F && defaultYaw <= 135.0F;
    }
}
