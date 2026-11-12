package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.vehicle.component.AddonBehaviours;
import com.ivomens.mcheli.vehicle.component.AmmoSupplyComponent;
import com.ivomens.mcheli.vehicle.component.BehavioursAfterMovement;
import com.ivomens.mcheli.vehicle.component.BehavioursBeforeMovement;
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
import com.ivomens.mcheli.vehicle.component.EngineSound;
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
import com.ivomens.mcheli.vehicle.component.RestState;
import com.ivomens.mcheli.vehicle.component.RollComponent;
import com.ivomens.mcheli.vehicle.component.RunningGearComponent;
import com.ivomens.mcheli.vehicle.component.ScriptedPilotComponent;
import com.ivomens.mcheli.vehicle.component.SearchLightComponent;
import com.ivomens.mcheli.vehicle.component.SeatChanges;
import com.ivomens.mcheli.vehicle.component.SeatsComponent;
import com.ivomens.mcheli.vehicle.component.StationShutdown;
import com.ivomens.mcheli.vehicle.component.StatusWord;
import com.ivomens.mcheli.vehicle.component.SyncedValue;
import com.ivomens.mcheli.vehicle.component.ThrottleSync;
import com.ivomens.mcheli.vehicle.component.TowComponent;
import com.ivomens.mcheli.vehicle.component.UavLinkComponent;
import com.ivomens.mcheli.vehicle.component.VehicleNbtCodec;
import com.ivomens.mcheli.vehicle.component.WeaponComponent;
import com.ivomens.mcheli.vehicle.component.WreckComponent;
import com.ivomens.mcheli.vehicle.settings.VehicleSettingsSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The components every vehicle is made of, wired together in one place.
 * <p>
 * Each component gets its collaborators through its constructor, so the order below is the
 * dependency order: a component is built after everything it uses. Read it top to bottom to see who
 * depends on whom; docs/COMPONENTS.md draws the same graph. The few places where a lower component
 * has to react to a higher one (the wreck to fatal damage, the crew exit to the hatch and to hover
 * mode) are subscriptions the higher component makes in its own constructor, never a dependency the
 * other way round.
 * <p>
 * Each vehicle kind adds its own components in its entity's constructor, on top of these. The
 * assembly also owns the save format's order ({@link #save}, {@link #load}).
 * <p>
 * The settings come in the same way: each component that follows a setting gets a supplier of the
 * narrow part of the vehicle's {@link VehicleSettingsSnapshot} it reads, never the config itself.
 */
final class AircraftComponents {

    private final AircraftEntity vehicle;
    private final SynchedEntityData data;
    private final boolean clientSide;
    private final Supplier<VehicleSettingsSnapshot> settings;

    // what the vehicle is and where its hull is
    private StatusWord status;
    private IdentityComponent identity;
    private AppearanceComponent appearance;
    private RollComponent roll;
    private PartBoxesComponent partBoxes;
    private BodyComponent body;
    private PositionHistory history;
    private ControlKeysComponent keys;
    private TowComponent tow;
    private RestState restState;
    private ScriptedPilotComponent scriptedPilot;
    private OwnershipComponent ownership;
    // the crew and the vehicle's life
    private CrewAimComponent aim;
    private SeatsComponent seats;
    private EngineSoundComponent sound;
    private AircraftInventory cargo;
    private LifecycleComponent lifecycle;
    // the hull
    private CountermeasureComponent countermeasures;
    private DamageComponent damage;
    private HullDamageComponent hullDamage;
    // fuel, engine and the physics
    private ThrottleSync throttleSync;
    private FuelComponent fuel;
    private EngineComponent engine;
    private AircraftPhysics physics;
    private RunningGearComponent runningGear;
    // parts and weapons
    private PartsComponent parts;
    private WeaponComponent weapons;
    private AmmoSupplyComponent ammoSupply;
    // the crew's stations
    private FlightModesComponent modes;
    private CameraComponent cameras;
    private RadarComponent radar;
    private SearchLightComponent searchLight;
    private HullLighting lighting;
    private UavLinkComponent uavLink;
    // getting in and out
    private RepellingRopes ropes;
    private CrewExitComponent crewExit;
    private RackComponent racks;
    private PilotPreferences pilotPreferences;
    private PilotHandover pilotHandover;
    private SeatChanges seatChanges;
    private PilotBoarding pilotBoarding;
    private BoardingComponent boarding;
    private EjectionSeats ejectionSeats;
    private StationShutdown stationShutdown;
    private WreckComponent wreck;
    // the vehicle among others
    private RestComponent rest;
    private RepairComponent repairStation;
    private CarrierComponent carrier;
    private MotionSyncComponent motionSync;
    private CollisionExclusions collisionExclusions;
    // what other mods add
    private BehavioursBeforeMovement behavioursBefore;
    private BehavioursAfterMovement behavioursAfter;

