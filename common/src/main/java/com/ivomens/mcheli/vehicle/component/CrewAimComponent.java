package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.util.EntityIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Where the crew aims. The vehicle remembers where its pilot last looked (turrets and the seat camera
 * keep that aim when the pilot leaves). A hull-steered vehicle can decouple the pilot's weapon from
 * the view ("detached aim"), and a fire packet can carry the aim its shooter had on the client. Both
 * overrides apply only to the entity that produced them; other crew aim with their own view.
 */
public final class CrewAimComponent implements VehicleComponent {

    private final AircraftEntity vehicle;
    private final TowComponent tow;
    private boolean detachedTurretAllowed;
    /**
     * Client render state: set once a rider has aimed the turret, after which an empty vehicle keeps
     * the last aim instead of snapping back to the hull's heading.
     */
    private boolean aimed;
    private float riderYaw;
    private float prevRiderYaw;
    private float riderPitch;
    private float prevRiderPitch;

    private boolean detachedActive;
    private float detachedYaw;
    private float prevDetachedYaw;
    private float detachedPitch;
    private float prevDetachedPitch;

    private boolean packetAimActive;
    @Nullable
    private Entity packetAimUser;
    private float packetYaw;
    private float packetPitch;

    public CrewAimComponent(AircraftEntity vehicle, TowComponent tow) {
        this.vehicle = vehicle;
        this.tow = tow;
    }

    /**
     * Hull-steered vehicles (tanks and ground vehicles) never turn the chassis from the mouse, so the
     * mouse is free to slew a rate-limited turret instead. Helicopters, planes and ships fly with the
     * mouse, so handing it to a turret would leave the pilot unable to steer.
     */
    public void allowDetachedTurret() {
        this.detachedTurretAllowed = true;
    }

    /** Whether the weapon aim can come apart from the hull: a hull-steered vehicle with a turret to slew. */
    public boolean supportsDetachedTurret() {
        return this.detachedTurretAllowed && WeaponAiming.hasAnyIndependentMount(this.vehicle.spec());
    }

    public boolean hasBeenAimed() {
        return this.aimed;
    }

    public void markAimed() {
        this.aimed = true;
    }

    public float riderYaw() {
        return this.riderYaw;
    }

    public float prevRiderYaw() {
        return this.prevRiderYaw;
    }

    public float riderPitch() {
        return this.riderPitch;
    }

    public float prevRiderPitch() {
        return this.prevRiderPitch;
    }

    public void setRiderLook(float prevYaw, float yaw, float prevPitch, float pitch) {
        this.prevRiderYaw = prevYaw;
        this.riderYaw = yaw;
        this.prevRiderPitch = prevPitch;
        this.riderPitch = pitch;
    }

    public void setRiderYaw(float yaw) {
        this.riderYaw = yaw;
    }

    public void setRiderPitch(float pitch) {
        this.riderPitch = pitch;
    }

    /** Points the remembered look straight at the given attitude, for a new vehicle or type. */
    public void resetRiderLook(float yaw, float pitch) {
        setRiderLook(yaw, yaw, pitch, pitch);
    }

    /**
     * Per tick: remembers where the pilot is looking. Without a pilot a towed or carried vehicle looks
     * straight ahead; otherwise the last look is kept.
     */
    public void rememberRiderLook() {
        AircraftEntity ac = this.vehicle;
        Entity pilot = ac.getRiddenByEntity();
        if (pilot != null && !pilot.isRemoved() && !ac.isDestroyed()) {
            if (!(pilot instanceof Player) || !supportsDetachedTurret()) {
                this.detachedActive = false;
            }
            if (!this.detachedActive) {
                setRiderLook(pilot.yRotO, pilot.getYRot(), pilot.xRotO, pilot.getXRot());
            }
        } else if (this.tow.towedByChain() != null || ac.getVehicle() != null) {
            this.detachedActive = false;
            setRiderLook(ac.yRotO, ac.getYRot(), ac.xRotO, ac.getXRot());
        }
    }

    public boolean isDetachedActive() {
        return this.detachedActive;
    }

    public float detachedYaw() {
        return this.detachedYaw;
    }

