package com.ivomens.mcheli.tank;

import com.ivomens.mcheli.core.sim.attitude.GroundAttitude;
import com.ivomens.mcheli.core.sim.attitude.GroundTurning;
import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.LegacyMath;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.model.TankHandling;
import com.ivomens.mcheli.core.sim.model.TankModel;
import com.ivomens.mcheli.core.sim.model.TrackedModel;
import com.ivomens.mcheli.core.sim.model.WheeledModel;
import com.ivomens.mcheli.core.sim.port.Collider;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftEntityTypes;
import com.ivomens.mcheli.aircraft.AircraftParticles;
import com.ivomens.mcheli.aircraft.AircraftPhysics;
import com.ivomens.mcheli.aircraft.PartBoundingBox;
import com.ivomens.mcheli.aircraft.RiderLook;
import com.ivomens.mcheli.aircraft.VehicleAttitude;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypeSteps;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.entity.McheliEntity;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.vehicle.VehicleMotion;
import com.ivomens.mcheli.vehicle.WaterSplashParticles;
import com.ivomens.mcheli.vehicle.component.FlightModeRules;
import com.ivomens.mcheli.vehicle.component.PropellerComponent;
import com.ivomens.mcheli.vehicle.component.TickStep;
import com.ivomens.mcheli.vehicle.world.EntityCollider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

/** Tracked and wheeled ground vehicles: hull-steered, with the mouse free to aim the turret. */
public class TankEntity extends AircraftEntity {

    /** {@link VehicleSpec#weightType} values. */
    private static final int WEIGHT_CAR = 1;
    private static final int WEIGHT_TANK = 2;

    private static final TankModel TRACKED = new TrackedModel();
    private static final TankModel WHEELED = new WheeledModel();
    /** Unmanned tanks settle onto the slope once per tick. */
    private static final float TICK_SECONDS = 0.05F;

    /** Suspension probes that follow the terrain and give the hull its pitch and roll. */
    private final WheelManager wheels;
    private final TankThrottle throttle = new TankThrottle(this, engine(), physics(), modes(), keys(), parts(),
            pilotPreferences());
    private final PropellerComponent propeller = new PropellerComponent(this, engine());
    private final TankRamming ramming = new TankRamming(this);
    private final TankDamageEffects damageEffects = new TankDamageEffects(this);
    @Nullable
    private VehicleSpec tankSpec;

