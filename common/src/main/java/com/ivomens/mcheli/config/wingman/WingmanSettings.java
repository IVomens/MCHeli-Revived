package com.ivomens.mcheli.config.wingman;

import com.ivomens.mcheli.config.BoolOption;
import com.ivomens.mcheli.config.ConfigValues;
import com.ivomens.mcheli.config.DoubleOption;
import com.ivomens.mcheli.config.IntOption;
import com.ivomens.mcheli.config.Option;
import com.ivomens.mcheli.config.RangePolicy;

/**
 * One immutable snapshot of {@code mcheli-wingman.cfg}. A value outside its range falls back to the
 * default when read from the file; values set by commands are taken as given.
 */
public record WingmanSettings(ConfigValues values, int uavControllerRange, int uavMaxDistance,
                              boolean forceChunkload, boolean tvMissileChunkLoad, int tvMissileMaxRange,
                              double formationSideDist, double formationAltOffset, double formationRearDist,
                              int maxWingmen, double minAttackAltitude, double maxAttackAltitude) {

    /** At or above this configured range (blocks) the UAV comm-range cut-off is treated as unlimited. */
    public static final int UAV_UNLIMITED_THRESHOLD = 30000;
    private static final double MIN_UAV_SEARCH_RANGE = 120.0;

    public static final IntOption UAV_CONTROLLER_RANGE = intOption("controllerRange", 99999, -1, Integer.MAX_VALUE,
            "UAV controller effective range in blocks. -1 for unlimited.");
    public static final IntOption UAV_MAX_DISTANCE = intOption("maxDistance", 99999, -1, Integer.MAX_VALUE,
            "UAV maximum flight distance in blocks. -1 for unlimited.");
    public static final BoolOption FORCE_CHUNKLOAD = BoolOption.of("forceChunkload", false).describe(
            "Keep a small area of chunks loaded around a UAV while it is being flown by a player",
            "(or is flying an autonomous mission) and is outside every player's loaded area, so it keeps",
            "simulating far from its operator. Manned aircraft never need this: their pilot loads them.",
            "Same effect as EnableUavChunkLoading in mcheli.cfg; the radius comes from UavChunkLoadRadius.",
            "Off by default: each active far UAV costs a forced chunk area.");
    public static final BoolOption TV_MISSILE_CHUNK_LOAD = BoolOption.of("chunkLoad", false).describe(
            "While a player is steering a TV/TA missile, keep the chunks along its path loaded with a",
            "short lease so the missile keeps flying (and can hit) beyond the simulation distance.");
    public static final IntOption TV_MISSILE_MAX_RANGE = intOption("maxRange", 500, -1, Integer.MAX_VALUE,
            "Maximum distance (blocks) a TV missile may travel from its operator before self-destructing.",
            "-1 = unlimited.");
    public static final DoubleOption FORMATION_SIDE_DIST = doubleOption("formationSideDist", 20.0, 1.0, 500.0,
            "Lateral distance from the leader centre-line (blocks).");
    public static final DoubleOption FORMATION_ALT_OFFSET = doubleOption("formationAltOffset", 0.0, -500.0, 500.0,
            "Altitude offset relative to leader Y (blocks). Positive = above, negative = below.");
    public static final DoubleOption FORMATION_REAR_DIST = doubleOption("formationRearDist", 30.0, 1.0, 500.0,
            "Distance behind the leader (blocks).");
    public static final IntOption MAX_WINGMEN = intOption("maxWingmen", 10, 1, 64, "Maximum wingmen per aircraft.");
    public static final DoubleOption MIN_ATTACK_ALTITUDE = doubleOption("minAttackAltitude", 0.0, 0.0, 10000.0,
            "Minimum absolute Y altitude for attack runs regardless of weapon type. 0 = no floor.");
    public static final DoubleOption MAX_ATTACK_ALTITUDE = doubleOption("maxAttackAltitude", 0.0, 0.0, 10000.0,
            "Maximum absolute Y altitude for attack runs regardless of weapon type. 0 = no ceiling.");

    public static WingmanSettings defaults() {
        return from(ConfigValues.DEFAULTS);
    }

    public static WingmanSettings from(ConfigValues v) {
        return new WingmanSettings(v, v.get(UAV_CONTROLLER_RANGE), v.get(UAV_MAX_DISTANCE), v.get(FORCE_CHUNKLOAD),
                v.get(TV_MISSILE_CHUNK_LOAD), v.get(TV_MISSILE_MAX_RANGE), v.get(FORMATION_SIDE_DIST),
                v.get(FORMATION_ALT_OFFSET), v.get(FORMATION_REAR_DIST), v.get(MAX_WINGMEN),
                v.get(MIN_ATTACK_ALTITUDE), v.get(MAX_ATTACK_ALTITUDE));
    }

    public <T> WingmanSettings with(Option<T> option, T value) {
        return from(this.values.with(option, value));
    }

    /** The same settings with another formation. */
    public WingmanSettings withFormation(double sideDist, double altOffset, double rearDist) {
        return with(FORMATION_SIDE_DIST, sideDist).with(FORMATION_ALT_OFFSET, altOffset)
                .with(FORMATION_REAR_DIST, rearDist);
    }

    /** The same settings with other attack altitude limits; 0 removes a limit. */
    public WingmanSettings withAttackAltitudes(double min, double max) {
        return with(MIN_ATTACK_ALTITUDE, min).with(MAX_ATTACK_ALTITUDE, max);
    }

    /** Search radius (blocks) for re-linking a UAV station to its last controlled aircraft after load. */
    public double uavSearchRange() {
        if (this.uavControllerRange < 0) {
            return UAV_UNLIMITED_THRESHOLD;
        }
        return Math.min(UAV_UNLIMITED_THRESHOLD, Math.max(MIN_UAV_SEARCH_RANGE, this.uavControllerRange));
    }

    private static IntOption intOption(String key, int defaultValue, int min, int max, String... comment) {
        return IntOption.of(key, defaultValue).range(min, max, RangePolicy.USE_DEFAULT).describe(comment);
    }

    private static DoubleOption doubleOption(String key, double defaultValue, double min, double max,
                                             String... comment) {
        return DoubleOption.of(key, defaultValue).range(min, max, RangePolicy.USE_DEFAULT).describe(comment);
    }
}
