package com.ivomens.mcheli.vehicle;

import com.ivomens.mcheli.core.sim.attitude.GroundVehicleAttitude;
import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.model.GroundVehicleHandling;
import com.ivomens.mcheli.core.sim.model.GroundVehicleModel;
import com.ivomens.mcheli.core.sim.propulsion.ThrottleLever;
import com.ivomens.mcheli.core.sim.propulsion.ThrottleModel;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftEntityTypes;
import com.ivomens.mcheli.aircraft.AircraftPhysics;
import com.ivomens.mcheli.aircraft.VehicleAttitude;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypeSteps;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.vehicle.component.FlightModeRules;
import com.ivomens.mcheli.vehicle.component.MotionSyncComponent;
import com.ivomens.mcheli.vehicle.component.TickStep;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

/** Emplaced guns and light ground vehicles; turrets aim in world space, the hull steers like a car. */
public class GroundVehicleEntity extends AircraftEntity {

    private static final GroundVehicleModel MODEL = new GroundVehicleModel();
    /** Spool-down per tick while nobody is aboard. */
    private static final double IDLE_SPOOL_DOWN = 0.00125;

    private final PlacedPositionHold placedHold = new PlacedPositionHold(this, () -> settings().carriage());
    private VehicleSpec groundVehicleSpec;
    /** Front wheel angle in -1..1 of full lock. */
    private float steerAngle;

    public GroundVehicleEntity(EntityType<? extends GroundVehicleEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.setDeltaMovement(Vec3.ZERO);
        this.weapons().createWeapons(0);
        this.aim().allowDetachedTurret();
        this.sound().setSound(new MotorSound(this));
        this.cameras().useLinearZoom();
        this.weapons().aimInWorldSpace();
        this.motionSync().smoothAs(MotionSyncComponent.Smoothing.GROUND);
        this.motionSync().predictWith(this::predictMovement);
        this.motionSync().blendOverExtraTicks(() -> 5);
        this.motionSync().driverSeesOwnVehicleSooner();
        this.modes().setRules(new FlightModeRules() {
            @Override
            public boolean allowsFreeLook() {
                return false;
            }

            @Override
            public boolean alwaysCameraView() {
                return true;
            }
        });
    }

    public GroundVehicleEntity(Level level) {
        this(AircraftEntityTypes.VEHICLE.get(), level);
    }

    @Override
    public VehicleKind kind() {
        return VehicleKind.VEHICLE;
    }

    @Nullable
    public VehicleSpec groundVehicleSpec() {
        return groundVehicleSpec;
    }

    @Override
    public void changeType(String type) {
        if (!type.isEmpty()) {
            groundVehicleSpec = VehicleTypes.get(VehicleKind.VEHICLE, type).orElse(null);
        }

        if (groundVehicleSpec == null) {
            ModLogger.log(this, "##### GroundVehicleEntity.changeType(): no definition for %d, %s, %s", getId(), type,
                    getName().getString());
            lifecycle().destroy(false);
            return;
        }

        setSpec(groundVehicleSpec);
        seats().reset(groundVehicleSpec.crew().seatsAndRacks());
        weapons().createWeapons(1 + seats().count());
        initPartRotation(getYRot(), getXRot());
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (groundVehicleSpec != null) {
            return;
        }

        groundVehicleSpec = VehicleTypes.get(VehicleKind.VEHICLE, identity().typeName()).orElse(null);
        if (groundVehicleSpec == null) {
            ModLogger.log(this, "##### GroundVehicleEntity.readAdditionalSaveData(): no definition for %d, %s",
                    getId(), getName().getString());
            lifecycle().destroy(false);
        } else {
            setSpec(groundVehicleSpec);
        }
    }

    @Nullable
    @Override
    public Item getItem() {
        return groundVehicleSpec != null ? VehicleTypes.item(groundVehicleSpec).orElse(null) : null;
    }

