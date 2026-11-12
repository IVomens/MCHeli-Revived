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

### Damage, permission and vehicle activity events (API 1.1)

All of these fire on the server thread. Every one of them costs nothing while nobody listens: the event is
not built, and the per-tick checks behind takeoff, landing and water are one listener lookup each.

**Damage.** `VehicleDamaged` fires once per hit a vehicle takes, whatever dealt it (MCHeli weapons,
explosions, crashes, fire, vanilla sources, other mods' guns), before armor. Hits on a passenger seat or an
extra hit box reach the vehicle and fire it once, not once per part. It names the `source()`, `attacker()`,
`directEntity()` and, for MCHeli rounds, beams and their explosions, the `weaponId()`. It is `Cancellable`,
and `setAmount(float)` changes the damage before armor. `VehicleDamageTaken` follows once the hit was taken
off the hit points (`damage()` after armor, `hp()` left), before `VehicleDestroyed`. It is a separate event
rather than result fields on `VehicleDamaged` because an event object is only valid while its listeners run:
the damage after armor is known only once they have all returned.

**Permissions.** These ask before a player does something to a vehicle. They fire after MCHeli's own checks
(the owner's lock, the server's limits), carry the `ServerPlayer` and the `Vehicle`, and are allowed unless a
listener cancels them. A refusal is silent, so tell the player why. MCHeli undoes what the player's client
already predicted: a refused pilot gets the server's view rotation back, a refused placement the server's
inventory.

| Event | Asked before | Notes |
|---|---|---|
| `VehicleBoardAttempt` | a player takes a seat: clicking the vehicle or a seat, or the seat keys | `seatId()`; mobs, commands and loading are not asked |
| `VehiclePlaceAttempt` | a player places a vehicle from its item, on a block or minecart or from a UAV station | `vehicle()` is not in the level yet; `item()` |
| `VehicleDismantleAttempt` | a player breaks a vehicle down into its item by hitting it | after the hit's `VehicleDamaged`; refused, the hit is an ordinary one |
| `VehicleScreenOpenAttempt` | the pilot's panel or the cargo opens | `screen()`: `VehicleScreen.PANEL` or `CARGO` |
| `VehicleLockAttempt` | the owner's sneak-click or `/mcheli lock`/`unlock` changes the lock | `locking()`, `claimsOwnership()` |
| `WeaponFired` (API 1.0) | a weapon fires a shot | its `shooter()` can be any entity, not only a player |

**What vehicles do.**

| Event | When |
|---|---|
| `VehicleTookOff` | a helicopter or plane has been off the ground for 20 ticks in a row, so a hop or bounce is no takeoff |
| `VehicleLanded` | it has been back on the ground or water for 10 ticks in a row; `onWater()` |
| `VehicleEnteredWater`, `VehicleLeftWater` | any vehicle has been in or out of water for 10 ticks in a row |
| `MissileWarned` | the crew's missile warning sounds (about once a second while missiles home on the vehicle); `seeker()` is a `SeekerType` (`HEAT`, `RADAR`, `DUAL`, `OTHER`), `missiles()` the missiles |
| `CrewEjected` | the pilot or copilot left by ejection seat; `rider()`, `seatId()` |
| `VehicleRearmed` | a weapon's ammunition was restocked by its pilot's round items or a supply vehicle; `weaponId()`, `rounds()`, `supplier()` |
| `VehicleRefueled` | fuel went into the tank from the fuel slot, a pipe or cable, or a tanker; `amount()`, `supplier()` |

Ground and water contact are only followed while someone listens: the first state seen after a listener
registers counts as where the vehicle already was. Parked vehicles that nothing wakes, wrecks and vehicles
riding another entity are not followed.

```java
EventBus events = McheliApi.get().events();
events.register(VehicleBoardAttempt.class, attempt -> {
    if (attempt.seatId() == 0 && inNoFlyZone(attempt.player())) {
        attempt.setCancelled(true);
        attempt.player().displayClientMessage(Component.literal("No flying here"), true);
    }
});
events.register(VehicleDamaged.class, damaged -> {
    if (damaged.source().typeHolder().unwrapKey()
            .filter(type -> type.location().getNamespace().equals("othergunmod")).isPresent()) {
        damaged.setAmount(damaged.amount() * 0.5F);
    }
});
events.register(VehicleLanded.class, landed -> logLanding(landed.vehicle(), landed.onWater()));
events.register(MissileWarned.class, warned -> {
    if (warned.seeker() == SeekerType.HEAT) {
        hintFlares(warned.vehicle());
    }
});
```

## The example addon

[`examples/example-addon`](../examples/example-addon) is a NeoForge mod built against the API alone, and
part of this build (`./gradlew :example-addon:build`, run by CI):

* `ExamplePacks` registers the jar's `mcheli_pack/` folder, which adds one weapon, `example_cannon`, that
  reuses the bundled 2A42's sound and effects;
* `ExampleAddon` counts every shot through `WeaponFired` and logs the total every 100 shots;
* it cancels shots fired by players inside the server's spawn protection, unless its server config
  (`ExampleConfig`, `blockFireInSpawnProtection`) turns that off;
* `ExampleAirspace` makes the spawn area a no-fly zone (`noFlyZoneAtSpawn`): players inside spawn protection
  may not take a helicopter's or plane's pilot seat or place one (`VehicleBoardAttempt`,
  `VehiclePlaceAttempt`), and it halves the damage vehicles take from one other mod's damage types
  (`halfDamageFromMod`, through `VehicleDamaged`).

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
* `noflyzonekeepspilotsout` and `halvesdamagefromtheconfiguredmod` (batch `mcheli_example_airspace`):
  a player inside the protected area cannot board the helicopter as pilot until the protection goes, and with
  `halfDamageFromMod` set to `minecraft` a later listener sees vanilla damage halved.

The test mod reaches MCHeli's internals to spawn, board and fire the helicopter; the addon itself uses only
the API. CI runs this after the main game tests. MCHeli's own game tests check the API from inside the mod
as well (`common/src/gametest/.../api`): `TestContentPacks` registers a pack through the same
`META-INF/services` mechanism, and `ApiGameTests` checks that it loads with its pack id, that the lookups
find a spawned helicopter, and that `VehicleSpawned`, `SeatBoarded`, `WeaponFired` (cancelled, then
allowed) and `VehicleRemoved` fire, on NeoForge and on Fabric. `ApiEventGameTests` checks the API 1.1
damage and activity events (one `VehicleDamaged` per hit through a seat, takeoff and landing without
counting hops, water, the missile warning's seeker, ejection, rearming, refuelling) and
`ApiPermissionGameTests` refuses and then allows each permission event.

### Fabric addons

A Fabric addon registers its packs the same way: the `ContentPackProvider` class listed in
`META-INF/services/com.ivomens.mcheli.api.content.ContentPackProvider` in its jar, and `addPack` with its
own mod id (the id in its `fabric.mod.json`). No entrypoint is needed. Fabric loads every mod's classes with
one class loader, so MCHeli's `ServiceLoader` sees the service files of all mod jars; the folder is then
looked up in the addon's mod container. Declare `"depends": { "mcheli": "*" }` in `fabric.mod.json`. On
Fabric, MCHeli's game tests are a mod of their own (`mcheli_gametest`), so `ApiGameTests` proves the
discovery across mods there too.
