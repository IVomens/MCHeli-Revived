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

### HUD widgets (package `com.ivomens.mcheli.api.client`, since API 1.1)

The client half of the API lives in `com.ivomens.mcheli.api.client`. It names no Minecraft client class
either, but it is only useful on a physical client: call it from your client set-up code
(`FMLClientSetupEvent` on NeoForge, the client entrypoint on Fabric), never from code a dedicated server runs.
`McheliClientApi.get()` finds it the same way `McheliApi.get()` does.

A HUD widget is a small panel drawn in the in-vehicle HUD, after MCHeli's own seat HUD, in the modern HUD and
in the legacy scripted one. Widgets follow the HUD's own rules: they are not drawn with the GUI hidden (F1), in
third person unless `DisplayHUDThirdPerson` is on, or in a wreck, and they scale with `HudScale`. With no widget
registered the HUD draws and costs exactly what it did before.

| Type | Contract |
|---|---|
| `McheliClientApi` | `get()`, `hudWidgets()`. Implemented by MCHeli. |
| `HudWidgets` | `register(id, widget)` adds a widget and returns a `Subscription` that removes it. Ids are unique (`IllegalArgumentException` otherwise) and use the addon's mod id as namespace. Any thread. |
| `HudWidget` | Implemented by the addon: `anchor()`, `width()`, `height()` (HUD units, read every frame), `order()` (read at registration, default 0), `isShown(context)` (default true) and `render(context)`. Called on the render thread. New methods only ever come with a default. |
| `HudAnchor` | The slot a widget goes in: `TOP_LEFT`, `TOP_RIGHT`, `LEFT`, `RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`, `BOTTOM`. May gain constants. |
| `HudWidgetContext` | What `isShown` and `render` see, valid only during the call. The frame: `vehicle()`, `kind()`, `viewer()`, `seat()` (0 the pilot, -1 for a UAV operator), `partialTick()`, `screenWidth()`, `screenHeight()`. The settings: `modernHud()`, `hudScale()`, `color()`, `cautionColor()`, `warningColor()`, `opacity()`, `withAlpha(argb, alpha)`. The box: `width()`, `height()`. The pens, in the modern HUD's style: `text`, `textRight`, `textCentered`, `textWidth` (monospaced font), `label`, `labelWidth` (caption font), `line`, `box`, `fill`, `bar` (a gauge filled by a fraction) and `readout` (a caption over a boxed value, `READOUT_HEIGHT` = 28 high). `LINE_HEIGHT` = 12. |

**Units and layout.** Widgets work in HUD units: the modern HUD lays itself out on a virtual screen 640 units
wide and 360 high, scaled to the window height times `HudScale`, and widgets use the same units in both
styles. A widget draws inside its own box, (0, 0) at its top left corner; nothing clips it. MCHeli places the
boxes: each anchor is a free area of the HUD, and the widgets of one anchor are stacked in it from the edge or
corner inwards, by `order()` and then id, 4 units apart. A widget that `isShown` hides takes no room. A widget
wider than its slot, or one that no longer fits next to those stacked before it, is not drawn in that frame,
so widgets never overlap each other or MCHeli's readouts. Plan for the smallest slot your anchor has:

| Anchor | Modern HUD, flight (helicopters and planes) | Modern HUD, gunner sight (ground vehicles, gunner view) | Legacy HUD |
|---|---|---|---|
| `TOP_LEFT` (down, left-aligned) | 175 x 48 at (20, 4), above the position line | 136 x 72 at (20, 100), below speed, hull and fuel | 180 x 100 at (8, 8) |
| `TOP_RIGHT` (down, right-aligned) | 175 x 48 at (445, 4), above the vehicle name | 160 x 120 at (460, 48), below name and heading | 180 x 100 at (452, 8) |
| `LEFT` (down, left-aligned) | 90 x 160 at (76, 100), between speed tape and pitch ladder | 136 x 120 at (20, 176) | 180 x 120 at (8, 112) |
| `RIGHT` (down, right-aligned) | 88 x 130 at (474, 100), between pitch ladder and altitude tape | 136 x 58 at (484, 172) | 180 x 120 at (452, 112) |
| `BOTTOM_LEFT` (up, left-aligned) | 145 x 34 at (20, 266), above the system bars | 136 x 56 at (20, 300) | 180 x 116 at (8, 236) |
| `BOTTOM_RIGHT` (up, right-aligned) | 88 x 44 at (474, 234), above the weapon card | 150 x 44 at (470, 234), above the weapon card | 180 x 116 at (452, 236) |
| `BOTTOM` (up, centred) | 290 x 52 at (175, 304), below the pitch ladder | 290 x 22 at (175, 334) | 256 x 52 at (192, 300) |