    public float prevDetachedYaw() {
        return this.prevDetachedYaw;
    }

    public float detachedPitch() {
        return this.detachedPitch;
    }

    public float prevDetachedPitch() {
        return this.prevDetachedPitch;
    }

    /** Moves the turret aim on from where it is now. */
    public void setDetached(float yaw, float pitch) {
        setDetached(this.detachedYaw, yaw, this.detachedPitch, pitch);
    }

    /** The turret aim moves; the remembered rider look follows it. */
    public void setDetached(float prevYaw, float yaw, float prevPitch, float pitch) {
        this.detachedActive = true;
        this.prevDetachedYaw = prevYaw;
        this.detachedYaw = yaw;
        this.prevDetachedPitch = prevPitch;
        this.detachedPitch = pitch;
        setRiderLook(prevYaw, yaw, prevPitch, pitch);
    }

    public void clearDetached() {
        this.detachedActive = false;
    }

    public void setPacketAim(Entity user, float yaw, float pitch) {
        this.packetAimActive = true;
        this.packetAimUser = user;
        this.packetYaw = yaw;
        this.packetPitch = pitch;
    }

    public void clearPacketAim() {
        this.packetAimActive = false;
        this.packetAimUser = null;
    }

    private boolean usesPacketAim(@Nullable Entity entity) {
        return this.packetAimActive && EntityIds.same(entity, this.packetAimUser);
    }

    private boolean usesDetachedAim(@Nullable Entity entity) {
        return this.detachedActive && supportsDetachedTurret() && this.vehicle.isPilot(entity);
    }

    public float userYaw(@Nullable Entity entity) {
        if (usesPacketAim(entity)) {
            return this.packetYaw;
        }
        if (usesDetachedAim(entity)) {
            return this.detachedYaw;
        }
        return entity != null ? entity.getYRot() : this.riderYaw;
    }

    public float userYaw(@Nullable Entity entity, float partialTicks) {
        if (usesPacketAim(entity)) {
            return this.packetYaw;
        }
        if (usesDetachedAim(entity)) {
            return lerpYaw(this.prevDetachedYaw, this.detachedYaw, partialTicks);
        }
        if (entity != null) {
            return lerpYaw(entity.yRotO, entity.getYRot(), partialTicks);
        }
        return lerpYaw(this.prevRiderYaw, this.riderYaw, partialTicks);
    }

    /** Interpolates a yaw the short way round, wrapped to [-180, 180). */
    private static float lerpYaw(float previous, float current, float partialTicks) {
        if (current - previous > 180.0F) {
            previous += 360.0F;
        } else if (current - previous < -180.0F) {
            previous -= 360.0F;
        }
        return Mth.wrapDegrees(previous + (current - previous) * partialTicks);
    }

    public float userPitch(@Nullable Entity entity) {
        if (usesPacketAim(entity)) {
            return this.packetPitch;
        }
        if (usesDetachedAim(entity)) {
            return this.detachedPitch;
        }
        return entity != null ? entity.getXRot() : this.riderPitch;
    }

    public float userPitch(@Nullable Entity entity, float partialTicks) {
        if (usesPacketAim(entity)) {
            return this.packetPitch;
        }
        if (usesDetachedAim(entity)) {
            return this.prevDetachedPitch + (this.detachedPitch - this.prevDetachedPitch) * partialTicks;
        }
        if (entity != null) {
            return entity.xRotO + (entity.getXRot() - entity.xRotO) * partialTicks;
        }
        return this.prevRiderPitch + (this.riderPitch - this.prevRiderPitch) * partialTicks;
    }

    public void save(CompoundTag tag) {
        tag.putFloat(VehicleNbtCodec.LAST_RIDER_YAW, this.riderYaw);
        tag.putFloat(VehicleNbtCodec.LAST_RIDER_PITCH, this.riderPitch);
    }

    public void load(CompoundTag tag) {
        float yaw = tag.getFloat(VehicleNbtCodec.LAST_RIDER_YAW);
        float pitch = tag.getFloat(VehicleNbtCodec.LAST_RIDER_PITCH);
        setRiderLook(yaw, yaw, pitch, pitch);
    }
}
