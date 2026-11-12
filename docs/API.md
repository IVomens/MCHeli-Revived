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
| `targets()` | Other mods' entities as radar and lock-on targets, and entities never targeted (`TargetRegistry`). Since 1.1. |
| `wingmen()` | The AI wingmen: list, read and order them (`Wingmen`). Since 1.1. |

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

### Targets (package `com.ivomens.mcheli.api.target`, since API 1.1)

Another mod's entities (its jets, tanks, drones) can be targets for MCHeli as if they were MCHeli vehicles: the
radar display shows them, lock-on seekers, AI gunners and target designation lock them, guided missiles fly
at them and are decoyed by their flares and chaff, and the mod hears of every lock and missile so it can warn
its own crew. An addon registers a `TargetProfile` for an entity type (or a predicate); the profile answers,
each time a seeker asks, with a `TargetSignature`.

| Type | Contract |
|---|---|
| `TargetRegistry` | `register(EntityType, TargetProfile)`, `register(Predicate<Entity>, TargetProfile)`, `exclude(EntityType)`, `exclude(Predicate<Entity>)`, each returning a `Subscription`; `signatureOf(entity)`, `isExcluded(entity)`. Any thread; registrations hold on both sides until unsubscribed. |
| `TargetProfile` | Implemented by the addon. `signature(entity)`: the signature now, or null for "no target right now" (both sides, often: return constants). Default methods `onLockedOn(TargetLock)` and `onMissileLaunched(IncomingMissile)`. |
| `TargetSignature` | Immutable: `domain()` (`TargetDomain`), `shownAs()` (the `VehicleKind` the radar draws), `heat()` and `radarCrossSection()` (0 invisible to that sensor, 1 as visible as a vehicle), `isFlareUsing()`, `isChaffUsing()`. Start from `AIR` or `GROUND` and change it with the `with...` methods. |
| `TargetDomain` | `AIR`, `GROUND`, or `BY_HEIGHT` (as MCHeli vehicles: on the ground near solid blocks, else in the air). Decides which seekers (air-to-air, anti-tank) may lock it. |
| `SeekerType` | `HEAT`, `RADAR`, `DUAL`, `OTHER`: what a lock or missile seeks with, so whether flares or chaff help. |
| `TargetLock` | `target()`, `user()`, `vehicle()`, `seeker()`. |
| `IncomingMissile` | `target()`, `missile()` (the missile entity, which may be watched until removed), `shooter()`, `weaponId()`, `seeker()`. |

How the signature is used, the same rules MCHeli's own vehicles follow:

* A heat seeker sees `heat()`, a radar seeker `radarCrossSection()`, a seeker that uses both the stronger,
  any other seeker (optical, laser) all of it. 0 means that seeker never locks the entity; in between it is
  treated as stealth: a narrower cone, a longer lock, a shorter reach. The radar display shows the entity
  while `radarCrossSection()` is above 0, drawn as `shownAs()`; planes and helicopters low and slow are hidden
  in ground clutter as MCHeli's are.
* Flares hide the entity from heat seekers looking for a target and destroy heat-seeking missiles homing on it;
  chaff does the same to radar seekers and radar missiles, and also breaks a radar lock that is held. The flags
  spawn nothing: the addon draws its own decoys.
* `onLockedOn` runs once per completed lock where the seeker runs: on the server for AI gunners and for a
  pilot's beyond-visual-range radar lock, on the locking player's client for a player's own lock-on weapon.
  `onMissileLaunched` runs on the server when a guided missile is fired at the entity or turned on it.
