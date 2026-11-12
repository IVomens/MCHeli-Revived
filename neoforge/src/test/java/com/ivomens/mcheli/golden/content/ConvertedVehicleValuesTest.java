package com.ivomens.mcheli.golden.content;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.bind.TxtReader;
import com.ivomens.mcheli.content.def.vehicle.Parts;
import com.ivomens.mcheli.content.def.vehicle.Physics.RotationLimits;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile.RideRack;
import com.ivomens.mcheli.content.def.vehicle.VehicleRules;
import com.ivomens.mcheli.content.def.vehicle.VehicleSchema;
import com.ivomens.mcheli.content.registry.LoadedContent;
import com.ivomens.mcheli.content.source.ContentFormat;
import com.ivomens.mcheli.core.content.def.throwable.ThrowableDefinition;
import com.ivomens.mcheli.entity.McheliEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Values McHeli CE lost when it converted its vehicle files from TXT to YAML, checked in the bundled files
 * against the original TXT definitions: paint schemes, the parked pitch of 18 aircraft, rotation limits, the
 * second rack on a carrier, ship propellers, a dummy rotor's tilt, search light alpha, second HUDs, rack
 * angles, a display name, every recipe and the height of the cameras.
 */
class ConvertedVehicleValuesTest {

    private static Map<String, LoadedContent<?, ?>> bundled;

    @BeforeAll
    static void load() {
        bundled = ContentPipeline.load(ContentPipeline.bundledPack());
    }

    private static LoadedContent<?, ?> loaded(String file) {
        LoadedContent<?, ?> loaded = bundled.get("assets/mcheli/" + file + ".yml");
        assertNotNull(loaded, file);
        return loaded;
    }

    private static VehicleFile vehicle(String file) {
        return ((VehicleSpec) loaded(file).value()).file();
    }

    @Test
    void vehiclesOfferTheirPaintSchemes() {
        assertEquals(List.of("ah-64-il", "ah-64-us-1", "ah-64-us-2", "ah-64-jp", "ah-64-en"),
                vehicle("helicopters/ah-64").render().textures());
        assertEquals(List.of("mark5-camo1", "mark5-camo2"), vehicle("ships/mark5").render().textures());
        assertEquals(List.of("mim-23_tan", "mim-23_white"), vehicle("vehicles/mim-23").render().textures());
        assertEquals(7, vehicle("planes/t-4").render().textures().size(), "the three schemes never shipped stay out");
    }

    @Test
    void aircraftParkNoseUp() {
        // the definition keeps the game's sign: negative is nose up
        assertEquals(-3.5F, vehicle("helicopters/ah-64").physics().onGroundPitch());
        assertEquals(-14.3F, vehicle("planes/a6m2").physics().onGroundPitch());
        assertEquals(2.0F, vehicle("planes/f4a").physics().onGroundPitch());
    }

    @Test
    void enabledRotationLimitsStayEnabled() {
        for (String tank : List.of("growler", "mxtmv", "bnr32")) {
            RotationLimits limits = vehicle("tanks/" + tank).physics().rotationLimits();
            assertTrue(limits.limited(), tank);
            // the TXT files say -90 to 90, which the ground vehicle range clamps to its widest, -89.9 to 80
            assertEquals(-89.9F, limits.minPitch(), tank);
            assertEquals(80.0F, limits.maxPitch(), tank);
        }
    }

    @Test
    void vehiclesRideOnEveryRackTheyName() {
        assertEquals(List.of(new RideRack("c5", 1), new RideRack("c5", 2)),
                vehicle("helicopters/ah-64").crew().rideRacks());
        assertEquals(List.of(new RideRack("c5", 1), new RideRack("c5", 2), new RideRack("mv-22", 1),
                new RideRack("mh-53e", 1), new RideRack("mh-53e", 2)), vehicle("tanks/growler").crew().rideRacks());
        assertEquals(List.of(new RideRack("ch47", 5)), vehicle("ships/zodiac").crew().rideRacks(),
                "the single number still reads");
    }

    @Test
    void theEmitterListsSeveralRacksOfACarrierOnly() {
        String growler = emit("tanks/growler");
        assertTrue(growler.contains("RideRack:\n  c5: [1, 2]\n  mv-22: 1\n  mh-53e: [1, 2]\n"), growler);
        assertTrue(emit("ships/zodiac").contains("RideRack:\n  ch47: 5\n"));
    }

    @Test
    void shipsTurnTheirPropellers() {
        assertEquals(1, vehicle("ships/mark5").components().propellers().size());
        assertEquals(1, vehicle("ships/project1204").components().propellers().size());
        assertEquals(2, vehicle("ships/stinger_390x").components().propellers().size());
    }

    @Test
    void theF35bDummyRotorDoesNotTilt() {
        Parts.Propeller dummy = vehicle("planes/f-35b").components().propellers().getFirst();
        assertEquals("rotor0", dummy.name());
        assertEquals(0.0F, dummy.tilt());
    }

