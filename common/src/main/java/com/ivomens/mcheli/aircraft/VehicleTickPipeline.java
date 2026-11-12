package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.vehicle.component.LifecycleComponent;
import com.ivomens.mcheli.vehicle.component.RestComponent;
import com.ivomens.mcheli.vehicle.component.ScheduledStep;
import com.ivomens.mcheli.vehicle.component.ScriptedPilotComponent;
import com.ivomens.mcheli.vehicle.component.TickContext;
import com.ivomens.mcheli.vehicle.component.TickOrder;
import com.ivomens.mcheli.vehicle.component.TickPhase;
import com.ivomens.mcheli.vehicle.component.TickStep;

import java.util.List;
import net.minecraft.world.entity.Entity;

/**
 * The whole tick of a vehicle, in order. This is the only place that decides what runs when.
 * <p>
 * The order is the one the vehicles have always had, quirks included, because the golden
 * trajectories depend on it. The ones worth knowing about:
 * <ul>
 *   <li>Wheels, tracks and the light hatch animate before the physics step, so they show last
 *       tick's throttle and keys.</li>
 *   <li>Parts (canopy, gear, nozzles, wings) animate after the physics step, so thrust follows the
 *       nozzle angle one tick late.</li>
 *   <li>The vehicle's age is counted up halfway through the tick: steps before "age" see the old
 *       value, steps after it the new one.</li>
 *   <li>The vanilla base tick (fire, portals, fluids) runs between the lifecycle and the systems.</li>
 *   <li>A scripted pilot (the wingman autopilot) takes the controls before anything else, even
 *       before a resting vehicle decides to skip the tick; see {@link ScriptedPilotComponent}.</li>
 * </ul>
 * Reordering anything here needs a golden diff and a decision.
 * <p>
 * The steps are grouped by {@link TickPhase}. A component with a tick of its own declares the phase it
 * belongs in and the components it has to tick after ({@link TickOrder}); the pipeline checks the order
 * against those declarations when it is built, and again once the vehicle type's steps join, and
 * refuses to run an order that breaks one.
 */
final class VehicleTickPipeline {

    private final AircraftEntity vehicle;
    private final TickContext context;
    private final RestComponent rest;
    /** Phase one of {@link ScriptedPilotComponent}: what the autopilot decided before the level ticked. */
    private final TickStep scriptedPilot;
    /** Stands in the plan for the vehicle type's own steps, which are known only once the type is. */
    private final TickStep typeUpdate;
    private final TickPlan plan;
    private final List<TickStep> steps;
    private List<TickStep> typeSteps;
    /** Every step in order, the vehicle type's included once they are known; for the vehicle inspector. */
    private List<ScheduledStep> schedule;

    VehicleTickPipeline(AircraftEntity vehicle, RestComponent rest, LifecycleComponent lifecycle) {
        this.vehicle = vehicle;
        this.context = new TickContext(lifecycle);
        this.rest = rest;
        this.scriptedPilot = TickStep.server("scripted pilot: take the controls",
                vehicle.scriptedPilot()::takeControls);
        this.typeUpdate = new TickStep("physics: the vehicle type's update", ctx -> runTypeSteps(ctx, false),
                ctx -> runTypeSteps(ctx, true));
        this.plan = new TickPlan()
                .then(TickPhase.INPUT, input(vehicle))
                .then(TickPhase.LIFECYCLE, lifecycle(vehicle))
                .then(TickPhase.SYSTEMS, systems(vehicle))
                .then(TickPhase.PHYSICS, physics(vehicle))
                .then(TickPhase.FOLLOW_HULL, followHull(vehicle))
                .then(TickPhase.BOOKKEEPING, bookkeeping(vehicle));
        this.plan.check(false);
        this.steps = this.plan.steps();
        this.schedule = this.plan.schedule();
    }

    /** The steps of the tick in order with their phases; the type's own once its first tick built them. */
    List<ScheduledStep> schedule() {
        return this.schedule;
    }

    void tick() {
        boolean clientSide = this.vehicle.level().isClientSide;
        this.scriptedPilot.run(this.context, clientSide);
        // a parked vehicle skips everything until something wakes it
        if (this.rest.skipTick()) {
            return;
        }
        for (TickStep step : this.steps) {
            step.run(this.context, clientSide);
        }
        this.rest.afterTick();
    }

    /** The pilot's attitude report and keys, last tick's throttle, and a stopped vehicle's sync. */
    private static TickStep[] input(AircraftEntity v) {
        return new TickStep[]{
                TickStep.both("attitude: apply the pilot's report", () -> v.attitudeControl().startTick()),
                TickStep.both("history: start at the spawn point", () -> v.history().startAtSpawn(v.lifecycle().age())),
                TickStep.both("throttle: remember last tick's", v.engine()::rememberThrottle),
                TickStep.sided("controls: publish or mirror the keys", v.keys()::publish, () -> {
                    // the local pilot's keys are their own; everyone else's come from the server
                    if (LocalClient.get().player() != v.getRiddenByEntity()) {
                        v.keys().mirror();
                    }
                }),
                TickStep.server("motion: tell clients the vehicle stopped", v.motionSync()::announceStop)};
    }

    /** Riding a minecart, riders who just left, the wreck and the roll, before vanilla's base tick. */
    private static TickStep[] lifecycle(AircraftEntity v) {
        return new TickStep[]{
                TickStep.of("minecart: hop on, turn with it, hop off", v.carrier()),
                TickStep.both("crew: hold riders who just left", v.crewExit()::tickUnmountReserves),
                TickStep.both("wreck: set the crew on fire", v.wreck()::burnCrew),
                TickStep.sided("attitude: report or sync the roll", v.rollAngle()::publish,
                        () -> v.attitudeControl().reportToServer()),
                TickStep.both("attitude: remember the roll", () -> v.rollAngle().setPrevious(v.roll())),
                TickStep.of("wreck: drone fuel-out, despawn countdown", v.wreck())};
    }

