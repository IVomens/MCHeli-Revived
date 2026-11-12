package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.AircraftFeatures.Aps;
import com.ivomens.mcheli.content.def.vehicle.AircraftFeatures.Ecm;
import com.ivomens.mcheli.content.def.vehicle.AircraftFeatures.Flare;
import com.ivomens.mcheli.content.def.vehicle.AircraftFeatures.Parachuting;
import com.ivomens.mcheli.content.def.vehicle.AircraftFeatures.Radar;
import com.ivomens.mcheli.content.def.vehicle.AircraftFeatures.Rwr;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.KeyAlias;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.content.schema.Vectors;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Function;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.FLOAT;
import static com.ivomens.mcheli.core.content.schema.Codecs.INT;
import static com.ivomens.mcheli.core.content.schema.Codecs.TEXT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;
import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * YAML {@code AircraftFeatures}: equipment shared by every kind. The {@code Radar}, {@code RWR}, {@code ECM},
 * {@code APS} and {@code Parachuting} sections switch their feature on by being there; each takes
 * {@code Enabled: false} to keep its settings with the feature off.
 * <pre>
 * AircraftFeatures:
 *   NightVision: true
 *   Flare: { Pos: [0.0, 0.0, 0.0], Types: [SIDE], Chaff: true }
 *   Radar: { MaxTargetRange: 3000.0 }
 *   RWR: { Name: FIXRWR }
 * </pre>
 */
final class FeaturesSchema {

    private static final int MAX_TXT_TIME = 10_000;

    static final Field<AircraftFeatures, Boolean> GUNNER_MODE = flag("GunnerMode", AircraftFeatures::gunnerMode,
            "enablegunnermode");
    static final Field<AircraftFeatures, Integer> INVENTORY_SIZE = Field.of("InventorySize",
            ints(0, Short.MAX_VALUE), 0, AircraftFeatures::inventorySize).txt(ints(0, 54), "inventorysize");
    static final Field<AircraftFeatures, Boolean> NIGHT_VISION = flag("NightVision", AircraftFeatures::nightVision,
            "enablenightvision");
    static final Field<AircraftFeatures, Boolean> ENTITY_RADAR = flag("EntityRadar", AircraftFeatures::entityRadar,
            "enableentityradar");
    static final Field<AircraftFeatures, Boolean> CAN_REVERSE = flag("CanReverse", AircraftFeatures::canReverse,
            "enableback");
    static final Field<AircraftFeatures, Boolean> CONCURRENT_GUNNER = flag("ConcurrentGunner",
            AircraftFeatures::concurrentGunner, "concurrentgunnermode");
    static final Field<AircraftFeatures, Boolean> EJECTION_SEAT = flag("EjectionSeat",
            AircraftFeatures::ejectionSeat, "enableejectionseat");
    static final Field<AircraftFeatures, Float> THROTTLE_UP_DOWN = Field.of("ThrottleUpDown", floats(0, 3), 1.0F,
            AircraftFeatures::throttleUpDown).txt("throttleupdown");
    static final Field<AircraftFeatures, Float> THROTTLE_UP_DOWN_ON_ENTITY = Field.of("ThrottleUpDownEntity",
            floats(0, 100_000), 2.0F, AircraftFeatures::throttleUpDownOnEntity).txt("throttleupdownonentity");

    static final Field<Parachuting, Boolean> PARACHUTING =
            Field.of("Enabled", BOOLEAN, false, Parachuting::enabled).txt("enableparachuting");
    /** TXT: the first three values of {@code MobDropOption}. */
    static final Field<Parachuting, Vec3> DROP_POSITION =
            Field.of("Pos", Vectors.VEC3, Vec3.ZERO, Parachuting::position).alias("Position");
    /** TXT: the fourth value of {@code MobDropOption}, 12 when left out. */
    static final Field<Parachuting, Integer> DROP_INTERVAL = Field.of("Interval", INT, 1, Parachuting::interval);
    static final Section<AircraftFeatures, Parachuting> PARACHUTING_SECTION = Section.of("Parachuting",
                    AircraftFeatures::parachuting, PARACHUTING, DROP_POSITION, DROP_INTERVAL)
            .enabledBy(PARACHUTING).shorthand(PARACHUTING);

    /** TXT: {@code FlareOption}. */
    static final Field<Flare, Vec3> FLARE_POSITION = Field.of("Pos", Vectors.VEC3, Vec3.ZERO, Flare::position)
            .alias("Positions");
    /** The flare patterns; a {@code Flare} section without them fires none. */
    static final Field<Flare, List<Integer>> FLARE_TYPES = Field.of("Types", VehicleCodecs.FLARE_PATTERNS,
            List.of(), Flare::types).alias("Type").txt("flaretype");
    static final Field<Flare, Boolean> CHAFF = Field.of("Chaff", VehicleCodecs.CHAFF, false, Flare::chaff)
            .alias("HasChaff").txt("haschaff");
    static final Field<Flare, Integer> CHAFF_USE_TIME = Field.of("ChaffUseTime", INT, 100, Flare::chaffUseTime)
            .txt(ints(0, MAX_TXT_TIME), "chaffusetime");
    static final Field<Flare, Integer> CHAFF_WAIT_TIME = Field.of("ChaffWaitTime", INT, 400, Flare::chaffWaitTime)
            .alias("ChaffCooldown").txt(ints(0, MAX_TXT_TIME), "chaffwaittime");
    static final Section<AircraftFeatures, Flare> FLARE = Section.of("Flare", AircraftFeatures::flare,
                    FLARE_POSITION, FLARE_TYPES, CHAFF, CHAFF_USE_TIME, CHAFF_WAIT_TIME)
            .afterYaml(FeaturesSchema::flaresFireSomething);

