package com.ivomens.mcheli.golden.content;

import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.content.def.vehicle.AircraftFeatures;
import com.ivomens.mcheli.content.def.vehicle.KindFeatures;
import com.ivomens.mcheli.content.def.vehicle.Physics;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.content.def.vehicle.VehicleRules;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.hud.Hud;
import com.ivomens.mcheli.hud.HudManager;
import com.ivomens.mcheli.hud.client.VehicleHuds;
import com.ivomens.mcheli.platform.Services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A vehicle as {@code AircraftInfo} and its five kind subclasses dumped it before the content rewrite: the old
 * field names over today's {@link VehicleSpec}, its layouts and its file.
 */
final class LegacyVehicleView {

    private LegacyVehicleView() {
    }

    static LegacyObject of(ContentEntry<?> entry, VehicleSpec vehicle) {
        LegacyObject.Builder view = LegacyViews.file(className(vehicle), entry);
        content(view, vehicle);
        crewAndArmament(view, entry, vehicle);
        parts(view, vehicle);
        settings(view, vehicle.file());
        physics(view, vehicle.file().physics());
        features(view, vehicle.file().features());
        kind(view, vehicle);
        return view.build();
    }

    private static String className(VehicleSpec vehicle) {
        return switch (vehicle.kind()) {
            case HELICOPTER -> "com.ivomens.mcheli.helicopter.HelicopterInfo";
            case PLANE -> "com.ivomens.mcheli.plane.PlaneInfo";
            case SHIP -> "com.ivomens.mcheli.ship.ShipInfo";
            case TANK -> "com.ivomens.mcheli.tank.TankInfo";
            case VEHICLE -> "com.ivomens.mcheli.vehicle.VehicleInfo";
        };
    }

    /** Names, item, recipe and textures. */
    private static void content(LegacyObject.Builder view, VehicleSpec vehicle) {
        VehicleFile file = vehicle.file();
        view.put("name", file.name())
                .put("speed", vehicle.speed())
                .put("displayName", file.displayName().name())
                .put("displayNameLang", new HashMap<>(file.displayName().translations()))
                .put("itemID", file.itemId())
                // the info was dumped as read, before items were registered
                .put("item", null)
                .put("recipe", List.of())
                .put("recipeString", file.recipe().pattern())
                .put("isShapedRecipe", file.recipe().shaped())
                .put("category", file.category())
                .put("creativeOnly", file.creativeOnly())
                .put("invulnerable", file.invulnerable())
                .put("textureNameList", vehicle.textures())
                .put("textureCount", 0);
    }

    private static void crewAndArmament(LegacyObject.Builder view, ContentEntry<?> entry, VehicleSpec vehicle) {
        List<VehicleParts.WeaponSet> sets = vehicle.armament().weaponSets();
        List<VehicleParts.PartWeapon> partWeapons = vehicle.parts().weapons();
        view.put("seatList", vehicle.crew().seats())
                .put("mobSeatNum", vehicle.crew().riderSeats())
                .put("entityRackNum", vehicle.crew().racks())
                .put("entityRackList", List.of())
                .put("exclusionSeatList", vehicle.crew().exclusions())
                .put("cameraPosition", vehicle.crew().cameraPositions())
                .put("rideRacks", vehicle.crew().rideRacks())
                .put("repellingHooks", vehicle.crew().repellingHooks())
                .put("mobDropOption", vehicle.crew().mobDrop())
                .put("hudList", LegacyViews.map(huds(vehicle.file()), LegacyViews::hudReference))
                .put("hudTvMissile", LegacyViews.hudReference(HudManager.get(VehicleHuds.TV_MISSILE).orElse(null)))
                .put("weaponSetList", sets)
                .put("flare", vehicle.armament().flare())
                .put("chaff", vehicle.armament().chaff())
                .put("lastWeaponType", sets.isEmpty() ? "" : sets.getLast().type)
                .put("lastWeaponIndex", sets.size() - 1)
                .put("lastWeaponPart", entry.isLegacyTxt() && !partWeapons.isEmpty() ? partWeapons.getLast() : null)
                .put("extraBoundingBox", vehicle.hitBoxes().boxes())
                .put("wheels", vehicle.hitBoxes().wheels())
                .put("markerWidth", vehicle.hitBoxes().markerWidth())
                .put("markerHeight", vehicle.hitBoxes().markerHeight())
                .put("bbZmin", vehicle.hitBoxes().zMin())
                .put("bbZmax", vehicle.hitBoxes().zMax())
                .put("bbZ", vehicle.hitBoxes().z());
    }

