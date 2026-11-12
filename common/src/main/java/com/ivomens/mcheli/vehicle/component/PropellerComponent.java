package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;

/** Propellers and fans that turn with the throttle, at the definition's rotor speed. Only drawn. */
@TickOrder(phase = TickPhase.PHYSICS)
public final class PropellerComponent implements VehicleComponent {

    private final AircraftEntity vehicle;
    private final EngineComponent engine;
    private float angle;
    private float prevAngle;

    public PropellerComponent(AircraftEntity vehicle, EngineComponent engine) {
        this.vehicle = vehicle;
        this.engine = engine;
    }

    public float angle() {
        return this.angle;
    }

    public float prevAngle() {
        return this.prevAngle;
    }

    @Override
    public void serverTick(TickContext ctx) {
        spin();
    }

    @Override
    public void clientTick(TickContext ctx) {
        spin();
    }

    /** Both angles move back a turn together, so the drawn angle never jumps. */
    private void spin() {
        this.prevAngle = this.angle;
        this.angle = (float) (this.angle + this.engine.throttle() * this.vehicle.spec().rotorSpeed());
        if (this.angle > 360.0F) {
            this.angle -= 360.0F;
            this.prevAngle -= 360.0F;
        }
        if (this.angle < 0.0F) {
            this.angle += 360.0F;
            this.prevAngle += 360.0F;
        }
    }
}
