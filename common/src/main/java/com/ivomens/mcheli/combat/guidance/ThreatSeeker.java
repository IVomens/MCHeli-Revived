package com.ivomens.mcheli.combat.guidance;

import com.ivomens.mcheli.combat.weapon.WeaponSpec;

import javax.annotation.Nullable;

/**
 * What a threat homing on a vehicle seeks with, told by the decoys that fool it: the radar warning
 * receiver and the missile warning name it so the crew know whether flares or chaff will help.
 * <p>
 * A missile is classified by {@link TargetFilter#decoyedByFlares} and {@link TargetFilter#decoyedByChaff},
 * the rules the soft kill itself follows ({@code MissileWarning}), so the warning and the game never
 * disagree. Sent as its ordinal, so constants are only ever appended.
 */
public enum ThreatSeeker {
    /** Not a missile: a radar searching for or locked on the vehicle. */
    NONE(false, false),
    /** An infrared seeker, which flares decoy. */
    HEAT(true, false),
    /** A radar seeker, which chaff decoys. */
    RADAR(false, true),
    /** Infrared and radar together; flares and chaff each decoy it. */
    DUAL(true, true),
    /** A missile no decoy fools. */
    OTHER(false, false);

    private final boolean flares;
    private final boolean chaff;

    ThreatSeeker(boolean flares, boolean chaff) {
        this.flares = flares;
        this.chaff = chaff;
    }

    /**
     * The seeker of a missile of the weapon. A missile without a definition is a heat seeker, as flares
     * destroy it.
     */
    public static ThreatSeeker of(@Nullable WeaponSpec info) {
        if (info == null) {
            return HEAT;
        }
        return of(TargetFilter.decoyedByFlares(info), TargetFilter.decoyedByChaff(info));
    }

    /** The missile seeker that the decoys named fool. */
    public static ThreatSeeker of(boolean decoyedByFlares, boolean decoyedByChaff) {
        if (decoyedByFlares) {
            return decoyedByChaff ? DUAL : HEAT;
        }
        return decoyedByChaff ? RADAR : OTHER;
    }

    /** Whether flares decoy it. */
    public boolean decoyedByFlares() {
        return this.flares;
    }

    /** Whether chaff decoys it. */
    public boolean decoyedByChaff() {
        return this.chaff;
    }

    /** Whether it is a missile, of any seeker. */
    public boolean isMissile() {
        return this != NONE;
    }
}