    AircraftComponents(AircraftEntity vehicle, AircraftCamera camera,
            VehicleClientHooks.EngineSoundPlayer soundPlayer, Supplier<VehicleSettingsSnapshot> settings) {
        this.vehicle = vehicle;
        this.data = vehicle.getEntityData();
        this.clientSide = vehicle.level().isClientSide;
        this.settings = settings;
        buildHull();
        buildCrew(soundPlayer);
        buildDamage();
        buildPowerplant();
        buildArmament();
        buildStations(camera);
        buildExits();
        buildNeighbourhood();
    }

    private <T> SyncedValue<T> synced(EntityDataAccessor<T> key) {
        return new SyncedValue<>(this.data, key);
    }

    /** Synced state only the server writes; a client's write is ignored. */
    private <T> SyncedValue<T> serverOwned(EntityDataAccessor<T> key) {
        return SyncedValue.serverOwned(this.data, key, this.clientSide);
    }

    private void buildHull() {
        AircraftEntity v = this.vehicle;
        this.status = new StatusWord(this.data, VehicleEntity.STATUS, this.clientSide);
        this.identity = new IdentityComponent(v, synced(VehicleEntity.ID_TYPE));
        this.appearance = new AppearanceComponent(v::spec, synced(VehicleEntity.TEXTURE_NAME),
                synced(VehicleEntity.LIVERY));
        this.roll = new RollComponent(this.data, VehicleEntity.ROT_ROLL);
        this.partBoxes = new PartBoxesComponent(v, synced(VehicleEntity.ERA_STATE));
        this.body = new BodyComponent(v, this.partBoxes, this.roll);
        this.history = new PositionHistory(v);
        this.keys = new ControlKeysComponent(this.status);
        this.tow = new TowComponent();
        this.restState = new RestState();
        this.scriptedPilot = new ScriptedPilotComponent(v);
        this.ownership = new OwnershipComponent(serverOwned(VehicleEntity.OWNER), serverOwned(VehicleEntity.OWNER_NAME),
                serverOwned(VehicleEntity.LOCK_STATE), () -> this.settings.get().locks());
    }

    private void buildCrew(VehicleClientHooks.EngineSoundPlayer soundPlayer) {
        AircraftEntity v = this.vehicle;
        this.aim = new CrewAimComponent(v, this.tow);
        this.seats = new SeatsComponent(v, this.status, this.identity, this.body, this.aim);
        this.sound = new EngineSoundComponent(v, soundPlayer, EngineSound.NONE);
        this.cargo = new AircraftInventory(v);
        this.lifecycle = new LifecycleComponent(v, this.status, this.seats, this.sound, this.tow, this.cargo,
                () -> this.settings.get().supply());
    }

    private void buildDamage() {
        AircraftEntity v = this.vehicle;
        this.countermeasures = new CountermeasureComponent(v, this.status, this.seats, this.body);
        this.damage = new DamageComponent(v, serverOwned(VehicleEntity.DAMAGE), this.lifecycle, this.countermeasures,
                this.partBoxes, this.seats, this.ownership, () -> this.settings.get().durability());
        this.hullDamage = new HullDamageComponent(v, this.damage, this.lifecycle,
                () -> this.settings.get().durability());
    }

    private void buildPowerplant() {
        AircraftEntity v = this.vehicle;
        this.throttleSync = new ThrottleSync(synced(VehicleEntity.THROTTLE));
        this.fuel = new FuelComponent(v, this.status, serverOwned(VehicleEntity.FUEL),
                serverOwned(VehicleEntity.FUEL_FF), this.throttleSync, this.lifecycle, this.seats, this.body,
                this.cargo);
        this.engine = new EngineComponent(v, this.throttleSync, this.restState, this.damage, this.fuel,
                this.status);
        this.physics = new AircraftPhysics(v, this.engine, this.keys, this.body, this.damage,
                () -> this.settings.get().sim());
        this.runningGear = new RunningGearComponent(v, this.engine, this.keys, this.physics);
    }

    private void buildArmament() {
        AircraftEntity v = this.vehicle;
        this.parts = new PartsComponent(v, VehicleEntity.PART_STAT, this.engine);
        this.weapons = new WeaponComponent(v, this.status, synced(VehicleEntity.USE_WEAPON), this.seats, this.aim,
                this.tow, this.parts, this.roll, this.lifecycle, () -> this.settings.get().sim().quaternionAttitude(),
                () -> this.settings.get().testMode());
        this.ammoSupply = new AmmoSupplyComponent(v, this.status, this.weapons, this.lifecycle, this.body);
    }