    public TankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.setDeltaMovement(Vec3.ZERO);
        this.weapons().createWeapons(0);
        this.weapons().clampAimToTurret();
        this.aim().allowDetachedTurret();
        this.sound().setSound(new TrackSound(this));
        this.wheels = new WheelManager(this, RandomSource.create());
        this.keys().fitBrake();
        this.hullDamage().setMaxSafeTilt(91.0F);
        this.parts().unfoldGearBelowThrottle(() -> 0.7F);
        this.pilotBoarding().onBoard(this::lookWhereTheLastDriverLooked);
        // a new driver starts with a fresh step-time average
        this.pilotHandover().onPilotFirstTick(() -> attitudeControl().resetMemory());
        this.motionSync().predictWith(this::predictMovement);
        // light vehicles are blended in over fewer ticks so they react sooner
        this.motionSync().blendOverExtraTicks(() -> isCar() ? 2 : 5);
        this.motionSync().driverSeesOwnVehicleSooner();
        this.modes().setRules(new FlightModeRules() {
            @Override
            public boolean allowsGunnerMode() {
                return false;
            }
        });
    }

    public TankEntity(Level level) {
        this(AircraftEntityTypes.TANK.get(), level);
    }

    @Override
    public VehicleKind kind() {
        return VehicleKind.TANK;
    }

    @Nullable
    public VehicleSpec tankSpec() {
        return this.tankSpec;
    }

    public WheelManager wheels() {
        return this.wheels;
    }

    public PropellerComponent propeller() {
        return this.propeller;
    }

    private boolean isCar() {
        return this.tankSpec.file().kindFeatures().weightType() == WEIGHT_CAR;
    }

    /** Heavy tanks shrug off what they run over; lighter vehicles take some of the damage back. */
    boolean isHeavy() {
        return this.tankSpec.file().kindFeatures().weightType() == WEIGHT_TANK;
    }

    @Override
    public void changeType(String type) {
        if (!type.isEmpty()) {
            this.tankSpec = VehicleTypes.get(VehicleKind.TANK, type).orElse(null);
        }
        if (this.tankSpec == null) {
            ModLogger.log(this, "##### TankEntity.changeType(): no definition for %d, %s, %s", getId(), type,
                    getName().getString());
            lifecycle().destroy(false);
            return;
        }
        setSpec(this.tankSpec);
        seats().reset(this.tankSpec.crew().seatsAndRacks());
        modes().predictFreeLook(this.tankSpec.file().camera().defaultFreeLook());
        weapons().createWeapons(1 + seats().count());
        initPartRotation(getYRot(), getXRot());
        this.wheels.createWheels(level(), this.tankSpec.hitBoxes().wheels(),
                new Vec3(0.0, -McheliEntity.GLOBAL_Y_OFFSET, this.tankSpec.file().kindFeatures().weightedCenterZ()));
    }

    @Nullable
    @Override
    public Item getItem() {
        return this.tankSpec != null ? VehicleTypes.item(this.tankSpec).orElse(null) : null;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (this.tankSpec != null) {
            return;
        }
        this.tankSpec = VehicleTypes.get(VehicleKind.TANK, identity().typeName()).orElse(null);
        if (this.tankSpec == null) {
            ModLogger.log(this, "##### TankEntity.readAdditionalSaveData(): no definition for %d, %s", getId(),
                    getName().getString());
            lifecycle().destroy(false);
        } else {
            setSpec(this.tankSpec);
        }
    }

    /** The driver boards looking where the previous driver looked, so the turret does not swing around. */
    private void lookWhereTheLastDriverLooked(Player player) {
        float yaw = aim().riderYaw();
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setXRot(aim().riderPitch());
    }

    @Override
    protected List<TickStep> typeSteps() {
        return List.of(
                VehicleTypeSteps.askForWeaponState(this),
                VehicleTypeSteps.selectWeaponForNewPilot(this),
                VehicleTypeSteps.weapons(this),
                VehicleTypeSteps.seats(this),
                TickStep.of("throttle: drive, reverse and brake", this.throttle),
                TickStep.of("propeller: spin with the throttle", this.propeller),
                VehicleTypeSteps.rememberLastPosition(this),
                VehicleTypeSteps.spoolDownWreck(this),
                VehicleTypeSteps.cameraViewers(this),
                TickStep.server("physics: settle an empty tank on the slope", this::followTerrainUnmanned),
                TickStep.sided("physics: tracks or wheels", this::stepPhysics, motionSync()::tick),
                TickStep.server("ramming: blocks and entities in the way", this.ramming::tick),
                TickStep.client("effects: wheels, damage, sound, splashes and dust", this::tickEffects),
                VehicleTypeSteps.followCamera(this),
                VehicleTypeSteps.dropRemovedPilot(this));
    }

    private void tickEffects() {
        updateWheels();
        this.damageEffects.tick();
        sound().tickFade();
        this.wheels.particleLandingGear();
        WaterSplashParticles.spawn(this);
        AircraftParticles.spawnGroundCloud(this, true);
    }

    @Override
    public boolean displayFireAnimation() {
        return isDestroyed() || super.displayFireAnimation();
    }

    /** The wheels follow the planned motion right before the hull moves. */
    @Override
    protected Collider createCollider() {
        return new EntityCollider(this, this::updateWheels);
    }

    /**
     * The tank moves with the shared vehicle collision code; its hull boxes are brought up to date
     * first so the step-up and block-breaking checks see the current hull.
     */
    @Override
    public void move(@NotNull MoverType type, @NotNull Vec3 movement) {
        for (PartBoundingBox box : partBoxes().boxes()) {
            box.updatePosition(getX(), getY(), getZ(), getYRot(), getXRot(), roll());
        }
        super.move(type, movement);
    }

    @Override
    protected VehicleAttitude<?, ?> createAttitude() {
        return new VehicleAttitude<>(this, new GroundAttitude(), this::attitudeSetup).turnRiderWith(this::aimTurret);
    }

    GroundAttitude.Setup attitudeSetup() {
        VehicleSpec info = spec();
        Vec3 motion = getDeltaMovement();
        GroundAttitude.Motion hull = new GroundAttitude.Motion(new Vec3d(motion.x, motion.y, motion.z),
                horizontalMoveSqr(), engine().throttle(), physics().reverseThrust());
        return new GroundAttitude.Setup(isCar(),
                new GroundTurning(info.file().physics().mobility().yawOnGround(),
                info.file().physics().canRotOnGround()),
                info.file().physics().pivotTurnThrottle(), info.file().physics().canFloat(),
                        info.file().physics().rotationLimits().limited(), info.file().physics().rotationSnap(),
                        modes().isGunnerMode(),
                hull, new GroundAttitude.Slope(this.wheels.targetPitch(), this.wheels.targetRoll()), weapons().recoil());
    }

    private double horizontalMoveSqr() {
        double dx = getX() - this.xo;
        double dz = getZ() - this.zo;
        return dx * dx + dz * dz;
    }

    /** Dead reckoning between server updates. */
    private void predictMovement() {
        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        if (!isDestroyed() && (onGround() || !VehicleMotion.isClearBelow(this, 1, 2))) {
            VehicleMotion.applyGroundGrip(this, getYRot(),
                    isCar() ? WheeledModel.PREDICTED_GRIP : TrackedModel.PREDICTED_GRIP);
        }
        if (isInWater()) {
            Vec3 inWater = getDeltaMovement();
            setDeltaMovement(inWater.x * 0.99, inWater.y, inWater.z * 0.99);
        }
    }

    private void followTerrainUnmanned() {
        if (McheliConfig.flight().serverTerrainFollow() && getRiddenByEntity() == null && !isDestroyed()) {
            Attitude settled = GroundAttitude.followTerrainUnmanned(body().attitude(), attitudeSetup(),
                    attitudeControl().terrain(), TICK_SECONDS);
            setXRot(settled.pitch());
            rollAngle().set(settled.roll());
        }
    }

    private void stepPhysics() {
        AircraftPhysics physics = physics();
        TankModel model = isCar() ? WHEELED : TRACKED;
        MotionStep step = model.step(physics.body(), physics.propulsion(0.0F), physics.controls(modes().isGunnerMode()),
                physics.handling(TankHandling.class), physics.context());
        physics.apply(step);
        body().turnTo(getYRot(), getXRot());
        VehicleMotion.trampleSoftBlocks(this);
    }

    private void updateWheels() {
        Vec3 motion = getDeltaMovement();
        this.wheels.move(motion.x, motion.y, motion.z);
    }

    /** Turns the player's view (and so the turret) with the mouse, limited to the weapon's traverse speed. */
    private void aimTurret(Entity player, RiderLook look) {
        float deltaLimit = spec().file().camera().rotationSpeed() * 5;
        WeaponGroup weapon = weapons().current(player);
        if (weapon != null && weapon.spec() != null) {
            deltaLimit *= weapon.spec().camera().rotationSpeedPitch();
        }
        // unlike other vehicles the seat's yaw and pitch offsets apply even when the seat is not fixed
        if (!modes().viewFollowsYaw() && !look.fixed()) {
            player.turn(Mth.clamp(look.mouseX(), -deltaLimit, deltaLimit), 0.0F);
        } else {
            RiderLook.followYaw(this, player, look.fixedYaw());
        }
        if (!modes().viewFollowsPitch() && !look.fixed()) {
            player.turn(0.0F, Mth.clamp(look.mouseY(), -deltaLimit, deltaLimit));
        } else {
            player.xRotO = getXRot() + look.fixedPitch();
            player.setXRot(getXRot() + look.fixedPitch());
        }

        // the hull's pitch as seen along the player's view direction
        float relativeYaw = Mth.wrapDegrees(getYRot() - player.getYRot());
        float hullPitch = getXRot() * Mth.cos((float) (relativeYaw * Math.PI / 180.0))
                - roll() * Mth.sin((float) (relativeYaw * Math.PI / 180.0));
        if (LocalClient.get().isFirstPerson()) {
            float limited = LegacyMath.limit(player.getXRot(), hullPitch
                    + spec().file().physics().rotationLimits().minPitch(),
                    hullPitch + spec().file().physics().rotationLimits().maxPitch());
            player.setXRot(LegacyMath.limit(limited, -90.0F, 90.0F));
        }
        player.xRotO = player.getXRot();
    }

    /** Blocks per second, over the ground. */
    @Override
    public double getCurrentSpeed() {
        return getDeltaMovement().horizontalDistance() * 20.0;
    }
}
