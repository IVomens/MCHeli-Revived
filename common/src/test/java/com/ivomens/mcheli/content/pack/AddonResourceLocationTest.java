package com.ivomens.mcheli.content.pack;

import com.ivomens.mcheli.content.def.vehicle.RWRType;
import com.ivomens.mcheli.content.def.vehicle.RadarType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonResourceLocationTest {

    @Test
    void parsesAddonDomain() {
        AddonResourceLocation loc = new AddonResourceLocation("mcheli:myaddon|ah-64");
        assertEquals("mcheli", loc.getNamespace());
        assertEquals("myaddon", loc.getAddonDomain());
        assertEquals("ah-64", loc.getPath());
        assertEquals("mcheli:myaddon|ah-64", loc.toString());
        assertFalse(loc.isShareDomain());
    }

    @Test
    void shareDomainMatchesAnyAddon() {
        AddonResourceLocation shared = new AddonResourceLocation("ah-64");
        assertTrue(shared.isShareDomain());
        assertEquals("mcheli", shared.getNamespace());
        AddonResourceLocation specific = new AddonResourceLocation("mcheli:other|ah-64");
        assertTrue(shared.equalsIgnore(specific));
        assertTrue(specific.equalsIgnore(shared));
        assertFalse(specific.equalsIgnore(new AddonResourceLocation("mcheli:third|ah-64")));
        assertEquals("mcheli:ah-64", specific.asVanillaDomainPath().toString());
    }

    @Test
    void domainConstructor() {
        AddonResourceLocation loc = new AddonResourceLocation("mcheli:f22a", "@builtin");
        assertEquals("@builtin", loc.getAddonDomain());
        assertEquals("f22a", loc.getPath());
        assertEquals(loc, new AddonResourceLocation("mcheli:f22a", "@builtin"));
    }

    @Test
    void enumParsing() {
        assertEquals(RadarType.EARLY_AS, RadarType.parse("early_as", RadarType.MODERN_AA));
        assertEquals(RadarType.EARLY_AA, RadarType.parse(1, RadarType.MODERN_AA));
        assertEquals(RadarType.MODERN_AA, RadarType.parse("bogus", RadarType.MODERN_AA));
        assertEquals(RWRType.ALL_WAY_EARLY, RWRType.parse("All-Way-Early", RWRType.DIGITAL));
        assertEquals(RWRType.FOUR_WAY, RWRType.parse("0", RWRType.DIGITAL));
    }
}