    @Override
    protected List<TickStep> typeSteps() {
        return List.of(
                TickStep.of("placed: hold where it was placed", this.placedHold),
                VehicleTypeSteps.askForWeaponState(this),
                TickStep.both("crew: a new driver looks ahead and picks a weapon", this::greetNewDriver),
                VehicleTypeSteps.weapons(this),
                VehicleTypeSteps.seats(this),
                TickStep.sided("throttle and drive", this::driveOnServer, this::followThrottleOnClient),
                VehicleTypeSteps.rememberLastPosition(this),
                TickStep.both("water: the nose sinks", () -> {
                    if (isInWater()) {
                        setXRot(getXRot() * 0.9F);
                    }
                }),
                VehicleTypeSteps.cameraViewers(this),
                TickStep.sided("physics: ground vehicle", this::stepPhysics, motionSync()::tick),
                VehicleTypeSteps.followCamera(this),
                VehicleTypeSteps.dropRemovedPilot(this));
    }

    private void greetNewDriver() {
        if (pilotHandover().pilotJustBoarded()) {
            Entity driver = getRiddenByEntity();
            driver.setXRot(0.0F);
            driver.xRotO = 0.0F;
            weapons().selectFirst(driver);
        }
    }

    /** Server: the keys drive the vehicle, and the throttle goes out to clients. */
    private void driveOnServer() {
        ThrottleLever lever = idleWithoutDriver();
        Entity rider = getRiddenByEntity();
        if (rider != null && !rider.isRemoved()
                && (this.groundVehicleSpec.file().kindFeatures().canMove()
                || this.groundVehicleSpec.file().kindFeatures().canRotate())) {
            drive();
        }
        ThrottleModel.clampAtZero(lever);
        engine().publishThrottle(lever.throttle());
        physics().commit(lever);
    }

    /** Client: the motion comes from the server; the throttle eases towards the synced one. */
    private void followThrottleOnClient() {
        ThrottleLever lever = idleWithoutDriver();
        ThrottleModel.clampAtZero(lever);
        if (!LocalClient.get().isLocalPlayer(getRiddenByEntity())) {
            ThrottleModel.followSynced(lever, engine().syncedThrottle());
        }
        physics().commit(lever);
    }

    private ThrottleLever idleWithoutDriver() {
        ThrottleLever lever = physics().throttleLever();
        Entity rider = getRiddenByEntity();
        if (rider == null || rider.isRemoved()) {
            ThrottleModel.idleDown(lever, IDLE_SPOOL_DOWN);
        }
        return lever;
    }

    /** Server: the keys push the vehicle and steer its front wheels; the throttle stays where it is. */
    private void drive() {
        Vec3 motion = getDeltaMovement();
        Vec3d velocity = new Vec3d(motion.x, motion.y, motion.z);
        GroundVehicleModel.Drive drive = MODEL.drive(velocity, getYRot(), steerAngle, physics().controls(false),
                physics().handling(GroundVehicleHandling.class));
        steerAngle = drive.wheelAngle();
        if (drive.yaw() != getYRot()) {
            setYRot(drive.yaw());
        }
        if (drive.velocity() != velocity) {
            setDeltaMovement(drive.velocity().x(), drive.velocity().y(), drive.velocity().z());
        }
    }

    /** Dead reckoning between server updates; the local driver's grip comes from the server. */
    private void predictMovement() {
        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        if (onGround() && !LocalClient.get().isLocalPlayer(getRiddenByEntity())) {
            VehicleMotion.applyGroundGrip(this, getYRot(), GroundVehicleModel.PREDICTED_GRIP);
        }
        if (isInWater()) {
            Vec3 inWater = getDeltaMovement();
            setDeltaMovement(inWater.x * 0.99, inWater.y, inWater.z * 0.99);
        }
    }

    private void stepPhysics() {
        AircraftPhysics physics = physics();
        MotionStep step = MODEL.step(physics.body(), physics.propulsion(0.0F), physics.controls(false),
                physics.handling(GroundVehicleHandling.class), physics.context());
        physics.apply(step);
        VehicleMotion.trampleSoftBlocks(this);
    }

    @Override
    protected VehicleAttitude<?, ?> createAttitude() {
        return new VehicleAttitude<>(this, new GroundVehicleAttitude(), physics()::stickHandling);
    }

}