    /**
     * The seat HUDs as the old info resolved them when it loaded: a client resolved each seat's own HUD, any
     * other side only the kind's defaults.
     */
    private static List<Hud> huds(VehicleFile file) {
        if (Services.PLATFORM.isClient()) {
            return VehicleHuds.of(VehicleTypes.get(file.kind(), file.name()).orElseThrow());
        }
        List<Hud> huds = new ArrayList<>();
        int seatCount = VehicleRules.riderSeats(file).size();
        for (int seat = 0; seat < seatCount; seat++) {
            huds.add(HudManager.get(VehicleRules.defaultHud(file, seat)).orElse(null));
        }
        if (seatCount == 1) {
            String gunnerHud = file.crew().gunnerHud();
            huds.add(gunnerHud != null ? HudManager.get(gunnerHud).orElse(Hud.NoDisp)
                    : HudManager.get(VehicleRules.defaultHud(file, 1)).orElse(null));
        }
        return huds;
    }

    private static void parts(LegacyObject.Builder view, VehicleSpec vehicle) {
        var parts = vehicle.parts();
        view.put("hatchList", parts.hatches())
                .put("lightHatchList", parts.lightHatches())
                .put("cameraList", parts.cameras())
                .put("canopyList", parts.canopies())
                .put("landingGear", parts.landingGear())
                .put("partThrottle", parts.throttles())
                .put("partRotPart", parts.rotatingParts())
                .put("partCrawlerTrack", parts.crawlerTracks())
                .put("partTrackRoller", parts.trackRollers())
                .put("partWheel", parts.wheels())
                .put("partSteeringWheel", parts.steeringWheels())
                .put("partWeapon", parts.weapons())
                .put("partWeaponBay", parts.weaponBays())
                .put("searchLights", parts.searchLights())
                .put("particleSplashs", parts.splashes());
    }

    /** The top-level settings of the file, and those the game derives from them. */
    private static void settings(LegacyObject.Builder view, VehicleFile file) {
        view.put("maxHp", file.maxHp())
                .put("armorMinDamage", file.armor().armorMinDamage())
                .put("armorMaxDamage", file.armor().armorMaxDamage())
                .put("armorDamageFactor", file.armor().armorDamageFactor())
                .put("damageFactor", file.armor().damageFactor())
                .put("submergedDamageHeight", file.armor().submergedDamageHeight())
                .put("isUAV", VehicleRules.isUav(file))
                .put("isSmallUAV", file.uav().small())
                .put("isNewUAV", file.uav().newStyle())
                .put("isTargetDrone", file.uav().targetDrone())
                .put("uavRange", file.uav().range())
                .put("regeneration", file.regeneration())
                .put("maxFuel", file.fuel().maxFuel())
                .put("fuelConsumption", file.fuel().consumption())
                .put("fuelSupplyRange", file.fuel().supplyRange())
                .put("fuelSupplyType", file.fuel().supplyType())
                .put("fuelSupplyInfinite", file.fuel().infiniteSupply())
                .put("electric", file.fuel().electric())
                .put("allowBattery", file.fuel().batteries())
                .put("fluidType", file.fuel().types())
                .put("ammoSupplyRange", file.ammoSupplyRange())
                .put("repairOtherVehiclesRange", file.repairOtherVehicles().range())
                .put("repairOtherVehiclesValue", file.repairOtherVehicles().value())
                .put("stealth", file.stealth())
                .put("canRide", file.canRide())
                .put("rotorSpeed", VehicleRules.rotorSpeed(file))
                .put("turretPosition", file.turretPosition())
                .put("unmountPosition", file.unmountPosition())
                .put("cameraZoom", file.camera().zoom())
                .put("defaultFreelook", file.camera().defaultFreeLook())
                .put("thirdPersonDist", file.camera().thirdPersonDistance())
                .put("cameraRotationSpeed", file.camera().rotationSpeed())
                .put("alwaysCameraView", VehicleRules.alwaysCameraView(file))
                .put("explosionSizeByCrash", (float) file.explosionSizeByCrash())
                .put("throttleDownFactor", file.throttleDownFactor())
                .put("nameOnModernAARadar", file.radarNames().modernAirToAir())
                .put("nameOnEarlyAARadar", file.radarNames().earlyAirToAir())
                .put("nameOnModernASRadar", file.radarNames().modernAirToSurface())
                .put("nameOnEarlyASRadar", file.radarNames().earlyAirToSurface())
                .put("entityWidth", file.render().modelWidth())
                .put("entityHeight", file.render().modelHeight())
                .put("entityPitch", file.render().modelPitch())
                .put("entityRoll", file.render().modelRoll())
                .put("enableSeaSurfaceParticle", file.render().seaSurfaceParticles())
                .put("particlesScale", file.render().particleScale())
                .put("hideEntity", file.render().hideRiders())
                .put("smoothShading", file.render().smoothShading())
                .put("soundMove", file.sound().moveSound())
                .put("soundVolume", file.sound().volume())
                .put("soundPitch", file.sound().pitch())
                .put("soundRange", file.sound().range())
                .put("trackRollerRot", file.wheels().trackRotation())
                .put("partWheelRot", file.wheels().wheelRotation())
                .put("isEnableParachuting", VehicleRules.dropsCrew(file));
    }

