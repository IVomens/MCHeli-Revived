package com.ivomens.mcheli.vehicle.sensor;

import com.ivomens.mcheli.combat.guidance.ThreatSeeker;
import net.minecraft.util.Mth;

/**
 * Data class for RWR threat events.
 */
public class RWRThreatEvent {
    public static final byte EMITTER_UNKNOWN = 0;
    public static final byte EMITTER_AIRCRAFT = 1;
    public static final byte EMITTER_MISSILE = 2;
    public static final byte EMITTER_SAM = 3;

    public static final byte MODE_SEARCH = 0;
    public static final byte MODE_STT = 1;      // Single Target Track (Lock-on)
    public static final byte MODE_MSL_DATALINK = 2;
    public static final byte MODE_MSL_ACTIVE = 3;

    private final int emitterEntityId;
    private final byte emitterKind;
    private final byte threatMode;
    private final ThreatSeeker seeker;
    private final String sourceName;
    private final float angleDeg;
    private final float strength;   // 0.0 to 1.0
    private final float confidence; // 0.0 to 1.0

    public float angleDeg() {
        return this.angleDeg;
    }

    public float confidence() {
        return this.confidence;
    }

    public int emitterEntityId() {
        return this.emitterEntityId;
    }

    public byte emitterKind() {
        return this.emitterKind;
    }

    /** What a missile seeks with; {@link ThreatSeeker#NONE} for a radar. */
    public ThreatSeeker seeker() {
        return this.seeker;
    }

    public String sourceName() {
        return this.sourceName;
    }

    public float strength() {
        return this.strength;
    }

    public byte threatMode() {
        return this.threatMode;
    }

    /** Whether a missile is homing on the receiver (rather than a radar painting it). */
    public boolean isMissile() {
        return this.threatMode == MODE_MSL_ACTIVE || this.threatMode == MODE_MSL_DATALINK;
    }

    public RWRThreatEvent(int emitterId, byte emitterKind, byte threatMode, ThreatSeeker seeker, String sourceName,
                          float angleDeg, float strength, float confidence) {
        this.emitterEntityId = emitterId;
        this.emitterKind = emitterKind;
        this.threatMode = threatMode;
        this.seeker = seeker != null ? seeker : ThreatSeeker.NONE;
        this.sourceName = sourceName != null ? sourceName : "?";
        this.angleDeg = angleDeg;
        this.strength = clamp01(strength);
        this.confidence = clamp01(confidence);
    }

    public static float clamp01(float f) {
        return Mth.clamp(f, 0.0F, 1.0F);
    }
}
