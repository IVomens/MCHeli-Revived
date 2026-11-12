package com.ivomens.mcheli.content.def.vehicle;

import java.util.List;

/**
 * YAML {@code Components}: the drawn parts, by type. A file only has the part types of its kind; the lists of
 * other kinds stay empty. {@link PartSchemas} and {@link KindPartSchemas} list the keys of each type.
 *
 * @param propellers plane {@code PlaneRotor} or ship {@code ShipRotor} entries
 */
public record Components(List<Parts.Camera> cameras, List<Parts.Canopy> canopies, List<Parts.Hatch> hatches,
                         List<Parts.Hatch> lightHatches, List<Parts.WeaponBay> weaponBays,
                         List<Parts.Rotating> rotatingParts, List<Parts.Wheel> steeringWheels,
                         List<Parts.Wheel> wheels, List<Parts.LandingGear> landingGear,
                         List<Parts.Weapon> weapons, List<Parts.SearchLight> searchLights,
                         List<Parts.Plain> trackRollers, List<Parts.Throttle> throttles,
                         List<Parts.CrawlerTrack> crawlerTracks, List<Parts.HeliRotor> heliRotors,
                         List<Parts.RepellingHook> repellingHooks, List<Parts.Propeller> propellers,
                         List<Parts.Wing> wings, List<Parts.Plain> nozzles, List<Parts.ExhaustFlame> exhaustFlames,
                         List<Parts.TurretPart> turretParts) {

    public Components {
        cameras = List.copyOf(cameras);
        canopies = List.copyOf(canopies);
        hatches = List.copyOf(hatches);
        lightHatches = List.copyOf(lightHatches);
        weaponBays = List.copyOf(weaponBays);
        rotatingParts = List.copyOf(rotatingParts);
        steeringWheels = List.copyOf(steeringWheels);
        wheels = List.copyOf(wheels);
        landingGear = List.copyOf(landingGear);
        weapons = List.copyOf(weapons);
        searchLights = List.copyOf(searchLights);
        trackRollers = List.copyOf(trackRollers);
        throttles = List.copyOf(throttles);
        crawlerTracks = List.copyOf(crawlerTracks);
        heliRotors = List.copyOf(heliRotors);
        repellingHooks = List.copyOf(repellingHooks);
        propellers = List.copyOf(propellers);
        wings = List.copyOf(wings);
        nozzles = List.copyOf(nozzles);
        exhaustFlames = List.copyOf(exhaustFlames);
        turretParts = List.copyOf(turretParts);
    }
}