    @Test
    void searchLightsKeepTheirAlpha() {
        Parts.SearchLight light = vehicle("helicopters/bell207").components().searchLights().getFirst();
        assertEquals(0x50FFFFFF, light.colorStart());
        assertEquals(0x10FFFFC0, light.colorEnd());
        Parts.SearchLight tail = vehicle("tanks/bnr32").components().searchLights().get(2);
        assertEquals(0xFFFF0000, tail.colorStart());
        assertEquals(0x10FF0000, tail.colorEnd());
    }

    @Test
    void oneSeatVehiclesKeepTheirSecondHud() {
        assertEquals("ww2tank_hud", vehicle("tanks/m26").crew().gunnerHud());
        assertEquals("mbt_hud", vehicle("tanks/t84").crew().gunnerHud());
        assertEquals("none", vehicle("planes/macchi_m33").crew().gunnerHud());
        assertEquals("none", vehicle("planes/macchi_mc72").crew().gunnerHud());
    }

    @Test
    void racksKeepTheirAngles() {
        vehicle("helicopters/mh-53e").crew().racks().forEach(rack -> assertEquals(180.0F, rack.yaw()));
        assertEquals(180.0F, vehicle("planes/mv-22").crew().racks().getFirst().yaw());
        assertEquals(-15.0F, vehicle("ships/mark5").crew().racks().getFirst().pitch());
    }

    @Test
    void theBnr32KeepsItsName() {
        assertEquals("Skyline GT-R BNR32", vehicle("tanks/bnr32").displayName().name());
    }

    @Test
    void vehiclesAndGrenadesHaveTheirRecipes() {
        VehicleFile.Recipe cb90 = vehicle("ships/cb90").recipe();
        assertFalse(cb90.shaped());
        assertEquals(List.of("IRON_BLOCK,IRON_BLOCK"), cb90.pattern());
        assertEquals(2, vehicle("helicopters/sh-60").recipe().pattern().size());
        long craftable = bundled.values().stream().filter(loaded -> loaded.value() instanceof VehicleSpec spec
                && !spec.file().recipe().pattern().isEmpty()).count();
        assertEquals(113, craftable);
        ThrowableDefinition smoke = (ThrowableDefinition) loaded("throwable/m18_red").value();
        assertEquals(List.of("\"DY\",  Y, IRON_INGOT, D,DYE,1"), smoke.recipe().pattern());
    }

    /**
     * The cameras read from the bundled files sit where the original TXT lines put them, in the definition and
     * in game: a third-person camera, a gunner's camera, a rack's loading point, a gunner seat without a camera,
     * and two cameras whose depth the converted files had rounded.
     */
    @Test
    void camerasSitWhereTheOriginalFilesPutThem() {
        VehicleFile ah64 = txt(VehicleKind.HELICOPTER, "CameraPosition = 0.0, 1.1, 4.5",
                "AddSeat = 0.00, 1.50, -0.28", "AddGunnerSeat = 0.00, 1.30, 1.26, 0.0, 1.1, 4.5, true");
        assertSameCameras(ah64, vehicle("helicopters/ah-64"));
        assertEquals(1.1 + VehicleRules.CAMERA_RAISE + McheliEntity.GLOBAL_Y_OFFSET,
                spec("helicopters/ah-64").crew().cameraPositions().getFirst().pos().y, "the camera in game");
        assertEquals(new Vec3(0.0, 1.5, -12.8), vehicle("helicopters/ch47").crew().racks().getFirst().entry().position(),
                "AddRack = container, 0.0, 1.5, -3.0, 0.0, 0.0, -12.8, 8.0, 20");
        VehicleFile f4a = txt(VehicleKind.PLANE, "AddSeat = 0.00, 1.18, 0.0366",
                "AddGunnerSeat = 0.00, 1.53, -1.43, 0.0, 0.31, -1.9862, true");
        assertEquals(f4a.crew().seats().get(1).camera(), vehicle("planes/f4a").crew().seats().get(1).camera());
        // a gunner seat without a camera of its own
        assertSameCameras(txt(VehicleKind.TANK, "CameraPosition = 0.00, 3.6, 0.7542, true",
                "AddGunnerSeat = 0.00, 0.00, 0.00"), vehicle("tanks/m26"));
    }

    private static VehicleSpec spec(String file) {
        return (VehicleSpec) loaded(file).value();
    }

    private static VehicleFile txt(VehicleKind kind, String... lines) {
        return TxtReader.read(VehicleSchema.forKind(kind), "original", "original.txt", List.of(lines), false);
    }

    private static void assertSameCameras(VehicleFile original, VehicleFile converted) {
        assertEquals(original.camera().positions(), converted.camera().positions(), "third-person cameras");
        for (int i = 0; i < original.crew().seats().size(); i++) {
            assertEquals(original.crew().seats().get(i).camera(), converted.crew().seats().get(i).camera(),
                    "the camera of seat " + (i + 1));
        }
    }

    private static String emit(String file) {
        LoadedContent<?, ?> loaded = loaded(file);
        return ContentFormat.of(loaded.entry().type()).emit(loaded.entry());
    }
}
