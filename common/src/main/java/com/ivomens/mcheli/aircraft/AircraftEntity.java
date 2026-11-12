package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.api.vehicle.Targetable;
import com.ivomens.mcheli.combat.weapon.ArmedVehicle;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.entity.SinglePassenger;
import com.ivomens.mcheli.networking.SpawnDataEntity;
import com.ivomens.mcheli.core.sim.port.Collider;
import com.ivomens.mcheli.util.EntityIds;
import com.ivomens.mcheli.vehicle.component.AmmoSupplyComponent;
import com.ivomens.mcheli.vehicle.component.AppearanceComponent;
import com.ivomens.mcheli.vehicle.component.BoardingComponent;
import com.ivomens.mcheli.vehicle.component.BodyComponent;
import com.ivomens.mcheli.vehicle.component.CameraComponent;
import com.ivomens.mcheli.vehicle.component.CarrierComponent;
import com.ivomens.mcheli.vehicle.component.CollisionExclusions;
import com.ivomens.mcheli.vehicle.component.ControlKeysComponent;
import com.ivomens.mcheli.vehicle.component.CountermeasureComponent;
import com.ivomens.mcheli.vehicle.component.CrewAimComponent;
import com.ivomens.mcheli.vehicle.component.CrewExitComponent;
import com.ivomens.mcheli.vehicle.component.DamageComponent;
import com.ivomens.mcheli.vehicle.component.EjectionSeats;
import com.ivomens.mcheli.vehicle.component.EngineComponent;
import com.ivomens.mcheli.vehicle.component.EngineSoundComponent;
import com.ivomens.mcheli.vehicle.component.FlightModesComponent;
import com.ivomens.mcheli.vehicle.component.FuelComponent;
import com.ivomens.mcheli.vehicle.component.HullDamageComponent;
import com.ivomens.mcheli.vehicle.component.IdentityComponent;
import com.ivomens.mcheli.vehicle.component.LifecycleComponent;
import com.ivomens.mcheli.vehicle.component.MotionSyncComponent;
import com.ivomens.mcheli.vehicle.component.OwnershipComponent;
import com.ivomens.mcheli.vehicle.component.PartBoxesComponent;
import com.ivomens.mcheli.vehicle.component.PartsComponent;
import com.ivomens.mcheli.vehicle.component.PilotBoarding;
import com.ivomens.mcheli.vehicle.component.PilotHandover;
import com.ivomens.mcheli.vehicle.component.PilotPreferences;
import com.ivomens.mcheli.vehicle.component.PositionHistory;
import com.ivomens.mcheli.vehicle.component.RackComponent;
import com.ivomens.mcheli.vehicle.component.RadarComponent;
import com.ivomens.mcheli.vehicle.component.RepairComponent;
import com.ivomens.mcheli.vehicle.component.RepellingRopes;
import com.ivomens.mcheli.vehicle.component.RestComponent;
import com.ivomens.mcheli.vehicle.component.RollComponent;
import com.ivomens.mcheli.vehicle.component.RunningGearComponent;
import com.ivomens.mcheli.vehicle.component.ScheduledStep;
import com.ivomens.mcheli.vehicle.component.ScriptedPilotComponent;
import com.ivomens.mcheli.vehicle.component.SearchLightComponent;
import com.ivomens.mcheli.vehicle.component.SeatChanges;
import com.ivomens.mcheli.vehicle.component.SeatsComponent;
import com.ivomens.mcheli.vehicle.component.StationShutdown;
import com.ivomens.mcheli.vehicle.component.StatusWord;
import com.ivomens.mcheli.vehicle.component.TickStep;
import com.ivomens.mcheli.vehicle.component.TowComponent;
import com.ivomens.mcheli.vehicle.component.UavLinkComponent;
import com.ivomens.mcheli.vehicle.component.WeaponComponent;
import com.ivomens.mcheli.vehicle.component.WreckComponent;
import com.ivomens.mcheli.vehicle.definition.VehicleDefinition;
import com.ivomens.mcheli.vehicle.settings.VehicleSettingsHolder;
import com.ivomens.mcheli.vehicle.settings.VehicleSettingsSnapshot;
import com.ivomens.mcheli.vehicle.world.EntityCollider;
import com.ivomens.mcheli.vehicle.world.LevelTerrainProbe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * Base class of every player-driven vehicle (helicopter, plane, tank, vehicle, ship): the components it is
 * made of, the type it is made from, and what other features see of it ({@link ArmedVehicle}).
 * <p>
 * Each component owns one part of the vehicle's state and is reached through its accessor, e.g.
 * {@code vehicle.fuel()}. {@link VehicleTickPipeline} ticks them in a fixed order, and each vehicle type
 * plugs in its own physics and controls ({@link #typeSteps}). How the vehicle behaves as an entity of the
 * game is {@link VehicleEntity}'s business.
 * <p>
 * Crew: the pilot rides the vehicle itself; every other crew member rides a {@link SeatEntity} which the
 * vehicle positions each tick.
 */
public abstract class AircraftEntity extends VehicleEntity implements ArmedVehicle, IRideableAboard,
        SpawnDataEntity, SinglePassenger {

    /** Every component of the vehicle, wired in dependency order; see {@link AircraftComponents}. */
    private final AircraftComponents components;
    private final VehicleTickPipeline tickPipeline;
    /** Where this vehicle's settings come from: the published snapshot, unless a test pinned its own. */
    private Supplier<VehicleSettingsSnapshot> settingsSource = VehicleSettingsHolder::current;
    private VehicleAttitude<?, ?> attitudeControl;
    private VehicleSpec spec;
    /** This type's frozen definition, set with {@link #spec}; a reload swaps the object, never its contents. */
    @Nullable
    private VehicleDefinition definition;

    public AircraftEntity(EntityType<? extends AircraftEntity> type, Level world) {
        super(type, world);
        this.components = new AircraftComponents(this, new AircraftCamera(world, this, this.getX(), this.getY(),
                this.getZ()), VehicleClientHooks.INSTANCE.engineSound(this), this::settings);
        this.setSpec(null);
        this.setDropContentsWhenDead(false);
        this.setRenderDistanceWeight(2.0 * McheliConfig.render().renderDistanceWeight());
        this.tickPipeline = new VehicleTickPipeline(this, this.components);
        this.setBoundingBox(this.makeBoundingBox());
        this.blocksBuilding = true;
        if (!world.isClientSide) {
            this.components.lifecycle().applyInfiniteSupplies();
            this.components.seats().setGunnersAllowed(true);
        }
    }

    /** The settings this vehicle follows now; the vehicle kinds read them here to hand them to components. */
    protected final VehicleSettingsSnapshot settings() {
        return this.settingsSource.get();
    }

    /** Test harnesses: pins the vehicle to fixed settings, whatever the config says; call before its first tick. */
    public void pinSettings(VehicleSettingsSnapshot pinned) {
        this.settingsSource = () -> pinned;
    }

    // What the vehicle is

    /** The content type this vehicle is made from. */
    public abstract VehicleKind kind();

    /** Makes the vehicle one of the named type of its kind; see {@link IdentityComponent#setTypeName}. */
    public abstract void changeType(String typeName);

    @Override
    @Nullable
    public VehicleSpec spec() {
        return this.spec;
    }

    @Override
    @Nullable
    public VehicleDefinition definition() {
        return this.definition;
    }

    /** Takes the type's spec and rebuilds the parts, wheels and hit boxes it describes. */
    public void setSpec(@Nullable VehicleSpec info) {
        this.spec = info;
        this.definition = info != null ? info.definition() : null;
        if (info != null) {
            this.components.parts().rebuild(info);
            this.components.runningGear().rebuild(info);
            this.components.partBoxes().replaceFrom(info);
            this.components.body().updateCullingRadius();
            this.setInventorySize((short) info.file().features().inventorySize());
        }
    }

    /** The item the vehicle is picked up as, or null before the type is known. */
    @Nullable
    public abstract Item getItem();

    @Override
    public boolean isUAV() {
        return this.spec() != null && this.spec().isUAV();
    }

    public boolean isTargetDrone() {
        return this.spec() != null && this.spec().file().uav().targetDrone();
    }

    @Override
    public boolean isDestroyed() {
        return this.components.wreck().isWreck();
    }

    public boolean canFloatWater() {
        return this.spec() != null && this.spec().file().physics().canFloat() && !this.isDestroyed();
    }

    /** Submerged fraction of the hull in fifths, measured from the float line. */
    public double getWaterDepth() {
        VehicleSpec info = spec();
        return info == null ? 0.0 : LevelTerrainProbe.slicedWaterDepth(this, info.file().physics().floatOffset());
    }

    /** Speed in blocks per second. */
    public double getCurrentSpeed() {
        return this.getDeltaMovement().length() * 20.0;
    }

    @Override
    public @NotNull Component getName() {
        return this.spec() != null ? Component.literal(this.spec().name()) : super.getName();
    }

    // As other features see it

    @Override
    public Entity entity() {
        return this;
    }

    @Override
    public float roll() {
        return this.components.roll().get();
    }

    @Override
    public double throttle() {
        return this.components.engine().syncedThrottle();
    }

    @Override
    public Targetable signature() {
        return this.components.countermeasures();
    }

    /** The pilot: the first passenger, or a UAV's operator at the station. */
    @Nullable
    @Override
    public Entity getRiddenByEntity() {
        if (this.components.uavLink().station() != null) {
            return this.components.uavLink().station().getOperator();
        }
        List<Entity> passengers = this.getPassengers();
        return passengers.isEmpty() ? null : passengers.getFirst();
    }

    public boolean isPilot(Entity player) {
        return EntityIds.same(this.getRiddenByEntity(), player);
    }

    @Override
    public void destruct() {
        this.components.lifecycle().destruct();
    }

    // The type's own part

    /** How the physics moves this vehicle through the world. */
    protected Collider createCollider() {
        return new EntityCollider(this);
    }

    /** Binds the vehicle type's attitude model to this vehicle. */
    protected abstract VehicleAttitude<?, ?> createAttitude();

    public VehicleAttitude<?, ?> attitudeControl() {
        if (this.attitudeControl == null) {
            this.attitudeControl = this.createAttitude();
        }
        return this.attitudeControl;
    }

    /**
     * The vehicle type's own part of the tick, in order: its controls, its physics model and its
     * effects. Built once, on the first tick that knows the type.
     */
    protected abstract List<TickStep> typeSteps();

    /** Every vehicle ticks through {@link VehicleTickPipeline}; vehicle types plug in {@link #typeSteps}. */
    @Override
    public final void tick() {
        this.tickPipeline.tick();
    }

    /** The steps of this vehicle's tick in order, with their phases, for the vehicle inspector. */
    public List<ScheduledStep> tickSchedule() {
        return this.tickPipeline.schedule();
    }

    /** The synced status bits, for the vehicle inspector. */
    public StatusWord statusWord() {
        return this.components.status();
    }

    void readCommonStatus() {
        this.components.status().readSynced();
    }

    /** Faces the hull to {@code yaw} and points the crew's look, search light and turrets the same way. */
    public void initRotationYaw(float yaw) {
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.components.aim().setRiderYaw(yaw);
        this.components.searchLight().aimAt(yaw, this.components.searchLight().aimPitch());
        for (WeaponGroup w : this.components.weapons().all()) {
            w.turret().setYaw(w.turret().defaultYaw());
            w.turret().setPitch(0.0F);
        }
    }

    /** Points the crew's look, search light and camera gimbals along the hull. */
    protected void initPartRotation(float yaw, float pitch) {
        this.components.aim().resetRiderLook(yaw, pitch);
        this.components.searchLight().aimAt(yaw, pitch);
        this.components.cameras().resetGimbals(yaw, pitch);
    }

    // The game's calls that need the whole vehicle

    @Override
    protected @NotNull AABB makeBoundingBox() {
        return new AircraftBoundingBox(this, super.makeBoundingBox());
    }

    @Override
    public void startSeenByPlayer(@NotNull ServerPlayer player) {
        super.startSeenByPlayer(player);
        SpawnDataEntity.sendSpawnData(this, player);
    }

    @Override
    public void writeSpawnData(@NotNull RegistryFriendlyByteBuf buffer) {
        VehicleSpawnData.write(this, buffer);
    }

    @Override
    public void readSpawnData(@NotNull RegistryFriendlyByteBuf data) {
        VehicleSpawnData.read(this, data);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        this.components.load(tag, super::readAdditionalSaveData);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        this.components.save(tag, super::addAdditionalSaveData);
    }

    /** The vehicle drops as its item, which keeps the vehicle's data ({@link VehicleItemData}). */
    @Nullable
    @Override
    public ItemEntity spawnAtLocation(@NotNull ItemStack is, float offsetY) {
        if (is.isEmpty()) {
            return null;
        }
        VehicleItemData.write(this, is);
        return super.spawnAtLocation(is, offsetY);
    }

    @Override
    public int getSizeInventory() {
        return this.spec() != null ? this.spec().file().features().inventorySize() : 0;
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        this.components.lifecycle().beforeRemoval(reason);
        super.remove(reason);
        this.components.lifecycle().afterRemoval(reason);
    }

    @Override
    public void onClientRemoval() {
        super.onClientRemoval();
        this.components.sound().play();
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        return this.components.boarding().interact(player, hand) ? InteractionResult.sidedSuccess(this.level().isClientSide)
                : InteractionResult.PASS;
    }

    @Override
    public boolean canRideAircraft(AircraftEntity carrier, int seatIndex, SeatRackInfo rack) {
        return this.components.racks().canRideAircraft(carrier, seatIndex, rack);
    }

    @Override
    public boolean isSkipNormalRender() {
        return this.getVehicle() instanceof SeatEntity;
    }

    // The components

    public IdentityComponent identity() {
        return this.components.identity();
    }

    public LifecycleComponent lifecycle() {
        return this.components.lifecycle();
    }

    @Override
    public BodyComponent body() {
        return this.components.body();
    }

    public AircraftPhysics physics() {
        return this.components.physics();
    }

    public PartsComponent parts() {
        return this.components.parts();
    }

    public RunningGearComponent runningGear() {
        return this.components.runningGear();
    }

    public ControlKeysComponent keys() {
        return this.components.keys();
    }

    public RollComponent rollAngle() {
        return this.components.roll();
    }

    @Override
    public SeatsComponent seats() {
        return this.components.seats();
    }

    @Override
    public CrewExitComponent crewExit() {
        return this.components.crewExit();
    }

    /** The ropes crew slide down from a hovering helicopter. */
    public RepellingRopes ropes() {
        return this.components.ropes();
    }

    public EjectionSeats ejectionSeats() {
        return this.components.ejectionSeats();
    }

    public RackComponent racks() {
        return this.components.racks();
    }

    public PositionHistory history() {
        return this.components.history();
    }

    public UavLinkComponent uavLink() {
        return this.components.uavLink();
    }

    public TowComponent tow() {
        return this.components.tow();
    }

    @Override
    public CameraComponent cameras() {
        return this.components.cameras();
    }

    public CrewAimComponent aim() {
        return this.components.aim();
    }

    @Override
    public WeaponComponent weapons() {
        return this.components.weapons();
    }

    public FuelComponent fuel() {
        return this.components.fuel();
    }

    @Override
    public DamageComponent damage() {
        return this.components.damage();
    }

    public PartBoxesComponent partBoxes() {
        return this.components.partBoxes();
    }

    public WreckComponent wreck() {
        return this.components.wreck();
    }

    @Override
    public HullDamageComponent hullDamage() {
        return this.components.hullDamage();
    }

    RepairComponent repairStation() {
        return this.components.repairStation();
    }

    public AmmoSupplyComponent ammoSupply() {
        return this.components.ammoSupply();
    }

    public SearchLightComponent searchLight() {
        return this.components.searchLight();
    }

    public RadarComponent radar() {
        return this.components.radar();
    }

    public AppearanceComponent appearance() {
        return this.components.appearance();
    }

    public HullLighting lighting() {
        return this.components.lighting();
    }

    public EngineSoundComponent sound() {
        return this.components.sound();
    }

    @Override
    public CollisionExclusions collisionExclusions() {
        return this.components.collisionExclusions();
    }

    @Override
    public MotionSyncComponent motionSync() {
        return this.components.motionSync();
    }

    public CarrierComponent carrier() {
        return this.components.carrier();
    }

    public BoardingComponent boarding() {
        return this.components.boarding();
    }

    /** The pilot's client settings, and sending this client's. */
    public PilotPreferences pilotPreferences() {
        return this.components.pilotPreferences();
    }

    /** The pilot seat from tick to tick: a new pilot's first tick, the pilot leaving. */
    public PilotHandover pilotHandover() {
        return this.components.pilotHandover();
    }

    /** Crew taking and leaving the seats behind the pilot's. */
    public SeatChanges seatChanges() {
        return this.components.seatChanges();
    }

    /** A player taking the pilot's seat. */
    public PilotBoarding pilotBoarding() {
        return this.components.pilotBoarding();
    }

    /** The crew stations switching off when the vehicle is destroyed. */
    public StationShutdown stationShutdown() {
        return this.components.stationShutdown();
    }

    public OwnershipComponent ownership() {
        return this.components.ownership();
    }

    public FlightModesComponent modes() {
        return this.components.modes();
    }

    public EngineComponent engine() {
        return this.components.engine();
    }

    @Override
    public RestComponent rest() {
        return this.components.rest();
    }

    public ScriptedPilotComponent scriptedPilot() {
        return this.components.scriptedPilot();
    }

    public CountermeasureComponent countermeasures() {
        return this.components.countermeasures();
    }

    public AircraftInventory getGuiInventory() {
        return this.components.cargo();
    }
}