    private void buildStations(AircraftCamera camera) {
        AircraftEntity v = this.vehicle;
        this.modes = new FlightModesComponent(v, this.status, this.seats, this.parts, this.engine, this.weapons,
                camera);
        this.cameras = new CameraComponent(v, camera, this.seats, this.modes);
        this.radar = new RadarComponent(v, this.status);
        this.searchLight = new SearchLightComponent(v, this.status, this.seats, this.body, this.runningGear);
        this.lighting = new HullLighting(v, this.searchLight);
        this.uavLink = new UavLinkComponent(v, serverOwned(VehicleEntity.UAV_STATION), this.modes, this.engine,
                () -> this.settings.get().uav());
    }

    private void buildExits() {
        AircraftEntity v = this.vehicle;
        this.ropes = new RepellingRopes(v, this.status, this.seats, this.body, this.history, this.keys,
                this.lifecycle, this.modes);
        this.crewExit = new CrewExitComponent(v, this.seats, this.body, this.history, this.lifecycle, this.parts,
                this.ropes);
        this.racks = new RackComponent(v, this.crewExit, this.seats, this.body, this.engine, this.lifecycle,
                () -> this.settings.get().carriage());
        this.pilotPreferences = new PilotPreferences(this.cameras, () -> this.settings.get().client());
        this.pilotHandover = new PilotHandover(v, this.seats, this.aim, this.keys, this.modes, this.cameras,
                this.crewExit, this.pilotPreferences);
        this.seatChanges = new SeatChanges(v, this.seats, this.modes, this.weapons, this.cameras, this.crewExit,
                this.pilotPreferences);
        this.pilotBoarding = new PilotBoarding(v, this.parts, this.radar, this.cameras, this.weapons, this.crewExit,
                this.pilotPreferences, this.pilotHandover);
        this.boarding = new BoardingComponent(v, this.restState, this.seats, this.appearance, this.ownership,
                this.parts, this.cargo, this.pilotBoarding);
        this.ejectionSeats = new EjectionSeats(v, this.crewExit, this.pilotHandover, this.seats, this.body, this.parts,
                this.cargo);
        this.stationShutdown = new StationShutdown(this.seats, this.cameras, this.modes, this.searchLight);
        this.wreck = new WreckComponent(v, serverOwned(VehicleEntity.COMMAND), this.damage, this.engine,
                this.lifecycle, this.seats, this.ejectionSeats, this.stationShutdown,
                () -> this.settings.get().durability());
    }

    private void buildNeighbourhood() {
        AircraftEntity v = this.vehicle;
        this.rest = new RestComponent(v, this.restState, this.engine, this.tow, this.seats, this.uavLink, this.damage,
                this.fuel);
        this.repairStation = new RepairComponent(v, this.lifecycle, this.body);
        this.carrier = new CarrierComponent(v, this.engine, this.body, this.roll,
                () -> this.settings.get().carriage());
        this.motionSync = new MotionSyncComponent(v, this.restState, this.roll, this.body);
        this.collisionExclusions = new CollisionExclusions(v, this.seats, this.tow);
        AddonBehaviours addonBehaviours = new AddonBehaviours(v, this.lifecycle, this.keys);
        this.behavioursBefore = new BehavioursBeforeMovement(addonBehaviours);
        this.behavioursAfter = new BehavioursAfterMovement(addonBehaviours);
    }

    /**
     * Writes every component's saved state, in the order the save format has always had.
     *
     * @param inventory writes the vanilla inventory, where the save has always had it
     */
    void save(CompoundTag tag, Consumer<CompoundTag> inventory) {
        this.appearance.save(tag);
        tag.putString(VehicleNbtCodec.UNIQUE_ID, this.identity.uniqueId());
        tag.putString(VehicleNbtCodec.TYPE_NAME, this.identity.typeName());
        this.parts.save(tag);
        this.fuel.save(tag);
        this.wreck.save(tag);
        tag.putFloat(VehicleNbtCodec.ROLL, this.roll.get());
        this.searchLight.save(tag);
        this.aim.save(tag);
        tag.putString(VehicleNbtCodec.COMMAND, this.wreck.command());
        tag.putBoolean(VehicleNbtCodec.GUNNER_STATUS, this.seats.gunnersAllowed());
        this.partBoxes.save(tag);
        inventory.accept(tag);
        this.cargo.writeToNBT(tag);
        this.ammoSupply.save(tag);
        this.damage.save(tag);
        this.carrier.save(tag);
        this.uavLink.save(tag);
        this.ownership.save(tag);
        this.vehicle.data().save(tag);
    }

