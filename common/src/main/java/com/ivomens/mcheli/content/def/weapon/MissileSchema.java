package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Arm;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Heat;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.LockOn;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Missile;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.MissileBallistic;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Radar;
import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.Values;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.DOUBLE;
import static com.ivomens.mcheli.core.content.schema.Codecs.FLOAT;
import static com.ivomens.mcheli.core.content.schema.Codecs.INT;
import static com.ivomens.mcheli.core.content.schema.Codecs.TEXT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;
import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * YAML {@code Missile}: guidance, with the {@code LockOn}, {@code Radar} and {@code Heat} seeker settings in
 * their own sections. These settings only matter for guided weapon types.
 */
final class MissileSchema {

    /** Legacy TXT writes {@code NULL} for a missile the RWR shows without a name. */
    private static final ValueCodec<String> TXT_RWR_NAME = ValueCodec.of(
            raw -> Codecs.text(raw).trim(),
            text -> text.trim().equals("NULL") ? "" : text.trim(),
            name -> name);

    /** TV missiles steer by laser. */
    static final Field<Missile, Boolean> LASER_GUIDANCE =
            Field.of("LaserGuidance", BOOLEAN, false, Missile::laserGuidance).txt("laserguidance");
    static final Field<Missile, Boolean> HAS_LASER_GUIDANCE_POD =
            Field.of("HasLaserGuidancePod", BOOLEAN, true, Missile::hasLaserGuidancePod).txt("haslaserguidancepod");
    /** Air-to-air missiles may be fired off boresight. */
    static final Field<Missile, Boolean> OFF_AXIS =
            Field.of("IsOffAxis", BOOLEAN, true, Missile::offAxis).txt("enableoffaxis");
    static final Field<Missile, Boolean> GUIDED_TORPEDO =
            Field.of("GuidedTorpedo", BOOLEAN, false, Missile::guidedTorpedo).txt("guidedtorpedo");
    /** Lead the target instead of chasing it. */
    static final Field<Missile, Boolean> PREDICT_TARGET_POS =
            Field.of("PredictTargetPos", BOOLEAN, true, Missile::predictTargetPos).txt("predicttargetpos");
    /** Beyond-visual-range targeting. */
    static final Field<Missile, Boolean> ENABLE_BVR = Field.of("EnableBVR", BOOLEAN, false, Missile::enableBvr)
            .txt("enablebvr");
    static final Field<Missile, Integer> MIN_RANGE_BVR =
            Field.of("MinRangeBVR", INT, 300, Missile::minRangeBvr).txt("minrangebvr");
    /** Ticks between active radar scans. */
    static final Field<Missile, Integer> SCAN_INTERVAL =
            Field.of("ScanInterval", INT, 20, Missile::scanInterval).txt("scaninterval");
    static final Field<Missile, String> NAME_ON_RWR =
            Field.of("NameOnRWR", TEXT, "MSL", Missile::nameOnRwr).txt(TXT_RWR_NAME, "nameonrwr");
    static final Field<Missile, Boolean> GPS_MISSILE =
            Field.of("IsGPSMissile", BOOLEAN, false, Missile::gpsMissile).txt("isgpsmissile");
    /** Ticks a lost lock is kept; -1 keeps it for good. */
    static final Field<Missile, Integer> TICK_END_HOMING =
            Field.of("TickEndHoming", ints(-1, 100_000), -1, Missile::tickEndHoming).txt("tickendhoming");
    /** Largest look-down angle of a pulse-Doppler seeker; the default is outside the range on purpose (no limit). */
    static final Field<Missile, Float> PDHDN_MAX_DEGREE =
            Field.of("PDHDNMaxDegree", floats(-1, 90), 1000.0F, Missile::pdhdnMaxDegree).txt("pdhdnmaxdegree");
    /** Ticks past that angle before the lock breaks. */
    static final Field<Missile, Integer> PDHDN_LOCK_OUT_COUNT =
            Field.of("PDHDNMaxDegreeLockOutCount", ints(0, 200), 10, Missile::pdhdnMaxDegreeLockOutCount)
                    .txt("pdhdnmaxdegreelockoutcount");
    /** Manoeuvrability: 1 is vanilla, 0.1 recommended. */
    static final Field<Missile, Double> TURNING_FACTOR = Field.of("TurningFactor", DOUBLE, 0.5,
            Missile::turningFactor).txt("turningfactor", "laserstartdistance");
    /** Largest angle the seeker tracks. */
    static final Field<Missile, Integer> MAX_DEGREE =
            Field.of("MaxDegreeOfMissile", ints(0, 100_000), 60, Missile::maxDegreeOfMissile)
                    .txt("maxdegreeofmissile");
    static final Field<Missile, Boolean> CAN_BE_INTERCEPTED =
            Field.of("CanBeIntercepted", BOOLEAN, false, Missile::canBeIntercepted).txt("canbeintercepted");

