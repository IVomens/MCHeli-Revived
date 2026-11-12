# The MCHeli: Revived API

Other mods build against `mcheli-revived-api`, a small jar of interfaces and enums that MCHeli implements.
With it an addon can find vehicles and read their state, look up the loaded vehicle and weapon types, listen
to what vehicles and weapons do (and stop a shot), and ship content packs inside its own jar. The API is the
same on NeoForge and Fabric: it uses vanilla Minecraft types only, and its events have their own small bus
instead of either loader's.

Everything else in MCHeli is internal and changes without notice. If the API lacks something you need,
open an issue rather than reaching into `com.ivomens.mcheli` outside `com.ivomens.mcheli.api`.

## Getting the jar

The API has its own version, independent of the mod's: `api_version` in `gradle.properties` (1.1.0 at
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
* `exampleweaponsusetheaddonbehaviours`, `beamriderfliesalongthegunnerssight` and `emprocketstopsanengine`
  (`ExampleWeaponGameTests`): the pack's weapons picked up the addon's behaviours, the beam rider launched off
  its gunner's line of sight closes onto it, and the EMP rocket's burst stalls a helicopter's engine for four
  seconds and breaks nothing.

The test mod reaches MCHeli's internals to spawn, board and fire the helicopter; the addon itself uses only
the API. CI runs this after the main game tests. MCHeli's own game tests check the API from inside the mod
as well (`common/src/gametest/.../api`): `TestContentPacks` registers a pack through the same
`META-INF/services` mechanism, and `ApiGameTests` checks that it loads with its pack id, that the lookups
find a spawned helicopter, and that `VehicleSpawned`, `SeatBoarded`, `WeaponFired` (cancelled, then
allowed) and `VehicleRemoved` fire, on NeoForge and on Fabric. `TestWeaponBehaviours` registers weapon
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
