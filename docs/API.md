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
the time of writing). It is on a public Maven repository, served by GitHub Pages; no account or token is
needed:
The API has its own version, independent of the mod's: `api_version` in `gradle.properties` (1.1.0 at
the time of writing).

```groovy
repositories {
    maven {
        url = 'https://ivomens.github.io/MCHeli-Revived/maven/'
        content { includeModule 'com.ivomens.mcheli', 'mcheli-revived-api' }
    }
}

dependencies {
    compileOnly 'com.ivomens.mcheli:mcheli-revived-api:1.0.0'
}
```

The repository has the jar, its `-sources` and `-javadoc` jars (so IDEs show the documentation), the POM and
checksums. Every release of MCHeli adds its API version there; a version, once published, never changes.
Nightly builds are not published.

**The quickest start is the addon template**, [`templates/addon-template`](../templates/addon-template): a
NeoForge and Fabric project with this already set up, a sample content pack, a sample listener, a game test
and CI workflows. [ADDON_TEMPLATE.md](ADDON_TEMPLATE.md) goes from nothing to a running addon.

The dependency is **compile only**. At run time the API's classes come from the MCHeli mod jar, so never
bundle the API jar in yours; declare MCHeli as a required dependency instead (`neoforge.mods.toml` or
`fabric.mod.json`).

**Development runs** need the MCHeli mod jar on their class path. The jars are not on the Maven repository
(about 80 MB each); Gradle can take them from the GitHub release with an Ivy repository instead:

```groovy
repositories {
    ivy {
        url = 'https://github.com/IVomens/MCHeli-Revived/releases/download/'
        patternLayout { artifact 'v[revision]/[module]-[revision](-[classifier]).[ext]' }
        metadataSources { artifact() }
        content { includeModule 'com.ivomens.mcheli', 'mcheli-revived' }
    }
}

dependencies {
    // NeoForge (ModDevGradle): loaded as a mod of its own, as from a mods folder
    runtimeOnly 'com.ivomens.mcheli:mcheli-revived:2.0.0:neoforge'
    // Fabric (Loom): remapped for the run. SnakeYAML is nested in MCHeli's jar, which Fabric Loader does not
    // open on a development class path, so it is added here.
    modLocalRuntime 'com.ivomens.mcheli:mcheli-revived:2.0.0:fabric'
    runtimeOnly 'org.yaml:snakeyaml:2.5'
}
```

