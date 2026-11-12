package com.ivomens.mcheli.helicopter;

import com.ivomens.mcheli.core.sim.attitude.RotorcraftAttitude;
import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.model.HelicopterModel;
import com.ivomens.mcheli.core.sim.model.RotorcraftHandling;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftEntityTypes;
import com.ivomens.mcheli.aircraft.AircraftParticles;
import com.ivomens.mcheli.aircraft.AircraftPhysics;
import com.ivomens.mcheli.aircraft.VehicleAttitude;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypeSteps;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.vehicle.VehicleMotion;
import com.ivomens.mcheli.vehicle.component.FlightModeRules;
import com.ivomens.mcheli.vehicle.component.SyncedValue;
import com.ivomens.mcheli.vehicle.component.TickStep;
import com.ivomens.mcheli.vehicle.component.VehicleNbtCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/** Helicopters: a rotorcraft flight model, rotors whose blades fold, and a collective throttle. */
public class HelicopterEntity extends AircraftEntity {

    private static final EntityDataAccessor<Byte> FOLD_STAT = SynchedEntityData.defineId(HelicopterEntity.class,
            EntityDataSerializers.BYTE);
    private static final HelicopterModel MODEL = new HelicopterModel();
    /** Gunner and hover mode need the helicopter within this many degrees of level. */
    private static final float MAX_MODE_SWITCH_TILT = 40.0F;

    private final RotorBladesComponent blades;
    private final RotorControls controls;
    private final RotorDamageSmoke damageSmoke = new RotorDamageSmoke(this);
    @Nullable
    private VehicleSpec helicopterSpec;

    public HelicopterEntity(EntityType<? extends HelicopterEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.setDeltaMovement(Vec3.ZERO);
        this.weapons().createWeapons(0);
        this.blades = new RotorBladesComponent(this, SyncedValue.serverOwned(this.entityData, FOLD_STAT,
                level.isClientSide), engine());
        if (level.isClientSide) {
            this.blades.waitForServerFoldState();
        }
        this.controls = new RotorControls(this, this.blades, engine(), modes(), physics(), parts(), pilotPreferences());
        this.sound().setSound(new RotorSound(this));
        this.carrier().onLoaded(this.blades::foldOnRack);
        this.wreck().setMotion(new RotorWreckSpin());
        this.parts().unfoldGearBelowThrottle(this::gearUnfoldThrottle);
        this.modes().setRules(new FlightModeRules() {
            @Override
            public boolean allowsGunnerMode() {
                return blades.canLift() && isNearlyLevel();
            }

            @Override
            public boolean allowsHovering() {
                return blades.canLift() && isNearlyLevel();
            }

            @Override
            public boolean hoverFreesPitch() {
                return true;
            }
        });
    }

    public HelicopterEntity(Level level) {
        this(AircraftEntityTypes.HELI.get(), level);
    }

    @Override
    public VehicleKind kind() {
        return VehicleKind.HELICOPTER;
    }

    @Nullable
    public VehicleSpec helicopterSpec() {
        return this.helicopterSpec;
    }

    public RotorBladesComponent blades() {
        return this.blades;
    }

    @Override
    public void changeType(String type) {
        if (!type.isEmpty()) {
            this.helicopterSpec = VehicleTypes.get(VehicleKind.HELICOPTER, type).orElse(null);
        }
        if (this.helicopterSpec == null) {
            ModLogger.log(this, "##### HelicopterEntity.changeType(): no definition for %d, %s, %s", getId(), type,
                    getName().getString());
            lifecycle().destroy(true);
            return;
        }
        setSpec(this.helicopterSpec);
        seats().reset(this.helicopterSpec.crew().seatsAndRacks());
        this.blades.build(this.helicopterSpec);
        weapons().createWeapons(1 + seats().count());
        initPartRotation(getYRot(), getXRot());
    }