    static final Field<AircraftFeatures, Integer> ENGINE_SHUTDOWN = Field.of("EngineShutdownThreshold", INT, 20,
                    AircraftFeatures::engineShutdownHp).alias("EngineShutdown")
            .txt(ints(0, 100), "engineshutdownthreshold");
    static final Section<AircraftFeatures, AircraftFeatures> MAINTENANCE = Section.of("Maintenance",
                    Function.identity(), ENGINE_SHUTDOWN)
            .unsupported("UseTime", "Cooldown").shorthandUnsupported();

    static final Field<Aps, Boolean> APS = Field.of("Enabled", VehicleCodecs.ON_WHEN_PRESENT, false, Aps::enabled)
            .txt("hasaps");
    static final Field<Aps, Integer> APS_USE_TIME = Field.of("UseTime", INT, 100, Aps::useTime)
            .txt(ints(0, MAX_TXT_TIME), "apsusetime");
    static final Field<Aps, Integer> APS_WAIT_TIME = Field.of("WaitTime", INT, 400, Aps::waitTime)
            .txt(ints(0, MAX_TXT_TIME), "apswaittime");
    static final Field<Aps, Integer> APS_RANGE = Field.of("Range", INT, 8, Aps::range).txt(ints(0, 100), "apsrange");
    static final Section<AircraftFeatures, Aps> APS_SECTION = Section.of("APS", AircraftFeatures::aps, APS,
            APS_USE_TIME, APS_WAIT_TIME, APS_RANGE).enabledBy(APS);

    static final Field<AircraftFeatures, Float> EXPLOSION_DAMAGE_FACTOR = Field.of("ArmorExplosionDamageMultiplier",
                    FLOAT, 1.0F, AircraftFeatures::explosionDamageFactor).alias("ArmorExplosionDamage")
            .txt("armorexplosiondamage", "armorexplosiondamagemultiplier");

    static final Field<Radar, Boolean> RADAR = Field.of("Enabled", BOOLEAN, false, Radar::enabled)
            .txt("enableradar");
    /** Also the top-level {@code RadarType}, which reads a name it does not know as {@code MODERN_AA}. */
    static final Field<Radar, RadarType> RADAR_TYPE = Field.of("Type", VehicleCodecs.radarType(null),
            RadarType.EARLY_AA, Radar::type).txt("radartype");
    static final Field<Radar, Float> RADAR_RANGE = Field.of("MaxTargetRange", FLOAT, 3000.0F, Radar::maxTargetRange)
            .txt(floats(50, 20_000), "radarmaxtargetrange");
    static final Field<Radar, Float> RADAR_AZIMUTH = Field.of("ScanAzimuth", FLOAT, 120.0F, Radar::scanAzimuth)
            .txt(floats(0, 360), "radarscanazimuthdeg");
    static final Field<Radar, Float> RADAR_ELEVATION = Field.of("ScanElevation", FLOAT, 40.0F,
            Radar::scanElevation).txt(floats(0, 180), "radarscanelevationdeg");
    static final Field<Radar, Integer> RADAR_SCAN_TICK = Field.of("ScanTick", INT, 12, Radar::scanTick)
            .txt(ints(1, 1200), "radarscantick");
    static final Field<Radar, Boolean> RADAR_BVR = Field.of("EnableBVR", BOOLEAN, false, Radar::bvr)
            .txt("enablebvr");
    static final Field<Radar, Boolean> RADAR_PASSIVE = Field.of("Passive", BOOLEAN, false, Radar::passive);
    static final Section<AircraftFeatures, Radar> RADAR_SECTION = Section.of("Radar", AircraftFeatures::radar,
                    RADAR, RADAR_TYPE, RADAR_RANGE, RADAR_AZIMUTH, RADAR_ELEVATION, RADAR_SCAN_TICK, RADAR_BVR,
                    RADAR_PASSIVE)
            .enabledBy(RADAR)
            .unsupported("PanelFillAlpha", "FollowTurretYaw", "DetectChanceBase", "GainNearFactor", "GainFarFactor",
                    "RcsFrontFactor", "RcsSideFactor", "RcsRearFactor", "RcsTimeFactor", "ContactHoldTick",
                    "ElevationReference", "ElevationCoverage", "MinScanAltitude", "MaxScanAltitude", "SearchType",
                    "TrackAzimuth", "TrackElevation", "RetargetCooldownTick");

