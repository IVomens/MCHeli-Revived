package com.ivomens.mcheli.plane;

import com.ivomens.mcheli.content.def.vehicle.VehicleKinds;
import com.ivomens.mcheli.core.sim.attitude.FixedWingAttitude;
import com.ivomens.mcheli.core.sim.attitude.GroundTurning;
import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.model.FixedWingHandling;
import com.ivomens.mcheli.core.sim.model.FixedWingModel;
import com.ivomens.mcheli.core.sim.model.TargetDroneAutopilot;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftParticles;
import com.ivomens.mcheli.aircraft.AircraftPhysics;
import com.ivomens.mcheli.aircraft.VehicleAttitude;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypeSteps;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.vehicle.VehicleMotion;
import com.ivomens.mcheli.vehicle.WaterSplashParticles;
import com.ivomens.mcheli.vehicle.component.FlightModeRules;
import com.ivomens.mcheli.vehicle.component.PropellerComponent;
import com.ivomens.mcheli.vehicle.component.TickStep;
import com.ivomens.mcheli.vehicle.definition.VehicleDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Flight model shared by planes and ships, which use the same definition keys: VTOL nozzles, folding
 * or swept wings and tilting rotors.
 *
 * @param <I> the definition type
 */
public abstract class AbstractPlaneEntity extends AircraftEntity {

    private static final FixedWingModel MODEL = new FixedWingModel();
    private static final float MAX_GUNNER_MODE_TILT = 40.0F;
    private static final float MIN_GUNNER_MODE_THROTTLE = 0.6F;

    private final VtolNozzles nozzles = new VtolNozzles(this, parts());
    private final FoldingWings wings = new FoldingWings(this, parts(), engine(), physics());
    private final FixedWingThrottle throttle = new FixedWingThrottle(this, engine(), physics(), modes(), keys(),
            parts(), pilotPreferences(), this.wings);
    private final PropellerComponent propeller = new PropellerComponent(this, engine());
    private final FixedWingEffects effects = new FixedWingEffects(this);
    @Nullable
    private VehicleSpec planeSpec;

    protected AbstractPlaneEntity(EntityType<? extends AbstractPlaneEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.setDeltaMovement(Vec3.ZERO);
        this.weapons().createWeapons(0);
        this.sound().setSound(new JetSound(this));
        this.pilotBoarding().onBoard(player -> attitudeControl().resetMemory());
        this.wreck().setMotion(new FixedWingWreckTumble());
        this.ejectionSeats().fit();
        this.parts().unfoldGearBelowThrottle(() -> 0.7F);
        this.parts().followVtolMode(this.nozzles::mode);
        this.parts().afterServerAnimation(this.wings::unsweepWhenAccelerating);
        this.modes().setRules(new FlightModeRules() {
            @Override
            public boolean allowsGunnerMode() {
                return canFlyHandsOff();
            }

            @Override
            public boolean hoverFreesPitch() {
                return true;
            }

            @Override
            public boolean hoverFreesYaw() {
                return true;
            }
        });
    }

    /** Looks up the definition registered under {@code typeName}, or null if there is none. */
    @Nullable
    protected abstract VehicleSpec findSpec(String typeName);

    @Nullable
    public VehicleSpec planeSpec() {
        return this.planeSpec;
    }

    public VtolNozzles nozzles() {
        return this.nozzles;
    }

    public FoldingWings wings() {
        return this.wings;
    }

    public PropellerComponent propeller() {
        return this.propeller;
    }

    @Override
    public void changeType(String type) {
        if (!type.isEmpty()) {
            this.planeSpec = findSpec(type);
        }
        if (this.planeSpec == null) {
            ModLogger.log(this, "##### %s.changeType(): no definition for %d, %s, %s", VehicleKinds.legacyEntityType(kind()), getId(), type,
                    getName().getString());
            lifecycle().destroy(false);
            return;
        }
        setSpec(this.planeSpec);
        seats().reset(spec().crew().seatsAndRacks());
        this.nozzles.build(this.planeSpec);
        this.wings.build(this.planeSpec);
        weapons().createWeapons(1 + seats().count());
        initPartRotation(getYRot(), getXRot());
    }

    @Nullable
    @Override
    public Item getItem() {
        return this.planeSpec != null ? VehicleTypes.item(this.planeSpec).orElse(null) : null;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (this.planeSpec != null) {
            return;
        }
        this.planeSpec = findSpec(identity().typeName());
        if (this.planeSpec == null) {
            ModLogger.log(this, "##### %s.readAdditionalSaveData(): no definition for %d, %s", VehicleKinds.legacyEntityType(kind()),
                    getId(), getName().getString());
            lifecycle().destroy(false);
        } else {
            setSpec(this.planeSpec);
        }
    }