**Before a release** (to build against API changes on `main`), publish from a checkout instead:
`./gradlew publishToMavenLocal` puts `com.ivomens.mcheli:mcheli-revived-api:<api version>` (with sources and
javadoc) and the mod jars as `com.ivomens.mcheli:mcheli-revived:<mod version>` (classifiers `neoforge` and
`fabric`, the same coordinates as above) in `~/.m2`. Then use `mavenLocal()` in place of the two repositories
above; the template switches with `-Pmcheli_repo=local`. Every
[GitHub release](https://github.com/IVomens/MCHeli-Revived/releases) also carries the API jar and its sources
as plain files.

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

The interfaces MCHeli implements (all but the providers and the behaviours addons implement:
`ContentPackProvider`, `WeaponBehaviourProvider`, `GuidanceLaw`, `ProjectileEffect`, `ProjectileVisualProvider`,
`ProjectileVisual` and their `Factory` interfaces) may gain methods in a minor version, so do not implement them. The two enums may gain constants in a minor version, so a `switch` over them needs a
`default` branch. `ContentPackProvider`, which addons implement, only changes in a major version; so do the
other providers, while the behaviours addons implement (`GuidanceLaw`, `ProjectileEffect`, `ProjectileVisual`)
only gain default methods in a minor version.

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
| `targets()` | Other mods' entities as radar and lock-on targets, and entities never targeted (`TargetRegistry`). Since 1.1. |
| `wingmen()` | The AI wingmen: list, read and order them (`Wingmen`). Since 1.1. |
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
| `SeekerType` | In `com.ivomens.mcheli.api.event`, shared with `MissileWarned`. `HEAT`, `RADAR`, `DUAL`, `OTHER`: what a lock or missile seeks with, so whether flares or chaff help. |
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

### Weapon behaviours (API 1.1)

Since API 1.1 an addon can add **guidance laws** and **projectile effects** (package
`com.ivomens.mcheli.api.weapon`) that weapon files pick by id, next to the `Type` that still decides how the
weapon fires and flies ([ADDON_AUTHORING.md](ADDON_AUTHORING.md), section 7):

```yaml
Type: rocket
Guidance: { Id: mymod:beam_rider, Lead: 8 }
ProjectileEffects: [ mymod:emp, { Id: mymod:incendiary, Radius: 3 } ]
```

| Type | Contract |
|---|---|
| `WeaponBehaviourProvider` | Implemented by an addon, found with `ServiceLoader` (`META-INF/services/com.ivomens.mcheli.api.weapon.WeaponBehaviourProvider`) before content loads, like `ContentPackProvider`. |
| `WeaponBehaviourRegistrar` | `guidance(id, factory)`, `projectileEffect(id, factory)`. A factory gets the settings next to the id as a `CompoundTag` (decode it with your own `Codec` and `NbtOps`) once per weapon file and content load, on both sides; one that throws is reported. |
| `GuidanceLaw` | Addon-implemented. `steer(GuidanceContext)` returns one tick's `SteeringCommand`; `decoyed(GuidanceContext)` (default: self-destruct) answers flares or chaff the weapon falls for. |
| `GuidanceContext` | The projectile (`ProjectileView`) plus the weapon's limits: `turnRate()`, `seekerConeDegrees()`, `isInSeekerCone(point)`, `isTargetDecoyed()`, and `setTarget(entity)`. |
| `SteeringCommand` | `keepCourse()`, `steerTowards(point)` (turn-rate limited), `track(point)` (also loses the lock outside the seeker cone or doppler gate), `dropTarget()`, `detonate()`, `selfDestruct()`. |
| `ProjectileEffect` | Addon-implemented, all hooks default to nothing: `onSpawn`, `onTick`, `onEntityHit`, `onBlockHit`, `onDetonate`, `onExpire`. |
| `ProjectileEffectContext` | The projectile plus `spawnProjectile(weaponId, pos, velocity)` (a sub-munition of another MCHeli weapon), `explode(at, power)` (with the weapon's explosion rules) and `stallEngine(vehicle, ticks)`. |
| `DetonationContext` | `at()`, `cancelExplosion()` (replace MCHeli's explosion with your own), `isExplosionCancelled()`. |
| `ProjectileView` | `entity()`, `level()`, `weapon()`, `position()`, `velocity()`, `speed()`, `ticksFlown()`, `isSubmunition()`, `shooter()`, `launcher()`, `target()`, `targetPoint()`, and `data()`, a per-projectile `CompoundTag` for the behaviours' own state. |

`WeaponInfo` gained `guidance()` and `projectileEffects()`: the registered behaviours a loaded weapon uses.

```java
public final class MyBehaviours implements WeaponBehaviourProvider {
    public void registerBehaviours(WeaponBehaviourRegistrar behaviours) {
        behaviours.guidance(ResourceLocation.fromNamespaceAndPath("mymod", "pure_pursuit"), settings -> missile ->
                missile.target() == null ? SteeringCommand.keepCourse()
                        : SteeringCommand.track(missile.target().position()));
        behaviours.projectileEffect(ResourceLocation.fromNamespaceAndPath("mymod", "slow"), settings -> {
            int ticks = settings.contains("Ticks") ? settings.getInt("Ticks") : 100;
            return new ProjectileEffect() {
                public void onEntityHit(ProjectileEffectContext projectile, EntityHitResult hit) {
                    if (hit.getEntity() instanceof LivingEntity living) {
                        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks));
                    }
                }
            };
        });
    }
}
```

How it behaves:

* **Server side only.** Laws and effects run on the server thread, after the projectile's own work for that
  point (a guidance law instead of the type's guidance, after the move; effects in file order). One law or effect
  object serves every projectile of the weapon, so keep per-projectile state in `ProjectileView.data()`.
* **The same limits as MCHeli's missiles.** No steering during `LockOn: Delay` or after `TickEndHoming`; the
  turn is limited by `TurningFactor`; `track` breaks the lock outside `MaxDegreeOfMissile` or the doppler gate;
  a target over 1850 blocks away ends the missile; a target hidden by decoys the weapon falls for goes to
  `decoyed`. The weapon's fuzes keep working. `ThreatSeeker` still classifies the missile by its `Heat` and
  `Radar` settings, and a locked projectile is found by missile warnings as before.
* **Clients.** Clients see a steered projectile through MCHeli's normal entity sync: rounds and rockets, which
  clients fly themselves, get a position and motion correction each tick their law turned them. Nothing in the
  network protocol changed, and an addon needs no packets of its own. For client-side visuals register a
  `ProjectileVisual` (package `com.ivomens.mcheli.api.client.weapon`) under the same id with a
  `ProjectileVisualProvider` (`META-INF/services/com.ivomens.mcheli.api.client.weapon.ProjectileVisualProvider`):
  `onTick`, `onImpact` and `onRemoved` run on each client's copy of the projectile. A dedicated server never
  loads those classes.
* **Failures.** An id nobody registered, or settings a factory rejects, is a warning in the content report, and
  the weapon keeps its built-in behaviour. A law or effect that throws while a projectile flies is logged and
  dropped for the rest of that projectile's life (a failed law hands steering back to the type's guidance).
* **Cost.** Weapons that name no behaviour (all bundled ones) carry nothing extra: their projectiles tick exactly
  as before.
* A custom *fire mode* (how a trigger pull turns into projectiles) is not part of the API: pick the `Type` whose
  firing fits, and shape the flight with a guidance law and effects.

## The example addon

[`examples/example-addon`](../examples/example-addon) is a NeoForge mod built against the API alone, and
part of this build (`./gradlew :example-addon:build`, run by CI):

* `ExamplePacks` registers the jar's `mcheli_pack/` folder, which adds one weapon, `example_cannon`, that
  reuses the bundled 2A42's sound and effects;
* `ExampleAddon` counts every shot through `WeaponFired` and logs the total every 100 shots;
* it cancels shots fired by players inside the server's spawn protection, unless its server config
  (`ExampleConfig`, `blockFireInSpawnProtection`) turns that off;
* `ExampleExtensions` registers a movement model, `mcheli_example:hovercraft` (`HovercraftModel`: an air
  cushion, thrust along the heading, turning with the side keys, a glide with little drag, the same drag
  predicted on clients), and a behaviour, `mcheli_example:beacon` (`BeaconBehaviour`: a column of particles on
  clients every few ticks, counted on the server), both decoding their settings with a `Codec` and `NbtOps`.
  The pack's `example_hovercraft` vehicle uses both;
* `ExampleAirspace` makes the spawn area a no-fly zone (`noFlyZoneAtSpawn`): players inside spawn protection
  may not take a helicopter's or plane's pilot seat or place one (`VehicleBoardAttempt`,
  `VehiclePlaceAttempt`), and it halves the damage vehicles take from one other mod's damage types
  (`halfDamageFromMod`, through `VehicleDamaged`);
* `ExampleTargets` makes armor stands tagged `mcheli_example_drone` radar and lock-on targets (a small jet;
  tagged `mcheli_example_chaff` too, it hides behind chaff) and counts the locks and missiles they hear of;
* `ExampleWingmen` sends every wingman of a player's formation after one target;
* `ExampleSorties` keeps a sortie count (synced) and the last pilot's name (server only) on every vehicle with
  addon data keys, counting each time a player takes the pilot's seat (`SeatBoarded`);
* `ExampleBehaviours` registers two weapon behaviours (API 1.1): `BeamRider`, a guidance law that flies a
  missile along its gunner's line of sight, and `EmpEffect`, a warhead that stops the engines of the vehicles
  around its burst for a few seconds instead of exploding, both reading their settings with a `Codec`; the pack's
  `example_beam_rider` and `example_emp_rocket` use them, and `ExampleVisuals` (client only) adds sparks to the
  EMP rocket's flight.

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
* `noflyzonekeepspilotsout` and `halvesdamagefromtheconfiguredmod` (batch `mcheli_example_airspace`):
  a player inside the protected area cannot board the helicopter as pilot until the protection goes, and with
  `halfDamageFromMod` set to `minecraft` a later listener sees vanilla damage halved.
* `radarmissilelocksthedrone` and `wingmanattacksthedrone` (`ExampleTargetGameTests`): a radar missile's seeker
  locks a tagged armor stand, the drone hears of the lock and of a missile turned on it, and its chaff hides it;
  a player flying a helicopter takes another as wingman and sends it after the drone.
* `addonchangesavehicle` (`ExampleVehicleActionTests`): the addon spawns a helicopter with a pilot through a
  `VehicleSpawner`, then damages, repairs, refuels and rearms it, empties its gun, ejects the pilot, and locks it and
  clears its owner through a `VehicleControl`.
* `sortiesaresavedandsynced`: the pilot's boarding counts a sortie, the count and the pilot's name survive a
  save and load, and the sync messages give a client copy the count but not the name.
* `exampleweaponsusetheaddonbehaviours`, `beamriderfliesalongthegunnerssight` and `emprocketstopsanengine`
  (`ExampleWeaponGameTests`): the pack's weapons picked up the addon's behaviours, the beam rider launched off
  its gunner's line of sight closes onto it, and the EMP rocket's burst stalls a helicopter's engine for four
  seconds and breaks nothing.

The test mod reaches MCHeli's internals to spawn, board and fire the helicopter; the addon itself uses only
the API. CI runs this after the main game tests. MCHeli's own game tests check the API from inside the mod
as well (`common/src/gametest/.../api`): `TestContentPacks` registers a pack through the same
`META-INF/services` mechanism, and `ApiGameTests` checks that it loads with its pack id, that the lookups
find a spawned helicopter, and that `VehicleSpawned`, `SeatBoarded`, `WeaponFired` (cancelled, then
allowed) and `VehicleRemoved` fire, on NeoForge and on Fabric.
`TestVehicleExtensions` registers a movement model and two behaviours the same way, and `ExtensionGameTests`
checks that the test pack's `api_test_hover` hovers and flies as that model says, that its behaviours tick
before and after the move and hear of its removal, and that unknown ids and rejected settings are warnings.
`ApiEventGameTests` checks the API 1.1 damage and activity events (one `VehicleDamaged` per hit through a seat, takeoff and landing without
counting hops, water, the missile warning's seeker, ejection, rearming, refuelling) and
`ApiPermissionGameTests` refuses and then allows each permission event. `ApiActionGameTests` runs every vehicle action
and the ways a spawn is refused; `ApiDataGameTests` covers addon data: saving, values of unknown keys kept,
batched syncing of synced keys only, and an old save that stays as it was. `TestWeaponBehaviours` registers weapon
behaviours the same way, and `WeaponBehaviourGameTests` checks the turn-rate and seeker-cone limits of a guidance
law, the order and settings of effects at each point of a projectile's life, the replaced explosion, the engine
stall, and the fall-back for unknown ids; `ProjectileSyncGameTests` checks that clients' copies of an
addon-guided rocket follow the server's.

### Fabric addons

A Fabric addon registers its packs the same way: the `ContentPackProvider` class listed in
`META-INF/services/com.ivomens.mcheli.api.content.ContentPackProvider` in its jar, and `addPack` with its
own mod id (the id in its `fabric.mod.json`). No entrypoint is needed. Fabric loads every mod's classes with
one class loader, so MCHeli's `ServiceLoader` sees the service files of all mod jars (the weapon behaviour and
projectile visual providers are found the same way); the folder is then
looked up in the addon's mod container. Declare `"depends": { "mcheli": "*" }` in `fabric.mod.json`. On
Fabric, MCHeli's game tests are a mod of their own (`mcheli_gametest`), so `ApiGameTests` proves the
discovery across mods there too.
