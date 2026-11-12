package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.aircraft.VehicleParts.DrawnPart;
import com.ivomens.mcheli.aircraft.VehicleParts.ExhaustFlame;
import com.ivomens.mcheli.aircraft.VehicleParts.HeliRotor;
import com.ivomens.mcheli.aircraft.VehicleParts.Propeller;
import com.ivomens.mcheli.aircraft.VehicleParts.VPart;
import com.ivomens.mcheli.aircraft.VehicleParts.Wing;

import java.util.List;

/**
 * The drawn parts only one kind of vehicle has; the lists of the other kinds are empty.
 *
 * @param heliRotors    helicopters: the rotors with their blades
 * @param propellers    planes and ships: propellers or screws, with their blades
 * @param wings         planes and ships: folding or tilting wings with their pylons; empty when the plane has
 *                      hatches, which share the control
 * @param nozzles       planes and ships: VTOL nozzles
 * @param exhaustFlames planes: afterburner flames
 * @param turretParts   ground vehicles: the turret's parts
 */
public record KindParts(List<HeliRotor> heliRotors, List<Propeller> propellers, List<Wing> wings,
                        List<DrawnPart> nozzles, List<ExhaustFlame> exhaustFlames, List<VPart> turretParts) {

    public KindParts {
        heliRotors = List.copyOf(heliRotors);
        propellers = List.copyOf(propellers);
        wings = List.copyOf(wings);
        nozzles = List.copyOf(nozzles);
        exhaustFlames = List.copyOf(exhaustFlames);
        turretParts = List.copyOf(turretParts);
    }

    public boolean haveNozzle() {
        return !this.nozzles.isEmpty();
    }

    public boolean haveRotor() {
        return !this.propellers.isEmpty();
    }

    public boolean haveWing() {
        return !this.wings.isEmpty();
    }
}
