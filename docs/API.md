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
Java, the `javax.annotation` nullness annotations, non-client Minecraft (with vanilla's
`com.mojang.serialization` codecs) and each other, and all of them come from the `:api` module.

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
| `vehicleActions()` | Since 1.1: spawning vehicles and changing them (`VehicleActions`), server only. |
| `vehicleData()` | Since 1.1: the keys of addon data on vehicles (`VehicleDataKeys`). |

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
| `Vehicle` | A vehicle entity, read-only: `entity()`, `kind()`, `isUAV()`, `isDestroyed()`, `roll()`, `throttle()`, `seats()`, `signature()`, `data()` (addon data, since 1.1). Every MCHeli vehicle entity implements it. |
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

### Vehicle actions (package `com.ivomens.mcheli.api.vehicle.action`, since API 1.1)

`Vehicle` stays a read-only view. To change vehicles, `McheliApi.get().vehicleActions()` hands out a
`VehicleSpawner` for new ones and a `VehicleControl` for existing ones. Both are server only, on the server
thread; `control(vehicle)` throws for a client-side vehicle.

| Type | Contract |
|---|---|
| `VehicleActions` | `spawner(level, kind, typeId, position)` (or with a `VehicleInfo`), `control(vehicle)`. |
| `VehicleSpawner` | A builder: `rotation(yaw, pitch)`, `owner(player)`, `rider(seatId, entity)`, `respectLimits(bool)` (off by default), `requireFreeSpace(bool)` (on by default), then `spawn()`. |
| `SpawnResult` | `status()` (`SpawnStatus`), `vehicle()`, `message()` (the limit's text for `LIMIT_REACHED`), `riders()` (an `ActionResult` per requested seat). |
| `SpawnStatus` | `SPAWNED`, `UNKNOWN_TYPE`, `CHUNK_NOT_LOADED`, `OBSTRUCTED`, `LIMIT_REACHED`, `INVALID_SEAT`, `REFUSED`. |
| `VehicleControl` | Health: `health()`, `maxHealth()`, `repair()`, `repair(amount)`, `damage(source, amount)`, `destroy(attacker)`. Fuel: `fuel()`, `fuelCapacity()`, `refuel()`, `setFuel(amount)`. Weapons (slots from 0): `weaponCount()`, `weapon(slot)`, `ammo(slot)`, `maxAmmo(slot)`, `rearm()`, `rearm(slot)`, `setAmmo(slot, amount)`. Crew (seat 0 is the pilot): `board(entity, seat)`, `eject(seat)`, `ejectAll()`, `dismount(entity)`. Owner and lock: `owner()`, `ownerName()`, `setOwner(player)`, `setOwner(uuid, name)`, `clearOwner()`, `isLocked()`, `setLocked(bool)`, `mayBoard(player, seat)`, `mayChangeLock(player)`. Addon data: `data()`. |
| `ActionResult` | `SUCCESS`, `NO_CHANGE`, or why not: `VEHICLE_REMOVED`, `VEHICLE_DESTROYED`, `INVALID_SEAT`, `SEAT_EMPTY`, `SEAT_OCCUPIED`, `SEAT_BLOCKED`, `NOT_ABOARD`, `NO_SUCH_WEAPON`, `NO_FUEL_TANK`, `NO_OWNER`, `LOCKING_DISABLED`, `REFUSED`. `isSuccess()` is true for the first two. |

```java
VehicleActions actions = McheliApi.get().vehicleActions();
SpawnResult result = actions.spawner(level, VehicleKind.HELICOPTER, "ah-64", pos)
        .rotation(player.getYRot(), 0.0F)
        .owner(player)
        .rider(0, player)
        .respectLimits(true)
        .spawn();
if (!result.isSpawned()) {
    player.sendSystemMessage(result.message().orElse(Component.literal("Cannot spawn: " + result.status())));
    return;
}
VehicleControl control = actions.control(result.vehicle().get());
control.refuel();
control.rearm();
if (control.damage(level.damageSources().explosion(null, null), 20.0F) == ActionResult.REFUSED) {
    LOGGER.info("the armor absorbed it");
}
```

How the actions relate to the game's own rules:

* **Spawning** places the vehicle as a player placing its item would, with the same starting fuel and
  ammunition (usually none; call `refuel()` and `rearm()`), and `VehicleSpawned` fires. Rules meant for players are not applied: creative-only types,
  target drones and `PlaceableOnSpongeOnly` are not checked. The server's vehicle limits
  (`MaxVehiclesPerPlayer` for the owner, per chunk, per level, per kind, with `OpsBypassLimits` for an
  operator owner) apply only with `respectLimits(true)`, because addons usually spawn vehicles for reasons
  of their own. A spawn never loads a chunk.
* **Damage** calls the vehicle's own damage handling, the same as any hit: armor, hit parts, the external
  damage config, friendly fire and invulnerability apply, the damage events fire, and the vehicle is
  destroyed when its hit points run out. A player attacker's punch can pick the vehicle up, as their own
  would. **Destroy** turns the vehicle into a wreck as running out of hit points does (`VehicleDestroyed`
  fires, it explodes); a wreck cannot be repaired, refuelled, rearmed or boarded. To remove a vehicle
  without a wreck, discard its entity.
* **Repair, fuel and ammunition** set the values directly and sync them as the game does (fuel to every
  client, ammunition counts to the crew). The infinite fuel and ammunition settings do not matter here; they
  only decide whether the vehicle uses any up. An empty tank is filled with the default fuel.
* **Crew** actions put riders where a player would sit or get off: `board` seats a player pilot as if they
  had clicked the vehicle, and ejected riders land at the seat's exit, without a parachute. The vehicle
  type's seat exclusion groups still apply (`SEAT_BLOCKED`).
* **Owner and lock** wrap the same ownership the admin tools use. Locking needs an owner and respects
  `VehicleLocking` (`LOCKING_DISABLED` while it is off); who a lock keeps out still follows `LockAllowsTeam`,
  `LockPassengerSeats` and the operator level. Unlike `/mcheli lock`, `setLocked` never makes anyone the
  owner. No action checks a player's rights or the lock, since the addon decides who may do what: check
  `mayBoard` or `mayChangeLock` when acting for a player.

### Addon data on vehicles (package `com.ivomens.mcheli.api.vehicle.data`, since API 1.1)

An addon keeps typed values of its own on vehicles. It declares each key once, with a vanilla `Codec` for
saving and, if clients should see the value, a `StreamCodec` for syncing:

```java
public static final VehicleDataKey<Integer> SORTIES = VehicleDataKey.of(
        ResourceLocation.fromNamespaceAndPath("mymod", "sorties"), Codec.INT, ByteBufCodecs.VAR_INT, 0);
public static final VehicleDataKey<String> LAST_PILOT = VehicleDataKey.of(
        ResourceLocation.fromNamespaceAndPath("mymod", "last_pilot"), Codec.STRING, "");   // server only

int sorties = vehicle.data().get(SORTIES);                                  // either side; 0 when unset
McheliApi.get().vehicleActions().control(vehicle).data().update(SORTIES, n -> n + 1);   // server
```

| Type | Contract |
|---|---|
| `VehicleDataKey` | `of(id, codec, syncCodec or null, default)`; `id()`, `codec()`, `syncCodec()`, `isSynced()`, `defaultValue()`. Ids are unique; declaring one twice throws. |
| `VehicleDataKeys` | `McheliApi.get().vehicleData()`: `declare(...)` (what `of` calls), `key(id)`, `keys()`. |
| `VehicleData` | `Vehicle.data()`, read-only: `get(key)` (the default when unset), `has(key)`. On a client only synced keys have values. |
| `MutableVehicleData` | `VehicleControl.data()`, server only: `set(key, value)`, `update(key, function)`, `remove(key)`. |

* **Declare keys while your mod is constructed**, on both sides (a static field is enough), so they exist
  before vehicles load and before a client receives values.
* **Saving.** All values are saved in one compound, `McheliAddonData`, of the vehicle's NBT, each under its
  key's id. A vehicle without values writes no compound at all, so saves without addon data are unchanged.
  Values whose key nobody declares (an addon that was removed, or is not loaded yet) are kept and saved
  again as they were; a value that fails to decode is kept the same way and logged. Addon data does not
  travel with the vehicle's item when it is picked up.
* **Syncing.** A change to a synced key reaches the players that track the vehicle at the end of the server
  tick, all of the tick's changes to one vehicle in one message, and a player who starts tracking it gets
  every synced value. Values of unsynced keys never leave the server, and nothing is sent while no addon
  declared a synced key. A synced value may take at most 16 KiB encoded; a larger one is logged and not
  sent. A client without the addon skips its values.
* **Values should be immutable** (numbers, strings, records, immutable lists): only `set`, `update` and
  `remove` mark a value as changed.
* **`Codec` in the API.** The keys take vanilla's `com.mojang.serialization.Codec`, the serialization
  library every Minecraft save format is written with and which ships with the game, so the API allows
  `com.mojang.serialization` next to `net.minecraft`.

The sync message is new in network protocol 14: clients and servers must both run a MCHeli with API 1.1
or later.

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

## The example addon

[`examples/example-addon`](../examples/example-addon) is a NeoForge mod built against the API alone, and
part of this build (`./gradlew :example-addon:build`, run by CI):

* `ExamplePacks` registers the jar's `mcheli_pack/` folder, which adds one weapon, `example_cannon`, that
  reuses the bundled 2A42's sound and effects;
* `ExampleAddon` counts every shot through `WeaponFired` and logs the total every 100 shots;
* it cancels shots fired by players inside the server's spawn protection, unless its server config
  (`ExampleConfig`, `blockFireInSpawnProtection`) turns that off;
* `ExampleSorties` keeps a sortie count (synced) and the last pilot's name (server only) on every vehicle with
  addon data keys, counting each time a player takes the pilot's seat (`SeatBoarded`).

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
* `addonchangesavehicle` (`ExampleVehicleActionTests`): the addon spawns a helicopter with a pilot through a
  `VehicleSpawner`, then damages, repairs, refuels and rearms it, empties its gun, ejects the pilot, and locks it and
  clears its owner through a `VehicleControl`.
* `sortiesaresavedandsynced`: the pilot's boarding counts a sortie, the count and the pilot's name survive a
  save and load, and the sync messages give a client copy the count but not the name.

The test mod reaches MCHeli's internals to spawn, board and fire the helicopter; the addon itself uses only
the API. CI runs this after the main game tests. MCHeli's own game tests check the API from inside the mod
as well (`common/src/gametest/.../api`): `TestContentPacks` registers a pack through the same
`META-INF/services` mechanism, and `ApiGameTests` checks that it loads with its pack id, that the lookups
find a spawned helicopter, and that `VehicleSpawned`, `SeatBoarded`, `WeaponFired` (cancelled, then
allowed) and `VehicleRemoved` fire, on NeoForge and on Fabric. `ApiActionGameTests` runs every vehicle action
and the ways a spawn is refused; `ApiDataGameTests` covers addon data: saving, values of unknown keys kept,
batched syncing of synced keys only, and an old save that stays as it was.

### Fabric addons

A Fabric addon registers its packs the same way: the `ContentPackProvider` class listed in
`META-INF/services/com.ivomens.mcheli.api.content.ContentPackProvider` in its jar, and `addPack` with its
own mod id (the id in its `fabric.mod.json`). No entrypoint is needed. Fabric loads every mod's classes with
one class loader, so MCHeli's `ServiceLoader` sees the service files of all mod jars; the folder is then
looked up in the addon's mod container. Declare `"depends": { "mcheli": "*" }` in `fabric.mod.json`. On
Fabric, MCHeli's game tests are a mod of their own (`mcheli_gametest`), so `ApiGameTests` proves the
discovery across mods there too.