    static final Field<Rwr, Boolean> RWR = Field.of("Enabled", BOOLEAN, false, Rwr::enabled).txt("enablerwr");
    /** Also the top-level {@code RWRType}, which reads a name it does not know as {@code DIGITAL}. */
    static final Field<Rwr, RWRType> RWR_TYPE = Field.of("Type", VehicleCodecs.rwrType(null), RWRType.DIGITAL,
            Rwr::type).txt("rwrtype");
    /** The name the vehicle shows on other vehicles' warning receivers. */
    static final Field<Rwr, String> RWR_NAME = Field.of("Name", TEXT, "?", Rwr::name).txt("nameonrwr");
    static final Section<AircraftFeatures, Rwr> RWR_SECTION = Section.of("RWR", AircraftFeatures::rwr, RWR,
            RWR_TYPE, RWR_NAME).enabledBy(RWR);

    static final Field<Ecm, Boolean> ECM = Field.of("Enabled", VehicleCodecs.ON_WHEN_PRESENT, false, Ecm::enabled)
            .txt("hasecmjammer");
    static final Field<Ecm, Integer> ECM_WAIT_TIME = Field.of("WaitTime", INT, 400, Ecm::waitTime)
            .txt(ints(0, MAX_TXT_TIME), "ecmjammerwaittime");
    static final Field<Ecm, Integer> ECM_USE_TIME = Field.of("UseTime", INT, 100, Ecm::useTime)
            .txt(ints(0, MAX_TXT_TIME), "ecmjammerusetime");
    static final Field<Ecm, Integer> ECM_TYPE = Field.of("Type", INT, 0, Ecm::type).txt("ecmjammertype");
    static final Section<AircraftFeatures, Ecm> ECM_SECTION = Section.of("ECM", AircraftFeatures::ecm, ECM,
            ECM_WAIT_TIME, ECM_USE_TIME, ECM_TYPE).enabledBy(ECM).unsupported("PhotoelectricJammer", "DIRCM");

    static final Section<VehicleFile, AircraftFeatures> SECTION = Section.of("AircraftFeatures",
                    VehicleFile::features, GUNNER_MODE, INVENTORY_SIZE, NIGHT_VISION, ENTITY_RADAR, CAN_REVERSE,
                    new KeyAlias<>("CanRotateOnGround", PhysicsSchema.CAN_ROT_ON_GROUND),
                    new KeyAlias<>("CanRotOnGround", PhysicsSchema.CAN_ROT_ON_GROUND), CONCURRENT_GUNNER,
                    EJECTION_SEAT, THROTTLE_UP_DOWN, THROTTLE_UP_DOWN_ON_ENTITY, PARACHUTING_SECTION, FLARE,
                    MAINTENANCE, APS_SECTION, EXPLOSION_DAMAGE_FACTOR, RADAR_SECTION, RWR_SECTION, ECM_SECTION)
            .unsupported("ImpactAngles");

    private FeaturesSchema() {
    }

    static AircraftFeatures build(Values v) {
        return new AircraftFeatures(v.get(GUNNER_MODE), v.get(INVENTORY_SIZE), v.get(NIGHT_VISION),
                v.get(ENTITY_RADAR), v.get(CAN_REVERSE), v.get(CONCURRENT_GUNNER), v.get(EJECTION_SEAT),
                v.get(THROTTLE_UP_DOWN), v.get(THROTTLE_UP_DOWN_ON_ENTITY),
                new Parachuting(v.get(PARACHUTING), v.get(DROP_POSITION), v.get(DROP_INTERVAL)),
                new Flare(v.get(FLARE_POSITION), v.get(FLARE_TYPES), v.get(CHAFF), v.get(CHAFF_USE_TIME),
                        v.get(CHAFF_WAIT_TIME)),
                v.get(ENGINE_SHUTDOWN),
                new Aps(v.get(APS), v.get(APS_USE_TIME), v.get(APS_WAIT_TIME), v.get(APS_RANGE)),
                v.get(EXPLOSION_DAMAGE_FACTOR),
                new Radar(v.get(RADAR), v.get(RADAR_TYPE), v.get(RADAR_RANGE), v.get(RADAR_AZIMUTH),
                        v.get(RADAR_ELEVATION), v.get(RADAR_SCAN_TICK), v.get(RADAR_BVR), v.get(RADAR_PASSIVE)),
                new Rwr(v.get(RWR), v.get(RWR_TYPE), v.get(RWR_NAME)),
                new AircraftFeatures.Ecm(v.get(ECM), v.get(ECM_WAIT_TIME), v.get(ECM_USE_TIME), v.get(ECM_TYPE)));
    }

    /** A {@code Flare} section that names no pattern fires the empty pattern, which still counts as flares. */
    private static void flaresFireSomething(Values values) {
        if (values.get(FLARE_TYPES).isEmpty()) {
            values.set(FLARE_TYPES, List.of(0));
        }
    }

    private static Field<AircraftFeatures, Boolean> flag(String key, Function<AircraftFeatures, Boolean> getter,
                                                        String txtKey) {
        return Field.of(key, BOOLEAN, false, getter).txt(txtKey);
    }
}
