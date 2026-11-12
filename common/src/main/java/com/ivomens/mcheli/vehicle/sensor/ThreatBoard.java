package com.ivomens.mcheli.vehicle.sensor;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.combat.guidance.ThreatSeeker;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.net.RwrSignal;
import com.ivomens.mcheli.combat.weapon.net.RwrThreat;
import com.ivomens.mcheli.combat.weapon.net.RwrThreatKind;
import com.ivomens.mcheli.combat.weapon.net.RwrThreatsMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.vehicle.world.AircraftIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Server side radar warning receiver (RWR) logic: which aircraft are painted by whose radar, locked
 * on, or chased by a missile, synced to the crews as threat tables.
 * <p>
 * Radar threats reach aircraft with an RWR. Missile threats also reach aircraft that carry flares or chaff,
 * whose dispensers warn of missiles ({@code MissileWarning}); each missile is told by its seeker
 * ({@link ThreatSeeker}), so the crew know which decoy fools it.
 * <p>
 * Loaded aircraft are indexed as they enter and leave the level, so radar emitters are only tested
 * against the (few) RWR-equipped receivers, and a level's entities are only walked for homing
 * missiles when it actually contains a receiver.
 */
public final class ThreatBoard {

    private static final long LOCK_REPORT_EXPIRE_TICK = 40L;
    private static final int MIN_SCAN_DISTANCE = 15;
    private static final int LOCK_EVENT_TTL_TICK = 8;
    private static final int SEARCH_EVENT_TTL_TICK = 40;
    private static final int MISSILE_EVENT_TTL_TICK = 8;
    private static final int MAX_RADAR_SCAN_TICK = 400;
    private static final long SCAN_COUNTER_STALE_TICK = 400L;
    private static final long THREAT_STALE_TICK = 200L;
    private static final double DEFAULT_RADAR_RANGE = 4096.0;
    private static final double MISSILE_SIGNAL_RANGE = 2048.0;
    /** At most this many threats (the strongest) are sent to a crew. */
    private static final int MAX_EVENTS_PER_TABLE = 12;

    private final Map<Integer, RadarTrackingReport> trackingReports = new HashMap<>();
    private final Map<Integer, RadarTrackingReport> gunnerTrackingReports = new HashMap<>();
    private final Map<Long, Integer> scanHitCounters = new HashMap<>();
    private final Map<Long, Long> scanPairLastSlot = new HashMap<>();
    private final Map<Long, Long> scanPairLastTouchedTick = new HashMap<>();
    private final Map<ThreatKey, ActiveThreatState> activeThreats = new HashMap<>();

    /** Threat tables are pushed to crews every this many ticks (event TTLs are longer). */
    private static final int SYNC_INTERVAL_TICK = 2;

    private long snapshotSeq = 0L;
    private long threatManagerTick = 0L;

    // per-tick scratch lists
    private final List<AircraftEntity> emitters = new ArrayList<>();
    private final List<AircraftEntity> receivers = new ArrayList<>();
    /** Whether the level being processed holds an aircraft warned of missiles (with an RWR or a dispenser). */
    private boolean anyWarned;
    private final List<ProjectileEntity> missiles = new ArrayList<>();
    /** Receivers that were last sent a non-empty table (so an empty one is sent once to clear the HUD). */
    private final Set<Integer> receiversWithThreats = new HashSet<>();

    /** Implemented on {@link MinecraftServer} by a mixin, so every server owns its own board. */
    public interface Holder {
        ThreatBoard mcheli$threatBoard();
    }

    private final MinecraftServer server;

    public ThreatBoard(MinecraftServer server) {
        this.server = server;
    }

    public static ThreatBoard of(MinecraftServer server) {
        return ((Holder) server).mcheli$threatBoard();
    }

    public void reportRadarTracking(ServerPlayer reporter, int emitterAircraftId, int trackingTargetId) {
        if (reporter == null || emitterAircraftId <= 0) {
            return;
        }
        Entity emitter = reporter.level().getEntity(emitterAircraftId);
        if (!(emitter instanceof AircraftEntity ac)) {
            return;
        }
        if (!canEmitRadarThreat(ac)) {
            trackingReports.remove(emitterAircraftId);
            return;
        }
        if (!isReporterControllingAircraft(ac, reporter)) {
            return;
        }
        long now = this.threatManagerTick;
        if (trackingTargetId <= 0) {
            trackingReports.remove(emitterAircraftId);
        } else {
            trackingReports.put(emitterAircraftId, new RadarTrackingReport(trackingTargetId, now + LOCK_REPORT_EXPIRE_TICK));
        }
    }

