package com.ivomens.mcheli.vehicle.component.countermeasure;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.api.event.SeekerType;
import com.ivomens.mcheli.event.ActivityEvents;
import com.ivomens.mcheli.vehicle.component.CountermeasureComponent;
import com.ivomens.mcheli.vehicle.component.SeatsComponent;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.combat.countermeasure.CountermeasureFlag;
import com.ivomens.mcheli.combat.guidance.TargetFilter;
import com.ivomens.mcheli.combat.guidance.ThreatSeeker;
import com.ivomens.mcheli.combat.projectile.HomingProjectiles;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.net.LockWarningMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.sound.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Missile warning and the soft kills that come with it: lock-on alerts for the crew, flares and chaff
 * destroying the missiles homing on the vehicle that they decoy, and an ECM jammer breaking radar locks.
 * <p>
 * Seekers of other mods report locks through {@link CountermeasureFlag#LOCKED_ON} and
 * {@link CountermeasureFlag#TRACKED}.
 */
public final class MissileWarning {

    public static final int SEARCH_RANGE = 60;
    private static final double ECM_RANGE = 80.0;
    /** Only this ECM jammer type breaks missile locks. */
    private static final int ECM_TYPE_LOCK_BREAKER = 2;
    /** The "is a missile homing on us" scan is cached for this many ticks. */
    private static final int LOCK_SCAN_INTERVAL = 5;
    private static final int LOCK_ON_ALERT_TICKS = 10;
    private static final int MISSILE_ALERT_TICKS = 20;
    /** The alert is played lower for a radar missile, so the crew hear whether to drop flares or chaff. */
    private static final float HEAT_ALERT_PITCH = 1.0F;
    private static final float RADAR_ALERT_PITCH = 0.75F;
    private static final int SCAN_HEAT = 1;
    private static final int SCAN_RADAR = 2;
    private static final int SCAN_ANY = 4;

    private final AircraftEntity aircraft;
    private final Level level;
    private final CountermeasureComponent countermeasures;
    private final SeatsComponent seats;
    private int alertCooldown;
    private int lastLockScanTick = -1000;
    /** The seekers of the missiles homing on the vehicle at the last scan: bit 1 heat, bit 2 radar, bit 4 any. */
    private int lastLockScan;
    private int alerts;
    /** Server: ticks until a UAV's next missile warning reaches the API's listeners. */
    private int uavWarningCooldown;

    public MissileWarning(AircraftEntity aircraft, Level level, CountermeasureComponent countermeasures,
            SeatsComponent seats) {
        this.aircraft = aircraft;
        this.level = level;
        this.countermeasures = countermeasures;
        this.seats = seats;
    }

    public void update() {
        AircraftEntity ac = this.aircraft;
        if (this.countermeasures.isECMJammerUsing() && ac.tickCount % 2 == 0) {
            this.breakRadarLocks();
        }
        if (!this.countermeasures.haveFlare() && !this.countermeasures.haveChaff()) {
            return;
        }
        if (this.alertCooldown > 0) {
            this.alertCooldown--;
        }

        boolean tracked = CountermeasureFlag.TRACKED.clear(ac);
        if (CountermeasureFlag.LOCKED_ON.clear(ac) && this.alertCooldown == 0) {
            this.alertCooldown = LOCK_ON_ALERT_TICKS;
            if (!ac.isDestroyed()) {
                notifyCrewOfLock();
            }
        }

        if (ac.isDestroyed()) {
            return;
        }
        Entity rider = ac.getRiddenByEntity();
        if (rider == null) {
            rider = this.seats.riderOf(1);
        }
        if (rider == null) {
            return;
        }
        boolean flares = this.countermeasures.isFlareUsing();
        boolean chaff = this.countermeasures.isChaffUsing();
        if (flares || chaff) {
            this.destroyDecoyedMissiles(flares, chaff);
        } else {
            this.alert(ac, rider, tracked);
        }
    }

    /** The missile alert, while missiles home on the vehicle or a seeker of another mod tracks it. */
    private void alert(AircraftEntity ac, Entity rider, boolean tracked) {
        if (!ac.isUAV() && !this.level.isClientSide) {
            if (this.alertCooldown == 0 && (tracked || this.isLockedByMissileCached())) {
                this.alertCooldown = MISSILE_ALERT_TICKS;
                ac.playSound(ModSounds.getSound("alert"), 1.0F, this.alertPitch());
                this.warnListeners();
            }
        } else if (ac.isUAV() && this.level.isClientSide && this.alertCooldown == 0
                && (tracked || this.isLockedByMissileCached())) {
            // the UAV operator is far away, so the alert is played on their client only
            this.alertCooldown = MISSILE_ALERT_TICKS;
            if (LocalClient.get().isLocalPlayer(rider)) {
                LocalClient.get().playUiSound(ModSounds.toLocation("alert"), 1.0F, this.alertPitch());
            }
        } else if (ac.isUAV() && !this.level.isClientSide && ActivityEvents.listensToMissileWarnings()) {
            // the server keeps its own pace for a UAV's alert, which only the operator's client plays
            if (this.uavWarningCooldown > 0) {
                this.uavWarningCooldown--;
            } else if (tracked || this.isLockedByMissileCached()) {
                this.uavWarningCooldown = MISSILE_ALERT_TICKS;
                this.warnListeners();
            }
        }
    }

    /** Tells the API's listeners the alert went off, with the missiles homing on the vehicle and their seekers. */
    private void warnListeners() {
        if (!ActivityEvents.listensToMissileWarnings()) {
            return;
        }
        List<ProjectileEntity> missiles = this.getMissilesAround(SEARCH_RANGE);
        boolean heat = false;
        boolean radar = false;
        for (ProjectileEntity missile : missiles) {
            ThreatSeeker seeker = ThreatSeeker.of(missile.spec());
            heat |= seeker.decoyedByFlares();
            radar |= seeker.decoyedByChaff();
        }
        ActivityEvents.missileWarned(this.aircraft, seekerType(heat, radar), missiles);
    }

    /** The seekers of the incoming missiles as the API names them: heat, radar, both, or neither. */
    static SeekerType seekerType(boolean heat, boolean radar) {
        if (heat) {
            return radar ? SeekerType.DUAL : SeekerType.HEAT;
        }
        return radar ? SeekerType.RADAR : SeekerType.OTHER;
    }

    /**
     * The pitch of the next alert: high for heat seekers, low for radar missiles, and both in turn when
     * both kinds are inbound.
     */
    private float alertPitch() {
        boolean heat = (this.lastLockScan & SCAN_HEAT) != 0;
        boolean radar = (this.lastLockScan & SCAN_RADAR) != 0;
        this.alerts++;
        if (radar && (!heat || (this.alerts & 1) == 0)) {
            return RADAR_ALERT_PITCH;
        }
        return HEAT_ALERT_PITCH;
    }

    private void notifyCrewOfLock() {
        for (int seatId = 0; seatId < 2; seatId++) {
            if (this.seats.riderOf(seatId) instanceof ServerPlayer player) {
                Network.sendToPlayer(player, new LockWarningMessage());
            }
        }
    }

    /** The missiles near the vehicle that home on it or its crew; only projectiles with a target can. */
    private List<ProjectileEntity> getMissilesAround(double range) {
        return HomingProjectiles.of(this.level).inArea(this.level,
                this.aircraft.getBoundingBox().inflate(range, range, range), this::isTargetingAircraft);
    }

    private boolean isTargetingAircraft(ProjectileEntity missile) {
        return missile.isAlive() && TargetFilter.isHomingOn(missile, this.aircraft);
    }

    /**
     * Flares destroy every heat seeker homing on the aircraft or its crew, chaff every radar missile (see
     * {@link ThreatSeeker}, which follows {@link TargetFilter#decoyedByFlares} and
     * {@link TargetFilter#decoyedByChaff}); flares destroy a missile without a weapon definition too.
     */
    private void destroyDecoyedMissiles(boolean flares, boolean chaff) {
        for (ProjectileEntity missile : this.getMissilesAround(SEARCH_RANGE)) {
            ThreatSeeker seeker = ThreatSeeker.of(missile.spec());
            if (flares && seeker.decoyedByFlares() || chaff && seeker.decoyedByChaff()) {
                missile.setTargetUnannounced(null);
                missile.discard();
            }
        }
    }

    /**
     * ECM soft-kill (jammer type 2, server only): radar-guided and anti-radiation missiles homing on
     * this aircraft lose their target and fly on ballistically.
     */
    private void breakRadarLocks() {
        VehicleSpec info = this.aircraft.spec();
        if (this.level.isClientSide || info == null || info.file().features().ecm().type() != ECM_TYPE_LOCK_BREAKER) {
            return;
        }
        for (ProjectileEntity missile : this.getMissilesAround(ECM_RANGE)) {
            if (missile.spec() != null
                    && (missile.spec().missile().radar().radarMissile()
                    || missile.spec().missile().arm().antiRadiationMissile())) {
                missile.setTarget(null);
            }
        }
    }

    public boolean isLockedByMissile() {
        return !this.getMissilesAround(SEARCH_RANGE).isEmpty();
    }

    private boolean isLockedByMissileCached() {
        int now = this.aircraft.tickCount;
        if (now - this.lastLockScanTick >= LOCK_SCAN_INTERVAL || now < this.lastLockScanTick) {
            this.lastLockScanTick = now;
            int scan = 0;
            for (ProjectileEntity missile : this.getMissilesAround(SEARCH_RANGE)) {
                ThreatSeeker seeker = ThreatSeeker.of(missile.spec());
                scan |= SCAN_ANY | (seeker.decoyedByFlares() ? SCAN_HEAT : 0) | (seeker.decoyedByChaff() ? SCAN_RADAR : 0);
            }
            this.lastLockScan = scan;
        }
        return this.lastLockScan != 0;
    }
}