The modern slots are the space its readouts leave when the aircraft flies level; the pitch ladder turns with the
bank angle and can sweep over `LEFT` and `RIGHT`. A legacy HUD draws wherever its script files say, so there
the slots hug the screen edges and only keep widgets apart from each other. The TV missile view uses the legacy
slots, since it always draws the scripted TV HUD.

**Errors.** A widget whose method throws is logged once and not drawn again until it is registered anew; the
other widgets and the HUD still draw.

```java
public final class ThrottleWidget implements HudWidget {
    @Override public HudAnchor anchor() { return HudAnchor.BOTTOM_LEFT; }
    @Override public int width() { return 90; }
    @Override public int height() { return HudWidgetContext.READOUT_HEIGHT; }

    @Override
    public boolean isShown(HudWidgetContext hud) {
        return hud.kind() == VehicleKind.HELICOPTER && hud.seat() == 0;   // the pilot of a helicopter only
    }

    @Override
    public void render(HudWidgetContext hud) {
        double throttle = hud.vehicle().throttle();
        hud.readout(0, 0, 90, "THROTTLE", Math.round(throttle * 100) + "%",
                throttle > 0.95 ? hud.cautionColor() : hud.color());
    }
}

// client set-up
McheliClientApi.get().hudWidgets().register(ResourceLocation.fromNamespaceAndPath("mymod", "throttle"),
        new ThrottleWidget());
```


## The example addon

[`examples/example-addon`](../examples/example-addon) is a NeoForge mod built against the API alone, and
part of this build (`./gradlew :example-addon:build`, run by CI):

* `ExamplePacks` registers the jar's `mcheli_pack/` folder, which adds one weapon, `example_cannon`, that
  reuses the bundled 2A42's sound and effects;
* `ExampleAddon` counts every shot through `WeaponFired` and logs the total every 100 shots;
* it cancels shots fired by players inside the server's spawn protection, unless its server config
  (`ExampleConfig`, `blockFireInSpawnProtection`) turns that off;
* on the client, `ExampleAddonClient` (a `@Mod` class for `Dist.CLIENT`) registers `ExampleClockWidget` at
  client set-up: the pilot sees the world's time of day as a readout with a gauge in the top right corner of
  the vehicle HUD.

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
* `clockreadsthetimeofday` and `clockwidgetdrawsforthepilot`: the clock widget, registered through the client
  API, is drawn by MCHeli's widget layer for the pilot of a helicopter and not for a player in no seat, and is
  gone once unsubscribed. A game test server has no screen, so the layer draws on a canvas that records text.

The test mod reaches MCHeli's internals to spawn, board and fire the helicopter; the addon itself uses only
the API. CI runs this after the main game tests. MCHeli's own game tests check the API from inside the mod
as well (`common/src/gametest/.../api`): `TestContentPacks` registers a pack through the same
`META-INF/services` mechanism, and `ApiGameTests` checks that it loads with its pack id, that the lookups
find a spawned helicopter, and that `VehicleSpawned`, `SeatBoarded`, `WeaponFired` (cancelled, then
allowed) and `VehicleRemoved` fire, on NeoForge and on Fabric. `HudWidgetGameTests` registers HUD widgets
through `McheliClientApi` and draws them for the pilot of a helicopter, and the unit test `HudWidgetTraceTest`
records every draw call of a set of sample widgets in each HUD layout against a golden file
(`hud/widget/sample.trace`).

### Fabric addons

A Fabric addon registers its packs the same way: the `ContentPackProvider` class listed in
`META-INF/services/com.ivomens.mcheli.api.content.ContentPackProvider` in its jar, and `addPack` with its
own mod id (the id in its `fabric.mod.json`). No entrypoint is needed. Fabric loads every mod's classes with
one class loader, so MCHeli's `ServiceLoader` sees the service files of all mod jars; the folder is then
looked up in the addon's mod container. Declare `"depends": { "mcheli": "*" }` in `fabric.mod.json`. On
Fabric, MCHeli's game tests are a mod of their own (`mcheli_gametest`), so `ApiGameTests` proves the
discovery across mods there too.