    public void reportGunnerTracking(AircraftEntity emitter, Entity tracker, Entity targetEntity) {
        if (emitter == null) {
            return;
        }
        int emitterAircraftId = emitter.getId();
        if (emitterAircraftId <= 0) {
            return;
        }
        if (!canEmitRadarThreat(emitter)) {
            gunnerTrackingReports.remove(emitterAircraftId);
            return;
        }
        if (tracker != null && emitter.seats().seatIdOf(tracker) < 0) {
            return;
        }
        long now = this.threatManagerTick;
        AircraftEntity target = resolveThreatReceiverAircraft(targetEntity);
        if (target == null || target == emitter || target.isRemoved() || isSameTeam(emitter, target)) {
            gunnerTrackingReports.remove(emitterAircraftId);
            return;
        }
        gunnerTrackingReports.put(emitterAircraftId, new RadarTrackingReport(target.getId(), now + LOCK_REPORT_EXPIRE_TICK));
    }

    public void serverTick() {
        this.threatManagerTick++;
        long now = this.threatManagerTick;
        this.snapshotSeq++;
        for (ServerLevel level : this.server.getAllLevels()) {
            this.collectAircraft(level);
            if (!this.anyWarned) {
                continue;
            }
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ProjectileEntity bullet && bullet.target() != null && !bullet.isRemoved()) {
                    this.missiles.add(bullet);
                }
            }
            for (AircraftEntity emitter : this.emitters) {
                this.processRadarEmission(emitter, now);
            }
            for (ProjectileEntity bullet : this.missiles) {
                this.processMissileEmission(bullet, now);
            }
        }
        this.emitters.clear();
        this.receivers.clear();
        this.missiles.clear();

        this.cleanup(now);
        if (now % SYNC_INTERVAL_TICK == 0) {
            this.syncThreatsToPlayers(this.server, now);
        }
    }

    private void collectAircraft(ServerLevel level) {
        this.emitters.clear();
        this.receivers.clear();
        this.missiles.clear();
        this.anyWarned = false;
        for (AircraftEntity aircraft : AircraftIndex.of(level).aircraft()) {
            if (aircraft.isRemoved()) {
                continue;
            }
            if (this.canEmitRadarThreat(aircraft)) {
                this.emitters.add(aircraft);
            }
            if (this.isTargetRwrReceivable(aircraft)) {
                this.receivers.add(aircraft);
            }
            this.anyWarned |= this.isWarnedOfMissiles(aircraft);
        }
    }

    private void processRadarEmission(AircraftEntity emitter, long now) {
        VehicleSpec info = emitter.spec();
        if (info == null) return;

        double maxRange = info.file().features().radar().maxTargetRange() > 0.0F
                ? info.file().features().radar().maxTargetRange() : DEFAULT_RADAR_RANGE;
        float scanAz = Mth.clamp(info.file().features().radar().scanAzimuth(), 0.0F, 360.0F);
        float scanEl = Mth.clamp(info.file().features().radar().scanElevation(), 0.0F, 180.0F);
        int radarScanTick = normalizeRadarScanTick(info.file().features().radar().scanTick());
        int hitNeed = getScanHitNeed(radarScanTick);
        long scanSlot = now / radarScanTick;
        String sourceName = getEmitterRwrName(emitter);

        RadarTrackingReport tracking = selectTrackingSource(emitter.getId(), now);

        for (AircraftEntity target : receivers) {
            if (target == emitter || target.isRemoved()) continue;

            if (tracking != null && tracking.targetEntityId == target.getId()) {
                // single target track: locked on
                touchThreat(target, emitter.getId(), getEmitterKind(emitter), RWRThreatEvent.MODE_STT, sourceName, now + LOCK_EVENT_TTL_TICK, maxRange);
            } else if (!isTargetCountermeasureActive(target)) {
                // search mode: a target must be swept a few times before it notices
                if (isTargetInsideScanCone(emitter, target, maxRange, scanAz, scanEl)) {
                    long pairKey = toPairKey(emitter.getId(), target.getId());
                    Long lastSlot = scanPairLastSlot.get(pairKey);
                    if (lastSlot == null || lastSlot != scanSlot) {
                        scanPairLastSlot.put(pairKey, scanSlot);
                        int hits = scanHitCounters.getOrDefault(pairKey, 0) + 1;
                        scanHitCounters.put(pairKey, hits);
                        scanPairLastTouchedTick.put(pairKey, now);
                        if (hits >= hitNeed) {
                            touchThreat(target, emitter.getId(), getEmitterKind(emitter), RWRThreatEvent.MODE_SEARCH, sourceName, now + SEARCH_EVENT_TTL_TICK, maxRange);
                        }
                    }
                } else {
                    decreaseScanHitCounter(emitter.getId(), target.getId());
                }
            }
        }
    }

    private void processMissileEmission(ProjectileEntity bullet, long now) {
        if (bullet.isRemoved()) return;
        Entity targetEntity = bullet.target();
        AircraftEntity target = resolveThreatReceiverAircraft(targetEntity);
        if (target == null || target.isRemoved() || !isWarnedOfMissiles(target)) return;

        // each missile is a threat of its own, so the crew can count them; every homing missile is reported
        // as actively guided, datalink guidance is not told apart yet
        RwrThreatKind kind = new RwrThreatKind(RWRThreatEvent.EMITTER_MISSILE, RWRThreatEvent.MODE_MSL_ACTIVE,
                ThreatSeeker.of(bullet.spec()));
        touchThreatFromPosition(target, bullet.getId(), kind, bullet.weaponName(), now + MISSILE_EVENT_TTL_TICK,
                bullet.getX(), bullet.getY(), bullet.getZ(), MISSILE_SIGNAL_RANGE, false);
    }

    private void touchThreat(AircraftEntity receiver, int emitterId, byte emitterKind, byte mode, String sourceName, long expireTick, double maxRange) {
        touchThreatFromPosition(receiver, emitterId, new RwrThreatKind(emitterKind, mode, ThreatSeeker.NONE), sourceName,
                expireTick, 0, 0, 0, maxRange, true);
    }

    /** Records or refreshes a threat; the direction comes from the emitter entity or the given position. */
    private void touchThreatFromPosition(AircraftEntity receiver, int emitterId, RwrThreatKind kind, String sourceName,
                                         long expireTick, double ex, double ey, double ez, double maxRange, boolean useEmitterEntity) {
        Entity emitter = useEmitterEntity ? receiver.level().getEntity(emitterId) : null;
        if (emitter != null) {
            ex = emitter.getX();
            ey = emitter.getY();
            ez = emitter.getZ();
        }
        double dx = ex - receiver.getX();
        double dy = ey - receiver.getY();
        double dz = ez - receiver.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        ThreatKey key = new ThreatKey(receiver.getId(), emitterId, kind.threatMode());
        ActiveThreatState state = this.activeThreats.computeIfAbsent(key, k -> new ActiveThreatState());
        state.key = key;
        state.emitterKind = kind.emitterKind();
        state.seeker = kind.seeker();
        state.sourceName = sourceName;
        state.bearingDeg = getRelativeBearing(receiver, dx, dz);
        state.strength = (float) (1.0 - Mth.clamp(distance / maxRange, 0.0, 1.0));
        state.expireTick = expireTick;
        state.lastTouchTick = this.threatManagerTick;
    }

    private void syncThreatsToPlayers(MinecraftServer server, long now) {
        Set<Integer> stillThreatened = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AircraftEntity ac = RiddenVehicle.of(player);
            if (ac == null || !isWarnedOfMissiles(ac)) continue;

            List<RWRThreatEvent> events = collectThreatEventsForReceiver(ac.getId(), now);
            if (events.isEmpty() && !receiversWithThreats.contains(ac.getId())) {
                // nothing new to tell this crew: the last table they got was already empty
                continue;
            }
            if (!events.isEmpty()) {
                stillThreatened.add(ac.getId());
            }
            Network.sendToPlayer(player, threatsMessage(ac.getId(), snapshotSeq, events));
        }
        receiversWithThreats.clear();
        receiversWithThreats.addAll(stillThreatened);
    }

    private List<RWRThreatEvent> collectThreatEventsForReceiver(int receiverId, long now) {
        List<RWRThreatEvent> list = new ArrayList<>();
        for (ActiveThreatState state : activeThreats.values()) {
            // a missile is homing this tick or gone: the crew count the missiles, so a lost one drops off at once
            boolean missileGone = state.seeker.isMissile() && state.lastTouchTick != now;
            if (state.key.receiverId == receiverId && state.expireTick >= now && !missileGone) {
                list.add(new RWRThreatEvent(state.key.emitterId, state.emitterKind, state.key.mode, state.seeker,
                        state.sourceName, state.bearingDeg, state.strength, 1.0F));
            }
        }
        list.sort((a, b) -> Float.compare(b.strength(), a.strength()));
        if (list.size() > MAX_EVENTS_PER_TABLE) {
            return new ArrayList<>(list.subList(0, MAX_EVENTS_PER_TABLE));
        }
        return list;
    }

    /** The lock reported by the pilot's radar takes precedence over one reported by an AI gunner. */
    private RadarTrackingReport selectTrackingSource(int emitterAircraftId, long now) {
        RadarTrackingReport report = this.trackingReports.get(emitterAircraftId);
        if (report != null && report.expireTick >= now && report.targetEntityId > 0) {
            return report;
        }
        report = this.gunnerTrackingReports.get(emitterAircraftId);
        if (report != null && report.expireTick >= now && report.targetEntityId > 0) {
            return report;
        }
        return null;
    }

    private AircraftEntity resolveThreatReceiverAircraft(Entity target) {
        if (target instanceof AircraftEntity aircraft) {
            return aircraft;
        }
        return target instanceof SeatEntity seat ? seat.getParent() : null;
    }

    private float getRelativeBearing(AircraftEntity receiver, double dx, double dz) {
        double absYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double yaw = receiver.getYRot();
        double rel = absYaw - yaw;
        while (rel > 180.0D) rel -= 360.0D;
        while (rel < -180.0D) rel += 360.0D;
        return (float) rel;
    }

    private byte getEmitterKind(AircraftEntity emitter) {
        return RWRThreatEvent.EMITTER_AIRCRAFT;
    }

    private boolean canEmitRadarThreat(AircraftEntity ac) {
        return ac != null && ac.spec() != null && ac.spec().file().features().radar().enabled()
                && ac.radar().isOn() && !ac.isDestroyed()
                && !ac.spec().file().features().radar().passive();
    }

    private boolean isReporterControllingAircraft(AircraftEntity ac, ServerPlayer reporter) {
        return RiddenVehicle.of(reporter) == ac;
    }

    private boolean isTargetRwrReceivable(AircraftEntity target) {
        return target != null && target.spec() != null && target.spec().file().features().rwr().enabled();
    }

    /** An RWR, or a flare or chaff dispenser, warns the crew of missiles homing on them. */
    private boolean isWarnedOfMissiles(AircraftEntity target) {
        return isTargetRwrReceivable(target)
                || target != null && target.spec() != null
                && (target.countermeasures().haveFlare() || target.countermeasures().haveChaff());
    }

    /** Chaff, an own ECM jammer or being jammed hides an aircraft from search radar; a lock is unaffected. */
    private boolean isTargetCountermeasureActive(AircraftEntity target) {
        if (target == null || target.spec() == null) {
            return false;
        }
        return target.countermeasures().isChaffUsing() || target.countermeasures().isECMJammerUsing() || target.countermeasures().jammingTicks() > 0;
    }

    private boolean isTargetInsideScanCone(AircraftEntity emitter, AircraftEntity target, double maxRange, float scanAzDeg, float scanElDeg) {
        double dx = target.getX() - emitter.getX();
        double dy = target.getY() - emitter.getY();
        double dz = target.getZ() - emitter.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < MIN_SCAN_DISTANCE * MIN_SCAN_DISTANCE || distSq > maxRange * maxRange) return false;

        double yaw = Math.toRadians(emitter.getYRot());
        double pitch = Math.toRadians(emitter.getXRot());
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);

        double horiz = Math.sqrt(dx * dx + dz * dz);
        double targetPitch = Math.toDegrees(Math.atan2(dy, horiz));
        double forwardPitch = Math.toDegrees(Math.atan2(fy, Math.sqrt(fx * fx + fz * fz)));
        double relElev = targetPitch - forwardPitch;

        double fhl = Math.sqrt(fx * fx + fz * fz);
        if (fhl <= 1.0E-6D) return false;
        double fhx = fx / fhl;
        double fhz = fz / fhl;
        double thx = dx / horiz;
        double thz = dz / horiz;
        double dot = Mth.clamp(fhx * thx + fhz * thz, -1.0, 1.0);
        double relAz = Math.toDegrees(Math.acos(dot));
        if (fhx * thz - fhz * thx < 0) relAz = -relAz;

        if (Math.abs(relAz) > scanAzDeg * 0.5D) return false;
        return Math.abs(relElev) <= scanElDeg * 0.5D;
    }

    private int getScanHitNeed(int radarScanTick) {
        if (radarScanTick >= 80) return 1;
        if (radarScanTick > 20) return 2;
        return 4;
    }

    private int normalizeRadarScanTick(int radarScanTick) {
        if (radarScanTick <= 0) return 1;
        return Math.min(radarScanTick, MAX_RADAR_SCAN_TICK);
    }

    private void decreaseScanHitCounter(int emitterId, int targetId) {
        long key = toPairKey(emitterId, targetId);
        scanHitCounters.computeIfPresent(key, (k, v) -> v > 0 ? v - 1 : 0);
    }

    private boolean isSameTeam(AircraftEntity emitter, AircraftEntity target) {
        Entity eOp = getPrimaryOperator(emitter);
        Entity tOp = getPrimaryOperator(target);
        if (eOp instanceof LivingEntity a && tOp instanceof LivingEntity b) {
            return a.isAlliedTo(b);
        }
        return false;
    }

    private Entity getPrimaryOperator(AircraftEntity ac) {
        if (ac.getRiddenByEntity() != null) return ac.getRiddenByEntity();
        for (int sid = 1; sid <= ac.seats().count(); sid++) {
            Entity crew = ac.seats().riderOf(sid);
            if (crew != null) return crew;
        }
        return null;
    }

    private String getEmitterRwrName(AircraftEntity emitter) {
        return emitter.spec() != null ? emitter.spec().file().features().rwr().name() : "?";
    }

    private void cleanup(long now) {
        trackingReports.entrySet().removeIf(e -> e.getValue().expireTick < now);
        gunnerTrackingReports.entrySet().removeIf(e -> e.getValue().expireTick < now);
        scanPairLastTouchedTick.entrySet().removeIf(e -> now - e.getValue() > SCAN_COUNTER_STALE_TICK);
        scanPairLastSlot.entrySet().removeIf(e -> !scanPairLastTouchedTick.containsKey(e.getKey()));
        scanHitCounters.entrySet().removeIf(e -> !scanPairLastTouchedTick.containsKey(e.getKey()));
        activeThreats.entrySet().removeIf(e -> e.getValue().expireTick < now || now - e.getValue().lastTouchTick > THREAT_STALE_TICK);
    }

    private long toPairKey(int a, int b) {
        return ((long) a << 32) ^ (b & 0xFFFFFFFFL);
    }

    private static class RadarTrackingReport {
        final int targetEntityId;
        final long expireTick;

        RadarTrackingReport(int targetEntityId, long expireTick) {
            this.targetEntityId = targetEntityId;
            this.expireTick = expireTick;
        }
    }

    private static class ThreatKey {
        final int receiverId;
        final int emitterId;
        final byte mode;

        ThreatKey(int receiverId, int emitterId, byte mode) {
            this.receiverId = receiverId;
            this.emitterId = emitterId;
            this.mode = mode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ThreatKey o)) return false;
            return this.receiverId == o.receiverId && this.emitterId == o.emitterId && this.mode == o.mode;
        }

        @Override
        public int hashCode() {
            return Objects.hash(receiverId, emitterId, mode);
        }
    }

    private static class ActiveThreatState {
        ThreatKey key;
        byte emitterKind;
        ThreatSeeker seeker;
        String sourceName;
        float bearingDeg;
        float strength;
        long expireTick;
        long lastTouchTick;
    }

    /** The table as it goes over the network; a crew is shown at most {@link RwrThreatsMessage#MAX_THREATS} threats. */
    private static RwrThreatsMessage threatsMessage(int receiverId, long snapshotSeq, List<RWRThreatEvent> events) {
        List<RwrThreat> threats = events.stream()
                .limit(RwrThreatsMessage.MAX_THREATS)
                .map(event -> new RwrThreat(event.emitterEntityId(),
                        new RwrThreatKind(event.emitterKind(), event.threatMode(), event.seeker()),
                        RwrThreat.shortName(event.sourceName()), event.angleDeg(),
                        new RwrSignal(event.strength(), event.confidence())))
                .toList();
        return new RwrThreatsMessage(receiverId, snapshotSeq, threats);
    }
}