    /**
     * Reads every component's saved state. The type comes before the inventory (its size depends on
     * the type) and the weapons (they are built for the type); a wreck loads fully damaged.
     *
     * @param inventory reads the vanilla inventory, once the type (and so its size) is known
     */
    void load(CompoundTag tag, Consumer<CompoundTag> inventory) {
        this.wreck.load(tag);
        this.appearance.load(tag);
        this.identity.setUniqueId(tag.getString(VehicleNbtCodec.UNIQUE_ID));
        this.roll.init(tag.getFloat(VehicleNbtCodec.ROLL));
        this.aim.load(tag);
        this.parts.load(tag);
        this.identity.setTypeName(tag.getString(VehicleNbtCodec.TYPE_NAME));
        inventory.accept(tag);
        this.cargo.readFromNBT(tag);
        this.wreck.setCommand(tag.getString(VehicleNbtCodec.COMMAND));
        // saves from before the flag existed default to gunners allowed
        this.seats.setGunnersAllowed(!tag.contains(VehicleNbtCodec.GUNNER_STATUS)
                || tag.getBoolean(VehicleNbtCodec.GUNNER_STATUS));
        this.fuel.load(tag);
        this.ammoSupply.load(tag);
        if (this.wreck.isWreck()) {
            this.damage.setDamageTaken(this.damage.maxHp());
        } else {
            this.damage.load(tag);
        }
        this.searchLight.load(tag);
        this.partBoxes.load(tag);
        this.carrier.load(tag);
        this.uavLink.load(tag);
        this.ownership.load(tag);
        this.vehicle.data().load(tag);
    }

    StatusWord status() {
        return this.status;
    }

    IdentityComponent identity() {
        return this.identity;
    }

    AppearanceComponent appearance() {
        return this.appearance;
    }

    RollComponent roll() {
        return this.roll;
    }

    PartBoxesComponent partBoxes() {
        return this.partBoxes;
    }

    BodyComponent body() {
        return this.body;
    }

    PositionHistory history() {
        return this.history;
    }

    ControlKeysComponent keys() {
        return this.keys;
    }

    TowComponent tow() {
        return this.tow;
    }

    ScriptedPilotComponent scriptedPilot() {
        return this.scriptedPilot;
    }

    OwnershipComponent ownership() {
        return this.ownership;
    }

    CrewAimComponent aim() {
        return this.aim;
    }

    SeatsComponent seats() {
        return this.seats;
    }

    EngineSoundComponent sound() {
        return this.sound;
    }

    AircraftInventory cargo() {
        return this.cargo;
    }

    LifecycleComponent lifecycle() {
        return this.lifecycle;
    }

    CountermeasureComponent countermeasures() {
        return this.countermeasures;
    }

    DamageComponent damage() {
        return this.damage;
    }

    HullDamageComponent hullDamage() {
        return this.hullDamage;
    }

    FuelComponent fuel() {
        return this.fuel;
    }

    EngineComponent engine() {
        return this.engine;
    }

    AircraftPhysics physics() {
        return this.physics;
    }

    RunningGearComponent runningGear() {
        return this.runningGear;
    }

    PartsComponent parts() {
        return this.parts;
    }

    WeaponComponent weapons() {
        return this.weapons;
    }

    AmmoSupplyComponent ammoSupply() {
        return this.ammoSupply;
    }

    FlightModesComponent modes() {
        return this.modes;
    }

    CameraComponent cameras() {
        return this.cameras;
    }

    RadarComponent radar() {
        return this.radar;
    }

    SearchLightComponent searchLight() {
        return this.searchLight;
    }

    HullLighting lighting() {
        return this.lighting;
    }

    UavLinkComponent uavLink() {
        return this.uavLink;
    }

    RepellingRopes ropes() {
        return this.ropes;
    }

    CrewExitComponent crewExit() {
        return this.crewExit;
    }

    RackComponent racks() {
        return this.racks;
    }

    PilotPreferences pilotPreferences() {
        return this.pilotPreferences;
    }

    PilotHandover pilotHandover() {
        return this.pilotHandover;
    }

    SeatChanges seatChanges() {
        return this.seatChanges;
    }

    PilotBoarding pilotBoarding() {
        return this.pilotBoarding;
    }

    StationShutdown stationShutdown() {
        return this.stationShutdown;
    }

    BoardingComponent boarding() {
        return this.boarding;
    }

    EjectionSeats ejectionSeats() {
        return this.ejectionSeats;
    }

    WreckComponent wreck() {
        return this.wreck;
    }

    RestComponent rest() {
        return this.rest;
    }

    RepairComponent repairStation() {
        return this.repairStation;
    }

    CarrierComponent carrier() {
        return this.carrier;
    }

    MotionSyncComponent motionSync() {
        return this.motionSync;
    }

    CollisionExclusions collisionExclusions() {
        return this.collisionExclusions;
    }

    BehavioursBeforeMovement behavioursBefore() {
        return this.behavioursBefore;
    }

    BehavioursAfterMovement behavioursAfter() {
        return this.behavioursAfter;
    }
}
