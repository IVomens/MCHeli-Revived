package com.ivomens.mcheli.core.content.def.hud;

/**
 * The moving scales {@code DrawGraduation} draws. The names are the YAML spelling, kept from the first YAML
 * format; the order is the number the renderer uses.
 */
public enum GraduationType {
    /** A compass tape; TXT {@code DrawGraduationYaw}. */
    YAW,
    /** A pitch ladder with side ticks; TXT {@code DrawGraduationPitch1}. */
    PITCH,
    /** A pitch ladder with numbered bars; TXT {@code DrawGraduationPitch2}. */
    PITCH_ROLL,
    /** The same ladder with wider bars; TXT {@code DrawGraduationPitch3}. */
    PITCH_ROLL_ALT
}