    static final Field<MissileBallistic, Integer> CANISTER =
            Field.of("Canister", INT, -1, MissileBallistic::canister).txt("canister");
    static final Field<MissileBallistic, Double> DRAG_IN_AIR =
            Field.of("DragInAir", DOUBLE, 0.0, MissileBallistic::dragInAir).txt("draginair");
    static final Section<Missile, MissileBallistic> BALLISTIC = Section.of("Ballistic", Missile::ballistic,
                    CANISTER, DRAG_IN_AIR)
            .unsupported("BallisticMissile", "ArcFactor", "ArcMinHeight", "ArcMaxHeight", "MinDistance",
                    "LateralSine", "LateralAmplitude", "LateralWaves", "LateralPhaseDeg", "LateralStartRatio",
                    "LateralEndRatio", "TerminalNoWeaveDist", "TerminalCylinderRadius", "CanisterType");

    /** Homes on radar emitters. */
    static final Field<Arm, Boolean> ANTI_RADIATION =
            Field.of("AntiRadiationMissile", BOOLEAN, false, Arm::antiRadiationMissile).txt("antiradiationmissile");
    static final Section<Missile, Arm> ARM = Section.of("ARM", Missile::arm, ANTI_RADIATION)
            .unsupported("EmitterLostGraceTick", "MemoryTimeTick", "CruiseEnable", "CruiseStartDistance",
                    "CruiseTerminalRadius", "CruiseTerminalHeight");

    static final Field<LockOn, Boolean> CAN_LOCK_MISSILE =
            Field.of("CanLockMissile", BOOLEAN, false, LockOn::canLockMissile).txt("canlockmissile");
    /** Ticks to lock. TXT clamps it to 2..1000. */
    static final Field<LockOn, Integer> LOCK_TIME =
            Field.of("Time", ints(0, 1000), 30, LockOn::time).txt(ints(2, 1000), "locktime");
    /** Ticks after launch before the seeker steers. */
    static final Field<LockOn, Integer> LOCK_DELAY =
            Field.of("Delay", ints(0, 1_000_000), 7, LockOn::delay).txt("rigiditytime");
    static final Field<LockOn, Integer> MAX_RANGE =
            Field.of("MaxRange", ints(0, 2000), 300, LockOn::maxRange).txt("maxlockonrange");
    /** Largest angle off the aircraft's nose a lock can start at. */
    static final Field<LockOn, Integer> MAX_ANGLE =
            Field.of("MaxAngle", ints(0, 200), 10, LockOn::maxAngle).txt("maxlockonangle");
    /** Below this height the target hides in ground clutter; -1 for no limit. */
    static final Field<LockOn, Integer> MIN_HEIGHT =
            Field.of("MinHeight", ints(-1, 100), 25, LockOn::minHeight).txt("lockminheight");
    /** Ticks a semi-active missile flies on after losing the launcher's guidance. */
    static final Field<LockOn, Integer> PASSIVE_RADAR_LOCK_OUT_COUNT =
            Field.of("PassiveRadarLockOutCount", ints(0, 200), 20, LockOn::passiveRadarLockOutCount)
                    .txt("passiveradarlockoutcount");
    /** Chaff locks before the missile goes ballistic. */
    static final Field<LockOn, Integer> LOCKED_CHAFF_MAX =
            Field.of("LockedChaffMax", INT, 2, LockOn::lockedChaffMax).txt("numlockedchaffmax");
    static final Field<LockOn, Integer> PROXIMITY_FUSE_TICK =
            Field.of("ProximityFuseTick", INT, -1, LockOn::proximityFuseTick).txt("proximityfusetick");
    static final Field<LockOn, Float> PROXIMITY_FUSE_DAMAGE =
            Field.of("ProximityFuseDamage", FLOAT, 0.0F, LockOn::proximityFuseDamage).txt("proximityfusedamage");
    static final Field<LockOn, Integer> PROXIMITY_FUSE_HEIGHT =
            Field.of("ProximityFuseHeight", INT, 20, LockOn::proximityFuseHeight).txt("proximityfuseheight");
    /** Targets sitting in water (ships, boats) may be locked. YAML only. */
    static final Field<LockOn, Boolean> LOCK_IN_WATER =
            Field.of("LockInWater", BOOLEAN, false, LockOn::lockInWater);
    static final Section<Missile, LockOn> LOCK_ON = Section.of("LockOn", Missile::lockOn, CAN_LOCK_MISSILE,
            LOCK_TIME, LOCK_DELAY, MAX_RANGE, MAX_ANGLE, MIN_HEIGHT, PASSIVE_RADAR_LOCK_OUT_COUNT, LOCKED_CHAFF_MAX,
            PROXIMITY_FUSE_TICK, PROXIMITY_FUSE_DAMAGE, PROXIMITY_FUSE_HEIGHT, LOCK_IN_WATER);