    /** Vanilla's base tick and the vehicle's systems; the age is counted in the middle of them. */
    private static TickStep[] systems(AircraftEntity v) {
        return new TickStep[]{
                TickStep.both("vanilla base tick", v::baseTick),
                TickStep.of("collision: refresh ignored entities", v.collisionExclusions()),
                TickStep.of("uav: station link", v.uavLink()),
                TickStep.server("supply: fuel to nearby vehicles", v.fuel()::supplyNearby),
                TickStep.server("supply: ammo to nearby vehicles", v.ammoSupply()::supplyNearby),
                TickStep.of("fuel: burn and refill", v.fuel()),
                TickStep.of("repair: nearby vehicles", v.repairStation()),
                TickStep.both("parts: mode switch cooldown", v.parts()::tickCooldown),
                TickStep.both("crew: a pilot just boarded", v.pilotHandover()::tickPilotBoarded),
                TickStep.server("supplies: infinite ammo and fuel by config", v.lifecycle()::applyInfiniteSupplies),
                TickStep.both("age: count the tick", v.lifecycle()::countTick),
                TickStep.client("status: read the synced word", v::readCommonStatus),
                TickStep.both("falling: never take fall damage", () -> clearFallDistances(v)),
                TickStep.both("missile warning", v.countermeasures()::updateMissileWarning),
                TickStep.both("engine sound", v.sound()::play),
                TickStep.both("tow: forget a removed chain", v.tow()::forgetRemovedChain),
                TickStep.of("ammo: reload menu and reloads", v.ammoSupply()),
                TickStep.of("damage: invulnerability and self repair", v.damage()),
                TickStep.of("countermeasures: flares, chaff, APS, ECM", v.countermeasures()),
                TickStep.both("crew: remember where the pilot looks", v.aim()::rememberRiderLook),
                TickStep.client("camera: gimbals follow the gunner", v.cameras()::turnGimbals),
                TickStep.client("parts: wheels", v.runningGear()::animateWheels),
                TickStep.client("parts: crawler tracks", v.runningGear()::animateTracks),
                TickStep.both("parts: search light hatch", v.searchLight()::animateHatch),
                TickStep.server("crew: regeneration", v.seats()::regenerateCrew),
                TickStep.both("crew: the pilot left", v.pilotHandover()::tickPilotLeft)};
    }

    /**
     * How the hull was falling, then the vehicle type's own steps ({@link AircraftEntity#typeSteps}),
     * physics included. They only run once the type is known; the tick that loads it skips them.
     */
    private TickStep[] physics(AircraftEntity v) {
        return new TickStep[]{
                TickStep.both("damage: how it was falling", v.hullDamage()::beforePhysics),
                this.typeUpdate};
    }

    private void runTypeSteps(TickContext ctx, boolean clientSide) {
        if (!this.vehicle.identity().loadTypeIfMissing()) {
            return;
        }
        if (this.typeSteps == null) {
            // built on first use: the vehicle type's components do not exist yet while this pipeline is made
            List<TickStep> ownSteps = this.vehicle.typeSteps();
            TickPlan complete = this.plan.expand(this.typeUpdate, ownSteps);
            complete.check(true);
            this.schedule = complete.schedule();
            this.typeSteps = ownSteps;
        }
        for (TickStep step : this.typeSteps) {
            step.run(ctx, clientSide);
        }
    }

    /** Everything that follows the hull to where the physics put it. */
    private static TickStep[] followHull(AircraftEntity v) {
        return new TickStep[]{
                TickStep.of("parts: hit boxes follow the hull", v.partBoxes()),
                TickStep.of("parts: canopy, gear, hatch, nozzles", v.parts()),
                TickStep.both("recoil: rock the hull", () -> {
                    v.weapons().tickRecoil();
                    v.attitudeControl().rockWithRecoil();
                }),
                TickStep.server("damage: explode on a hard landing", v.hullDamage()::checkCrashLanding),
                TickStep.client("parts: spinning parts", v.runningGear()::animateSpinningParts),
                TickStep.both("effects: weapon smoke", () -> AircraftParticles.spawnWeaponSmoke(v)),
                TickStep.both("seats: place on the hull", () -> v.seats().place(v.getX(), v.getY(), v.getZ(), false)),
                TickStep.of("damage: ground scrape and flooding", v.hullDamage()),
                TickStep.of("crew: parachute drop", v.crewExit()),
                TickStep.of("crew: repelling ropes", v.ropes()),
                TickStep.both("crew: rack in reach", v.racks()::tickRideRackStatus)};
    }

    /** What the next tick compares against. */
    private static TickStep[] bookkeeping(AircraftEntity v) {
        return new TickStep[]{
                TickStep.both("bookkeeping: riders and position history", () -> {
                    v.carrier().rememberCarrier();
                    v.pilotHandover().rememberPilot();
                    v.history().record();
                }),
                TickStep.server("ownership: publish the lock rules in force", v.ownership()::publish),
                TickStep.both("attitude: end the step", () -> v.attitudeControl().endTick())};
    }

    /** Vehicles and their pilots never take fall damage; crashes are the hull damage's business. */
    private static void clearFallDistances(AircraftEntity vehicle) {
        vehicle.fallDistance = 0.0F;
        Entity pilot = vehicle.getRiddenByEntity();
        if (pilot != null) {
            pilot.fallDistance = 0.0F;
        }
    }
}