    /** Gunner mode flies on autopilot, so it needs level, powered flight with some room below. */
    private boolean canFlyHandsOff() {
        float roll = Mth.abs(Mth.wrapDegrees(roll()));
        float pitch = Mth.abs(Mth.wrapDegrees(getXRot()));
        return roll <= MAX_GUNNER_MODE_TILT && pitch <= MAX_GUNNER_MODE_TILT
                && engine().throttle() > MIN_GUNNER_MODE_THROTTLE && VehicleMotion.isClearBelow(this, 3, 5);
    }

    @Override
    protected List<TickStep> typeSteps() {
        return List.of(
                VehicleTypeSteps.askForWeaponState(this),
                VehicleTypeSteps.selectWeaponForNewPilot(this),
                VehicleTypeSteps.weapons(this),
                VehicleTypeSteps.seats(this),
                TickStep.of("throttle: thrust and reverse", this.throttle),
                TickStep.of("propeller: spin with the throttle", this.propeller),
                TickStep.both("nozzles: back to VTOL once landed", this.nozzles::restOnNozzlesWhenLanded),
                VehicleTypeSteps.rememberLastPosition(this),
                TickStep.both("hover: level the nose", this::levelNoseWhileHovering),
                VehicleTypeSteps.spoolDownWreck(this),
                VehicleTypeSteps.cameraViewers(this),
                TickStep.sided("physics: fixed wing", this::stepPhysics, motionSync()::tick),
                TickStep.client("wreck: tumble", wreck()::tickMotion),
                TickStep.client("effects: sound, dust, exhaust, smoke and spray", this::tickEffects),
                VehicleTypeSteps.followCamera(this),
                VehicleTypeSteps.dropRemovedPilot(this));
    }

    private void levelNoseWhileHovering() {
        if (!isDestroyed() && modes().isHovering() && Mth.abs(getXRot()) < 70.0F) {
            setXRot(getXRot() * 0.95F);
        }
    }

    private void tickEffects() {
        sound().tickFade();
        this.effects.spawnTyreDust();
        this.effects.spawnNozzleExhaust();
        this.effects.spawnDamageSmoke();
        WaterSplashParticles.spawn(this);
        AircraftParticles.spawnGroundCloud(this, true);
    }

    @Override
    protected VehicleAttitude<?, ?> createAttitude() {
        // the exhaust animation follows the side keys at frame rate, outside the attitude model
        return new VehicleAttitude<>(this, new FixedWingAttitude(), this::attitudeSetup)
                .afterEachFrame(frameSeconds -> {
                    if (!isDestroyed()) {
                        animateWithKeys((float) frameSeconds);
                    }
                });
    }

    private FixedWingAttitude.Setup attitudeSetup() {
        VehicleSpec info = spec();
        FixedWingAttitude.Vtol vtol = new FixedWingAttitude.Vtol(this.nozzles.mode(),
                this.planeSpec.file().kindFeatures().vtol().yaw(),
                this.planeSpec.file().kindFeatures().vtol().pitch(), this.nozzles.angle());
        return new FixedWingAttitude.Setup(physics().stickHandling(), info.file().physics().onGroundPitch(),
                new GroundTurning(info.file().physics().mobility().yawOnGround(),
                        info.file().physics().canRotOnGround()), info.file().physics().mobility().roll(),
                        info.file().physics().rotationSnap(),
                info.file().physics().canFloat(), vtol, modes().isGunnerMode(), modes().isHovering());
    }

    /** What a plane type animates from the side keys every frame; nothing by default. */
    protected void animateWithKeys(float frameSeconds) {
    }

    private void stepPhysics() {
        AircraftPhysics physics = physics();
        boolean holdAltitude = modes().isGunnerMode() | flyTargetDrone();
        MotionStep step = MODEL.step(physics.body(), physics.propulsion(this.nozzles.angle()),
                physics.controls(holdAltitude), handling(), physics.context());
        physics.apply(step);
        body().turnTo(getYRot(), getXRot());
        VehicleMotion.trampleSoftBlocks(this);
    }

    /**
     * Target drones steer themselves while they have fuel and are not afloat.
     *
     * @return true while the drone cruises and holds its altitude
     */
    private boolean flyTargetDrone() {
        VehicleDefinition.TargetDroneDef drone = definition().targetDrone();
        if (drone == null || !engine().canRun() || isDestroyed() || canFloatWater() && getWaterDepth() != 0.0) {
            return false;
        }
        TargetDroneAutopilot.Command command = TargetDroneAutopilot.steer(body().attitude(), drone.circleRate(),
                physics().terrain());
        setYRot(command.attitude().yaw());
        setXRot(command.attitude().pitch());
        if (command.retractGear() && parts().canFoldLandingGear()) {
            parts().foldLandingGear();
        }
        return command.holdAltitude();
    }

    /** The definition's handling; only a swept wing changes it in flight. */
    private FixedWingHandling handling() {
        FixedWingHandling handling = physics().handling(FixedWingHandling.class);
        return definition().wingSweep() == null ? handling : handling.withMaxSpeed(this.wings.maxSpeed());
    }
}
