# Architecture

Where the code is today, where it is going, and the order we get there in. Paths are relative to
`common/src/main/java/com/ivomens/mcheli` unless noted (the `core` packages, `com.ivomens.mcheli.core`, are
under `core/src/main/java`, the `neoforge` package under `neoforge/src/main/java`).

Three promises hold through every stage:

* existing content packs (YAML, TXT, HUD scripts, `.obj`/`.mqo` models) load and behave the same;
* existing worlds keep their vehicles (same entity ids, compatible NBT);
* vehicles handle the way they do today unless a change is deliberate, flagged and approved.

Part 1 covers the vehicle core: entity, movement, sync and model rendering. Part 2 covers weapons,
content, HUD, GUI, networking, config, wingman and the rest.

---

# Part 1 — Vehicle core

## 1. Where we are

### 1.1 One class does everything

* `aircraft/AircraftEntity.java` is ~4,500 lines, ~300 methods and 56 public mutable fields
  (`currentSpeed`, `rotationRoll`, `throttleUp/Down`, `moveLeft/Right`, `rotWheel`, `chaff`,
  `extraBoundingBox[]`, `impactPos`, `camera`, ...). It handles physics, crew, seats, weapons, fuel,
  countermeasures, UAV link, towing, camera, HUD prediction, sounds, part animation, NBT and spawn
  data. 170 files reference it and there are 54 `instanceof` checks on its subtypes.
* The helpers are not real components: `AircraftCrew`, `AircraftWeaponController`,
  `AircraftDamageHandler`, `AircraftFuelSystem` and `AircraftPartAnimator` hold the entity and write
  its fields directly (`AircraftCrew:304`, `AircraftWeaponController:390-420`,
  `AircraftPartAnimator:16-38`).
* Hidden side effects: `AircraftBoundingBox` (an `AABB` subclass) writes hit information onto the
  entity from inside geometric queries, so any vanilla `intersects`/`clip` call changes entity state;
  `AbstractPlaneEntity.getThrustDirection()` is a getter that pitches the aircraft down.
* Client and server logic are interleaved (`isClientSide` 56 times in `AircraftEntity` alone), the
  common definition holds a client model (`AircraftInfo.model`), and physics reads global config
  statics and per-player client settings copied onto the entity.
* Definitions are mutable bags: `AircraftInfo` (1,790 lines) has public fields written by both
  parsers and reused across reloads.

### 1.2 The same logic, five times over

* The `onUpdateAircraft` preamble exists in five near-identical copies (helicopter, plane, tank,
  ground vehicle, ship by inheritance).
* Throttle easing has four variants with different constants; throttle input is copied between plane
  and tank; the vertical law `0.04 + g − 0.047(1 − throttle)` is copied between plane and tank;
  buoyancy exists in four slightly different forms.
* Client interpolation has three implementations, dead reckoning three, and ground grip is one helper
  called with eight different magic constant pairs.

### 1.3 How movement works today

The server tick (`AircraftEntity.tick`) runs, in order: rest skip, control mirror, rotation sync,
lifecycle, `super.tick`, systems (fuel, ammo, repair, missile detector, sound, countermeasures),
**per-type physics**, part animation, recoil, crash check, seats, ground and water damage, rest
bookkeeping.

| Type | Physics today |
|---|---|
| Helicopter | Throttle, strafe impulse, hover drift (unseeded random), gravity plus rotor tilt thrust `p³/20000`, buoyancy, speed clamp, a hidden `currentSpeed` that is not saved, ground settle, `move()`, post-damping. |
| Plane, ship, VTOL | Autopilot or target-drone steering, the vertical law (no aerodynamic lift), thrust along `pitch − 10°` or the nozzle vector, reverse thrust, speed clamp, ground pitch, `move()`, post-damping. The nozzle angle is animated after physics, so thrust lags it by a tick. |
| Tank | Vertical law, flat or nose-up thrust, pre-move grip, wheel probes, `move()`, post-move grip, random choice of which hull parts ram and break blocks. |
| Ground vehicle | Gravity or buoyancy, a fixed `0.035` impulse per held key (the throttle branch is effectively dead), bicycle steering on the server, damping **before** `move()` unlike every other type. |

### 1.4 Attitude is computed on the client, every frame

`MouseInputHandler` measures frame time with `System.nanoTime()` and turns the vehicle every render
frame; the result is sent once per tick and the server overwrites yaw, pitch and roll with it. So:

* handling depends on frame rate wherever clamps are not frame-rate invariant;
* tank steering and terrain following only run on the driving client, so **a parked or unmanned tank
  never settles to the slope on the server**;
* reversing steering writes yaw on both sides and the client overwrites the server;
* ground vehicles steer on the server yet the client can still send yaw back.

### 1.5 Nondeterminism

Unseeded `Entity.random` and static `new Random()` in several weapon and wheel classes, frame-time
attitude, edge-triggered control packets, global config statics, wingman AI writing motion directly,
and a truncating `(int)(x + 0.5)` block lookup that differs from `floor` at negative coordinates.

## 2. Where we are going

### 2.1 Layout

```
:api        the public API for other mods (com.ivomens.mcheli.api): interfaces and enums over vanilla
            Minecraft, no loader and nothing of the mod; its own semver, signatures in api/api.txt (API.md)

:core       pure Java 21 + JOML, no net.minecraft; unit-tested with JUnit only (package com.ivomens.mcheli.core)
  sim/         the vehicle physics:
    math/        vectors, Attitude (with the legacy ZXY quaternion conversions), LegacyMath
    body/        BodyState (position, velocity, attitude, flags), MoveResult
    control/     ControlInput, AttitudeInput (with an optional rudder), ThrottleServo
    assist/      opt-in pilot aids on the input: AutoLevel, CoordinatedTurn, AltitudeHold (docs/CONTROLS.md)
    model/       MotionModel: HelicopterModel, FixedWingModel (plane, ship, VTOL),
                 TrackedModel, WheeledModel, GroundVehicleModel, TargetDroneAutopilot
    attitude/    AttitudeModel per type (mouse to attitude, auto-level, key yaw/roll, terrain follow)
    propulsion/  ThrottleModel, RotorModel, NozzleModel, WheelSuspensionModel
    port/        TerrainProbe, Collider, SimRandom, SimSettings (implemented in :common)
    step/        VehicleStepper (fixed-order pipeline), StepTrace (for golden recording)
  math/        Degrees, Vec2d
  input/       joystick and key shaping: AxisCurve, ThrottleAxis, KeyRamp, StickFilter, InputCapture
  ballistics/  ProjectileForces, ProjectileSteps, ImpactPrediction (CCIP), FlightAngles
  guidance/    Pursuit (turn, lead), Seeker (field of view, doppler gate, notching), LockCone
  target/      TargetType
  hud/         HudVariable, HudFrame, HudTextValue; expr/ the formula compiler
  content/     schema/ the schema engine; def/ the definitions that need no Minecraft (hud/, throwable/)
  wingman/     brain/ the autopilot's decisions; airfield/ and mission/ the model it decides on

:common     vanilla Minecraft, loader-free
  content/              parsers (unchanged behaviour) -> immutable definitions
  vehicle/definition/   VehicleDefinition, HandlingProfile, SeatDef, MountDef, PartDef (records)
  vehicle/component/    Crew, Weapon, Fuel, Damage, Parts, Countermeasure, UavLink, Tow, Rest
  vehicle/entity/       VehicleEntity (thin), one thin final class per type, seats, hitboxes, NBT codec
  vehicle/world/        LevelTerrainProbe, EntityCollider (vanilla Entity.move), cached terrain samples
  weapon/  net/  ai/wingman/  platform/
  client/   (client-only, enforced)
    input/   motion/ (one smoother for interpolation and prediction)
    render/  VehicleRenderSnapshot, VehicleModelRenderer (Immediate | Flywheel), models, effects
    hud/  gui/  sound/  fx/

:neoforge  :fabric   loader glue only; each compiles :core and :common in and builds its loader's jar

:architecture         the rule checks of ARCHITECTURE_RULES.md over every module (tests only)
```

Shared build setup lives in `buildSrc` (convention plugins for every module, for the modules shipped
inside a loader jar, and for the loader modules), library versions in `gradle/libs.versions.toml`.

### 2.2 Core ideas

* **Definition, state, entity.** `VehicleDefinition` is immutable and built once per load from the
  parsed packs (the parsers keep their behaviour). State is mutable and owned by exactly one
  component. The entity is only the Minecraft adapter: it holds components, saves NBT and bridges to
  the world.
* **One fixed-step physics pipeline.** Each server tick runs one `VehicleStepper.step` with a fixed
  phase order; every vehicle type plugs a `MotionModel` and an `AttitudeModel` into it. Collision is
  an injected `Collider` that calls vanilla `Entity.move` in game (bit-exact) and a simple fake in
  unit tests.
* **Roles.** The server owns position, velocity, throttle and modes. Attitude stays rider-predicted
  so the feel is unchanged, but the pilot's client runs the same `AttitudeModel` at fixed `1/60 s`
  substeps and sends a sequenced `AttitudeReport`, which the server validates against the model's
  maximum rate and applies at a defined point. Unmanned and AI vehicles run attitude on the server.
  Other clients only interpolate.
* **Typed messages** replace the 30-field control packet and its 20-handler cascade.
* **Rendering** reads a `VehicleRenderSnapshot` and never touches entity internals.

### 2.3 Key types

```java
// :core (core.sim)
public record ControlInput(int keys, int seq) {}
public record AttitudeInput(float mouseX, float mouseY, boolean freeLook) {}

public interface TerrainProbe {
    int firstSolidBelow(double x, double y, double z, int size, int maxDepth); // legacy rounding
    double waterDepthFraction(BoxD box, double floatOffset);
    boolean inWater();
}
public interface Collider { MoveResult move(BodyState body, double dx, double dy, double dz); }
public interface SimRandom { int nextInt(int bound); double nextDouble(); }

public interface MotionModel<D extends HandlingProfile> {
    /** Forces, impulses, clamps and pre-move friction; returns the displacement to attempt. */
    Vec3d preMove(BodyState body, PropulsionState prop, ControlInput in, D def, TerrainSample env, SimRandom rnd);
    /** Damping after collision, in the legacy order. */
    void postMove(BodyState body, MoveResult moved, D def, TerrainSample env);
}
public interface AttitudeModel<D extends HandlingProfile> {
    void integrate(Attitude att, AttitudeInput mouse, ControlInput keys, D def, TerrainSample env, float dtSeconds);
}
public final class VehicleStepper {
    public StepTrace step(VehicleSimState s, ControlInput in, TerrainProbe terrain, Collider collider, SimRandom rnd);
}

// :common
public sealed interface HandlingProfile permits RotorcraftHandling, FixedWingHandling, TrackedHandling, WheeledHandling {}
public abstract class VehicleEntity extends Entity {
    @Override public final void tick() { pipeline.tick(this); } // subclasses never override tick
}
public interface VehicleComponent {        // collaborators come in through the constructor (§2.6)
    default void serverTick(TickContext ctx) {}
    default void clientTick(TickContext ctx) {}
    default void save(CompoundTag tag) {}
    default void load(CompoundTag tag) {}
}
@TickOrder(phase = TickPhase.FOLLOW_HULL, after = PartsComponent.class)   // where its own tick belongs
public record ControlInputMessage(int vehicleId, int seq, int keys) implements ServerboundMessage {}
public record AttitudeReportMessage(int vehicleId, int seq, float yaw, float pitch, float roll) implements ServerboundMessage {}
public sealed interface CrewCommand permits SwitchWeapon, Fire, ToggleGear, ToggleHover, FoldBlades, SwitchVtol, Eject {}

// client
public record VehicleRenderSnapshot(Pose pose, float[] partAngles, float rotorAngle, int tintArgb, ResourceLocation texture) {}
public interface VehicleModelRenderer { void render(VehicleRenderSnapshot s, RenderContext ctx); }
```

### 2.4 The tick, in one place

`VehicleTickPipeline`: rest skip → apply pending input and attitude report → lifecycle → crew and
seats → throttle and propulsion → **`VehicleStepper.step`** → part animation → weapons,
countermeasures, fuel, supply, repair → seat positions → ground and water damage → sync dirty flags →
rest bookkeeping. The first version reproduces today's order exactly, including the one-tick part
lag; any reordering needs a golden diff and a decision.

