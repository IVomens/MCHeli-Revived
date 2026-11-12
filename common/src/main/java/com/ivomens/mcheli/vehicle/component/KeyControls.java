package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.extension.VehicleControls;

/** The API's read-only view of a vehicle's keys and throttle, for the behaviours and movement models of other mods. */
public final class KeyControls implements VehicleControls {

    private final ControlKeysComponent keys;
    private final Vehicle vehicle;

    public KeyControls(ControlKeysComponent keys, Vehicle vehicle) {
        this.keys = keys;
        this.vehicle = vehicle;
    }

    @Override
    public boolean forward() {
        return this.keys.throttleUp();
    }

    @Override
    public boolean back() {
        return this.keys.throttleDown();
    }

    @Override
    public boolean left() {
        return this.keys.moveLeft() && !this.keys.moveRight();
    }

    @Override
    public boolean right() {
        return this.keys.moveRight() && !this.keys.moveLeft();
    }

    @Override
    public double throttle() {
        return this.vehicle.throttle();
    }
}
