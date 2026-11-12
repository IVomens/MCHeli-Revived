package com.ivomens.mcheli.core.hud;

import java.util.Date;
import java.util.Locale;
import java.util.Optional;

/**
 * The values a {@code DrawString} format can show, by the name scripts give them, each with the Java type of
 * its value. {@code DrawString} formats with {@link String#format}, so the type is part of the format: an
 * integer takes {@code %d}, a float or a double {@code %f}, anything {@code %s}.
 */
public enum HudTextValue {

    /** What an unknown name shows: {@code null}, which every conversion prints as "null". */
    NONE(null),
    NAME(String.class),
    ALTITUDE(Integer.class),
    DATE(Date.class),
    MC_THOR(Integer.class),
    MC_TMIN(Integer.class),
    MC_TSEC(Integer.class),
    MAX_HP(Integer.class),
    HP(Integer.class),
    HP_PER(Double.class),
    POS_X(Double.class),
    POS_Y(Double.class),
    POS_Z(Double.class),
    MOTION_X(Double.class),
    MOTION_Y(Double.class),
    MOTION_Z(Double.class),
    INVENTORY(Integer.class),
    WPN_NAME(String.class),
    WPN_AMMO(String.class),
    WPN_RM_AMMO(String.class),
    RELOAD_PER(Float.class),
    RELOAD_SEC(Float.class),
    MORTAR_DIST(Float.class),
    MC_VER(String.class),
    MOD_VER(String.class),
    MOD_NAME(String.class),
    YAW(Double.class),
    PITCH(Float.class),
    ROLL(Float.class),
    PLYR_YAW(Double.class),
    PLYR_PITCH(Float.class),
    TVM_POS_X(Double.class),
    TVM_POS_Y(Double.class),
    TVM_POS_Z(Double.class),
    TVM_DIFF(Double.class),
    CAM_ZOOM(Float.class),
    UAV_DIST(Double.class),
    KEY_GUI(String.class),
    THROTTLE(Double.class),
    AIRBURST_DIST(String.class);

    private final Class<?> type;

    HudTextValue(Class<?> type) {
        this.type = type;
    }

    /** The value a script names, in any case. */
    public static Optional<HudTextValue> named(String name) {
        try {
            return Optional.of(valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Whether {@code value} has this value's type; {@link #NONE} only takes null. */
    public boolean accepts(Object value) {
        return this.type == null ? value == null : value == null || this.type.isInstance(value);
    }

    /** A value of this type, to check a format against when its HUD loads. */
    public Object sample() {
        if (this.type == null) return null;
        if (this.type == Integer.class) return 0;
        if (this.type == Float.class) return 0.0F;
        if (this.type == Double.class) return 0.0;
        if (this.type == Date.class) return new Date(0L);
        return "";
    }

    /** Whether the text is hidden while no weapon is selected. */
    public boolean needsWeapon() {
        return switch (this) {
            case WPN_NAME, WPN_AMMO, WPN_RM_AMMO, RELOAD_PER, RELOAD_SEC, MORTAR_DIST -> true;
            default -> false;
        };
    }

    /** Whether the text is hidden while the selected weapon has no magazine. */
    public boolean needsMagazine() {
        return this == WPN_AMMO || this == WPN_RM_AMMO;
    }
}
