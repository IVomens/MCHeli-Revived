package com.ivomens.mcheli.golden.content;

import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.content.def.weapon.DamageFactors;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition;
import com.ivomens.mcheli.content.registry.LoadedContent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Values McHeli CE lost when it converted its weapon files from TXT to YAML, checked in the bundled files
 * against the original TXT definitions: cluster bombs and missiles released no bomblets, fuel-air bombs
 * exploded like plain bombs, targeting pods marked nothing, and no weapon dealt extra damage to the vehicle
 * kinds it was made for.
 */
class ConvertedWeaponValuesTest {

    private static Map<String, LoadedContent<?, ?>> bundled;

    @BeforeAll
    static void load() {
        bundled = ContentPipeline.load(ContentPipeline.bundledPack());
    }

    private static WeaponDefinition weapon(String name) {
        LoadedContent<?, ?> loaded = bundled.get("assets/mcheli/weapons/" + name + ".yml");
        assertNotNull(loaded, name);
        return ((WeaponSpec) loaded.value()).definition();
    }

    @Test
    void clusterWeaponsReleaseBomblets() {
        WeaponDefinition.Submunition agm154 = weapon("agm154").submunition();
        assertEquals(15, agm154.count());
        assertEquals(15, agm154.deployTime());
        assertEquals(1.1F, agm154.spread());
        WeaponDefinition.Submunition anm17 = weapon("anm17allc").submunition();
        assertEquals(25, anm17.count());
        assertEquals(20, anm17.deployTime());
        assertEquals(1.3F, anm17.spread());
    }

    @Test
    void fuelAirBombsAreFuelAir() {
        assertTrue(weapon("cbu-72").explosion().fuelAir());
        assertTrue(weapon("anm17allc").explosion().fuelAir());
    }

    @Test
    void targetingPodsHaveTheirReachAndMarkTime() {
        WeaponDefinition pod = weapon("kurganets_spot");
        assertEquals(100, pod.length());
        assertEquals(90, pod.radius());
        assertEquals(21, pod.markTime(), "MarkTime 20, plus the tick the mark is placed on");
    }

    @Test
    void weaponsKeepTheirDamageFactors() {
        assertEquals(Map.of(DamageFactors.HELICOPTER, 8.0F, DamageFactors.PLANE, 8.0F, DamageFactors.VEHICLE, 8.0F,
                DamageFactors.TANK, 8.0F), weapon("46cm").damageFactors());
        assertEquals(Map.of(DamageFactors.TANK, 2.0F), weapon("9k121_ka50").damageFactors());
    }
}