* An exclusion beats every registration and hides an entity (any entity, vanilla or MCHeli's) from the radar
  display, seekers, AI gunners and wingmen picking targets; a missile already homing on it keeps flying.
  Registrations by type win over those by predicate; for the same type, the earliest still registered wins.
  MCHeli's own vehicles, seats, projectiles and decoys never use a profile.
* With nothing registered, seekers and radar skip all of this after one check; a registration by type costs a
  map lookup per entity a seeker looks at, a predicate one call.

```java
public final class JetProfile implements TargetProfile {
    private static final TargetSignature JET = TargetSignature.AIR.withRadarCrossSection(0.8F);
    private static final TargetSignature JET_FLARES = JET.withFlares(true);

    @Override
    public TargetSignature signature(Entity entity) {
        MyJet jet = (MyJet) entity;
        return jet.isParked() ? null : jet.isDroppingFlares() ? JET_FLARES : JET;
    }

    @Override
    public void onMissileLaunched(IncomingMissile missile) {
        ((MyJet) missile.target()).warnPilot(missile.seeker());
    }
}

// in the mod's constructor, so the server and every client agree
McheliApi.get().targets().register(MyEntities.JET.get(), new JetProfile());
McheliApi.get().targets().exclude(EntityType.ARMOR_STAND);
```

MCHeli's own radar warning receiver and missile alarm are drawn by MCHeli vehicles only; a registered entity's
crew is warned by its mod, through the callbacks.

### Wingmen (package `com.ivomens.mcheli.api.wingman`, since API 1.1)

The AI wingmen the wingman panel and `/wingman` command manage, readable and orderable from code. Server side,
on the server thread; the roster belongs to the running server and is not saved.

| Type | Contract |
|---|---|
| `Wingmen` | `all(server)`, `orderableBy(player)`, `following(leader)`, `get(server, aircraftId)`; orders `follow(commander, aircraft)`, `release`, `attack(commander, aircraftId, target)`, `autoAttack`, `holdFire`, `setWeaponType`, `dispatch(commander, aircraftId, MissionRequest)`, `abort`, each returning a `WingmanOrderResult`. |
| `WingmanState` | A snapshot: `aircraftId()`, `aircraft()`, `owner()`, `leader()`, `formationSlot()`, `attack()` (`WingmanAttack`), `attackTarget()`, `weaponType()`, `status()` (`FOLLOWING`, `IDLE` or the flight phase of a mission, such as `ON_STATION` or `RTB`), `missionKinds()`. |
| `WingmanOrderResult` | `DONE`, `NOT_FOUND`, `NOT_A_WINGMAN`, `NOT_ALLOWED`, `NOT_IN_AIRCRAFT`, `FORMATION_FULL`, `INVALID`. Only `DONE` changed anything. |
| `WingmanAttack` | `HOLD_FIRE`, `TARGET`, `AUTO`. |
| `MissionRequest` | A mission order: `builder(MissionKind...)`, then `target(x, z)`, `baseId`, `weapon`, `orbitRadius`, `cruiseAltitude`, `strikePasses`, `timeLimitSeconds`, `build()`. Defaults are the planner's. |
| `MissionKind` | `CAP`, `CAS`, `STRIKE`, `ESCORT`, `RECON`, `FERRY`. |

Every order names the commanding player and goes through the same code and the same permission rules as the
panel: owners, operators and whoever flies the wingman's leader may order it, and an aircraft that is nobody's
wingman becomes the commander's (unless someone else is aboard or it is locked to its owner). Orders send no
chat messages; the result says what happened. `follow` puts the aircraft in the next free slot behind the
aircraft the commander flies. Formation spacing and size are server settings (`mcheli-wingman.cfg`,
`/wingman dist`, `/wingman maxwings`), not per formation, and are not set through the API. There is no
separate "hold position" or "return to base" order: a `CAP` order on a point keeps a wingman orbiting there,
every mission order ends with the flight back to its base, a landing and parking, and a `FERRY` order to a
point next to the base sends a wingman home.

```java
Wingmen wingmen = McheliApi.get().wingmen();
for (WingmanState wingman : wingmen.following(player.getVehicle())) {
    wingmen.attack(player, wingman.aircraftId(), target);
}
WingmanOrderResult result = wingmen.dispatch(player, jet.getUUID(),
        MissionRequest.builder(MissionKind.CAP).target(1200, -300).baseId("north_field").build());
```

## The example addon

[`examples/example-addon`](../examples/example-addon) is a NeoForge mod built against the API alone, and
part of this build (`./gradlew :example-addon:build`, run by CI):

* `ExamplePacks` registers the jar's `mcheli_pack/` folder, which adds one weapon, `example_cannon`, that
  reuses the bundled 2A42's sound and effects;
* `ExampleAddon` counts every shot through `WeaponFired` and logs the total every 100 shots;
* it cancels shots fired by players inside the server's spawn protection, unless its server config
  (`ExampleConfig`, `blockFireInSpawnProtection`) turns that off;
* `ExampleTargets` makes armor stands tagged `mcheli_example_drone` radar and lock-on targets (a small jet;
  tagged `mcheli_example_chaff` too, it hides behind chaff) and counts the locks and missiles they hear of;
* `ExampleWingmen` sends every wingman of a player's formation after one target.

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
* `radarmissilelocksthedrone` and `wingmanattacksthedrone` (`ExampleTargetGameTests`): a radar missile's seeker
  locks a tagged armor stand, the drone hears of the lock and of a missile turned on it, and its chaff hides it;
  a player flying a helicopter takes another as wingman and sends it after the drone.

The test mod reaches MCHeli's internals to spawn, board and fire the helicopter; the addon itself uses only
the API. CI runs this after the main game tests. MCHeli's own game tests check the API from inside the mod
as well (`common/src/gametest/.../api`): `TestContentPacks` registers a pack through the same
`META-INF/services` mechanism, and `ApiGameTests` checks that it loads with its pack id, that the lookups
find a spawned helicopter, and that `VehicleSpawned`, `SeatBoarded`, `WeaponFired` (cancelled, then
allowed) and `VehicleRemoved` fire, on NeoForge and on Fabric.

### Fabric addons

A Fabric addon registers its packs the same way: the `ContentPackProvider` class listed in
`META-INF/services/com.ivomens.mcheli.api.content.ContentPackProvider` in its jar, and `addPack` with its
own mod id (the id in its `fabric.mod.json`). No entrypoint is needed. Fabric loads every mod's classes with
one class loader, so MCHeli's `ServiceLoader` sees the service files of all mod jars; the folder is then
looked up in the addon's mod container. Declare `"depends": { "mcheli": "*" }` in `fabric.mod.json`. On
Fabric, MCHeli's game tests are a mod of their own (`mcheli_gametest`), so `ApiGameTests` proves the
discovery across mods there too.
