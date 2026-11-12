package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.aircraft.VehicleParts.Camera;
import com.ivomens.mcheli.aircraft.VehicleParts.Canopy;
import com.ivomens.mcheli.aircraft.VehicleParts.CrawlerTrack;
import com.ivomens.mcheli.aircraft.VehicleParts.Hatch;
import com.ivomens.mcheli.aircraft.VehicleParts.LandingGear;
import com.ivomens.mcheli.aircraft.VehicleParts.PartWeapon;
import com.ivomens.mcheli.aircraft.VehicleParts.PartWheel;
import com.ivomens.mcheli.aircraft.VehicleParts.ParticleSplash;
import com.ivomens.mcheli.aircraft.VehicleParts.RotPart;
import com.ivomens.mcheli.aircraft.VehicleParts.SearchLight;
import com.ivomens.mcheli.aircraft.VehicleParts.Throttle;
import com.ivomens.mcheli.aircraft.VehicleParts.TrackRoller;
import com.ivomens.mcheli.aircraft.VehicleParts.WeaponBay;

import java.util.Collections;
import java.util.List;

/**
 * The drawn parts every vehicle kind can have, as the renderer and the animations read them.
 *
 * @param hatches        empty on a plane that also has wings: the two share a control
 * @param crawlerTracks  a legacy TXT track with fewer than four points keeps its place as null
 * @param weaponBays     the bays that open for at least one mounted weapon
 * @param splashes       the spray drawn on water
 */
public record DrawnParts(List<Hatch> hatches, List<Hatch> lightHatches, List<Camera> cameras,
                         List<Canopy> canopies, List<LandingGear> landingGear, List<Throttle> throttles,
                         List<RotPart> rotatingParts, List<CrawlerTrack> crawlerTracks,
                         List<TrackRoller> trackRollers, List<PartWheel> wheels, List<PartWheel> steeringWheels,
                         List<PartWeapon> weapons, List<WeaponBay> weaponBays, List<SearchLight> searchLights,
                         List<ParticleSplash> splashes) {

    public DrawnParts {
        hatches = List.copyOf(hatches);
        lightHatches = List.copyOf(lightHatches);
        cameras = List.copyOf(cameras);
        canopies = List.copyOf(canopies);
        landingGear = List.copyOf(landingGear);
        throttles = List.copyOf(throttles);
        rotatingParts = List.copyOf(rotatingParts);
        // may hold nulls, see above
        crawlerTracks = Collections.unmodifiableList(crawlerTracks);
        trackRollers = List.copyOf(trackRollers);
        wheels = List.copyOf(wheels);
        steeringWheels = List.copyOf(steeringWheels);
        weapons = List.copyOf(weapons);
        weaponBays = List.copyOf(weaponBays);
        searchLights = List.copyOf(searchLights);
        splashes = List.copyOf(splashes);
    }

    public boolean haveHatch() {
        return !this.hatches.isEmpty();
    }

    public boolean haveCanopy() {
        return !this.canopies.isEmpty();
    }

    public boolean haveLandingGear() {
        return !this.landingGear.isEmpty();
    }

    public boolean havePartCamera() {
        return !this.cameras.isEmpty();
    }

    public boolean havePartThrottle() {
        return !this.throttles.isEmpty();
    }
}
