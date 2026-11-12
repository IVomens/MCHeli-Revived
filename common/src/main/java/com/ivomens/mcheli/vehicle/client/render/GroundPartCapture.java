package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.core.sim.math.LegacyMath;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.combat.weapon.RecoilBuffer;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.GroundParts;
import com.ivomens.mcheli.vehicle.component.CrewAimComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the aimed part tree of a ground vehicle (mounted guns, launchers) into {@link GroundParts}.
 * Recoiling and ammunition parts each take one mount of the first seat's weapon set, in tree order.
 */
final class GroundPartCapture {

    static final int PART_BARREL = 1;
    static final int PART_RECOILING = 2;
    static final int PART_AMMO = 3;

    private GroundPartCapture() {
    }

    /** The part tree's state, or null for vehicles that are not ground vehicles or carry no weapon. */
    @Nullable
    static GroundParts capture(AircraftEntity ac, float tickTime) {
        if (!(ac instanceof GroundVehicleEntity vehicle) || !(vehicle.groundVehicleSpec() instanceof VehicleSpec info)) {
            return null;
        }
        WeaponGroup weaponSet = vehicle.weapons().pilotWeapon();
        if (weaponSet == null) {
            return null;
        }
        Tree tree = new Tree(vehicle, weaponSet);
        for (VehicleParts.VPart part : info.kindParts().turretParts()) {
            tree.visit(part, tickTime);
        }
        Entity rider = vehicle.getRiddenByEntity();
        return new GroundParts(aimYaw(vehicle),
                LegacyMath.limit(aimPitch(vehicle), info.file().physics().rotationLimits().minPitch(),
                        info.file().physics().rotationLimits().maxPitch()),
                Mth.lerp(tickTime, weaponSet.barrels().prevAngle(), weaponSet.barrels().angle()),
                LocalClient.get().isLocalPlayer(rider) && LocalClient.get().isFirstPerson(), Angles.wrap(tree.recoil()),
                List.copyOf(tree.loaded));
    }

    /**
     * Where the parts aim: the detached aim, the gunner's look, or with nobody aboard the last aim (the
     * vehicle's heading until it was ever aimed). This is the value {@link VehicleFrameUpdates} keeps.
     */
    static float aimYaw(GroundVehicleEntity vehicle) {
        CrewAimComponent aim = vehicle.aim();
        Entity rider = vehicle.getRiddenByEntity();
        if (aim.isDetachedActive()) {
            return aim.detachedYaw();
        }
        if (rider != null && !vehicle.isDestroyed()) {
            return rider.getYRot();
        }
        return aim.hasBeenAimed() ? aim.riderYaw() : vehicle.getYRot();
    }

    static float aimPitch(GroundVehicleEntity vehicle) {
        CrewAimComponent aim = vehicle.aim();
        Entity rider = vehicle.getRiddenByEntity();
        if (aim.isDetachedActive()) {
            return aim.detachedPitch();
        }
        if (rider != null && !vehicle.isDestroyed()) {
            return rider.getXRot();
        }
        return aim.hasBeenAimed() ? aim.riderPitch() : vehicle.getXRot();
    }

    /** Walks the part tree in drawing order, as the renderer does, assigning mounts. */
    private static final class Tree {

        private final GroundVehicleEntity vehicle;
        private final WeaponGroup weaponSet;
        private final List<Float> recoil = new ArrayList<>();
        private final List<Boolean> loaded = new ArrayList<>();
        private int weaponIndex;

        Tree(GroundVehicleEntity vehicle, WeaponGroup weaponSet) {
            this.vehicle = vehicle;
            this.weaponSet = weaponSet;
        }

        /**
         * Children are interpolated with their parent's recoil as the tick time, which is what the
         * vehicle packs are tuned to.
         */
        void visit(VehicleParts.VPart part, float tickTime) {
            float partRecoil = 0.0F;
            if (this.weaponIndex < this.weaponSet.mountCount()) {
                RecoilBuffer state = this.weaponSet.recoil(this.weaponIndex);
                partRecoil = Mth.lerp(tickTime, state.previous(), state.value());
            }
            int ownIndex = this.weaponIndex;
            this.recoil.add(partRecoil);
            this.loaded.add(part.type != PART_AMMO || this.vehicle.weapons().isOnCooldown(this.weaponSet, ownIndex));
            if (part.type == PART_RECOILING || part.type == PART_AMMO) {
                this.weaponIndex++;
            }
            if (part.child != null) {
                for (VehicleParts.VPart child : part.child) {
                    this.visit(child, partRecoil);
                }
            }
        }

        float[] recoil() {
            float[] values = new float[this.recoil.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = this.recoil.get(i);
            }
            return values;
        }
    }
}