The steps are grouped into `TickPhase`s, in order: `INPUT`, `LIFECYCLE`, `SYSTEMS`, `PHYSICS` (the
vehicle type's own steps), `FOLLOW_HULL` and `BOOKKEEPING`. A component with a tick of its own
(`TickStep.of`) declares the phase it belongs in and the components whose tick it reads, with
`@TickOrder(phase = ..., after = ...)`. `TickPlan` checks the order against those declarations when
the pipeline is built and again once the vehicle type's steps join, and throws on a step in the wrong
phase or before a component it must follow, so an order nobody chose never runs. The declarations
describe today's order; they do not change it.

### 2.5 The core API (`api.vehicle` and `entityview`)

Features never reach into the vehicle core's classes. They see a vehicle through a few read-mostly
interfaces, slices of their own below the core and above `combat`, `fx` and the client toolkit. The core
implements them; neither slice knows an implementation. The vehicle views that suit other mods are public
API, in the `:api` module (`com.ivomens.mcheli.api.vehicle`, see [API.md](API.md)); the views only the mod
itself needs stay in common, in `entityview`.

| Type | Where | Contract |
|---|---|---|
| `Vehicle` | `api.vehicle` | A vehicle as other features see it: `entity()`, `kind()`, `isUAV()`, `isDestroyed()`, `roll()`, `throttle()`, its `seats()` (`Crew`) and its radar and infrared `signature()` (`Targetable`). `AircraftEntity` implements it. |
| `VehicleKind` | `api.vehicle` | The vehicle kinds and their content folders; `content.def.vehicle.VehicleKinds` adds the content type and save name. |
| `Crew` | `api.vehicle` | Who sits where: `count()`, `isValidSeatId`, `riderOf(seat)`, `seatIdOf(entity)`, `passengers()`, `isAboard(entity)`. Implemented by `SeatsComponent`. |
| `Targetable` | `api.vehicle` | What a seeker sees: stealth, flares, the announced chaff time, a lock-breaking jammer and the iron curtain. Implemented by `CountermeasureComponent`. |
| `VehicleSeat` | `api.vehicle` | A seat entity; `getParent()` is the vehicle it belongs to. |
| `VehicleHitBox` | `api.vehicle` | A part hit box entity; `owner()` is its vehicle. |
| `RappelRope`, `TowChain` | `entityview` | The rope a soldier slides down (a marker) and a tow chain with its puller and towed entity. |
| `AiGunner` | `entityview` | A gunner entity, `isCreative()` for its unlimited ammunition. |
| `RemoteStation` | `entityview` | A station that remote-controls a vehicle, `getControlled()`. |
| `Riders` | `entityview` | Static lookups: the `Vehicle` an entity rides (directly or through a seat) and the eye position it looks from. |

The rest of the public API (the `McheliApi` entry point, the content views, the events and the pack
registration) is implemented in `apiservice`, which nothing in the mod depends on, and the events are
posted through `event.ApiEvents` from the components, weapons and mixins where they happen.

`combat` adds its own ports on top, which the core implements: `ArmedVehicle` (its `weapons()`, a
`WeaponSelection` of each crew member's weapon; its `cameras()`, the `MissileCamera` its crew watches a TV
missile through; self-destruct) behind `Shooter.vehicle()`, and the level-scoped
`TargetDesignation` that team play implements for spotting and marking. Features that need more of the
core get it handed in: the screens of `aircraft` take a `Runnable` to open the options, the vehicle
renderer a `DrawnVehicleListener`, the weapons the vehicle classes their damage factors name
(`VehicleDamageTargets`).

### 2.6 Component dependencies

A component gets every collaborator through its constructor and never asks the entity for one. Where
it needs only a slice of another component, it takes that slice: `ThrottleSync` (the published
throttle) instead of the engine, `RestState` (the wake flag) instead of the rest policy, the
`AircraftCamera` instead of the camera component. Its host is the entity only where it touches the
world; `CollisionExclusions` and `PositionHistory` take a plain `Entity`.

* **Wiring.** `aircraft.AircraftComponents` builds the components every vehicle shares, in dependency
  order and in one place: hull, crew, damage, powerplant, armament, stations, exits, neighbourhood.
  Each vehicle kind's entity builds its own on top (rotor blades and controls, the plane's wings,
  nozzles and throttle, the tank's throttle, the placed position hold). The assembly also owns the save
  order (`save`, `load`); `VehicleNbtCodec` only names the keys. `AircraftEntity` keeps its public
  accessors (`weapons()`, `fuel()`, `cameras()`, ...), which hand out the assembly's components to
  other features.
* **No cycles.** Constructor injection without lazy suppliers cannot form a cycle, so each former
  cycle was broken by design: a lower component announces an event and the higher one subscribes in
  its own constructor (`DamageComponent.whenDestroyed` for the wreck, `PartsComponent.whenHatchCloses`
  for the crew exit, `FlightModesComponent.whenHoverSwitched` for the repelling ropes), small shared
  state moved into its own component (`RestState`, `ThrottleSync`), or a responsibility moved to its owner (the light hatch
  to the search light, the TV-missile view test to the camera, the turret check to a static of the
  weapon layout).
* **Settings come in too.** A component never reads the config. `vehicle.settings.VehicleSettingsHolder`
  derives one immutable `VehicleSettingsSnapshot` from the config stores (`mcheli.cfg`, the synced
  server settings, `mcheli-wingman.cfg`) and the vehicle hands its components a `Supplier` of the narrow
  record each one reads: `SimSettings` for the physics (built once per snapshot, not per call),
  `DurabilityRules`, `SupplyRules`, `CarriageRules`, `LockRules`, `UavRules`, and the client's own
  `ClientPreferences` (pilot settings, shaders, fixed-step attitude, handling assists). The snapshot is
  rebuilt the first time it is read after a load, `/mcheli reconfig`, a config screen save or a server
  settings sync replaced one of its sources, and swapped in atomically. A vehicle reads its snapshot
  through `AircraftEntity.settings()`, which a test harness can pin (`pinSettings`) to record a vehicle
  under known settings. The same holds for `AircraftPhysics`, the assembly, `VehicleTickPipeline`, the
  pilot assists (`aircraft.assist`) and `:core`.
* **Bounded fan-out.** A component's constructor takes at most 7 collaborators: other components and
  narrow ports (`AircraftCamera`, a sound player), counted with any `Supplier` of one. The host
  entity, plain values (records, enums, `SyncedValue` slots, callbacks) and settings do not count.
  A component that needs more is split along a seam it already has. That is how the former hubs were
  broken up: the click on the vehicle (`BoardingComponent`) is apart from a player taking the pilot's
  seat (`PilotBoarding`), the pilot seat from tick to tick (`PilotHandover`), crew taking the other
  seats (`SeatChanges`) and the pilot's client settings (`PilotPreferences`); the rope repelling
  (`RepellingRopes`) is apart from the parachute drop and exits (`CrewExitComponent`); and the
  stations going dark when a vehicle is destroyed (`StationShutdown`) are apart from the wreck.
  [COMPONENTS.md](COMPONENTS.md) lists each component's fan-in and fan-out.
* **Other vehicles.** A tanker, an ammunition or repair vehicle and a vehicle looking for a rack touch
  another vehicle's components; they do it through `aircraft.VehiclePeers`, the one place that lists
  those interactions.
* **Checked.** `ComponentRulesTest` fails when a component (or a helper in `vehicle.component`) calls an
  entity method that hands out a component, when a vehicle kind's constructor graph has a cycle, when
  `@TickOrder(after = ...)` names a component the constructor does not take, when a ticking
  component declares no phase, when a component's constructor takes more than 7 collaborators, or when
  component code (or the physics adapter, the assembly, the tick pipeline, the pilot assists or
  `:core`) depends on the config or the snapshot holder. It also keeps [COMPONENTS.md](COMPONENTS.md), the graph per vehicle kind
  generated from the classes (`./gradlew :architecture:writeComponentGraph`), from going stale.

## 3. The movement rewrite

First extract today's code into the models **bit-exact** (same types, same operation order, same
`Mth`/`Math` calls); only then clean up the maths, behind golden tolerances. Quirks we keep are named
(`LegacyOrder` flags) instead of hidden.

