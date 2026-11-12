package com.ivomens.mcheli.vehicle.client.input;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A turret that aims on its own (a tank's gun, a mounted weapon) while the player looks around freely:
 * every frame the turret slews toward the point under the crosshair at the weapon's rotation speed. The
 * slewed angles are written to the vehicle's aim, which sends them to the server.
 *
 * <p>The slew starts from where the turret points when a player takes over a seat, so taking over never
 * snaps the gun. One per client; only the render thread touches it.
 */
final class DetachedTurretAim {

    /**
     * Furthest point probed along the view for the convergence target. Beyond it the parallax of a
     * couple-block muzzle offset is well under a quarter degree.
     */
    private static final float CONVERGE_MAX_DISTANCE = 500.0F;
    /** Keeps a block right in front of the seat from whipping the turret around. */
    private static final float CONVERGE_MIN_DISTANCE = 4.0F;

    private boolean active;
    private int entityId = Integer.MIN_VALUE;
    private int seatId = Integer.MIN_VALUE;
    private float currentYaw;
    private float currentPitch;
    private float targetYaw;
    private float targetPitch;

    /** Whether the player's current weapon aims on its own rather than with the vehicle. */
    static boolean isUsed(AircraftEntity aircraft, Player player) {
        return aircraft.spec() != null && aircraft.weapons().aiming().hasIndependentAim(player);
    }

    /** Slews the turret toward the view at the weapon's rotation speed. */
    void slew(AircraftEntity aircraft, Player player, float deltaSeconds) {
        float rotationSpeed = Math.max(0.0F,
                aircraft.weapons().aiming().rotationSpeed(aircraft.weapons().current(player)));
        if (rotationSpeed <= 0.0F) {
            clear(aircraft);
            return;
        }

        takeOver(aircraft, player);
        float[] converged = convergedAim(aircraft, player);
        if (converged != null) {
            this.targetYaw = converged[0];
            this.targetPitch = converged[1];
        } else {
            this.targetYaw = player.getYRot();
            this.targetPitch = player.getXRot();
        }

        float maxStep = Math.max(0.0F, rotationSpeed * deltaSeconds);
        float yawStep = Mth.clamp(Mth.wrapDegrees(this.targetYaw - this.currentYaw), -maxStep, maxStep);
        float pitchStep = Mth.clamp(this.targetPitch - this.currentPitch, -maxStep, maxStep);

        float prevYaw = this.currentYaw;
        float prevPitch = this.currentPitch;
        this.currentYaw = Mth.wrapDegrees(this.currentYaw + yawStep);
        this.currentPitch = Mth.clamp(this.currentPitch + pitchStep, -90.0F, 90.0F);
        aircraft.aim().setDetached(prevYaw, this.currentYaw, prevPitch, this.currentPitch);
    }

    /** Hands the turret back to the vehicle and forgets the slew. */
    void clear(AircraftEntity aircraft) {
        aircraft.aim().clearDetached();
        reset();
    }

    /** Forgets the slew; the next one starts from where the turret then points. */
    void reset() {
        this.active = false;
        this.entityId = Integer.MIN_VALUE;
        this.seatId = Integer.MIN_VALUE;
        this.targetYaw = 0.0F;
        this.targetPitch = 0.0F;
    }

    private void takeOver(AircraftEntity aircraft, Player player) {
        int vehicleId = aircraft.getId();
        int seat = aircraft.seats().seatIdOf(player);
        if (!this.active || this.entityId != vehicleId || this.seatId != seat) {
            this.active = true;
            this.entityId = vehicleId;
            this.seatId = seat;
            // Start from where the turret points so taking over slews it instead of snapping.
            float turretYaw = Mth.wrapDegrees(aircraft.aim().riderYaw());
            float turretPitch = Mth.clamp(aircraft.aim().riderPitch(), -90.0F, 90.0F);
            this.currentYaw = turretYaw;
            this.currentPitch = turretPitch;
            this.targetYaw = player.getYRot();
            this.targetPitch = player.getXRot();
            aircraft.aim().setDetached(turretYaw, turretYaw, turretPitch, turretPitch);
        }
    }

    /**
     * The camera sits at the player's eye while the gun fires from the muzzle, so a barrel parallel to the
     * view would miss the crosshair by that offset. Returns the {yaw, pitch} that points the muzzle at the
     * point under the crosshair (first block hit, else a far point), or null when the weapon cannot be
     * resolved and the raw view angles should be used.
     */
    @Nullable
    private static float[] convergedAim(AircraftEntity aircraft, Player player) {
        WeaponGroup weaponSet = aircraft.weapons().current(player);
        if (weaponSet == null || weaponSet.firstMount() == null) {
            return null;
        }

        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = Rotations.direction(player.getYRot(), player.getXRot());

        double range = CONVERGE_MAX_DISTANCE;
        Vec3 farPoint = eye.add(look.x * CONVERGE_MAX_DISTANCE, look.y * CONVERGE_MAX_DISTANCE,
                look.z * CONVERGE_MAX_DISTANCE);
        BlockHitResult hit = player.level().clip(new ClipContext(eye, farPoint, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            range = Math.max(CONVERGE_MIN_DISTANCE, eye.distanceTo(hit.getLocation()));
        }
        Vec3 target = eye.add(look.x * range, look.y * range, look.z * range);

        // Same shot offset the firing code uses, so the corrected line matches where rounds spawn.
        Vec3 muzzleOffset = aircraft.weapons().shooter(null).muzzleOffset(weaponSet.firstMount());
        double dx = target.x - (aircraft.getX() + muzzleOffset.x);
        double dy = target.y - (aircraft.getY() + muzzleOffset.y);
        double dz = target.z - (aircraft.getZ() + muzzleOffset.z);

        // Inverse of Rotations.direction: x = -sin(yaw)cos(pitch), y = -sin(pitch), z = cos(yaw)cos(pitch).
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0E-4 && Math.abs(dy) < 1.0E-4) {
            return null;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, horiz));
        return new float[]{Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0F, 90.0F)};
    }
}
