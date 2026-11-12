package com.ivomens.mcheli.equipment.launcher.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.client.render.ScreenProjection;
import com.ivomens.mcheli.combat.guidance.LockOnSeeker;
import com.ivomens.mcheli.combat.guidance.TargetFilter;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The launcher the local player holds, as the client sees it: the weapon and its seeker, the mode and
 * zoom picked, the reload under way, and where the locked target appears on screen. The client shell
 * owns one; the launcher tick handler changes it and the sight reads it through {@link HandheldWeaponView}.
 */
public final class HandheldWeaponSession implements HandheldWeaponView {

    /** The sight of a launcher without a seeker locks nothing but flares, as it always has. */
    private static final TargetFilter UNGUIDED_SIGHT = new TargetFilter(false, false, false, 12, false, false, true, false);
    private static final double UNGUIDED_RANGE = 120.0;
    private static final int UNGUIDED_ANGLE = 10;
    private static final int UNGUIDED_LOCK_TICKS = 40;
    private static final float UNGUIDED_NOTCH_ANGLE = 1000.0F;

    @Nullable
    private WeaponMount weapon;
    @Nullable
    private LockOnSeeker seeker;
    private int mode;
    private int zoom;
    int reloadTicks;
    int lockToneCountdown;
    @Nullable
    private Entity markedEntity;
    private final ScreenProjection projection;
    private final double[] markerPos = new double[3];
    private final double[] markerCornerPos = new double[3];
    private boolean markerVisible;

    /** @param projection where the sight's marker goes on screen */
    public HandheldWeaponSession(ScreenProjection projection) {
        this.projection = projection;
    }

    @Override
    @Nullable
    public WeaponMount weapon() {
        return this.weapon;
    }

    @Override
    @Nullable
    public LockOnSeeker seeker() {
        return this.seeker;
    }

    @Override
    public int mode() {
        return this.mode;
    }

    @Override
    public int zoom() {
        return this.zoom;
    }

    /** Takes up the named launcher, or none if the name is unknown. */
    void hold(Player player, String weaponName) {
        this.reset();
        this.weapon = WeaponMount.create(player.level(), weaponName, Vec3.ZERO, 0.0F, 0.0F, false, null);
        if (this.weapon != null) {
            this.seeker = this.weapon.seeker() != null ? this.weapon.seeker()
                    : new LockOnSeeker(player.level(), UNGUIDED_SIGHT, UNGUIDED_RANGE, UNGUIDED_ANGLE,
                    UNGUIDED_LOCK_TICKS, UNGUIDED_NOTCH_ANGLE, null);
        }
    }

    /** Forgets the mode, zoom and reload, as when another launcher is taken up or the player changes. */
    void reset() {
        this.reloadTicks = 0;
        this.mode = 0;
        this.zoom = 0;
    }

    /** The server sets the lock range; the seeker follows it when it changes. */
    void useLockRange(double range) {
        if (this.seeker != null && this.seeker.range() != range) {
            this.seeker = this.seeker.withRange(range);
        }
    }

    void nextMode(int modes) {
        this.mode = (this.mode + 1) % modes;
    }

    /** Keeps the zoom index within the weapon's zoom levels; returns whether it changed. */
    boolean nextZoom(int levels) {
        int previous = this.zoom;
        this.zoom = (this.zoom + 1) % levels;
        return previous != this.zoom;
    }

    void clampZoom(int levels) {
        this.zoom = this.zoom % levels;
    }

    /** At the start of a tick: the marker goes with the lock. */
    void forgetLostMarker() {
        if (this.seeker == null || this.seeker.lockingTarget() == null) {
            this.markedEntity = null;
        }
    }

    /** At the end of a tick: the seeker drops its lock if the trigger was not held. */
    void releaseUnheldLock() {
        if (this.seeker != null) {
            this.seeker.update();
        }
    }

    /**
     * Called while entities render: records where the locked entity (and the corner of its marker box)
     * appears on screen. Positions are relative to the camera.
     */
    public void markEntity(Entity entity, double x, double y, double z) {
        if (this.seeker == null || this.seeker.lockingTarget() != entity) {
            return;
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        VehicleSpec info = entity instanceof AircraftEntity ac ? ac.spec() : null;
        float width = entity.getBbWidth() > entity.getBbHeight() ? entity.getBbWidth()
                : (info != null ? info.hitBoxes().markerWidth() : entity.getBbHeight());
        float height = info != null ? info.hitBoxes().markerHeight() : entity.getBbHeight();
        this.markerVisible = this.projection.project(cam.x + x, cam.y + y, cam.z + z, this.markerPos)
                && this.projection.project(cam.x + x + width, cam.y + y + height, cam.z + z + width,
                this.markerCornerPos);
        this.markedEntity = entity;
    }

    private boolean isMarkerShown() {
        return this.markerVisible && this.seeker != null && this.seeker.lockingTarget() == this.markedEntity
                && this.markedEntity != null;
    }

    @Override
    @Nullable
    public Vec3 markerPosition() {
        return this.isMarkerShown() ? new Vec3(this.markerPos[0], this.markerPos[1], this.markerPos[2]) : null;
    }

    @Override
    @Nullable
    public Vec3 markerCorner() {
        return this.isMarkerShown()
                ? new Vec3(this.markerCornerPos[0], this.markerCornerPos[1], this.markerCornerPos[2]) : null;
    }
}
