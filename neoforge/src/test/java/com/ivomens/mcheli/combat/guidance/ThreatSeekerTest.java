package com.ivomens.mcheli.combat.guidance;

import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.content.registry.LoadedContent;
import com.ivomens.mcheli.golden.content.ContentPipeline;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seeker the missile warning names for each bundled missile is the one the soft kill decoys: flares
 * for heat seekers, chaff for radar missiles (docs/CONTENT_FORMAT_NOTES.md).
 */
class ThreatSeekerTest {

    private static final Set<String> MISSILE_TYPES = Set.of("aamissile", "asmissile", "atmissile", "tvmissile");
    /** The bundled radar missiles, as docs/CONTENT_FORMAT_NOTES.md lists them. */
    private static final Set<String> RADAR_MISSILES = Set.of("aim120", "aim7", "aim7_f", "aim7_spa", "r-77", "r77-1",
            "r27ae", "hawk", "sa-2", "agm84", "kh-35");

    private static Map<String, WeaponSpec> missiles;

    @BeforeAll
    static void load() {
        missiles = new TreeMap<>();
        for (LoadedContent<?, ?> loaded : ContentPipeline.load(ContentPipeline.bundledPack()).values()) {
            if (loaded.value() instanceof WeaponSpec spec
                    && MISSILE_TYPES.contains(spec.definition().type().toLowerCase())) {
                missiles.put(spec.name(), spec);
            }
        }
    }

    @Test
    void everyBundledMissileIsNamedByTheDecoysThatFoolIt() {
        assertTrue(missiles.size() > 50, "bundled missiles: " + missiles.keySet());
        missiles.forEach((name, spec) -> {
            ThreatSeeker seeker = ThreatSeeker.of(spec);
            assertTrue(seeker.isMissile(), name);
            assertEquals(TargetFilter.decoyedByFlares(spec), seeker.decoyedByFlares(), name + " flares");
            assertEquals(TargetFilter.decoyedByChaff(spec), seeker.decoyedByChaff(), name + " chaff");
            assertEquals(RADAR_MISSILES.contains(name) ? ThreatSeeker.RADAR : ThreatSeeker.HEAT, seeker, name);
        });
    }

    @Test
    void theRadarMissilesAreBundled() {
        assertTrue(missiles.keySet().containsAll(RADAR_MISSILES), () -> "missing: " + RADAR_MISSILES);
        assertEquals(ThreatSeeker.RADAR, ThreatSeeker.of(missiles.get("aim120")));
        assertEquals(ThreatSeeker.HEAT, ThreatSeeker.of(missiles.get("aim9")));
    }

    @Test
    void decoysNameTheSeeker() {
        assertEquals(ThreatSeeker.HEAT, ThreatSeeker.of(true, false));
        assertEquals(ThreatSeeker.RADAR, ThreatSeeker.of(false, true));
        assertEquals(ThreatSeeker.DUAL, ThreatSeeker.of(true, true));
        assertEquals(ThreatSeeker.OTHER, ThreatSeeker.of(false, false));
        for (ThreatSeeker seeker : ThreatSeeker.values()) {
            if (seeker.isMissile()) {
                assertEquals(seeker, ThreatSeeker.of(seeker.decoyedByFlares(), seeker.decoyedByChaff()));
            }
        }
    }

    @Test
    void aMissileWithoutDefinitionIsAHeatSeekerAsFlaresDestroyIt() {
        assertEquals(ThreatSeeker.HEAT, ThreatSeeker.of(null));
        assertFalse(ThreatSeeker.NONE.isMissile());
        assertFalse(ThreatSeeker.NONE.decoyedByFlares() || ThreatSeeker.NONE.decoyedByChaff());
    }
}
