package com.ivomens.mcheli.api.target;

import com.ivomens.mcheli.api.vehicle.VehicleKind;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * How an addon's entity shows up to MCHeli's seekers and radar right now: whether it is an air or a ground
 * target, what the radar draws it as, how strong its heat and radar signatures are, and whether it is
 * dropping flares or chaff. A {@link TargetProfile} returns one each time it is asked.
 *
 * <p>Start from {@link #AIR} or {@link #GROUND} and change what differs with the {@code with...} methods,
 * each of which returns a new signature. Signatures are immutable, so keep the ones you use often in
 * constants instead of building them on every call:
 *
 * <pre>{@code
 * static final TargetSignature JET = TargetSignature.AIR.withHeat(1.0F).withRadarCrossSection(0.8F);
 * static final TargetSignature JET_FLARES = JET.withFlares(true);
 * }</pre>
 *
 * <p><b>Signatures.</b> {@link #heat()} and {@link #radarCrossSection()} run from 0 (the seeker cannot see the
 * entity at all) to 1 (as easy to see as an ordinary MCHeli vehicle). A seeker that sees less than all of a
 * target treats it like a stealthy vehicle: the lock cone is narrower, the lock takes longer and holds out to
 * a shorter range. Heat seekers use the heat signature, radar seekers the radar cross-section, and seekers that
 * use both the stronger of the two. Other seekers (optical, laser) see the entity in full. The radar display
 * shows the entity while its radar cross-section is above 0.
 *
 * <p><b>Decoys.</b> While {@link #isFlareUsing()} is true, heat seekers lose the entity and do not lock it, and
 * heat-seeking missiles homing on it are decoyed, as by a vehicle's flares. {@link #isChaffUsing()} does the
 * same to radar seekers and radar missiles. The flags spawn no flare or chaff entity; drawing those is up to
 * the addon.
 *
 * <p>Immutable; any thread, either side. Since API 1.1. Methods may be added in a minor version.
 */
public final class TargetSignature {

    /** An air target, drawn on the radar as a plane, fully visible, without decoys. */
    public static final TargetSignature AIR = new TargetSignature(TargetDomain.AIR, VehicleKind.PLANE, 1.0F, 1.0F,
            false, false);
    /** A ground target, drawn on the radar as a tank, fully visible, without decoys. */
    public static final TargetSignature GROUND = new TargetSignature(TargetDomain.GROUND, VehicleKind.TANK, 1.0F,
            1.0F, false, false);

    private final TargetDomain domain;
    private final VehicleKind shownAs;
    private final float heat;
    private final float radarCrossSection;
    private final boolean flares;
    private final boolean chaff;

    private TargetSignature(TargetDomain domain, VehicleKind shownAs, float heat, float radarCrossSection,
                            boolean flares, boolean chaff) {
        this.domain = Objects.requireNonNull(domain, "domain");
        this.shownAs = Objects.requireNonNull(shownAs, "shownAs");
        this.heat = clamp(heat);
        this.radarCrossSection = clamp(radarCrossSection);
        this.flares = flares;
        this.chaff = chaff;
    }

    /** Whether seekers count the entity as an air or a ground target. */
    public TargetDomain domain() {
        return this.domain;
    }

    /** The vehicle kind the radar display draws the entity as. */
    public VehicleKind shownAs() {
        return this.shownAs;
    }

    /** The heat signature, 0 (invisible to heat seekers) to 1 (fully visible). */
    public float heat() {
        return this.heat;
    }

    /** The radar cross-section, 0 (invisible to radar) to 1 (fully visible). */
    public float radarCrossSection() {
        return this.radarCrossSection;
    }

    /** Whether the entity is dropping flares right now. */
    public boolean isFlareUsing() {
        return this.flares;
    }

    /** Whether the entity is dropping chaff right now. */
    public boolean isChaffUsing() {
        return this.chaff;
    }

    /** This signature as an air target, a ground target, or decided by height. */
    public TargetSignature withDomain(TargetDomain newDomain) {
        return new TargetSignature(newDomain, this.shownAs, this.heat, this.radarCrossSection, this.flares,
                this.chaff);
    }

    /** This signature drawn on the radar as the given vehicle kind. */
    public TargetSignature withShownAs(VehicleKind kind) {
        return new TargetSignature(this.domain, kind, this.heat, this.radarCrossSection, this.flares, this.chaff);
    }

    /** This signature with the given heat signature, clamped to 0..1 (NaN counts as 0). */
    public TargetSignature withHeat(float newHeat) {
        return new TargetSignature(this.domain, this.shownAs, newHeat, this.radarCrossSection, this.flares,
                this.chaff);
    }

    /** This signature with the given radar cross-section, clamped to 0..1 (NaN counts as 0). */
    public TargetSignature withRadarCrossSection(float crossSection) {
        return new TargetSignature(this.domain, this.shownAs, this.heat, crossSection, this.flares, this.chaff);
    }

    /** This signature with flares burning or not. */
    public TargetSignature withFlares(boolean using) {
        return new TargetSignature(this.domain, this.shownAs, this.heat, this.radarCrossSection, using, this.chaff);
    }

    /** This signature with chaff out or not. */
    public TargetSignature withChaff(boolean using) {
        return new TargetSignature(this.domain, this.shownAs, this.heat, this.radarCrossSection, this.flares,
                using);
    }

    private static float clamp(float value) {
        return Float.isNaN(value) ? 0.0F : Math.max(0.0F, Math.min(1.0F, value));
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof TargetSignature that && this.domain == that.domain && this.shownAs == that.shownAs
                && Float.compare(this.heat, that.heat) == 0
                && Float.compare(this.radarCrossSection, that.radarCrossSection) == 0
                && this.flares == that.flares && this.chaff == that.chaff;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.domain, this.shownAs, this.heat, this.radarCrossSection, this.flares, this.chaff);
    }

    @Override
    public String toString() {
        return "TargetSignature[" + this.domain + ", shown as " + this.shownAs + ", heat " + this.heat + ", rcs "
                + this.radarCrossSection + (this.flares ? ", flares" : "") + (this.chaff ? ", chaff" : "") + "]";
    }
}
