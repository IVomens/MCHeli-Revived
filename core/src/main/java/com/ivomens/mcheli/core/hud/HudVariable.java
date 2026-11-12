package com.ivomens.mcheli.core.hud;

import com.ivomens.mcheli.core.hud.expr.ExprType;
import com.ivomens.mcheli.core.hud.expr.Variable;
import com.ivomens.mcheli.core.hud.expr.VariableTable;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The variables HUD formulas read, with the type every value of each one has. Formulas are bound to the
 * {@link HudFrame} slot of a variable when they compile, which is its ordinal. The order is the order the
 * test-mode overlay lists them in.
 * <p>
 * Integer variables stay integers in formulas ({@code hp/max_hp} is 0 until full health), which is why the
 * types are part of the format. Values that are floats in the game are doubles here, widened exactly.
 */
public enum HudVariable {

    /** The current colour as a signed ARGB integer, read as a double; {@code Color} commands change it. */
    COLOR(ExprType.DOUBLE),
    CENTER_X(ExprType.DOUBLE),
    CENTER_Y(ExprType.DOUBLE),
    WIDTH(ExprType.DOUBLE),
    HEIGHT(ExprType.DOUBLE),
    /** Ticks into the Minecraft day, 0 to 23999. */
    TIME(ExprType.INT),
    TEST_MODE(ExprType.BOOL, true),
    PLYR_YAW(ExprType.DOUBLE),
    PLYR_PITCH(ExprType.DOUBLE),
    BRL_YAW(ExprType.DOUBLE),
    BRL_PITCH(ExprType.DOUBLE),
    YAW(ExprType.DOUBLE),
    PITCH(ExprType.DOUBLE),
    ROLL(ExprType.DOUBLE),
    /** Blocks above the ground below the vehicle. */
    ALTITUDE(ExprType.INT),
    /** Blocks above sea level, never negative. */
    SEA_ALT(ExprType.DOUBLE),
    HAVE_RADAR(ExprType.BOOL, true),
    RADAR_ROT(ExprType.DOUBLE),
    HP(ExprType.INT),
    MAX_HP(ExprType.INT),
    HP_RTO(ExprType.DOUBLE),
    HP_PER(ExprType.DOUBLE),
    THROTTLE(ExprType.DOUBLE),
    POS_X(ExprType.DOUBLE),
    POS_Y(ExprType.DOUBLE),
    POS_Z(ExprType.DOUBLE),
    MOTION_X(ExprType.DOUBLE),
    MOTION_Y(ExprType.DOUBLE),
    MOTION_Z(ExprType.DOUBLE),
    SPEED(ExprType.DOUBLE),
    FUEL(ExprType.DOUBLE),
    /** 1 while the low fuel warning blinks on, else 0. */
    LOW_FUEL(ExprType.INT),
    STICK_X(ExprType.DOUBLE),
    STICK_Y(ExprType.DOUBLE),
    RELOADING(ExprType.BOOL, true),
    RELOAD_TIME(ExprType.DOUBLE),
    WPN_HEAT(ExprType.DOUBLE),
    IS_HEAT_WPN(ExprType.BOOL, true),
    /** 0 for no sight, 1 for a rocket sight, 2 for a lock-on sight. */
    SIGHT_TYPE(ExprType.INT),
    LOCK(ExprType.DOUBLE),
    DSP_MT_DIST(ExprType.BOOL, true),
    MT_DIST(ExprType.DOUBLE),
    HAS_AIRBURST(ExprType.INT),
    VTOL_STAT(ExprType.INT),
    FREE_LOOK(ExprType.INT),
    GUNNER_MODE(ExprType.BOOL, true),
    CAM_MODE(ExprType.INT),
    CAM_ZOOM(ExprType.DOUBLE),
    AUTO_PILOT(ExprType.INT),
    HAVE_FLARE(ExprType.BOOL, true),
    CAN_FLARE(ExprType.BOOL, true),
    INVENTORY(ExprType.INT),
    /** A boolean that was never an integer, so {@code hovering==1} is a comparison of two types: false. */
    HOVERING(ExprType.BOOL),
    IS_UAV(ExprType.BOOL, true),
    UAV_FS(ExprType.DOUBLE),
    UAV_DIST(ExprType.DOUBLE),
    TVM_DIFF(ExprType.DOUBLE),
    /** Missiles homing on the vehicle that flares decoy (heat seekers), 0 when its equipment cannot tell. */
    MSL_HEAT(ExprType.INT),
    /** Missiles homing on the vehicle that chaff decoys (radar missiles), 0 when its equipment cannot tell. */
    MSL_RADAR(ExprType.INT),
    /** Every missile homing on the vehicle that its RWR or countermeasure dispenser warns of. */
    MSL_COUNT(ExprType.INT);

    /** Every variable by its script name. */
    public static final VariableTable TABLE = table();

    private final Variable variable;

    HudVariable(ExprType type) {
        this(type, false);
    }

    /** @param legacyComparisons the variable was a 0/1 integer once, see {@link Variable#legacyComparisons} */
    HudVariable(ExprType type, boolean legacyComparisons) {
        this.variable = new Variable(name().toLowerCase(Locale.ROOT), type, ordinal(), legacyComparisons);
    }

    /** The name formulas use. */
    public String scriptName() {
        return this.variable.name();
    }

    public ExprType type() {
        return this.variable.type();
    }

    private static VariableTable table() {
        Map<String, Variable> byName = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(HudVariable::scriptName, variable -> variable.variable));
        return name -> Optional.ofNullable(byName.get(name));
    }

    /** The variable a script name names, if any. */
    public static Optional<HudVariable> named(String scriptName) {
        return TABLE.find(scriptName).map(variable -> values()[variable.slot()]);
    }
}
