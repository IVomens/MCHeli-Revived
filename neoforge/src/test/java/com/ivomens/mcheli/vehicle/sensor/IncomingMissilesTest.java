package com.ivomens.mcheli.vehicle.sensor;

import com.ivomens.mcheli.combat.guidance.ThreatSeeker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How the missile warning counts the threats of a table, and when it cues flares or chaff. */
class IncomingMissilesTest {

    private static RWRThreatEvent missile(ThreatSeeker seeker) {
        return new RWRThreatEvent(1, RWRThreatEvent.EMITTER_MISSILE, RWRThreatEvent.MODE_MSL_ACTIVE, seeker, "MSL",
                0.0F, 0.5F, 1.0F);
    }

    private static RWRThreatEvent radar(byte mode) {
        return new RWRThreatEvent(2, RWRThreatEvent.EMITTER_AIRCRAFT, mode, ThreatSeeker.NONE, "F15", 0.0F, 0.5F, 1.0F);
    }

    private static final List<RWRThreatEvent> TABLE = List.of(missile(ThreatSeeker.HEAT), missile(ThreatSeeker.HEAT),
            missile(ThreatSeeker.RADAR), missile(ThreatSeeker.DUAL), missile(ThreatSeeker.OTHER),
            radar(RWRThreatEvent.MODE_STT), radar(RWRThreatEvent.MODE_SEARCH));

    @Test
    void missilesAreCountedByTheDecoyThatFoolsThem() {
        assertEquals(new IncomingMissiles(3, 2, 5), IncomingMissiles.of(TABLE, true));
    }

    @Test
    void aReceiverThatCannotTellSeekersOnlyCountsMissiles() {
        assertEquals(new IncomingMissiles(0, 0, 5), IncomingMissiles.of(TABLE, false));
        assertFalse(IncomingMissiles.of(TABLE, false).cueFlares(true));
    }

    @Test
    void radarsAloneAreNoMissiles() {
        IncomingMissiles none = IncomingMissiles.of(List.of(radar(RWRThreatEvent.MODE_STT)), true);
        assertEquals(IncomingMissiles.NONE, none);
        assertFalse(none.any());
    }

    @Test
    void theCueNamesOnlyDecoysTheVehicleCarries() {
        IncomingMissiles heat = IncomingMissiles.of(List.of(missile(ThreatSeeker.HEAT)), true);
        assertTrue(heat.cueFlares(true));
        assertFalse(heat.cueFlares(false));
        assertFalse(heat.cueChaff(true));
        IncomingMissiles radar = IncomingMissiles.of(List.of(missile(ThreatSeeker.RADAR)), true);
        assertTrue(radar.cueChaff(true));
        assertFalse(radar.cueFlares(true));
        IncomingMissiles other = IncomingMissiles.of(List.of(missile(ThreatSeeker.OTHER)), true);
        assertTrue(other.any());
        assertFalse(other.cueFlares(true) || other.cueChaff(true));
    }
}