    private static void physics(LegacyObject.Builder view, Physics physics) {
        view.put("bodyHeight", physics.bodySize().height())
                .put("bodyWidth", physics.bodySize().width())
                .put("isFloat", physics.canFloat())
                .put("floatOffset", physics.floatOffset())
                .put("gravity", physics.gravity())
                .put("gravityInWater", physics.gravityInWater())
                .put("autoPilotRot", physics.rotationSnap())
                .put("onGroundPitch", physics.onGroundPitch())
                .put("canMoveOnGround", physics.canMoveOnGround())
                .put("canRotOnGround", physics.canRotOnGround())
                .put("stepHeight", physics.stepHeight())
                .put("pivotTurnThrottle", physics.pivotTurnThrottle())
                .put("onGroundPitchFactor", physics.groundTilt().pitch())
                .put("onGroundRollFactor", physics.groundTilt().roll())
                .put("motionFactor", physics.motionFactor())
                .put("mobilityYaw", physics.mobility().yaw())
                .put("mobilityPitch", physics.mobility().pitch())
                .put("mobilityRoll", physics.mobility().roll())
                .put("mobilityYawOnGround", physics.mobility().yawOnGround())
                .put("minRotationPitch", physics.rotationLimits().minPitch())
                .put("maxRotationPitch", physics.rotationLimits().maxPitch())
                .put("minRotationRoll", physics.rotationLimits().minRoll())
                .put("maxRotationRoll", physics.rotationLimits().maxRoll())
                .put("limitRotation", physics.rotationLimits().limited());
    }

    private static void features(LegacyObject.Builder view, AircraftFeatures features) {
        view.put("isEnableGunnerMode", features.gunnerMode())
                .put("isEnableConcurrentGunnerMode", features.concurrentGunner())
                .put("isEnableNightVision", features.nightVision())
                .put("isEnableEntityRadar", features.entityRadar())
                .put("isEnableEjectionSeat", features.ejectionSeat())
                .put("enableBack", features.canReverse())
                .put("inventorySize", features.inventorySize())
                .put("throttleUpDown", features.throttleUpDown())
                .put("throttleUpDownOnEntity", features.throttleUpDownOnEntity())
                .put("radarType", features.radar().type())
                .put("rwrType", features.rwr().type())
                .put("chaffUseTime", features.flare().chaffUseTime())
                .put("chaffWaitTime", features.flare().chaffWaitTime())
                .put("engineShutdownThreshold", features.engineShutdownHp())
                .put("apsUseTime", features.aps().useTime())
                .put("apsWaitTime", features.aps().waitTime())
                .put("apsRange", features.aps().range())
                .put("hasAPS", features.aps().enabled())
                .put("hasRWR", features.rwr().enabled())
                .put("enableRadar", features.radar().enabled())
                .put("passiveRadar", features.radar().passive())
                .put("radarMaxTargetRange", features.radar().maxTargetRange())
                .put("radarScanAzimuthDeg", features.radar().scanAzimuth())
                .put("radarScanElevationDeg", features.radar().scanElevation())
                .put("radarScanTick", features.radar().scanTick())
                .put("enableBVR", features.radar().bvr())
                .put("nameOnRWR", features.rwr().name())
                .put("enableECMJammer", features.ecm().enabled())
                .put("ecmJammerWaitTime", features.ecm().waitTime())
                .put("ecmJammerUseTime", features.ecm().useTime())
                .put("ecmJammerType", features.ecm().type())
                .put("armorExplosionDamageMultiplier", features.explosionDamageFactor());
    }

    /** The fields of the kind subclass. */
    private static void kind(LegacyObject.Builder view, VehicleSpec vehicle) {
        KindFeatures features = vehicle.file().kindFeatures();
        switch (vehicle.kind()) {
            case HELICOPTER -> view.put("isEnableFoldBlade", features.foldableBlades())
                    .put("rotorList", vehicle.kindParts().heliRotors());
            case PLANE, SHIP -> {
                view.put("nozzles", vehicle.kindParts().nozzles())
                        .put("rotorList", vehicle.kindParts().propellers())
                        .put("wingList", vehicle.kindParts().wings())
                        .put("isEnableVtol", features.vtol().enabled())
                        .put("isDefaultVtol", features.vtol().byDefault())
                        .put("vtolYaw", features.vtol().yaw())
                        .put("vtolPitch", features.vtol().pitch())
                        .put("isVariableSweepWing", features.variableSweepWing())
                        .put("sweepWingSpeed", VehicleRules.sweptSpeed(vehicle.file()));
                if (vehicle.kind() == com.ivomens.mcheli.api.vehicle.VehicleKind.PLANE) {
                    view.put("exhaustFlames", vehicle.kindParts().exhaustFlames());
                }
            }
            case TANK -> view.put("weightType", features.weightType())
                    .put("weightedCenterZ", features.weightedCenterZ());
            case VEHICLE -> view.put("isEnableMove", features.canMove())
                    .put("isEnableRot", features.canRotate())
                    .put("partList", vehicle.kindParts().turretParts());
        }
    }
}
