package com.ivomens.mcheli.vehicle.sensor;

/**
 * The missiles homing on a vehicle, counted by the decoy that fools them, as the crew's warning
 * equipment tells them: the HUD shows them and cues the countermeasure to use.
 *
 * @param heat  missiles that flares decoy (heat seekers, and dual seekers)
 * @param radar missiles that chaff decoys (radar missiles, and dual seekers)
 * @param total every missile, whatever its seeker; a missile no decoy fools only counts here
 */
public record IncomingMissiles(int heat, int radar, int total) {

    public static final IncomingMissiles NONE = new IncomingMissiles(0, 0, 0);

    /**
     * Counts the missile threats of a threat table.
     *
     * @param identifiesSeekers whether the receiver can tell seekers apart; if not, only the total counts
     */
    public static IncomingMissiles of(Iterable<RWRThreatEvent> threats, boolean identifiesSeekers) {
        int heat = 0;
        int radar = 0;
        int total = 0;
        for (RWRThreatEvent threat : threats) {
            if (threat == null || !threat.isMissile()) {
                continue;
            }
            total++;
            if (identifiesSeekers && threat.seeker().decoyedByFlares()) {
                heat++;
            }
            if (identifiesSeekers && threat.seeker().decoyedByChaff()) {
                radar++;
            }
        }
        return total == 0 ? NONE : new IncomingMissiles(heat, radar, total);
    }

    /** Whether any missile is inbound. */
    public boolean any() {
        return this.total > 0;
    }

    /** Whether to cue flares: a missile they decoy is inbound and the vehicle carries them. */
    public boolean cueFlares(boolean carriesFlares) {
        return carriesFlares && this.heat > 0;
    }

    /** Whether to cue chaff: a missile it decoys is inbound and the vehicle carries it. */
    public boolean cueChaff(boolean carriesChaff) {
        return carriesChaff && this.radar > 0;
    }
}
