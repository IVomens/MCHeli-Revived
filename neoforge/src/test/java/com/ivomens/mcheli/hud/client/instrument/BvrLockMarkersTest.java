package com.ivomens.mcheli.hud.client.instrument;

import com.ivomens.mcheli.combat.weapon.net.RadarContactKind;
import com.ivomens.mcheli.golden.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which radar contacts the BVR display and the mortar scope mark, by what the server says they are. */
class BvrLockMarkersTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.ensure();
    }

    @Test
    void incomingAirToAirMissilesAreMarked() {
        assertEquals(BvrLockMarkers.Mark.INCOMING_MISSILE,
                BvrLockMarkers.markOf(RadarContactKind.AIR_TO_AIR_MISSILE, false));
        assertEquals(BvrLockMarkers.Mark.INCOMING_MISSILE,
                BvrLockMarkers.markOf(RadarContactKind.AIR_TO_AIR_MISSILE, true));
    }

    @Test
    void aircraftAreTargets() {
        for (boolean radarMissile : new boolean[]{false, true}) {
            assertEquals(BvrLockMarkers.Mark.TARGET, BvrLockMarkers.markOf(RadarContactKind.PLANE, radarMissile));
            assertEquals(BvrLockMarkers.Mark.TARGET,
                    BvrLockMarkers.markOf(RadarContactKind.HELICOPTER, radarMissile));
        }
    }

    @Test
    void chaffDecoysRadarMissilesOnly() {
        assertEquals(BvrLockMarkers.Mark.TARGET, BvrLockMarkers.markOf(RadarContactKind.CHAFF, true));
        assertEquals(BvrLockMarkers.Mark.NONE, BvrLockMarkers.markOf(RadarContactKind.CHAFF, false));
    }

    @Test
    void surfaceContactsAndOtherThingsAreNotMarked() {
        for (RadarContactKind kind : new RadarContactKind[]{RadarContactKind.SHIP, RadarContactKind.TANK,
                RadarContactKind.GROUND_VEHICLE, RadarContactKind.OTHER_MISSILE, RadarContactKind.FLARE,
                RadarContactKind.OTHER}) {
            assertEquals(BvrLockMarkers.Mark.NONE, BvrLockMarkers.markOf(kind, true), kind.name());
        }
    }

    @Test
    void mortarScopeNamesVehiclesAndHidesDecoys() {
        for (RadarContactKind kind : RadarContactKind.values()) {
            boolean vehicle = kind == RadarContactKind.HELICOPTER || kind == RadarContactKind.PLANE
                    || kind == RadarContactKind.SHIP || kind == RadarContactKind.TANK
                    || kind == RadarContactKind.GROUND_VEHICLE;
            assertEquals(vehicle, kind.isVehicle(), kind.name());
        }
        assertTrue(RadarContactKind.CHAFF.isDecoy());
        assertTrue(RadarContactKind.FLARE.isDecoy());
        assertFalse(RadarContactKind.AIR_TO_AIR_MISSILE.isDecoy());
    }

    @Test
    void kindsRoundTripTheirIds() {
        for (RadarContactKind kind : RadarContactKind.values()) {
            assertEquals(kind, RadarContactKind.byId(kind.id()));
        }
        assertEquals(null, RadarContactKind.byId(RadarContactKind.values().length));
        assertEquals(null, RadarContactKind.byId(-1));
    }
}
