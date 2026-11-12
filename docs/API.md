# The MCHeli: Revived API

Other mods build against `mcheli-revived-api`, a small jar of interfaces and enums that MCHeli implements.
With it an addon can find vehicles and read their state, look up the loaded vehicle and weapon types, listen
to what vehicles and weapons do (and stop a shot), and ship content packs inside its own jar. The API is the
same on NeoForge and Fabric: it uses vanilla Minecraft types only, and its events have their own small bus
instead of either loader's.

Everything else in MCHeli is internal and changes without notice. If the API lacks something you need,
open an issue rather than reaching into `com.ivomens.mcheli` outside `com.ivomens.mcheli.api`.

## Getting the jar

The API has its own version, independent of the mod's: `api_version` in `gradle.properties` (1.0.0 at
the time of writing).

* **From a release:** every [GitHub release](https://github.com/IVomens/MCHeli-Revived/releases) carries
  `mcheli-revived-api-<api version>.jar` and its `-sources.jar`. Put them in your project (for example in
  `libs/`) and add `compileOnly files('libs/mcheli-revived-api-1.0.0.jar')`.
* **From a checkout:** `./gradlew :api:publishToMavenLocal` publishes
  `com.ivomens.mcheli:mcheli-revived-api:<api version>` (with sources and javadoc) to `~/.m2`. Then:

  ```groovy
  repositories { mavenLocal() }
  dependencies { compileOnly 'com.ivomens.mcheli:mcheli-revived-api:1.0.0' }
  ```

The dependency is **compile only**. At run time the API's classes come from the MCHeli mod jar, so never
bundle the API jar in yours; declare MCHeli as a required dependency instead (`neoforge.mods.toml` or
`fabric.mod.json`). For development runs, put the MCHeli mod jar on your run classpath
(`runtimeOnly files(...)` / `modLocalRuntime`).

The jar is compiled against Minecraft 1.21.1 with Mojang's names, as NeoForge's and Loom's development
environments use. Its POM lists no dependencies: Minecraft comes from your own loader setup.

## Versioning

The API follows [semantic versioning](https://semver.org/), checked by the build:

* **Major** (2.0.0): something was removed or changed. A method, type, enum constant or event goes away,
  a signature changes, or an event stops firing where it did. Addons have to be updated.
* **Minor** (1.1.0): something was added. Addons compiled against 1.0 keep working with every 1.x.
* **Patch** (1.0.1): documentation and fixes in how MCHeli implements the API; no signature changes.

An addon compiled against `a.b.x` works with every running `a.c.y` where `c >= b`;
`McheliApi.get().apiVersion()` says which one is running.

The interfaces MCHeli implements (all but `ContentPackProvider`) may gain methods in a minor version, so do
not implement them. The two enums may gain constants in a minor version, so a `switch` over them needs a
`default` branch. `ContentPackProvider`, which addons implement, only changes in a major version.

### The signature check

`api/api.txt` lists every public signature of the API, one per line, under a header naming the version it
was written for. `ApiSignaturesTest` (part of `./gradlew build`) fails while it differs from the compiled
API. To change the API:

1. Make the change, and bump `api_version` in `gradle.properties`: major when any signature was removed or
   changed, minor when signatures were only added.
2. Update `ApiService.API_VERSION` in `common` to match (`ApiServiceTest` checks it).
3. Run `./gradlew :api:updateApiDump`. It rewrites `api.txt`, and refuses when the bump is too small for
   the change: a removed or changed line needs a new major version, an added line a new minor one.
4. Commit `api.txt` with the change, and say in `CHANGELOG.md` what changed in the API.

The architecture tests also check that the API stays clean: classes in `com.ivomens.mcheli.api` use only
Java, the `javax.annotation` nullness annotations, non-client Minecraft and each other, and all of them come
from the `:api` module.

## Threads and sides

Each type's javadoc says on which side and thread it may be used. In short:

* `McheliApi.get()`, registering listeners and unsubscribing work from any thread.
* Vehicle views and level lookups read entities: use them on the level's thread (the server thread for a
  server level, the client thread for a client level).
* Events fire on the thread where they happen: the server thread for everything except the lock-on events,
  which fire wherever the seeker runs (the locking player's client, or the server for AI).
* `ContentPackProvider.registerPacks` runs once, while MCHeli is constructed.

## The types

### `McheliApi` (package `com.ivomens.mcheli.api`)

The entry point. `McheliApi.get()` returns the running implementation (found with `ServiceLoader`).

| Method | Contract |
|---|---|
| `apiVersion()` | The API version the running MCHeli implements. |
| `vehicleOf(Entity)` | The vehicle the entity is, belongs to (a seat or hit box) or is aboard (pilot, passenger, remote operator). |
| `vehicles(Level)` | Every vehicle loaded in the level. |
| `vehiclesIn(Level, AABB)` | The loaded vehicles whose bounding box intersects the area. |
| `content()` | The loaded vehicle and weapon types (`ContentLookup`). |
| `events()` | The event bus (`EventBus`). |

```java
McheliApi api = McheliApi.get();
for (Vehicle vehicle : api.vehiclesIn(level, new AABB(pos).inflate(64))) {
    if (vehicle.kind() == VehicleKind.HELICOPTER && !vehicle.isDestroyed()) {
        warnPlayersNear(vehicle.entity());
    }
}
```

### Vehicles (package `com.ivomens.mcheli.api.vehicle`)

| Type | Contract |
|---|---|
| `Vehicle` | A vehicle entity, read-only: `entity()`, `kind()`, `isUAV()`, `isDestroyed()`, `roll()`, `throttle()`, `seats()`, `signature()`. Every MCHeli vehicle entity implements it. |
| `VehicleKind` | `HELICOPTER`, `PLANE`, `SHIP`, `TANK`, `VEHICLE`; `directory()` is the kind's content folder. |
| `Crew` | Who sits where: seat 0 is the pilot, 1 and up the passenger seats. `count()`, `riderOf(seat)`, `seatIdOf(entity)`, `passengers()`, `isAboard(entity)`. |
| `Targetable` | What a seeker sees: `stealth()`, `isFlareUsing()`, `announcedChaffTime()`, `isBreakingRadarLocks()`, `isIronCurtainRunning()`. |
| `VehicleSeat` | A passenger seat entity; `getParent()` is its vehicle. |
| `VehicleHitBox` | An extra hit box entity of a large vehicle; `owner()` is its vehicle. |

```java
if (entity instanceof Vehicle vehicle) {
    Entity pilot = vehicle.seats().riderOf(0);
    boolean hidden = vehicle.signature().stealth() > 0.5F || vehicle.signature().isFlareUsing();
}
```

### Content (package `com.ivomens.mcheli.api.content`)

| Type | Contract |
|---|---|
| `ContentLookup` | `vehicles()`, `vehicle(kind, id)`, `vehicleOf(Vehicle)`, `weapons()`, `weapon(id)`: snapshots of the loaded types. A reload replaces them, so look up again rather than keeping them. |
| `VehicleInfo` | A vehicle type: `kind()`, `id()` (the file name), `displayName()`, `packId()` (the pack it came from). |
| `WeaponInfo` | A weapon type: `id()`, `displayName()`, `type()` (how it fires, such as `machinegun1` or `aamissile`), `packId()`. |
| `ContentPackProvider` | Implemented by an addon to ship content packs in its jar; found with `ServiceLoader`. |
| `ContentPackRegistrar` | `addPack(modId, folder)`: adds a folder of the mod's files as a content pack. |

```java
McheliApi.get().content().vehicleOf(vehicle)
        .ifPresent(type -> player.sendSystemMessage(Component.literal("You fly the " + type.displayName())));
```

#### Content packs in a mod's jar

1. Put the pack in a folder of your jar, laid out like a pack in `mcheli_addons/`
   ([ADDON_AUTHORING.md](ADDON_AUTHORING.md)): for example `src/main/resources/mcheli_pack/assets/mcheli/weapons/my_gun.yml`,
   with textures, models and sounds next to the content as in any pack, and an optional `pack.mcmeta`
   whose `addon.domain` names the pack (else the domain is your mod id).
2. Implement `ContentPackProvider`:

   ```java
   public final class MyPacks implements ContentPackProvider {
       @Override
       public void registerPacks(ContentPackRegistrar packs) {
           packs.addPack("mymod", "mcheli_pack");
       }
   }
   ```

3. List it in `src/main/resources/META-INF/services/com.ivomens.mcheli.api.content.ContentPackProvider`:

   ```
   com.example.mymod.MyPacks
   ```

MCHeli loads its content while it is constructed, possibly before your mod, so the provider is found
through `META-INF/services` and needs no code of yours to run first. At each start MCHeli copies the
registered packs into `mcheli_mod_packs/` in the game directory (a folder it owns and empties first), and
loads them after its bundled content and before the player's packs in `mcheli_addons/`: your files replace
bundled ones of the same type and name, and a player's pack can still replace yours. The packs of several
mods load in order of mod id. Everything else (validation, the content report, `/mcheli reload`, textures
and sounds) works as for any pack.

### Events (package `com.ivomens.mcheli.api.event`)

`EventBus.register(Type.class, listener)` adds a listener and returns a `Subscription` whose
`unsubscribe()` removes it. Listeners run in registration order; one that throws is logged and the others
still run. An event object is only valid while its listeners run.

| Event | When | Side |
|---|---|---|
| `VehicleSpawned` | A new vehicle entered a level (item, dispenser, command, another mod); not on chunk load. | server |
| `VehicleRemoved` | A vehicle left a level; `reason()` says whether for good (killed, discarded) or not (unloaded, changed dimension). | server |
| `VehicleDestroyed` | A vehicle's hit points ran out and it became a wreck; `attacker()` dealt the last damage. | server |
| `SeatBoarded` | An entity took a seat (`seatId()` 0 is the pilot), also when rider and vehicle load together. | server |
| `SeatLeft` | An entity left a seat, while the vehicle still lists it there. | server |
| `WeaponFired` | A weapon is about to fire one shot (vehicle, AI gunner, wingman or handheld launcher). **Cancellable**: a cancelled shot launches nothing and uses no ammunition. | server |
| `ProjectileImpact` | A projectile hit a block or entity, before its damage and explosion. | server |
| `LockOnStarted` | A seeker picked a target and started locking. | where the seeker runs |
| `LockOnCompleted` | A seeker's lock became complete. | where the seeker runs |
| `CountermeasureDeployed` | A vehicle started flares, chaff, an active protection system, the iron curtain or an ECM jammer (`CountermeasureType`). | server |

`Cancellable` (implemented by `WeaponFired`) has `isCancelled()` and `setCancelled(boolean)`; the last
listener's choice counts. `McheliEvent` is the marker every event type extends.

```java
EventBus events = McheliApi.get().events();
Subscription counting = events.register(WeaponFired.class, fired -> shots.incrementAndGet());
events.register(WeaponFired.class, fired -> {
    if (fired.shooter() instanceof ServerPlayer player && fired.level().getServer()
            .isUnderSpawnProtection(fired.level(), BlockPos.containing(fired.origin()), player)) {
        fired.setCancelled(true);
    }
});
events.register(VehicleDestroyed.class, destroyed -> {
    if (destroyed.attacker() instanceof ServerPlayer player) {
        awardKill(player, destroyed.vehicle());
    }
});
events.register(LockOnCompleted.class, locked -> {
    if (!locked.user().level().isClientSide()) {
        logAiLock(locked.user(), locked.target());
    }
});
counting.unsubscribe();
```

### Vehicle extensions (package `com.ivomens.mcheli.api.vehicle.extension`, since API 1.1)

An addon can change how vehicles behave in two ways, both chosen by the vehicle's file: **behaviours**
that ride along on vehicles of any kind (a winch, a beacon, a siren), and a **movement model** that replaces
a ground vehicle's physics (a hovercraft). MCHeli's own components and models stay internal; an addon sees
a vehicle only through the context objects below. The YAML side is in
[ADDON_AUTHORING.md](ADDON_AUTHORING.md#8-behaviours-and-movement-models-from-mods).

| Type | Who implements | Contract |
|---|---|---|
| `VehicleExtensionProvider` | addon | Found with `ServiceLoader` (`META-INF/services/com.ivomens.mcheli.api.vehicle.extension.VehicleExtensionProvider`) before any vehicle file is read; `register(registrar)` registers everything. |
| `VehicleExtensionRegistrar` | MCHeli | `behaviour(id, timing, decoder, factory)` and `movementModel(id, decoder, factory)`; an id is a `ResourceLocation` in your namespace. A duplicate id throws. |
| `SettingsDecoder<S>` | addon | `decode(CompoundTag)`: the file's settings map as NBT (maps, lists, int/long, double, string, byte for flags). Throw to reject; the message goes to the content report and the vehicle loads without it. Decode with a `Codec` through `NbtOps` if you like. |
| `BehaviourFactory<S>`, `MovementModelFactory<S>` | addon | Make the behaviour or model of one vehicle from the decoded settings; called again after a content reload replaced the file. |
| `VehicleBehaviour` | addon | Default hooks: `added`, `serverTick`, `clientTick`, `destroyed` (first tick it sees the wreck), `removed` (left the level for any reason, or replaced by a reload). |
| `BehaviourTiming` | enum | `BEFORE_MOVEMENT` (end of the systems phase, before the controls and physics) or `AFTER_MOVEMENT` (end of the follow-the-hull phase: hit boxes, parts and seats are in place). The ticks are steps of the vehicle's tick pipeline, checked like MCHeli's own components. |
| `BehaviourContext` | MCHeli | `id()`, `vehicle()` (the API `Vehicle`: entity, crew, state), `level()`, `isClientSide()`, `controls()`. |
| `MovementModel` | addon | `step(context)` on the server sets velocity and yaw; MCHeli then moves the vehicle with vanilla collision and calls `afterMove(context)`; `predict(context)` runs on clients between server updates. |
| `MovementContext` | MCHeli | Reads the state (`position`, `velocity`, `yaw`, `pitch`, `roll`, `onGround`, `inWater`, `waterDepth`, `horizontalCollision`, `isDestroyed`), the file's `gravity()`, `heightAboveGround(maxDepth)` and the `controls()`; writes `setVelocity`, `setYaw`. |
| `VehicleControls` | MCHeli | The pilot's `forward`, `back`, `left`, `right` keys and the engine `throttle`. |
| `VehicleExtensions` | MCHeli | `McheliApi.vehicleExtensions()`: `behaviourTypes()`, `movementModels()`, and per vehicle `behavioursOf(vehicle)`, `movementModelOf(vehicle)`. |

```java
public final class MyExtensions implements VehicleExtensionProvider {
    @Override
    public void register(VehicleExtensionRegistrar registrar) {
        registrar.behaviour(ResourceLocation.fromNamespaceAndPath("mymod", "beacon"), BehaviourTiming.AFTER_MOVEMENT,
                tag -> tag.contains("Interval") ? tag.getInt("Interval") : 10,
                (interval, context) -> new VehicleBehaviour() {
                    @Override
                    public void clientTick(BehaviourContext c) {
                        Entity hull = c.vehicle().entity();
                        if (hull.tickCount % interval == 0) {
                            c.level().addParticle(ParticleTypes.END_ROD, hull.getX(), hull.getY() + 3, hull.getZ(), 0, 0.05, 0);
                        }
                    }
                });
        registrar.movementModel(ResourceLocation.fromNamespaceAndPath("mymod", "hovercraft"),
                tag -> tag.getDouble("Thrust"), thrust -> context -> {
                    double yaw = Math.toRadians(context.yaw());
                    double push = context.controls().forward() ? thrust : 0.0;
                    double lift = context.heightAboveGround(4) < 1.0 ? 0.05 : context.gravity();
                    Vec3 v = context.velocity();
                    context.setVelocity(new Vec3((v.x - Math.sin(yaw) * push) * 0.95, lift, (v.z + Math.cos(yaw) * push) * 0.95));
                });
    }
}
```

**When things run.** The providers are asked once, the first time MCHeli reads a vehicle file or makes a
vehicle, on whichever thread that is (normally while MCHeli constructs). Settings are decoded on the
content-loading thread at every load (start-up, `/mcheli reload`, `/mcheli validate`), on both sides; one
settings object is shared by all vehicles of the type, so keep it immutable. Behaviours and models are made
per vehicle, on the level's thread, the first time the vehicle ticks with its type. A vehicle at rest (parked
on the ground, nobody aboard, engine off) skips its tick and with it its behaviours; a hovering vehicle never
rests. Nothing a behaviour or model holds is saved or synced: keep state on the server and send clients what
they need yourself. A hook that throws is logged; the behaviour is dropped from that vehicle, and a failing
movement model leaves the vehicle to its kind's own movement until the file is loaded again.

**Movement models in detail.** In API 1.1 only the `VEHICLE` kind (`vehicles/`) takes a `Movement`; other
kinds report the key as unknown. The model replaces the kind's key-driven push and steering and its physics
step; the kind's throttle, engine sound, attitude handling (pitch and roll), weapons, seats, HUD, camera and
networking stay. Each server tick the model's `step` sets the velocity (blocks per tick) and yaw; MCHeli moves
the vehicle by that velocity through the same collider the built-in models use (walls, step-up, landing), then
`afterMove` sees what collision left and may damp it. Clients treat the vehicle as they treat every vehicle:
position updates from the server are blended in over a few ticks, and between them the client dead-reckons
(moves by the last velocity) and calls `predict`, where the model can apply the drag it applies on the server.
The pilot's own client predicts the same way, so a model needs no client code of its own. Use no randomness or
wall clock in a model if its vehicles should behave the same every time.

**Cost.** With no provider installed, nothing changes: the vehicles' tick has no extra steps and the ground
vehicle's physics calls its built-in model directly. With a provider installed, a vehicle whose file names
none pays two list checks per tick for behaviours and, on ground vehicles, one identity check for the movement
model; vehicles of the built-in kinds still run their built-in models directly.

**Settings of your own in the schema.** The JSON schemas accept `Behaviours` as a map of ids to any settings
map, and `Movement` as an id or `{ Model, Settings }` with any settings map; MCHeli does not check what is
inside. Document your settings for pack authors yourself (the keys, their types, defaults and ranges, as the
example addon's javadoc does), and reject bad values in your decoder with a clear message: it shows in the
content report with the file and line.

## The example addon

[`examples/example-addon`](../examples/example-addon) is a NeoForge mod built against the API alone, and
part of this build (`./gradlew :example-addon:build`, run by CI):

* `ExamplePacks` registers the jar's `mcheli_pack/` folder, which adds one weapon, `example_cannon`, that
  reuses the bundled 2A42's sound and effects;
* `ExampleAddon` counts every shot through `WeaponFired` and logs the total every 100 shots;
* it cancels shots fired by players inside the server's spawn protection, unless its server config
  (`ExampleConfig`, `blockFireInSpawnProtection`) turns that off.
* `ExampleExtensions` registers a movement model, `mcheli_example:hovercraft` (`HovercraftModel`: an air
  cushion, thrust along the heading, turning with the side keys, a glide with little drag, the same drag
  predicted on clients), and a behaviour, `mcheli_example:beacon` (`BeaconBehaviour`: a column of particles on
  clients every few ticks, counted on the server), both decoding their settings with a `Codec` and `NbtOps`.
  The pack's `example_hovercraft` vehicle uses both.

### Running it

```
./gradlew :example-addon:runGameTestServer
```

starts a NeoForge game test server with the built MCHeli jar (`:neoforge:jar`) and the built addon jar on
the class path, so NeoForge loads them as two separate mods, as from a mods folder. The game directory is
`examples/example-addon/run`. The tests live in the addon's own `src/gametest`, a small third mod
(`mcheli_example_test`, game test namespace `mcheli_example`), so the addon jar stays as it ships:

* `examplepackloadsfromtheaddonjar`: the two mods come from different files, and `example_cannon` loaded
  with the pack id `mcheli_example`. This is MCHeli finding the `ContentPackProvider` of another mod's jar
  with `ServiceLoader` and copying that jar's `mcheli_pack/`.
* `addoncountsandcancelsshots`: a player fires a helicopter's gun. Outside spawn protection the addon's
  listener counts each shot; inside it, with `blockFireInSpawnProtection` on, the shots are cancelled and not
  counted; with the option off they go through and count again. A game test server has no spawn protection
  of its own (only a dedicated server does), so a test mixin in `mcheli_example_test` protects the test's
  area.
* `hovercraftfliesandbeaconpulses`: the hovercraft rises to its cushion height, flies exactly the distance
  the model's thrust and drag give while the forward key is held, and the beacon pulses on the server; the
  API names the model and behaviour the vehicle uses.

The test mod reaches MCHeli's internals to spawn, board and fire the helicopter; the addon itself uses only
the API. CI runs this after the main game tests. MCHeli's own game tests check the API from inside the mod
as well (`common/src/gametest/.../api`): `TestContentPacks` registers a pack through the same
`META-INF/services` mechanism, and `ApiGameTests` checks that it loads with its pack id, that the lookups
find a spawned helicopter, and that `VehicleSpawned`, `SeatBoarded`, `WeaponFired` (cancelled, then
allowed) and `VehicleRemoved` fire, on NeoForge and on Fabric.
`TestVehicleExtensions` registers a movement model and two behaviours the same way, and `ExtensionGameTests`
checks that the test pack's `api_test_hover` hovers and flies as that model says, that its behaviours tick
before and after the move and hear of its removal, and that unknown ids and rejected settings are warnings.

### Fabric addons

A Fabric addon registers its packs the same way: the `ContentPackProvider` class listed in
`META-INF/services/com.ivomens.mcheli.api.content.ContentPackProvider` in its jar, and `addPack` with its
own mod id (the id in its `fabric.mod.json`). No entrypoint is needed. Fabric loads every mod's classes with
one class loader, so MCHeli's `ServiceLoader` sees the service files of all mod jars; the folder is then
looked up in the addon's mod container. Declare `"depends": { "mcheli": "*" }` in `fabric.mod.json`. On
Fabric, MCHeli's game tests are a mod of their own (`mcheli_gametest`), so `ApiGameTests` proves the
discovery across mods there too.