    /** Chaff fools it. */
    static final Field<Radar, Boolean> RADAR_MISSILE =
            Field.of("IsRadarMissile", BOOLEAN, false, Radar::radarMissile).txt("isradarmissile");
    /** Tracks on its own after launch. */
    static final Field<Radar, Boolean> ACTIVE_RADAR =
            Field.of("ActiveRadar", BOOLEAN, false, Radar::activeRadar).txt("activeradar");
    /** Semi-active: needs the launcher to keep the lock. */
    static final Field<Radar, Boolean> PASSIVE_RADAR =
            Field.of("PassiveRadar", BOOLEAN, false, Radar::passiveRadar).txt("passiveradar");
    static final Section<Missile, Radar> RADAR = Section.of("Radar", Missile::radar, RADAR_MISSILE, ACTIVE_RADAR,
            PASSIVE_RADAR).unsupported("SemiActiveRadar");

    /** Flares fool it. */
    static final Field<Heat, Boolean> HEAT_MISSILE =
            Field.of("isHeatMissile", BOOLEAN, true, Heat::heatMissile).txt("isheatseekermissile");
    static final Field<Heat, Integer> HEAT_COUNT =
            Field.of("HeatCount", ints(0, 100_000), 0, Heat::heatCount).txt("heatcount");
    static final Field<Heat, Integer> MAX_HEAT_COUNT =
            Field.of("MaxHeatCount", ints(0, 100_000), 0, Heat::maxHeatCount).txt("maxheatcount");
    /** Ticks the seeker ignores flares; -1 for never. */
    static final Field<Heat, Integer> ANTI_FLARE_COUNT =
            Field.of("AntiFlareCount", ints(-1, 200), -1, Heat::antiFlareCount).txt("antiflarecount");
    static final Section<Missile, Heat> HEAT = Section.of("Heat", Missile::heat, HEAT_MISSILE, HEAT_COUNT,
            MAX_HEAT_COUNT, ANTI_FLARE_COUNT);

    static final Section<WeaponDefinition, Missile> SECTION = Section.of("Missile", WeaponDefinition::missile,
                    LASER_GUIDANCE, HAS_LASER_GUIDANCE_POD, OFF_AXIS, GUIDED_TORPEDO, PREDICT_TARGET_POS, ENABLE_BVR,
                    MIN_RANGE_BVR, SCAN_INTERVAL, NAME_ON_RWR, GPS_MISSILE, TICK_END_HOMING, PDHDN_MAX_DEGREE,
                    PDHDN_LOCK_OUT_COUNT, TURNING_FACTOR, MAX_DEGREE, CAN_BE_INTERCEPTED, BALLISTIC, ARM, LOCK_ON,
                    RADAR, HEAT)
            .unsupported("MarkerRocket", "InitGuidance", "Signature", "EnableDataLink", "OnlyDataLink", "EnableHMS");

    private MissileSchema() {
    }

    static Missile build(Values v) {
        return new Missile(v.get(LASER_GUIDANCE), v.get(HAS_LASER_GUIDANCE_POD), v.get(OFF_AXIS),
                v.get(GUIDED_TORPEDO), v.get(PREDICT_TARGET_POS), v.get(ENABLE_BVR), v.get(MIN_RANGE_BVR),
                v.get(SCAN_INTERVAL), v.get(NAME_ON_RWR), v.get(GPS_MISSILE), v.get(TICK_END_HOMING),
                v.get(PDHDN_MAX_DEGREE), v.get(PDHDN_LOCK_OUT_COUNT), v.get(TURNING_FACTOR), v.get(MAX_DEGREE),
                v.get(CAN_BE_INTERCEPTED), new MissileBallistic(v.get(CANISTER), v.get(DRAG_IN_AIR)),
                new Arm(v.get(ANTI_RADIATION)), lockOn(v),
                new Radar(v.get(RADAR_MISSILE), v.get(ACTIVE_RADAR), v.get(PASSIVE_RADAR)),
                new Heat(v.get(HEAT_MISSILE), v.get(HEAT_COUNT), v.get(MAX_HEAT_COUNT), v.get(ANTI_FLARE_COUNT)));
    }

    private static LockOn lockOn(Values v) {
        return new LockOn(v.get(CAN_LOCK_MISSILE), v.get(LOCK_TIME), v.get(LOCK_DELAY), v.get(MAX_RANGE),
                v.get(MAX_ANGLE), v.get(MIN_HEIGHT), v.get(PASSIVE_RADAR_LOCK_OUT_COUNT), v.get(LOCKED_CHAFF_MAX),
                v.get(PROXIMITY_FUSE_TICK), v.get(PROXIMITY_FUSE_DAMAGE), v.get(PROXIMITY_FUSE_HEIGHT),
                v.get(LOCK_IN_WATER));
    }
}
