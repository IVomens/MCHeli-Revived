package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.vehicle.component.TickStep;
import net.minecraft.world.entity.Entity;

/**
 * Steps every vehicle type puts in its own part of the tick (see {@link AircraftEntity#typeSteps}),
 * each type in its own order.
 */
public final class VehicleTypeSteps {

    private VehicleTypeSteps() {
    }

    /** Client, once the type is known: the server sends the weapons' ammo and timers. */
    public static TickStep askForWeaponState(AircraftEntity v) {
        return TickStep.client("weapons: ask the server for their state", v.ammoSupply()::requestCountsOnce);
    }

    /** A pilot who just boarded starts on the first weapon their seat can use. */
    public static TickStep selectWeaponForNewPilot(AircraftEntity v) {
        return TickStep.both("weapons: a new pilot's first weapon", () -> {
            if (v.pilotHandover().pilotJustBoarded()) {
                v.weapons().selectFirst(v.getRiddenByEntity());
            }
        });
    }

    /** Reactive armor, then the weapons: barrels, reloads, which fired, muzzle flashes and bay doors. */
    public static TickStep weapons(AircraftEntity v) {
        return new TickStep("weapons: armor and weapons", ctx -> {
            v.partBoxes().reactiveArmor().serverTick();
            v.weapons().serverTick(ctx);
        }, ctx -> {
            v.partBoxes().reactiveArmor().clientTick();
            v.weapons().clientTick(ctx);
        }, v.weapons());
    }

    public static TickStep seats(AircraftEntity v) {
        return TickStep.of("seats: keep them safe and linked", v.seats());
    }

    /** The position the physics step starts from, for the next tick's interpolation. */
    public static TickStep rememberLastPosition(Entity v) {
        return TickStep.both("history: where the step starts", () -> {
            v.xo = v.getX();
            v.yo = v.getY();
            v.zo = v.getZ();
        });
    }

    public static TickStep spoolDownWreck(AircraftEntity v) {
        return TickStep.both("engine: a wreck spools down", v.physics()::spoolDownWreck);
    }

    public static TickStep cameraViewers(AircraftEntity v) {
        return TickStep.both("camera: who looks through it", v.cameras()::updateViewers);
    }

    /** Client: the camera follows the vehicle to where it is now. */
    public static TickStep followCamera(AircraftEntity v) {
        return TickStep.client("camera: follow the vehicle", () -> v.cameras().follow(v.getX(), v.getY(), v.getZ()));
    }

    /** Server: a pilot who was removed (logged out, died) leaves the vehicle. */
    public static TickStep dropRemovedPilot(AircraftEntity v) {
        return TickStep.server("crew: a removed pilot leaves", () -> {
            Entity pilot = v.getRiddenByEntity();
            if (pilot != null && pilot.isRemoved()) {
                v.pilotHandover().pilotLeaves();
            }
        });
    }
}