| Model | Reproduces |
|---|---|
| `HelicopterModel`, `RotorcraftAttitude` | helicopter physics, strafing, settling, roll decay, rotation limits, folded-blade taxi. `currentSpeed` becomes explicit propulsion state; hover drift uses the injected random. |
| `FixedWingModel`, `FixedWingAttitude` | planes and ships (with the ship's water probe); the thrust getter's side effect becomes an explicit step. |
| VTOL | the same model with `NozzleModel` (the current parts state machine). |
| `TargetDroneAutopilot` | drone steering, forced throttle, fuel-out explosion. |
| `TrackedModel`, `WheeledModel`, `GroundAttitude` | tank physics, grip constants, wheel suspension as pure probes, terrain follow and steering at fixed substeps. Server-side terrain follow for unmanned tanks is a **deliberate change**, off until approved. |
| `GroundVehicleModel` | key impulses, bicycle steering, server-owned yaw. |
| UAVs | not a model: a link component routes the station's inputs to the vehicle's model. |
| Wingman | drives vehicles through `ControlInput`/`AttitudeInput` plus an explicit scripted-override phase. |

**Bit-exact:** throttle constants and 0.05 sync quantisation, the vertical law, rotor tilt, speed
clamps, damping and its order, buoyancy, `isClearBelow`/`getWaterDepth` semantics including the
rounding, vanilla collision and step height, the attitude composition order, part animation rates and
lag, rest-state timings.

**Within tolerance:** client attitude at render frame rates (now fixed substeps), tank attitude
smoothing, hover drift, interpolation and prediction (visual only), rotor, wheel and track angles,
effects.

**Performance:** one cached terrain sample per step replaces the 5–10 scans per tick done today.

## 4. Golden-trajectory harness

* **Inputs** go through the same entry points the network uses: real control packets delivered to a
  seated mock player, and the attitude call a 20 FPS client would make (plus a 60 FPS suite to
  measure frame-rate sensitivity). Applied at level-tick start through a game-test-only hook.
* **Scripts** per type on 2–3 representative definitions (hover, climb, bank, strafe, autorotation,
  folded taxi, water landing, wreck; takeoff, cruise, roll, glide, gear, reverse thrust; VTOL lift-off
  and transition; ship drift and beaching; tank flat, pivot, step, ramp, reverse, brake, ledge; ground
  vehicle steering and settle; target drone to fuel-out), plus a rest-and-wake script and a
  negative-coordinate copy for every type.
* **Recorded per tick:** position, velocity, yaw/pitch/roll, throttle and synced step, speed,
  ground/collision/water flags, water depth, part states, rotor angle, type-specific state, rest
  state, health.
* **Format:** gzipped CSV under
  `common/src/gametest/resources/golden/<type>/<name>.<script>.csv.gz` with a JSON header (schema,
  commit, definition hash, pinned config, seed, terrain, dt mode). Values written with
  `Double.toString`, so they round-trip exactly.
* **Tolerances:** extraction stages must match exactly, events on the same tick. Deliberate cleanups:
  angles within 0.01°, position within 1e-3 blocks at tick 200 and 0.05 at tick 1200, velocity within
  1e-4 relative, events within one tick. Intended handling changes are gated on feel metrics (time to
  90 % speed, turn rate, climb rate, stopping distance, hover drift) within 1 %.
* **Determinism:** one test per batch, a dedicated test terrain (pad, ramp, step, wall, pool, air
  column), fixed coordinates, no random ticks or mobs, seeded entity random, injectable static
  randoms, every config value physics reads pinned.
* **Save fixtures:** every vehicle type saved in several states (fresh, damaged, fuelled, armed,
  folded, VTOL, ERA popped, livery, paired UAV, cargo) with today's code and committed as SNBT. New
  code must load each with the same observable state and save a superset of the original keys.

## 5. Rendering

Today every vertex is re-sent by the CPU each frame, softened by distance LOD, unsorted translucency
and static tracks.

* **Flywheel 1.0.7 exists for 1.21.1 on NeoForge and Fabric**, with a loader-neutral API. Each vehicle
  becomes one embedding (one matrix update per frame) with an instance per model part; static parts
  never change, moving parts update only when their angle does, meshes are shared per vehicle type and
  LOD levels become separate models.
* Under an Iris shader pack Flywheel switches itself off (unless a third-party compat add-on is
  installed), so a complete non-Flywheel renderer is always needed.
* A custom VBO/VAO path would bypass vanilla shaders, break under shader packs and be thrown away by
  the 26.1 rendering API, so we do not build one.

**Decision:** a `VehicleRenderSnapshot` and a `VehicleModelRenderer` with two backends: the current
immediate renderer (the reference, the shader-pack path and the base for 26.x) and an optional
Flywheel backend. Effects stay immediate. Gates: no regression on the immediate path, at least 30 %
better frame time on Flywheel, and identical part transforms between the two.

**Done (stage 8)**, described in [RENDERING.md](RENDERING.md): one `VehicleEntityRenderer` takes a
`VehicleRenderSnapshot` per frame; `VehicleModelPoser` lays out the parts for both backends; effects
are `VehicleEffects`. The immediate output is image-identical to before (render check), the instanced
part matrices equal the `LegacyGL` stack (equivalence check, worst relative error 4e-6), and the
immediate path did not regress. The Flywheel backend works under Mesa llvmpipe but is no faster there,
where rasterization is done on the CPU either way, so it stays opt-in (`VehicleRenderer = auto`, on Flywheel's `indirect` backend only, see [RENDERING.md](RENDERING.md)) until
it is measured on real graphics hardware (see [PERFORMANCE_BASELINE.md](PERFORMANCE_BASELINE.md)).

## 6. Order of work

Every stage ends with the golden suite (exact unless stated), the game tests, the stress tests, the
NBT fixtures and a dedicated-server check passing.

| # | Stage | Risk |
|---|---|---|
| 0 | Harness: golden recorder and comparator, scripts, NBT fixtures, performance baselines, architecture-test skeleton with today's violations listed. | Low |
| 1 | Extract pure physics into `sim` (now `core.sim`) in place, bit-exact. | Medium |
| 2 | Attitude models, fixed client substeps, sequenced attitude reports, one owner of yaw. Playtest sign-off. | High (feel) |
| 3 | Immutable definitions; client models move out of the common definition. | Low–medium |
| 4 | Gradle split: `:sim` (now `:core`), `:common`, `:neoforge`, then the `:fabric` module. Done: see §2.1 and [FABRIC_PORT.md](FABRIC_PORT.md). | Medium |
| 5 | Break the entity into components, one per change. `AircraftEntity` ~4,500 → ~600 lines. | High |
| 6 | Typed messages, crew commands, one client smoother. | Medium |
| 7 | Weapons (see Part 2). | Medium |
| 8 | Render snapshot and the Flywheel backend. Done; Flywheel opt-in until measured on real hardware (§5). | Medium |
| 9 | HUD and GUI read snapshots only. | Low–medium |

## 7. Code standards

* **Names:** `XxxDefinition` (immutable, from packs), `XxxState` (mutable, one owner), `XxxModel`
  (pure behaviour), `XxxComponent` (owns state, ticks it), `XxxMessage` (network record),
  `XxxRenderer`/`XxxVisual` (client). No `onUpdate_*`, no `ac`/`prm` abbreviations.
* **Dependency direction:** `core.sim` ← `vehicle` ← `net` ← `client`. `core.sim` imports only `java.*`,
  `org.joml.*` and itself; nothing outside `client` imports client classes; physics never imports
  rendering, HUD, GUI or sound.
* **Size:** classes ≤ 500 lines, methods ≤ 50, without exceptions.
* **State:** no public non-final fields outside the legacy parser DTOs; records for messages and
  definitions; no static mutable state outside registries and client singletons; no `Random`,
  `nanoTime` or config statics in physics, components or weapon runtime.
* **Sides:** no `isClientSide` branches inside components; no getters with side effects.
* **Enforced** by the module boundaries (`:core` cannot see Minecraft, `:common` cannot see a
  loader), ArchUnit tests and source-length checks in the `:architecture` module (see
  [ARCHITECTURE_RULES.md](ARCHITECTURE_RULES.md)), and a stress-test budget (server ms per tick with
  300 vehicles ≤ stage 0 baseline × 1.02).

---

# Part 2 — Everything else

Scope: weapons, content, HUD, GUI, networking, config, wingman, and the remaining packages (about 55k of the 93k lines). The vehicle core (aircraft entity and its components, motion models, interpolation, state sync, model rendering) is out of scope. This plan only uses the interfaces the core will expose: `VehicleDefinition`, `VehicleState` and its components, fixed-step motion models, and a render snapshot.

All file:line references are under `common/src/main/java/com/ivomens/mcheli/`.

---

## 1. Diagnosis per area

### 1.1 Weapons (`weapon/` 8.0k, `flare/`, `lweapon/`, `throwable/`, `gltd/`, `helper/world/McheliExplosion`)

**One class per weapon kind, chosen by a string, set up by writing into public fields.**
- `weapon/registry/WeaponTypeRegistry.java:31` is a public static mutable `HashMap` that maps 18 strings ("machinegun1", "aamissile" ...) to 18 subclasses of `BaseWeapon`.
- `WeaponCreator.java:32-52` builds a weapon, then writes `power`, `acceleration`, `interval`, `heatCount` and `cartridge` into public fields. It gives client and server different intervals (`:44-51`). After that a `modifyParameters()` hook lets subclasses fix the result.
- Most subclasses only differ in three things: which projectile class they construct, which mode suffix they show (" [HE]", " [TA]", " [TV]"), and two ballistic step factors. Compare `MachineGunWeapon`, `TracerMachineGunWeapon` and `RocketWeapon`.

**Untyped side channels.**
- `BaseWeapon.optionParameter1/2` and `WeaponFireParams.option1/option2` are raw ints that mean "mode", "lock target" or "guidance type" depending on the subclass (`AntiTankMissileWeapon` sets `missile.guidanceType = prm.option2`).
- `TracerMachineGunWeapon.java:35` signals "only play the explosion sound" with a negative `explosionPower`.
- `WeaponFireParams` is a bag of public mutable fields that `BaseWeapon.use` changes in place (`prm.posX += offset.x`).

**The weapon base class knows about vehicle classes.**
- `BaseWeapon.calculateShotRotation` checks `instanceof TankEntity` (`:208`) and reads the tank's seat and weapon pitch limits.
- `getShotPos` checks `instanceof GroundVehicleEntity` and `instanceof AircraftEntity` (`:240-248`).
- Client-only calls sit in common code: `ClientHelper.playSound` and `PlayerViewHandler.applyRecoil`.

**`BaseBulletEntity` is a 1,425-line god class.** It handles fuzes, airburst, proximity, the spreader, bomblets, splashes, bounce, explosions, the HBM payload switch, chunk loading, target alerts and ignore lists.
- The base class branches on its own subclass: `this instanceof AirToAirMissileEntity` (`:454`), `this instanceof BulletEntity && entity instanceof Villager` (`:1290`).
- `IGNORED_ENTITY_CLASSES` (`:113`) is a static cache rebuilt from config.
- Eleven projectile entity classes mostly differ in `tick()` guidance and their default model.
- `AirToAirMissileEntity.updateGuidance` compares the proximity fuse against the squared distance; content packs depend on that quirk. The anti-tank missile compares against the linear distance. The same idea is coded twice with different semantics and nothing names the difference.

**Guidance and lock-on are stringly typed and duplicated.**
- `WeaponGuidanceSystem.canLockEntity` recognises other mods' vehicles by class-name substrings (`:310-340`, "EntityVehicle", "EntityMecha"). It also has magic `ecmJammerType == 2` (`:320`) and an instanceof list of four missile classes (`:326`).
- The same missile list appears again in `mob/GunnerEntity.java:293-298`.
- `WeaponProfile` and `WingmanCombat` re-derive weapon roles from the type string ("aamissile" → MISSILE).

**Countermeasures have magic modes and string flags.**
- `ActiveProtectionSystem.java:23,43`: a range of exactly 100 switches the APS into a different device ("iron curtain").
- `Flare` uses int types 1..10 to index parameter tables, with separate client and server tables (`:31-32`).
- Countermeasure state reaches missiles through string flags in persistent data (`FlareUsing`, `ChaffUsing`, `APSUsing`, `LockOn`, `Tracking`), written from `Flare`, `Chaff`, `ActiveProtectionSystem` and `MissileDetector`.

**Static radar state and a global singleton.**
- `aircraft/RWRThreatManager.java:33` keeps a static `LOADED_AIRCRAFT` set.
- `MCHeli.rwrThreatManager` is a global singleton.
- `EntityInfoManagerAsync.serverEntities` is a static `ConcurrentHashMap` and sends class names to clients.

**Explosions.**
- `Explosions.java` has four `newExplosion` overloads plus `newExplosionInWater`, with 13 to 17 positional parameters, including an ignored `isSmoking`. It sets about ten public fields on `McheliExplosion`.

**Handheld launchers.**
- `LightWeaponClientTickHandler` keeps all weapon state in public statics (`:23-31`, "because the HUD reads it").
- The server builds a new `BaseWeapon` on every shot (`networking/packet/control/PacketPlayerLightWeaponControl.java:89`).

**Wrong dependency direction.**
- `BaseBulletEntity` calls `wingman.handler.ProjectileChunkLoader`, so weapons depend on wingman.

### 1.2 Content system (`helper/info/` 6.5k, the info classes, `helper/addon`)

**Adding anything touches every format.**
- `IParser` has one method per content type (8 methods), and so does `IEmitter`.
- `ContentRegistries` holds 8 static fields plus an if-chain `get(Class)` (`:88-99`).
- A new content type means editing about 6 files.

**The same knowledge is coded three times.**
- `TxtParser` (1,605 lines, one `switch` per type), `YamlParser`, `WeaponParser`, `ComponentParser`, `HUDParser` and `ThrowableParser` (about 3.2k lines together), and `YamlEmitter` (1,800 lines) each hold their own copy of keys, ranges and defaults.
- The emitter must invert parser normalisations by hand (`YamlEmitter.ROTOR_SPEED_TRIM`).
- Ranges are duplicated: TXT `explosion` is clamped 0..200 at `TxtParser:1128`, and the YAML clamps live separately.

**TXT has order-dependent side effects.** The `type` key overwrites `gravity` and `gravityInWater` when the type is bomb or dispenser (`TxtParser.java:1113-1118`). A `gravity` line placed before `type` is silently lost. Any rewrite must replay this.

**Info classes are mutable grab-bags.**
- `WeaponInfo` has 195 public fields, including `public static Random rand` (`:37`) and HBM containers.
- They carry parse helpers (`BaseInfo.toBool/toFloat/hex2dec`) and source-format branching at run time (`BaseInfo.isLegacyTxt()`, used by `ThrowableInfo.java:99` to convert seconds to ticks).
- They are mutated after loading: `MCHeli.registerThrowableItems` writes `info.item` and `info.itemID`, and `WeaponInfoManager.setRoundItems` fills `RoundItem.itemStack`.
- They hold client-only objects: `ThrowableInfo.model` (`IModelCustom`) and `AircraftInfo.hudCache` (DirectDrawables, `:380-415`).

**Weak YAML typing.**
- Values are cast raw (`(String) value`, `(Boolean) value`), so a typo turns into a `ClassCastException` message.
- `HUDParser.toStringList` decides the element type from the first element.
- `YamlParser` keeps the current file in a `ThreadLocal` (`:75`).
- `WeaponParser.tryLoadClass` uses `Class.forName` on content strings (`:58-66, 369`).

**Side-mixing in the pipeline.** `ContentRegistries.loadContents` calls `MCHeli.proxy.onLoad*` client callbacks in the middle of parsing.

**Reloading is string-based.** `canReloadItem(String key)` whitelists TXT key strings (`AircraftInfo:747`).

### 1.3 HUD (`hud/` 3.0k)

**The content object holds per-frame execution state.** `Hud` has `isIfFalse`, `exit`, `isDrawing` and `broken` (`Hud.java:31-40`), next to a static shared `ScriptHudContext` (`:24`). `HudElement.parent` is mutable and set during `validate()`.

**Gate semantics instead of real block nesting.**
- An `if` always evaluates, even inside a false block (`HudItemConditional.canExecute() == true`).
- Any `endif` clears the gate.
- YAML `If/Do` is flattened into the same gates (`HUDParser:368-396`), so nested YAML `If`s do not nest.
- Existing packs depend on this behaviour.

**Formulas are rewritten with regexes, then evaluated through a boxed map.**
- `HudElement.BOOLEAN_REWRITES` rewrites `x==0` into `!x` for 9 variables before compiling with Aviator.
- About 45 variables are boxed into a `LinkedHashMap` every frame (`ScriptHudContext.updateVariables`), whether or not any loaded script reads them.
- Aviator's integer arithmetic is relied on (comment at `ScriptHudContext:24-26`).
- Unknown variable names only fail at draw time.

**Global or static rendering state.**
- `Gui2D` emulates immediate mode with a static `GuiGraphics` and GL constants (`gui/Gui2D.java:37-52`).
- Colour deliberately carries over between frames (`ScriptHudContext.color`).

**`DrawString` relies on value types.** It uses `String.format` with typed values (`%d` needs an Integer), chosen through the `HudItemStringArgs` switch.

**Three HUD systems with no shared model:** the scripted `Hud`, `hud/direct_drawable` (RWR, BVR, GPS, mortar radar, pulled from `AircraftInfo.getHudCache()`), and `hud/modern/ModernHud`.

These are the findings stage 7 started from; the status note in §2.4 says what was built.

### 1.4 GUI (`gui/`, `block/DraftingTableScreen`, `livery/client`, `wingman/client`, `multiplay/Scoreboard*`)

- 1.12-style int button ids with an `actionPerformed` switch: `ConfigScreen.java:36-52` defines 15 id constants and 30 button fields.
- `ConfigScreen` extends a container screen over an empty `ConfigMenu`.
- Five unrelated base classes: `LegacyContainerScreen`, `OverlayScreen`, `WingmanScreen` (with its own `IdButton`), `ScoreboardScreenBase` (which is not a Screen at all), and plain `Screen`.
- Oversized screens: `GuiBaseConfig` 1,055 lines, `AircraftGui` 721, `DraftingTableScreen` 647.
- 217 `§` format codes against 38 `Component.translatable` calls across the codebase.

These are the findings stage 9 started from; the status note in §2.8 says what was built.

### 1.5 Networking (`networking/` 5.9k, 72 packet classes)

- Each packet is a mutable class with hand-written symmetric `write`/`read` and logic in `onReceive`. `PacketHandleCommand.onReceive` runs server commands.
- Ids are derived from class names (`Network.register`), and direction from marker interfaces.
- Mega-packets dispatch on int constants plus CSV strings: `PacketBaseAction` has `int action` with `SAVE_ROUTE=0` ..., 23 fields, and `waypointsCsv`.
- Some packets are bidirectional, with one enum covering both directions (`PacketHandleCommand.CommandAction`).
- Because packets embed their handlers, `networking` imports 20 other packages: wingman 34 times, aircraft 34, livery 18. It sits in the middle of every dependency cycle.
- Validation is ad hoc per packet (`sanitizeWeaponPos`, `checkSize`).

### 1.6 Config (`McheliConfig` 1,254 lines, `ConfigOption`, `ServerSettings`, `WingmanConfig`)

- 173 `public static ConfigOption` fields and about 200 static mutable fields in total.
- `ConfigOption` is a tagged union: an int `type` plus four `prm*` slots.
- Callers write values directly: `command/McheliCommand.java:205,221` (`.prmBool = enable`).
- Derived lists (block lists, damage factors) are rebuilt by `correctionParameter()`, and code selects them with int switches (`getNoBreakableBlockListFromType(int n)`).
- `ServerSettings` holds 10 static fields overwritten by a packet.
- `WingmanConfig` is a second, independent option system with mirrored statics, also written at run time.
- `ItemID_*` options from the 1.7 era are passed into item constructors and ignored (`BaseItem(int id, ...)`).

### 1.7 Wingman (`wingman/` 8.9k)

- `WingmanEntry` has about 40 public mutable fields, int attack modes (`ATK_NONE/MANUAL/AUTO`), a string weapon filter, autopilot scratch values (`taxiPreX`, `vtolOnSent`), mission progress and diagnostics.
- `AutonomousState` has 22 flat states, and the transitions are spread across `RunwayPilot`, `VtolPilot`, `OrderPilot`, `NodeMissionPilot` and `WingmanTickHandler` (646 lines).
- `WingmanRegistry` is a static synchronized map. It is never cleared on server stop (`CommonEventHooks.onServerStopped` only resets liveries), so entries leak between singleplayer worlds.
- `McHeliWingman` holds static handler singletons.
- `/wingman` takes a greedy string and splits it by hand (`WingmanCommand.java:69-115`), with hard-coded `§` messages.
- The control code sets rotations on the entity directly. The AI, the actuation and the persistence (markers, taxi routes, entries) are mixed together.

### 1.8 Everything else

- **`MCHeli`:** 25 static mutable item, block and config fields. It creates `ClientProxy` reflectively (`:114`). `CommonProxy` has about 40 mostly-empty hooks, called 46 times.
- **`ModLib`:** math, world probes (`getBlockIdY` returns a numeric block id) and client render toggles in one common class.
- **Client code in common packages:** 26 classes import `net.minecraft.client` outside any client package (for example `InputKey`, `KeyNames`, `PlayerViewHandler`, `SoundRegistry`, `HitMarkerOverlay`, `block/CurrentRecipe`).
- **Wrappers:** `wrapper/` holds pure indirection: `BaseEntity`'s static helpers, `FakePlayerBase`'s inventory helpers, `SideHelper`, `WorldHelper`, `BlockHelper`, the int-id `BaseItem`, `LegacyButton`, `LegacyContainerScreen`, `LegacyParticle`.
- **Particles:** `particles/` and `fx/` are two particle systems, switched by `McheliConfig.useModernParticles()` checks in 6 places.
- **`mixin/MixinEntity`:** runs `getEntitiesOfClass(AircraftEntity, ...)` on every `Entity.collide` of every entity in the world. This is the most expensive line in my scope.
- **`GunnerEntity`** (610 lines) duplicates threat classification. **`UavStationEntity`** is 749 lines.
- **Packages depend on each other in cycles:** helper↔everything, weapon↔aircraft, networking↔everything.

---

## 2. Target design

### 2.1 Package tree (common module; client code always under a `client` subpackage)

The parts of these features that need no Minecraft live in the `:core` module, below
`com.ivomens.mcheli.core` (Part 1 §2.1): the expression compiler and frame model of `hud`, the brain of
`wingman` with its airfield and mission model, the schema engine of `content` with the definitions that need
no Minecraft, and the ballistics and guidance math of `combat`. The tree below shows where each feature
lives as a whole; the status note after it lists what moved.

```
com.ivomens.mcheli
├─ McheliMod                    entry facade: wires registries, content, services; no static mutable state
├─ platform/                    (exists) loader service interfaces
├─ util/                        pure helpers: math (Angles, Rotations, Quats), Rng, RateLimitedLog, Ids
├─ config/                      typed option model, cfg file reader/writer, sections, SettingsStore
│  └─ client/                   client sections plus the config screen
├─ content/                     content pipeline, independent of vehicle/weapon runtime
│  ├─ schema/                   Schema, Field, ValueCodec, SchemaVersion, migrations
│  ├─ source/{yaml,txt}/        readers: file → RawDocument (ordered entries with SourcePos)
│  ├─ bind/                     SchemaBinder, Diagnostics, LegacyTxtRules (ordered TXT side effects)
│  ├─ emit/                     schema-driven YAML emitter
│  ├─ pack/                     addon packs (folder/zip/builtin/generated)
│  ├─ registry/                 ContentType, ContentSet (immutable snapshot), ContentHolder, Ref<T>
│  ├─ recipe/                   recipe strings → recipe JSON, legacy recipe conversion
│  └─ def/                      definition records not owned by core: weapon/, throwable/, hud/
├─ combat/
│  ├─ weapon/                   WeaponKind table, FireMode impls, WeaponMount, WeaponGroup, ShotContext
│  ├─ projectile/               ProjectileEntity, ProjectileProfile, FlightModel, Fuze, Warhead impls
│  ├─ guidance/                 Guidance impls, seeker/lock-on, TargetFilter, ForeignVehicles
│  ├─ countermeasure/           Countermeasure impls, SignatureState, CountermeasureFlags (cross-mod NBT)
│  ├─ sensor/                   AircraftIndex, radar contacts, ThreatBoard (RWR)
│  ├─ explosion/                ExplosionSpec, Explosions, McheliExplosion
│  └─ client/                   tracers, CCIP, lock tones, projectile renderer bindings
├─ equipment/                   handheld launchers, throwables, GLTD, rangefinder, wrench (+ client/)
├─ hud/                         Hud, HudVariable, HudFrame, HudTextValue, HudCanvas
│  ├─ expr/                     formula lexer, parser, typed compiler, VariableTable
│  ├─ script/                   HudScript instructions; lowering to gates (format 1) or jumps (format 2)
│  └─ client/                   HudRenderer, HudSession, HudTelemetry, ScreenCanvas, styles,
│                               instrument/ (RWR, BVR, GPS, mortar), modern/
├─ ui/client/                   widget toolkit: Theme, layout, widgets, McScreen
├─ net/                         Message, MessageType, codecs, Network facade, ServerContext
├─ wingman/
│  ├─ brain/                    pure decision code: phases, FlightDirector, combat planner
│  ├─ mission/                  MissionOrder, MissionPlan, node missions (records)
│  ├─ roster/                   WingmanService (per server), WingmanAssignment, Roster
│  ├─ airfield/                 markers and taxi routes, AirfieldData (SavedData; keys unchanged)
│  ├─ control/                  AircraftControls adapter onto the core's VehicleState
│  ├─ command/                  Brigadier /wingman tree
│  └─ client/                   planner and panel screens
├─ world/                       standalone entities: gunner/, parachute/, chain/, cargo/, uavstation/, block/
├─ multiplay/                   teams, spotting, markers (+ client/)
├─ livery/                      server store (+ client/)
├─ sound/  fx/client/           one particle system with legacy/modern "styles" as data
├─ compat/                      optional-mod adapters (HBM payload no-op, energy)
├─ client/                      client shell: input, keys, camera, overlay layer registration
└─ neoforge/ (later fabric/)    loader glue only
```

Feature-local messages live in `<feature>/net/*Message.java`, with client handlers in `<feature>/client/`.

**Status (`:core` module).** The physics module `:sim` grew into `:core`, everything of the mod that needs no
Minecraft, provably: it compiles against Java, JOML and the `javax.annotation` annotations only, and the
architecture rules check that its classes use no Minecraft, Mojang or loader class and that its packages form
no cycles. Its tests run on JUnit alone, in seconds. Both loader jars compile its sources in, as they did
`:sim`'s.
- `core.sim`: the physics, unchanged (it was `com.ivomens.mcheli.sim`).
- `core.hud`: `HudVariable`, `HudFrame` and `HudTextValue`, and `core.hud.expr`, the formula compiler. The
  script, its compiler and the renderer stay in `hud` (they name textures by `ResourceLocation`).
- `core.content.schema`: the schema engine (fields, sections, codecs, value shapes, `Values`). The codecs of
  Minecraft's `Vec3` (`Vectors`) stay in common's `content.schema`, the readers in `content.bind` (they log
  through the mod logger and parse with SnakeYAML).
- `core.content.def.hud` and `core.content.def.throwable`: the definitions that need no Minecraft. Weapon and
  vehicle definitions use `Vec3`, `ResourceLocation` and the config, and stay in `content.def`.
- `core.wingman.brain`, with the airfield (`Marker`, `MarkerType`, `Airfield`, `TaxiRoute`) and mission
  (`MissionOrder`, `MissionNode`, `MissionType`) model it decides on. A `Marker` holds its block position as
  three ints (common's `Markers` converts), `MarkerType` names its block states through
  `serializedName()` (common's `MarkerTypeProperty` reads and writes the same names an `EnumProperty` did),
  and the brain logs through the `BrainLog` its `Perception` carries.
- `core.ballistics` and `core.guidance`: the math of `FlightModel`, `Trajectories`, `Homing` and
  `LockOnSeeker`, on JOML vectors and plain numbers, written out so the results stay bit-exact. The combat
  classes keep the entity and level reads and call it. Homing is pure pursuit with optional lead; there is no
  proportional navigation to move.
- `core.math` (`Degrees`, `Vec2d`) and `core.target.TargetType`.

### 2.2 Weapons: data-driven composition

**Definition: one immutable record, grouped by YAML section.**
```java
public record WeaponDefinition(ContentId id, SourceInfo source, String displayName, WeaponKind kind,
        FireControl fire, AmmoSpec ammo, Ballistics ballistics, GuidanceSpec guidance, FuzeSpec fuzes,
        WarheadSpec warhead, Optional<SubmunitionSpec> submunition, SightSpec sight,
        WeaponPresentation presentation, CompatPayloads compat) implements Definition {}

public record FireControl(int cooldownTicks, int modeCount, int fixedMode, float accuracy, HeatSpec heat,
        RecoilSpec recoil, boolean ridableOnly, boolean offAxis) {}
public sealed interface GuidanceSpec permits Unguided, Seeker, Command, Laser, Gps {}
public record Seeker(SeekerType type /*HEAT, RADAR_ACTIVE, RADAR_PASSIVE, AT*/, LockRules lock,
        float turnRate, int rigidityTicks, boolean canLockMissile) implements GuidanceSpec {}
public record FuzeSpec(int timeTicks, int delayTicks, float proximity, ProximityMetric metric,
        int altitude, Airburst airburst, float bounce, Optional<MineFuze> mine) {}
public sealed interface WarheadSpec permits Kinetic, Explosive, Fae, Dispense, Composite {}
```

**Kinds are a table, not a class hierarchy.** `WeaponKind` is a record registered under the legacy `Type` string, so content keeps loading:
```java
public record WeaponKind(String legacyName, FireMode fireMode, FlightModel.Factory flight,
        List<ModeSpec> modes, boolean heavy, ProjectileSkin skin, ProximityMetric defaultProximity) {}

// WeaponKinds (one file, about 120 lines):
//   "machinegun2" -> gun fire, BALLISTIC_MULTISTEP flight, modes ["", " [HE]" (explosive toggle)], skin BULLET
//   "aamissile"   -> single launch, SEEKER flight, skin AA_MISSILE, defaultProximity SQUARED
//   "atmissile"   -> single launch, SEEKER flight, modes ["", " [TA]" (top attack)], skin AT_MISSILE, LINEAR
```
The squared-distance proximity quirk and the HE sound-only trick become named data. That replaces 18 subclasses, `WeaponCreator` and `WeaponTypeRegistry`.

**Runtime.**
```java
public interface FireMode { ShotResult fire(ShotContext ctx); }   // Gun, Buckshot, Launch, Drop, Dispense,
                                                                  // Beam (laser), CasStrike, MarkerCall, Designate, None
public record ShotContext(ServerLevel level, Shooter shooter, MuzzlePose muzzle, WeaponMode mode,
        Optional<TargetRef> lock, int airburstDistance, RandomSource random) {}
public interface Shooter {                 // implemented by the core's vehicle seat adapter and by handhelds
    Entity user(); Entity platform();
    MuzzlePose muzzle(WeaponMount mount, Aim aim);   // replaces instanceof Tank/GroundVehicle/Aircraft
    AimLimits limits(WeaponMount mount);
}
public record WeaponMount(WeaponDefinition def, Vec3 offset, float yaw, float pitch, boolean onTurret) {}
public final class WeaponGroup {           // replaces WeaponSet: ammo, reserve, heat, cooldown, recoil, barrel spin
    public FireOutcome trigger(ShotContext ctx); public void tick(); public WeaponGroupView view();
}
public sealed interface FireRequest permits Plain, Locked, Guided {}   // replaces option1/option2 ints
```

**Cooldown padding.** The client and non-player cooldown padding (`WeaponCreator.delayedInterval`) becomes a pure function `Cooldowns.padded(int)` with a unit test. Values stay identical.

**Projectiles: one entity class with pluggable behaviour.**
```java
public final class ProjectileEntity extends Entity {          // all 11 EntityTypes construct this class
    private ProjectileProfile profile;   // immutable, cached per (definition, ContentSet generation)
    private final ProjectileState state = new ProjectileState(); // pos history, fuze timers, target, flags
}
public record ProjectileProfile(WeaponDefinition def, FlightModel flight, Guidance guidance,
        List<Fuze> fuzes, Warhead warhead, Optional<Spreader> spreader) {}
public interface FlightModel { void applyForces(ProjectileState s, Environment env); double stepFactor(); }
public interface Guidance   { Steering steer(ProjectileState s, GuidanceView view); }   // seekers, TV command, laser, GPS
public interface Fuze       { FuzeOutcome check(ProjectileState s, FuzeView view); }    // impact, time, delay, proximity, airburst, altitude, mine
public interface Warhead    { void detonate(Detonation d); }                             // explosive, FAE, dispense, effects, split
```
- **Fixed tick order**, written once in `ProjectileEntity.tick()` and taken from today's order: resolve → fuzes (server) → forces → chunk check → collide → airburst → proximity → move → spreader.
- **Entity ids stay.** The ids `rocket`, `bullet`, `aa_missile`, `as_missile`, `at_missile`, `tv_missile`, `bomb`, `torpedo`, `marker_rocket`, `dispensed_item` and `a10` stay registered. `ProjectileSkin` picks the type, which keeps renderer defaults, tracking ranges and world compatibility. Projectiles other than laid mines are not written to saves (`ProjectileLifetime`; one an older version saved is discarded on load), so their NBT is no constraint. One that waits in a chunk that ticks no entities for longer than `FrozenProjectileLifetime` is removed.
- **Classification moves to the projectile.** `instanceof <Missile>` checks become `projectile.profile().isGuidedThreat()`, used by guidance, the gunner and the APS.

**Lock-on.**
- `LockOnSeeker` keeps today's client behaviour and is a plain object driven by `TargetFilter`:
  ```java
  public record TargetFilter(boolean air, boolean ground, boolean water, int minHeight, boolean ridableOnly,
          boolean missiles, SeekerType seeker) { boolean accepts(Entity e, TargetContext ctx); }
  ```
- The class-name heuristics for other mods move to `combat/guidance/ForeignVehicles` (one table, one place).
- Stealth comes from a `Targetable` view that the core exposes.

**Countermeasures.**
```java
public sealed interface Countermeasure permits FlareDispenser, ChaffDispenser, ActiveProtection, IronCurtain,
        EcmJammer, Dircm, PhotoelectricJammer {
    boolean activate(CountermeasureContext ctx); void tick(CountermeasureContext ctx);
    boolean active(); int cooldownTicks(); SignatureEffect effect();
}
public record SignatureState(boolean decoysHeat, boolean decoysRadar, boolean breaksRadarLock,
        boolean interceptsProjectiles, boolean ignoresHits) {}
public final class CountermeasureSuite { /* per vehicle component; folds active effects into SignatureState */ }
```
- `IronCurtain` is its own type. The legacy "APS range == 100" maps to it in the content codec, not at run time.
- Flare patterns are an enum `FlarePattern`; the ints 1..10 are still accepted.
- Guidance queries `SignatureState` and never reads aircraft flags.
- `CountermeasureFlags` is the one place that writes and reads the cross-mod persistent-data keys (`FlareUsing`, `ChaffUsing`, `APSUsing`, `LockOn`, `Tracking`). The keys are unchanged.

**Sensors.**
- `AircraftIndex` is a per-server service that replaces the static `LOADED_AIRCRAFT`. It is also used by `MixinEntity`, which only queries aircraft that have extra collision boxes, through an index by chunk section.
- `ThreatBoard` replaces `RWRThreatManager` and is instance-owned by the server context.
- `RadarContact` records replace `EntityInfo`. Contacts carry a `RadarContactKind` that the server decides once (`EntityInfo.kindOf`); the instruments no longer match class names.

**Explosions.**
```java
public record ExplosionSpec(Vec3 at, float size, float blockSize, float damagePower, float blockPower,
        float damageRadius, boolean fire, boolean destroyBlocks, int igniteEntities, boolean sound,
        Medium medium, @Nullable DamageFactor factor) { public static Builder builder(Vec3 at, float size) }
public static ExplosionResult Explosions.detonate(ServerLevel level, ExplosionSpec spec, ExplosionSource source);
```

**Handheld launchers.**
- They use the same `WeaponGroup` and `FireMode` through a `HandheldShooter`.
- The server keeps one `WeaponGroup` per player, cached by definition, instead of building a weapon per packet.
- Client state lives in a `HandheldWeaponSession` instance owned by the client shell. The HUD reads it through a view, not through statics.

**Status.** Stages 5, 6 and 8 are built, with these differences from the sketch above:
- Weapons still read the mutable `WeaponInfo` adapter; `WeaponDefinition` sections are not yet the runtime input.
- `WeaponKind` is an enum, not a record, and `FireRequest` is one record (mode, target id) whose wire size matches the old packet.
- `ProjectileEntity` takes its `ProjectileKind` from its `EntityType`; every legacy id stays registered and the renderer is chosen per kind. `a10` stays its own entity (`combat/strike/A10StrikeEntity`), because it flies a scripted pass rather than a projectile path.
- Clients fly rounds and rockets themselves (`ProjectileSync`): the spawn packet carries the projectile's motion and age, and the server sends a position only after a change only it knows of, while the motion is too fast for the packets, and every 40 ticks (`MixinServerEntity` keeps vanilla's tracker from sending moves for them). Missiles, bombs, torpedoes and containers keep vanilla's updates.
- `HomingProjectiles` (per level) holds the projectiles that have a target, for the missile warnings.
- Countermeasures keep their per-vehicle state in `CountermeasureComponent`; there is no `SignatureState` yet, and guidance still reads the `CountermeasureFlag` keys.
- `ThreatBoard` and `RadarContactSync` are per server and `AircraftIndex` per level.
- Handheld equipment lives in `equipment/` (`launcher`, `gltd`, `throwable`).

### 2.3 Content: one schema, two readers, typed immutable records

```java
public interface Definition { ContentId id(); SourceInfo source(); }
public record SourceInfo(String pack, String path, Format format /*YAML, TXT*/, int schemaVersion) {}
public record ContentType<D extends Definition>(String folder, Schema<D> schema, Class<D> type) {}

public final class Schema<D> {                     // declared once per type, top to bottom in YAML order
    public static <D> Builder<D> builder(String typeName, int currentVersion, Supplier<Draft<D>> draft);
    // builder.field("Ammo.MagSize", txt("round"), Codecs.intRange(1, 30000), 0, Draft::magSize)
    //        .unsupported("Explosion.Type", txt())           // feeds UnsupportedSettings
    //        .txtRule(LegacyTxtRules.BOMB_TYPE_GRAVITY)      // ordered TXT side effect, replayed in line order
    //        .normalise(Normalisers.SECONDS_TO_TICKS, Format.TXT)  // replaces isLegacyTxt()
    //        .reloadable()                                    // replaces canReloadItem(String)
}
public interface ValueCodec<T> { T decode(RawValue raw, DiagnosticSink sink); Object encode(T value); String describe(); }
public record RawDocument(Format format, List<RawEntry> entries) {}    // entries keep their order and SourcePos
public record Diagnostic(Severity severity, SourcePos pos, String path, String message) {}
public final class ContentSet { <D extends Definition> Optional<D> get(ContentType<D> t, String name);
                                <D extends Definition> Collection<D> all(ContentType<D> t); long generation(); }
public final class ContentHolder { public static ContentSet current(); }   // the single atomic reference
public record Ref<D extends Definition>(ContentType<D> type, String name) { Optional<D> resolve(ContentSet s); }
```

**Pipeline:** discover packs → read (`ContentReader` per extension; still extensible like `ContentParsers.register`) → migrate (`SchemaVersion` steps on the `RawDocument`) → bind (`SchemaBinder` fills a `Draft`, codecs report diagnostics) → validate (cross-field rules) → link (resolve `Ref`s: vehicle→weapon, hud call→hud) → publish (`ContentSet`).

**Two kinds of files.** Key/value files (weapons, throwables, vehicles) use the schema. HUD scripts are a grammar, not a schema: `hud/program` has its own TXT and YAML front ends producing a `HudProgram`.

**Error messages** are specific, always non-fatal per file, and summarised once:
```
weapons/foo.yml:12 Ammo.MagSize: expected an integer from 1 to 30000, got "abc"; the default 0 is used
weapons/bar.txt:7 unknown key "relaodtime" (did you mean "reloadtime"?)
```

**Versioning.** YAML may carry `SchemaVersion: N`; when absent, it is version 1. TXT is version 0 and maps onto version 1 through the TXT aliases on each field. The emitter always writes the current version.

**Emitter.**
- It walks the same schema: values equal to the default are omitted, keys go out in declaration order, and the codecs' `encode` inverts the normalisers (no more hand-kept `ROTOR_SPEED_TRIM`).
- The `/mcheli` dump command keeps working.
- Emitting TXT is not offered, the same as today.

**Run-time bindings leave the definitions.**
- `ContentBindings` is filled after item registration: definition→`Item` and round-item stacks.
- Client caches (models, DirectDrawables) live in `client` maps keyed by `ContentId`.

**Shared with the vehicle core.** `VehicleDefinition` is the core's record, but its `Schema` uses this infrastructure. The vehicle schema is migrated last.

**Status.** Weapons and throwables are read and written through their schemas (stage 3). [CONTENT_SCHEMA.md](CONTENT_SCHEMA.md) describes the schema as built and the plan for vehicles and HUDs.

**Status (stage 12, content half).** The info adapters are gone: `AircraftInfo` and its five kind subclasses, `WeaponInfo`, `ThrowableInfo`, `BaseInfo`, `DefinedInfo`, `IContentData`, `IItemContent` and the per-kind info managers. Run-time code reads the definitions, or the narrow value its feature builds from a definition once when the file loads:
- Each feature owns the registries of its content types (`HudManager`, `Weapons`, `VehicleTypes`, `Throwables`) and hands them to `ContentRegistries`, which reads the files and has each registry build its value: `Hud`, `WeaponSpec`, `VehicleSpec` (the file, the core's `VehicleDefinition` and the layouts `CrewLayout`, `Armament`, `DrawnParts`, `KindParts`, `HitBoxes`), and the `ThrowableDefinition` itself. Lookups return `Optional`.
- `ContentBindings` holds the items, round stacks and dispensed items by `ContentId`; client caches (projectile models, spent casings, seat HUDs, exhaust flame frames) live in client maps.
- `content` depends on no feature: the vocabulary definitions use (`VehicleKind`, `RadarType`, `RWRType`, `HitBoxType`, `SightType`, `MineFuze`) moved into `content.def`, the fuel and rangefinder recharge recipes moved to their features, and the drafting table's recipe sources are handed in by the setup code. The `content -> core`, `content -> combat`, `content -> equipment` and `content -> hud` cycle entries are gone.
- The NTM payload is read into the records of `NtmSections`; `compat.hbm.NuclearTech` keeps the no-op calls.
- `content.recipe.Recipes` became `GeneratedRecipes`, `RecipeJson`, `RecipeStrings`, `InventoryIngredients`, `LegacyRecipeConverter`, `LegacyRecipePacks` and `LegacyItemIds`.
- The canonical dumps are unchanged: the tests write them through views that list today's values under the old field names.

**Status (stage 12, core half).**
- The feature packages form no cycles: `package-cycles.txt` is empty. Features see vehicles through the core API (Part 1 §2.5); vehicle-bound sensors, countermeasures, the UAV link and parachutes moved into the core; the core no longer reaches into world, equipment, team play, livery or set-up code.
- No class outside a `client` package uses client code: `client-classes.txt` is empty. `event` is gone. The per-client state lives in `setup.client.ClientSession`, one object the client entrypoint builds and hands to the features (view, vehicle controls, launcher, radar contacts, instruments, livery client, operator reports, UAV feed and the rest). Sided hooks (`SpentCases`, `ClientSounds`, `RangeFinderClient`, `LocalClient`) reach the client from common code through `Services.sided`.
- Per-server state lives on the server or level through mixin holders: `ThreatBoard`, `WingmanService`, `LiveryManager`, `OperatorRequests` on `MinecraftServer`; `AircraftIndex`, `CombatRandom`, `TargetDesignation` on `Level`.
- Static mutable fields are registry holders (`@RegistryHolder`) or resettable caches (`@StaticCache`); see §3.2. The last client ones became session state: the frame's `ScreenProjection`, the `TeamMarkers` (spotted entities, the marked point and the frame's markers), the `ThermalVision` texture swap, the `ItemRenderContext` of the item being drawn and the `ClientEffectSpawner`'s entity ids. Models reload through an explicit `ModelLoader` instead of a global switch, and `LegacyGL` keeps its open frames on one stack. Radar contacts are synced per server by `RadarContactSync`.
- No class has a public mutable field: particle parameters have accessors, part sounds, model vertices, model bounds and radar contacts are records, the smoke particles' settings are protected, and the tank's wheel probes are `WheelProbe`.
- Every class is at most 500 lines: `VehicleEntity` answers the game's calls (movement and collision, client motion, the pilot, the synced data keys) with the components `AircraftEntity` holds. Its age, infinite supplies and removal clean-up are `LifecycleComponent`, its type name and unique id `IdentityComponent`, its orientation part of `BodyComponent`; callers reach components directly (`vehicle.lifecycle().age()`, `vehicle.uavLink().pairedStation()`), and the entity's pass-throughs are gone.
- The architecture rules are absolute: every allow-list was empty, and the allow-list mechanism is removed.
- The accessors are named after what they return: `AircraftEntity.spec()`, `helicopterSpec()`, `planeSpec()`, `tankSpec()`, `groundVehicleSpec()`, `AircraftItem.spec()`, `WeaponGroup.spec()`, `WeaponMount.spec()`, `ProjectileEntity.spec()`.
- `LegacyGL` is split into `RenderFrame`, `RenderState` and `BakedEmitter`; the mouse input handler into the `vehicle.client.input` classes; every method outside `content.recipe` is at most 50 lines.
- After stage 12 the vehicle components declare their dependencies: each takes its collaborators through its constructor, `AircraftComponents` wires them, the tick pipeline checks its order against each component's `@TickOrder`, and [COMPONENTS.md](COMPONENTS.md) draws the graph per vehicle kind (Part 1 §2.6).

### 2.4 HUD: compiled expressions plus a declarative program

**Decision: replace Aviator with our own small expression compiler, and keep Aviator only as a test oracle.**

Reasons:
1. Aviator generates bytecode at run time, which `docs/FABRIC_PORT.md` already lists as a Knot risk. It is also a large jar-in-jar.
2. The current per-frame path boxes about 45 values into a `HashMap` for every HUD.
3. We need load-time diagnostics for unknown variables and type errors. Today they surface as a "broken" HUD at draw time.
4. The bundled HUDs call no functions at all: their expressions are made of literals, hex, identifiers, arithmetic, comparisons, `&&`, `||`, `!` and `?:`. The grammar is small.

The compiler reproduces Aviator's semantics where scripts depend on them:
- long arithmetic for int operands, so `7/2 == 3`;
- double promotion;
- booleans and the legacy `x==0` boolean rewrites, done in the type checker instead of by regex;
- `0x` hex as long;
- a whitelisted `math.*` set (`abs`, `sin`, `cos`, `sqrt`, `pow`, `round`, `floor`, `ceil`, `min`, `max`) for addon packs.

For one release, an expression our compiler rejects is logged with its file and line. A dev-mode flag compares both evaluators at load time.

```java
public enum ExprType { INT, DOUBLE, BOOL }
public record HudVariable(String name, ExprType type, int slot, VariableSource source) {}  // declared once in hud/vars
public interface CompiledExpr { double asDouble(HudFrame f); long asLong(HudFrame f); boolean asBool(HudFrame f); ExprType type(); }
public final class ExprCompiler { CompiledExpr compile(String source, VariableTable vars) throws ExprException; }
public final class HudFrame { final long[] ints; final double[] doubles; final boolean[] bools; }   // filled once per frame,
                                                                                                  // only for slots any loaded HUD uses
public record HudProgram(List<HudOp> ops) {}
public sealed interface HudOp permits Gate, EndGate, Exit, Call, SetColor, DrawLine, DrawLineStipple, DrawRect,
        DrawTexture, DrawString, DrawGraduation, DrawRadar, CameraRot {}
public record HudDefinition(ContentId id, SourceInfo source, boolean ignoreAutoScale, HudProgram program) implements Definition {}
public interface HudCanvas { void line(...); void rect(...); void texture(...); void text(String s, int x, int y, int argb, boolean centered); ... }
public final class HudRenderer { void render(HudDefinition hud, HudSession session, HudCanvas canvas); }   // client
```

**Legacy semantics, exactly.**
- TXT and YAML version 1 lower to `Gate`/`EndGate`. They keep today's non-nesting gate, where an inner `if` re-evaluates and any `endif` clears.
- `Exit` stops only the current program.
- `Call` uses a recursion guard and the auto-scale canvas switch.
- Colour persists across frames inside `HudSession`, which is per viewer and client-owned, not static.

**HUD format v2.** YAML `SchemaVersion: 2` may use properly nested `If/Then/Else`, which lowers to real jumps. This is a documented new format, not a change to existing files.

**One home for all HUD styles.** Instruments (RWR, BVR, GPS, mortar) and `modern/` render through the same `HudCanvas`. The `Gui2D` statics go away. `HudStyle` selection stays.

**Status.** Stage 7 is built, with these differences from the sketch above:
- The compiler lives in `core.hud.expr` (the `:core` module, see §2.1): `Lexer`, `Parser` (precedence climbing over `BinaryOperator`), a sealed `Ast`, and `ExprCompiler`, which binds names to `Variable` slots and builds `CompiledExpr` nodes. Types are sets: almost every node has exactly one type and is *settled*, read through its typed accessor with no check; a `?:` with an integer and a double branch, or a `max` of mixed values, picks its type per frame. `ExprType` has a fourth value, `NIL`, because Aviator read an unknown name as nil (`foo==1` is false, `foo` alone fails): an unknown name is reported at load time and keeps that meaning. `Semantics` holds the comparison rules (long compare for two integers, `Double.compare` otherwise, nil below everything, booleans never equal to numbers). The legacy `x==0` rewrite is done on tokens, exactly where the old regular expression applied it, so precedence is unchanged.
- An operation no type of its operands allows is reported at load time and compiled to a `Failure` node, which fails only when a frame reaches it, as the operation failed in Aviator; a text that is no formula at all compiles to one that always fails. Functions are Aviator's own names: `math.abs/sqrt/sin/cos/tan/asin/acos/atan/log/log10/floor/ceil/round/pow`, and `max`/`min` (Aviator had no `math.max`). Strings, assignments and statements are not supported.
- `HudVariable` is an enum: its ordinal is the slot, its type is fixed, and the nine legacy 0/1 variables are flagged. `HudFrame` holds the variables in typed arrays, plus the `DrawString` values (`HudTextValue`, each with its Java type) and the instrument inputs; `HudTelemetry` (client) fills it once per frame from the vehicle's components.
- The runtime program is `hud/script/HudScript`, a list of sealed `HudInstruction` records compiled by `HudScriptCompiler`. The content record keeps the name `HudProgram`. Format 1 lowers `If` to `Gate`/`EndGate`, format 2 (`SchemaVersion: 2`, nested `If`/`Then`/`Else`) to `Branch`/`Jump`. YAML commands now keep their line.
- `HudRenderer` (client) runs a script on a `HudCanvas`; `HudSession` keeps the carried colour, broken scripts, reported texts and texture sizes per viewer. `HudStyle` is a client interface with `ScriptedHudStyle`, `ModernHudStyle` and `ConfiguredHudStyle`; the overlays are handed the style, so `HudRenderers` and its static are gone. `Call` still looks HUDs up while drawing.
- `HudCanvas` has two kinds of primitive, pen (pixel-wide immediate lines, the scripted look) and shapes (batched, canvas-unit strokes, the modern look), so each style keeps its exact pixels. `ScreenCanvas` implements it. The `Gui2D` facade that kept one static for the older screens is gone since stage 9: overlays draw through `HudOverlay` on the canvas of their frame.
- A `DrawString` whose format does not fit its values is reported at load time and skipped; before, it stopped the whole HUD until reload. A called HUD that broke is skipped instead of failing again every frame.
- The HUD content dumps and the expression golden read the compiled HUDs through `LegacyHudView`, a test-side mapping onto the fields of the old element classes.

### 2.5 Networking: typed messages and uniform registration

```java
public interface Message {}                                             // implemented by records only
public record MessageType<M extends Message>(ResourceLocation id, Direction direction,
        StreamCodec<RegistryFriendlyByteBuf, M> codec) {}
public interface ServerHandler<M> { void handle(M msg, ServerContext ctx); }    // ctx: player, level, guards
public interface ClientHandler<M> { void handle(M msg); }
public final class MessageRegistry {                                    // common: built once from feature MessageSets
    public <M extends Message> MessageType<M> c2s(String id, StreamCodec<..., M> codec, ServerHandler<M> handler);
    public <M extends Message> MessageType<M> s2c(String id, StreamCodec<..., M> codec);
}
public final class ClientMessages { public static <M extends Message> void on(MessageType<M> type, ClientHandler<M> h); }
public interface Net { void toServer(Message m); void toPlayer(ServerPlayer p, Message m);
                       void toTracking(Entity e, Message m); void toAround(ServerLevel l, Vec3 c, double r, Message m); }
```
- **One set per feature.** Each feature has one `*Messages` class (`LiveryMessages`, `WingmanMessages`, `CombatMessages`, ...) declaring its types. `McheliMod` registers the list of sets.
- **Client handlers** are registered only from client init. Common code never references them, so `ClientPacketHandler` disappears.
- **Codecs** use vanilla `StreamCodec.composite` with nested sub-records once a message grows past 6 fields, plus `net/Codecs`: `boundedUtf(n)`, `finiteFloat()`, `list(codec, max)`, `enumOf(E)`, `uuid()`. Bounds live in the codec, not in handlers.
- **Mega-packets split into sealed records:**
  - `PacketBaseAction` becomes `SaveTaxiRoute`, `DeleteTaxiRoute` and `DispatchOrder`, with `List<String>` instead of CSV.
  - `PacketHandleCommand` becomes two messages, one per direction.
- **Server guards** live in `ServerContext`: `controlledAircraft()`, `requireOwnerOrOp(...)`, `withinReach(...)`. Sanitising weapon positions becomes one helper.
- **Pilot input.** The control packets carry the core's `PilotInput` record. This plan provides only the transport.
- **Protocol.** Ids follow `mcheli:<feature>/<name>`, and `PROTOCOL` is bumped. Ids are not persisted, so constraint (b) is not affected.

### 2.6 Config: typed sections, same file and keys

```java
public sealed interface Option<T> permits BoolOption, IntOption, DoubleOption, StringOption, ListOption, KeyOption {
    String key(); T defaultValue(); Side side(); String comment(); String validSince(); T coerce(String raw, DiagnosticSink s);
}
public record CombatSettings(DamageFactors damage, List<String> ignoredBulletHit, boolean explosionDestroysBlocks, ...) {}
public record McheliSettings(KeySettings keys, ControlSettings controls, RenderSettings render, HudSettings hud,
        CombatSettings combat, CollisionSettings collision, LiverySettings liveries, ChunkSettings chunks,
        DebugSettings debug, ExperimentalSettings experimental) {}
public final class SettingsStore<S> { S current(); void update(UnaryOperator<S> change); void save(); }
```
- **The file stays the same:** `config/mcheli.cfg`, case-insensitive `key = value`, `;` comments, the `MOD_Version` and `validVer` gate, repeated keys (`CommandPermission`, `IgnoreBulletHitItem`, `DamageVs*`) as `ListOption`, and the same write order.
- **Derived lists are computed once.** Block lists and damage factors are built in the record constructors and exposed as `BreakRules forVehicle(VehicleClass)` instead of `int n` switches.
- **Where the config may be read.** The vehicle layer gets its settings handed in as an immutable
  snapshot (Part 1 §2.6); only `vehicle.settings.VehicleSettingsHolder` reads `McheliConfig` for it. UI
  (screens, the HUD), commands, client input and rendering, and the features outside the vehicle core
  (weapons, explosions, wingmen, the tank's terrain collision helpers, item placement) still read
  `McheliConfig` directly for now; that is the boundary, and moving a reader behind a snapshot is
  always allowed.
- **Server-synced settings.** `ServerSettings` statics become a `SyncedServerSettings` record held by the client session and reset on logout.
- **Wingman config.** `WingmanConfig` uses the same machinery with its own file (`mcheli-wingman.cfg`; the legacy TOML import is kept). The commands that change it go through `SettingsStore.update` and save.
- **Ignored legacy options.** `ItemID_*` and the like are kept as read-and-written but unused, so existing files round-trip.

### 2.7 Wingman: brain, roster and persistence separated

```java
public final class WingmanService { /* per MinecraftServer; owns Roster, ticks brains, cleared on stop */ }
public record WingmanAssignment(UUID aircraft, @Nullable UUID owner, LeaderRef leader, int slot,
        AttackMode attack /*NONE, MANUAL, AUTO*/, WeaponRole role, @Nullable UUID manualTarget) {}
public sealed interface FlightPhase permits TaxiOut, Align, TakeoffRoll, VtolTakeoff, Climb, Transit, OnStation,
        StrikePass, Enroute, Attack, Loiter, Rtb, Descend, Downwind, Base, Final, Landing, VtolLand, TaxiIn, Parked,
        FollowLeader, Idle {
    PhaseStep tick(PhaseContext ctx);                 // returns ControlDemand and optionally the next phase
}
public record ControlDemand(float throttle, float yaw, float pitch, float roll, GearCommand gear,
        Optional<FireRequest> fire, Optional<TaxiPush> taxi, Optional<NozzleCommand> nozzle) {}
public interface AircraftControls { AircraftReadout read(); void apply(ControlDemand d); }   // over the core's VehicleState
public record Perception(AircraftReadout self, Optional<Pose> leader, List<Contact> contacts, TerrainProbe terrain) {}
public final class AirfieldData extends SavedData { /* markers + taxi routes; NBT keys exactly as today */ }
```
- **The brain is pure:** `(Perception, phase state) → ControlDemand`. It is unit-testable without a world.
- **Scratch state moves into phase records:** `taxiPreX` and `landingRollSpeed` go into `Landing` and `TaxiIn`, `vtolOnSent` into the `AircraftControls` adapter.
- **Weapon roles come from the projectile profile.** `WeaponRole` is derived from `WeaponKind` and the guidance spec instead of type strings.
- **Persistence stays runtime-only.** The roster is not persisted, as today, but it is now per server. That fixes the leak between worlds; this is a documented fix. Markers and routes keep their SavedData keys.
- **Commands** become a real Brigadier tree (`/wingman follow <uuid>`, `/wingman order ...`) with suggestions and translatable messages. The legacy greedy syntax stays parseable for one release.

**Status.** Stage 10 is built, with these differences from the sketch above:
- `WingmanService` sits in `wingman/` itself; `roster/` holds `Roster`, `Wingman` (owner, leader, slot, `AttackOrders`, `Autopilot`) and `WingmanAccess`. `control/` is the world side: it perceives entities, resolves targets, releases weapons and hands the brain's work to the aircraft. Chat lines are in `text/WingmanText`.
- The phases stay one enum, `FlightPhase`, whose names appear in status lines and recordings. Each state machine is one class that holds all its transitions: `FollowBrain`, `DepartureBrain`, `RecoveryBrain`, `VtolBrain`, `OrderBrain` (with `HomeBase` for the lookups), `NodeMissionBrain`, plus `CombatBrain` for attack geometry and `PhaseControl` for how each phase is flown. `WingmanBrain` runs them in four steps per tick. The brain lives in `:core` (`core.wingman.brain`), so it has no Minecraft dependency; the module boundary and an architecture rule check that.
- There is no `ControlDemand` routed through `ControlInput`/`AttitudeInput`. Everything the autopilot does is an absolute write (a throttle level, attitude angles, motion on the ground) that keys and stick deflections cannot reproduce bit for bit. The brain therefore works `Controls`: before the level ticks they are recorded into a `PilotCommand`, which the aircraft's `ScriptedPilotComponent` replays as the first step of its own tick; after the level has moved, corrections go through the same component at once, because the catch-up boost and weapon release need the leader and the target where they ended up. Flying through `ControlInput` is a behaviour change for later.
- `AirfieldData` is a facade over the two `SavedData` files the markers and taxi routes always had; mission routes stay in the config directory and are read when a server starts.
- `WeaponProfile` still comes from weapon type strings.

### 2.8 GUI: a small widget toolkit (`ui/client`)

```java
public record Theme(int text, int muted, int accent, int panel, int border, int padding, int rowHeight) {}
public abstract class McScreen extends Screen { protected final Theme theme; protected abstract void build(Layout root); }
public final class Widgets {
    static Button button(Component label, Runnable onPress); static Toggle toggle(Component label, BooleanSupplier get, BoolConsumer set);
    static <T> Slider<T> slider(Component label, SliderModel<T> model); static <T> ListView<T> list(...);
    static TextInput text(...); static TabBar tabs(...);
}
public interface Layout { Layout column(); Layout row(); Layout grid(int cols); Layout gap(int px); <W extends AbstractWidget> W add(W w); }
```
- No int ids. Container screens are used only where a real `AbstractContainerMenu` exists (aircraft inventory, UAV station, drafting table). `ConfigScreen` becomes a plain `McScreen` with tabs, and `ConfigMenu` is deleted.
- Every user-facing string is a `Component.translatable` with a key in `lang/en_us.json`.
- The large screens split into panels: `GuiBaseConfig` into Routes, Missions and Markers panels; `DraftingTableScreen` into a recipe browser, a preview and a craft panel.

**Status.** Stage 9 is built, with these differences from the sketch above:
- `ui/client` holds `Theme`, the two bases `McScreen` and `McContainerScreen`, `Layout` (column, row, grid; it places widgets and hands them to the screen), and `Widgets`, which creates `Button`s with `Runnable` handlers, `Toggle`, `Slider` over a `SliderModel`, `EditBox` text fields, `ListView`, `ScrollPane` (a clipped window onto widgets and painted content), the mod's textured `ScrollBar` and `TabBar`. `SlotFrames` draws inventory slot frames for container screens laid out in code. There is no `Layout` interface on the screen: a screen asks for `column(x, y)`, `row(x, y)` or `grid(...)`, which add to it.
- Screens build their widgets in `build()` and call `rebuild()` when their state changes the widget set; the state lives in the screen, not in widgets. The options screen keeps it in a `ConfigDraft` that applies every option it shows onto the config on save, with the sliders' old rounding.
- Screens over an empty menu became plain screens: the options (`config/client/ConfigScreen`) and the multiplayer manager (`multiplay/client/MultiplayManagerScreen`), both opened on the client; `ConfigMenu`, `ScoreboardMenu` and their menu types are gone. Container screens remain for the aircraft panel and trunk, the classic aircraft screen, the UAV station and the drafting table.
- The in-game overlays were `OverlayScreen`s that were never opened; `hud/client/HudOverlay` replaces it and draws on the frame's `ScreenCanvas`, so the static `Gui2D` frame is gone.
- The base planner splits into `BasePlannerScreen` with a `TaxiRoutesTab` and a `BaseMissionTab` (the markers are edited on their own screen, `MarkerSettingsScreen`); the drafting table into the screen, `RecipeBrowser` and `ModelPreview`.

### 2.9 Removing the wrappers and root-package debris

| Today | Target |
|---|---|
| `wrapper/BaseEntity` statics | inline vanilla; culling distance becomes a small `RenderDistance` helper used by the entities that need it |
| `GLOBAL_Y_OFFSET` | stays in the core |
| `FakePlayerBase`, `SideHelper`, `WorldHelper`, `BlockHelper` | `util/Inventories`; the rest is inlined vanilla |
| `BaseItem(int id)` | `McheliItem` with no id parameter |
| `LegacyButton`, `LegacyContainerScreen` | `ui/client` (done in stage 9) |
| `LegacyParticle`, `particles/` and `fx/` | one `fx/client` with `ParticleStyle` as data |
| `SoundHelper`, `SoundUpdaterBase`, `ClientHelper`, `GameRendererHelper`, `KeyMappingHelper` | `client/` or `sound/client` |
| `MCHeli` statics, `CommonProxy`/`ClientProxy` | `McheliItems`, `McheliBlocks` (Supplier holders) and the `ClientServices` entry point |
| `ModLib` | `util/math` and `world/Terrain` (`getBlockIdY` returns a `BlockState`, not an id) |
| client-only bits of `ModLib` | `client/` |
| `EntityInfo*`, `RWR*`, `RadarType` | `combat/sensor` |
| `ContentRecipes`, `Recipes` | `content/recipe` |

`LegacyGL` and `wrapper/modelloader` belong to the core's rendering and are covered in Part 1.

**Status.** Stage 11 is built, with these differences from the sketch above:

- The root package holds only `McheliMod`, the entry point. The wiring it delegates to is the `setup`
  package: block and item registration, the creative tabs, the message registry and the common event
  hooks, with `setup/client` for the client's (`McheliClient`, `ClientEventHooks`, renderer and item
  model registration). Nothing but the entry point and the loader glue depends on `setup`.
- `CommonProxy` and the reflective `ClientProxy` are gone. Common code reaches the client through three
  small interfaces that `Services.sided` finds through `META-INF/services` on a physical client, and
  that answer with harmless defaults on a dedicated server: `platform.LocalClient` (the player at the
  keyboard, the camera, local sounds), `aircraft.VehicleClientHooks` (engine sound, model reload) and
  `fx.EffectStyle`. Everything else the proxy did became a direct call.
- Fixed items and blocks are `RegistryRef` lookups in `registry.McheliItems` and `McheliBlocks`, so
  holding one needs no mutable static. Entity types are declared by the feature that owns them
  (`AircraftEntityTypes`, `CombatEntityTypes`, `EquipmentEntityTypes`, `WorldEntityTypes`).
- `ModLib` became `util.math.Degrees` (now `core.math.Degrees`) and `Rotations`, `util.Terrain` (block probes return a `Block`),
  `client.camera.ViewEntity` (view entity and held-item toggles), `combat.HurtTime` and its callers'
  own code. `BaseEntity` stays as the base class `entity.McheliEntity` (invulnerable by default,
  culled by size times a weight); its static helpers are gone, and entity ids are compared through
  `util.EntityIds`, which keeps the id semantics the integrated server relies on.
- The two particle systems sit behind `fx.Effects`, with `LegacyEffects` and `ModernEffects` (which
  draws the modern sprites and falls back to the legacy particles) in `fx/client`. The style is still
  picked per effect from `useModernParticles`.
- `LegacyGL` and the model loaders are in the client toolkit, `client.render` and `client.model`:
  they are plain client code that every feature's renderers share. Vehicle-only pieces (cameras,
  on-demand models, static crawler tracks) moved to `vehicle/client`.
- The gunner is split into `GunnerGunnery` and `GunnerCountermeasures`; which projectiles are guided
  threats homing on a vehicle is answered by `TargetFilter`. The UAV station is split into its
  pairings, last controlled UAV, launching, breaking, client motion, operator seat and tablet link.
- The legacy `weapon` package is folded into `combat.weapon`.
- After the GUI rebuild, its menus and screens live with their features: the aircraft panel, trunk and seat overlay in `aircraft`, the UAV station screen and camera feed in `world.uavstation`, the config screen and development reloads in `setup.client.config`, and the menu-to-screen bindings in `setup.client`. Menu types are declared like the entity types (`AircraftMenuTypes`, `WorldMenuTypes`). `gui`, `factories` and `wrapper` are gone. Stage 12 dissolved `event` too: the client tick handler base is `client.input.ClientTickHandler`, and each feature's tick handler sits in its own `client` package.
- `ArchitectureRulesTest.featurePackagesFormNoCycles` forbids package cycles. Since stage 12
  `package-cycles.txt` is empty: a new cycle fails the build.

---

## 3. Cross-cutting rules

### 3.1 Dependency direction (a lower layer never imports a higher one; features never form cycles)

```
                neoforge / fabric  (glue; may import anything)
                         │
     client shells:  */client/**  ──────────────► any common package
                         │
  ┌────────── features (no cycles between them) ─────────────────────────────┐
  │ wingman ─► combat ─► (core API) ◄─ equipment, world, multiplay, livery   │
  │ hud.program/vars ─► combat (views only), core render snapshot             │
  └───────────────────────────────────────────────────────────────────────────┘
                         │
     net (transport) ◄── features declare their own messages and handlers
     config      content.registry/def ─► core.content.schema ─► util
                         │
               platform (interfaces)      util (pure)
```
- `content.def` may use `ResourceLocation`, `Vec3` and `Component`, but never `world.entity..`, `world.level..` or `client..`.
- Every feature may use `:core`; `:core` uses no feature, no Minecraft and no loader (its module boundary).
- `combat`, `fx` and the other features see vehicles only through the core API, `api.vehicle` and `entityview` (Part 1 §2.5), and the ports `combat` declares (`Shooter`, `ArmedVehicle`, `TargetDesignation`).
- `net` knows no feature.

### 3.2 Immutability and state
- Definitions, specs, messages and settings snapshots are records. Their collections are `List.copyOf` or `Map.copyOf`.
- Settings that many readers share are derived snapshots, not copies kept in sync:
  `VehicleSettingsHolder` holds the vehicle settings snapshot in a final atomic reference next to the
  stores it is derived from, and rebuilds it when one of them was replaced.
- Mutable state lives only in entities, components, sessions (`HudSession`, `HandheldWeaponSession`), per-server services (`WingmanService`, `ThreatBoard`, `LiveryManager`, `OperatorRequests`, `ExplosionEffectSender` on the server; `AircraftIndex`, `CombatRandom`, `TargetDesignation` on the level) and per-client services, which `setup.client.ClientSession` builds and hands out.
- A static field is `final`, except in two cases, each marked with an annotation from `util.state` that the architecture rule reads:
  - `@RegistryHolder`: assigned while the game registers its content or constructs the mod, then only read (registry handles, the loader's payload registrar, values handed in at start-up such as the weapons' vehicle damage targets).
  - `@StaticCache(reset = "...")`: a cache whose loss only costs time. Its class has the named static method without parameters that drops it, and the owner of its lifecycle (a resource reload, leaving a world) calls that method. The rule fails when the method is missing.
- Anything else (a counter, the current frame, a pending request) is instance state of a per-client or per-server service, never a static field.
- Randomness is injected (`RandomSource`), never `static Random`, so behaviour snapshots can be deterministic.

### 3.3 Logging, errors and naming
- One SLF4J logger per subsystem (`Log.CONTENT`, `Log.COMBAT`, `Log.NET`, `Log.WINGMAN`).
- Nothing logs per tick or per frame without `RateLimitedLog`.
- Content and config problems become `Diagnostic`s that are collected, summarised once per load, and written in full to `logs/mcheli-content.log`. They never throw out of loading.
- At run time, a failing HUD program disables itself once, as today. A message that fails to decode is dropped and logged with the sender. Content lookups return `Optional` and never `null`.
- Naming:
  - messages are `*Message` records in `<feature>/net`;
  - definitions are `*Definition`, specs `*Spec`;
  - client classes live under `client`;
  - no `I` prefixes (`IParser` becomes `ContentReader`) and no `Manager` or `Helper` suffixes in new code;
  - user-visible text is translatable; no `§` codes.
- No Lombok in rewritten packages; records replace it.

### 3.4 Size targets
- Classes: 400 lines as the norm, 500 as the hard ceiling the architecture tests enforce, without exceptions.
- Methods: at most 40 lines.
- Schema declarations: one file per top-level YAML section once a type has more than about 60 fields.

### 3.5 Architecture tests (JUnit 5 in the `:architecture` module, ArchUnit plus small reflection and source scans)

The rules started with allow-lists of the violations of their day, which each migration stage shortened and never grew. Since stage 12 every list is empty and the mechanism is gone: the rules are absolute (see [ARCHITECTURE_RULES.md](ARCHITECTURE_RULES.md)).

1. Nothing outside `..client..` and `..neoforge..` depends on `net.minecraft.client..` or `com.mojang.blaze3d..`.
2. Nothing outside `..neoforge..` depends on `net.neoforged..`.
3. `slices().matching("com.ivomens.mcheli.(*)..")` is free of cycles.
4. Layer rules from 3.1: `content..` does not depend on `combat..`, `wingman..` or `hud.client..`; `net..` does not depend on feature packages; `content.def..` does not depend on `net.minecraft.world.entity..` or `..level..`.
5. Every class implementing `Message` is a record, and every `MessageType` constant is registered exactly once. This is a reflection scan against the registry.
6. Classes in `..content.def..`, `..config..Settings` and `..*Spec` are records or enums.
7. Static fields are `final` unless the class is annotated `@StaticCache`, `@RegistryHolder` or `@SessionState`.
8. No `Class.forName` or `setAccessible` outside `platform` and `neoforge`.
9. Source scans (plain Java over every module's `src/main/java`): line counts against the ceiling, no `§`, no `lombok.` imports in rewritten packages.
10. Vehicle components (`ComponentRulesTest`): no component fetches another from the vehicle entity, the constructor graph of every vehicle kind has no cycle, `@TickOrder(after = ...)` names only constructor dependencies, and the generated [COMPONENTS.md](COMPONENTS.md) is current.

---

## 4. Migration order (each stage ships a working mod)

| # | Stage | Size | Risk | Guarded by |
|---|---|---|---|---|
| 0 | Safety nets (section 5): golden dumps, firing snapshots, HUD traces, config round-trip, codec tests, arch-test baseline. Small enabling refactors only: injectable `RandomSource` in weapons and bullets, `ScriptHudContext` taking its canvas size as a parameter. | ~3k lines of tests | low | — |
| 1 | Config: typed sections behind the old `McheliConfig.X` names as delegating accessors; migrate the ~80 reader files; delete `ConfigOption`, `ServerSettings` and the `WingmanConfig` mirrors. | M (~2.5k) | low | config golden round-trip; full gametests |
| 2 | Networking transport: `MessageRegistry`, codecs and `Net`; migrate packets one feature at a time (livery → wingman → multiplay → combat → control transport); split mega-packets; delete `PacketBase` and `ClientPacketHandler`. | L (~6k) | medium (a forgotten field) | codec round-trip of every type; Livery, Wingman and Multiplay gametests; client scene |
| 3 | Content schema for throwables, then weapons; the old `WeaponInfo`/`ThrowableInfo` become thin adapters (`WeaponInfo.of(def)`) so runtime code is untouched. | L (~5k) | medium (TXT ordering, clamps) | canonical dump golden (YAML + TXT fixtures); emitter golden; diagnostics golden |
| 4 | Content schema for HUD, then reuse the infrastructure for `VehicleDefinition`; delete `IParser`, `IEmitter`, `TxtParser`, `YamlParser` and `ContentRegistries` statics once vehicles move. | M + coordination | medium | as stage 3, plus the HUD trace snapshot |
| 5 | Combat leaf extraction: `ExplosionSpec`, countermeasures (`IronCurtain` split), `TargetFilter`/`ForeignVehicles`, `AircraftIndex` (also used by `MixinEntity`), `ThreatBoard`. Done: see the status note in §2.2. | M (~3k) | medium | firing snapshots; RWR/lock gametest; Stress `firefight` ms/tick within ±5% |
| 6 | Weapons proper: `WeaponKinds`, `FireMode`s, `WeaponGroup`, `Shooter` (needs the core's seat API); then `ProjectileEntity` behind all 11 entity types; delete the 18 weapon and 11 bullet subclasses. Done: see the status note in §2.2. | XL (~8k) | high | firing snapshots (bit-exact trajectories for 60 ticks); VehicleGameTests weapon cycling; Stress `firefight` |
| 7 | HUD: expression compiler (differential test against Aviator), `HudProgram` lowering, `HudRenderer`/`HudCanvas`, instruments and the modern HUD on the canvas; drop Aviator from the jar. Done: see the status note in §2.4. | L (~3.5k) | medium (expression semantics) | expression differential corpus; HUD trace golden; client scene screenshots |
| 8 | Handheld equipment (launchers, GLTD, throwables, rangefinder) on `WeaponGroup` and sessions. Done for launchers, GLTD and throwables. | M | low | handheld firing snapshot gametest |
| 9 | UI toolkit, then screens: Config → GPS/overlays → UAV station → drafting table → livery → scoreboard → wingman planner. Done: see the status note in §2.8. | L (~5k) | low (visual) | client scene; manual checklist; drafting recipe gametest |
| 10 | Wingman: per-server `WingmanService`, `AirfieldData`, extracting `AircraftControls`, then phase-by-phase brain extraction (Follow → Departure → Recovery → Mission phases); Brigadier commands. Done: see the status note in §2.7. | L (~7k) | high (AI behaviour drift) | WingmanOwnershipGameTests plus the new scenario gametests and brain unit tests |
| 11 | Wrappers, root package, `world/` entities (gunner, parachute, chain, cargo, UAV station), sound and fx unification, `MixinEntity` index. Done: see the status note in §2.9. | M | low | gametests; Stress `parked` and `flying` |
| 12 | Remove adapters; empty the arch-test freeze store, which makes the rules strict. Done: see the status notes in §2.3; the rules have no allow-lists left. | S | low | arch tests |

Stages 1, 2 and 3 can run in parallel. Stage 6 waits for the core's `Shooter`, seat and `VehicleState` interfaces. Stage 10 waits for the core's control input API.

---

## 5. Tests to add before each rewrite

1. **Content canonical dump (before stages 3 and 4).** A test-only, reflection-based dumper writes every field of every loaded info as sorted JSON per file, under `src/test/resources/golden/content/`.
   - Inputs: all 353 bundled YAML files plus a TXT fixture corpus. No TXT files are bundled today, so the corpus must cover every `case` key of `TxtParser`, including the ordering cases: `gravity` before and after `type=bomb`, seconds-to-ticks conversion, repeated `addseat`.
   - A coverage check fails if a schema key appears in no fixture.
   - After the rewrite the same dump is produced from the records through a field-name mapping file. Golden files change only through a reviewed diff.
2. **Emitter round-trip:** TXT → definition → YAML → definition must be equal. The YAML output is a golden file.
3. **Diagnostics golden:** a set of broken files (wrong types, out-of-range values, unknown keys, unsupported keys, missing HUD `endif`, missing weapon references) with the expected messages.
4. **Weapon firing snapshots (gametest, before stages 5 and 6).** For each bundled weapon: a flat world, a fixed shooter pose, a seeded `RandomSource`, and a static target dummy for guided kinds. Record:
   - the spawned entities (type, position, motion);
   - per-tick projectile positions for 60 ticks;
   - fuze and impact tick, explosion parameters (`ExplosionSpec` fields), ammo, heat and cooldown afterwards, and the display name per mode.

   The output is compared to a golden JSON with a 1e-6 tolerance. There are separate snapshots for flare, chaff, APS and iron-curtain interactions against a heat seeker and a radar missile.
5. **Lock-on tests:** `TargetFilter`, `inLockAngle` and the lock-count progression against stealth, with gametest entities in air, on ground and in water.
6. **HUD expression differential (before stage 7).** Collect every formula from the bundled HUDs, the TXT HUD fixtures and the community packs we can get. Evaluate each with Aviator (test dependency) and with the new compiler over a grid of variable assignments that mixes int, double and bool types. Results must be equal.
7. **HUD trace snapshot.** A `RecordingHudCanvas` logs each draw call (primitive, coordinates, colour, text) for a set of fixed `HudFrame` inputs per bundled HUD, including gate, `exit` and `call` cases and colour carry-over across two frames. The output is a golden file.
8. **Config golden:** read sample `mcheli.cfg` and `mcheli-wingman.cfg` files from older versions (including old `MOD_Version` values and repeated keys), write them back, and require the same keys, values and order.
9. **Codec round-trip (before stage 2):** for every registered message, generate sample values per field type and check that encode then decode gives an equal record. Bounds are rejected (oversized lists and strings).
10. **Wingman scenarios (gametest, before stage 10):**
    - followers converge to their slots within N ticks;
    - an autonomous plane goes from a marker runway through takeoff to CLIMB, and lands back to PARKED;
    - a VTOL takes off and lands on a helipad;
    - the roster is empty after a server restart in the same JVM.

    After extraction, the same scenarios become brain unit tests with scripted `Perception`.
11. **Performance baselines:** record ms/tick for Stress `parked`, `flying` and `firefight` and `ClientStress` before stage 5, and fail CI on a regression above 5% at stages 5, 6, 10 and 11. Add a microbenchmark of `MixinEntity` under 500 mobs near a carrier. Done in part: the server benchmark and its CI gate (allocation per tick +10 %, median tick time against a calibration loop +35 %) and the in-game client benchmark are described in [BENCHMARKS.md](BENCHMARKS.md); the `MixinEntity` microbenchmark is not written yet.
12. **Architecture tests:** frozen baseline at stage 0, tightened at every stage.

**Gameplay changes, each to be listed explicitly in the release notes:**
- the wingman roster no longer leaks between worlds;
- invalid HUD formulas are reported at load time instead of failing at draw time;
- the server no longer builds a new weapon per handheld shot;
- HUD format v2 with real nesting is available.

Everything else, including the squared proximity of air-to-air missiles, the HUD gate quirks and the TXT ordering rule, is kept and given a name.