    @Nullable
    @Override
    public Item getItem() {
        return this.helicopterSpec != null ? VehicleTypes.item(this.helicopterSpec).orElse(null) : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FOLD_STAT, RotorBladesComponent.UNFOLDED);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        this.blades.save(nbt);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        boolean wasFolded = this.blades.state() == RotorBladesComponent.FOLDED;
        // very old saves used helicopter-specific keys
        if (identity().uniqueId().isEmpty()) {
            identity().setUniqueId(nbt.getString(VehicleNbtCodec.LEGACY_HELI_UNIQUE_ID));
        }
        if (identity().typeName().isEmpty()) {
            identity().setTypeName(nbt.getString(VehicleNbtCodec.LEGACY_HELI_TYPE));
        }
        this.blades.load(nbt);
        if (this.helicopterSpec == null) {
            this.helicopterSpec = VehicleTypes.get(VehicleKind.HELICOPTER, identity().typeName()).orElse(null);
            if (this.helicopterSpec == null) {
                ModLogger.log(this, "##### HelicopterEntity.readAdditionalSaveData(): no definition for %d, %s",
                        getId(), getName().getString());
                lifecycle().destroy(true);
            } else {
                setSpec(this.helicopterSpec);
            }
        }
        this.blades.settleAfterLoad(wasFolded);
    }

    @Override
    protected List<TickStep> typeSteps() {
        return List.of(
                TickStep.client("rotor: start folded if the server's is", this.blades::startFoldedIfServerIs),
                VehicleTypeSteps.askForWeaponState(this),
                VehicleTypeSteps.selectWeaponForNewPilot(this),
                VehicleTypeSteps.weapons(this),
                VehicleTypeSteps.seats(this),
                TickStep.of("rotor: collective, strafing and spin", this.controls),
                TickStep.of("rotor: fold and unfold the blades", this.blades),
                VehicleTypeSteps.rememberLastPosition(this),
                TickStep.both("hover: level the nose", this::levelNoseWhileHovering),
                VehicleTypeSteps.spoolDownWreck(this),
                VehicleTypeSteps.cameraViewers(this),
                TickStep.sided("physics: rotorcraft", this::stepPhysics, motionSync()::tick),
                TickStep.client("wreck: spin", wreck()::tickMotion),
                TickStep.client("effects: rotor wash and smoke", () -> {
                    AircraftParticles.spawnGroundCloud(this, false);
                    this.damageSmoke.tick();
                }),
                VehicleTypeSteps.followCamera(this),
                VehicleTypeSteps.dropRemovedPilot(this));
    }

    private void levelNoseWhileHovering() {
        if (!isDestroyed() && modes().isHovering() && Mth.abs(getXRot()) < 70.0F) {
            setXRot(getXRot() * 0.95F);
        }
    }

    private void stepPhysics() {
        AircraftPhysics physics = physics();
        MotionStep step = MODEL.step(physics.body(), physics.propulsion(0.0F), physics.controls(modes().isHovering()),
                physics.handling(RotorcraftHandling.class), physics.context());
        physics.apply(step);
        body().turnTo(getYRot(), getXRot());
        VehicleMotion.trampleSoftBlocks(this);
    }

    @Override
    protected VehicleAttitude<?, ?> createAttitude() {
        return new VehicleAttitude<>(this, new RotorcraftAttitude(), this::attitudeSetup);
    }

    private RotorcraftAttitude.Setup attitudeSetup() {
        return new RotorcraftAttitude.Setup(physics().stickHandling(), spec().file().physics().onGroundPitch(),
                modes().isHovering(), this.blades.areStowed());
    }

    private boolean isNearlyLevel() {
        return Mth.abs(Mth.wrapDegrees(roll())) < MAX_MODE_SWITCH_TILT
                && Mth.abs(Mth.wrapDegrees(getXRot())) < MAX_MODE_SWITCH_TILT;
    }

    /** Slow helicopters unfold the gear at a high rotor speed, fast ones only once nearly idle. */
    private double gearUnfoldThrottle() {
        double dx = getX() - this.xo;
        double dy = getY() - this.yo;
        double dz = getZ() - this.zo;
        float threshold = spec().speed() / 3.5F;
        return dx * dx + dy * dy + dz * dz <= threshold ? 0.8F : 0.3F;
    }
}
