# Changelog

All notable changes to MCHeli: Revived are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[semantic versioning](https://semver.org/); [docs/RELEASING.md](docs/RELEASING.md) explains what
counts as a major, minor or patch change.

## [Unreleased]

The first release of MCHeli: Revived, a port of McHeli CE (Minecraft 1.12.2) to Minecraft 1.21.1.
Existing content packs (YAML, TXT, HUD scripts, `.obj` and `.mqo` models) load as they did on
McHeli CE.

### API 1.1

- The API is published to a public Maven repository on GitHub Pages,
  `https://ivomens.github.io/MCHeli-Revived/maven/`, with sources, javadoc and checksums; no credentials
  needed. Addon development runs can take the mod jars from the GitHub release assets
  ([docs/API.md](docs/API.md#getting-the-jar)).
- An addon template, `templates/addon-template`: a NeoForge and Fabric project built against the API, with
  a sample content pack, event listener, game test and CI workflows, and a rename task
  ([docs/ADDON_TEMPLATE.md](docs/ADDON_TEMPLATE.md)).
- Vehicle behaviours from addons: a mod registers a behaviour type by id through a
  `VehicleExtensionProvider` (found with `ServiceLoader`), and a vehicle file opts in with
  `Behaviours: { mymod:winch: { ... } }`. Behaviours get server and client ticks before or after the
  vehicle moves (`BehaviourTiming`), and hear when they are added, when the vehicle is wrecked and when
  it is removed. Settings arrive as a `CompoundTag` for the mod's own decoder (a `Codec` with `NbtOps`
  works).
- Movement models from addons: a mod registers a `MovementModel`, and a ground vehicle file (`vehicles/`)
  picks it with `Movement: mymod:hovercraft`; the model sets velocity and heading, MCHeli moves the
  vehicle with its own collision, sync and client prediction. Built-in vehicles move as before.
- `McheliApi.vehicleExtensions()`: the registered ids, and which ones a vehicle uses.
- Unknown behaviour or movement ids, and settings a mod rejects, are warnings in the content report;
  the vehicle loads without them. The JSON schemas accept `Behaviours` (and `Movement` for vehicles)
  with any settings map.
- `VehicleDamaged`: every hit a vehicle takes, once per hit (seats and hit boxes included), before armor;
  cancellable, with a changeable amount and the MCHeli weapon of the hit. `VehicleDamageTaken` after the hit
  points went down.
- Permission events, cancellable and fired on the server before the action: `VehicleBoardAttempt`,
  `VehiclePlaceAttempt`, `VehicleDismantleAttempt`, `VehicleScreenOpenAttempt` (with `VehicleScreen`) and
  `VehicleLockAttempt`. A refused boarding or placement leaves nothing on the player's client.
- `VehicleTookOff` and `VehicleLanded` (with hysteresis, so bounces do not count), `VehicleEnteredWater` and
  `VehicleLeftWater`, `MissileWarned` (with `SeekerType`), `CrewEjected`, `VehicleRearmed` and
  `VehicleRefueled`.

- Vehicle actions (`McheliApi.vehicleActions()`): spawn a vehicle of a content type at a position and
  rotation, with an owner and riders, optionally held to the server's vehicle limits
  (`VehicleSpawner`, `SpawnResult`); and through a `VehicleControl`, repair, damage (through the
  vehicle's own damage handling), destroy, refuel, rearm or set a weapon's ammunition, board, eject
  and dismount crew, and read or change the owner and the lock. Actions return an `ActionResult`
  that says why one was not done.
- Addon data on vehicles: addons declare typed keys (`VehicleDataKey.of`, with a `Codec` and an
  optional `StreamCodec`), read them with `Vehicle.data()` and change them with
  `VehicleControl.data()`. Values are saved with the vehicle under `McheliAddonData`, values of
  removed addons are kept, and synced keys reach the players who see the vehicle, batched once per
  tick. A vehicle without addon data saves exactly as before.
- Network protocol 14 (one new message, `vehicledata/values`): clients and servers must update
  together.

- Weapon behaviours (`com.ivomens.mcheli.api.weapon`): addons register guidance laws and projectile effects
  through a `WeaponBehaviourProvider`, and weapon files pick them with `Guidance` and `ProjectileEffects`
  (settings as a `CompoundTag`). Laws obey the weapon's turn rate, seeker cone and decoys; effects hook spawn,
  tick, entity and block hits, detonation (and can replace the explosion) and expiry, and can spawn
  sub-munitions, explode and stall a vehicle's engine. Unknown ids are content warnings and fall back to the
  built-in behaviour.
- Client visuals for those behaviours (`com.ivomens.mcheli.api.client.weapon`): `ProjectileVisualProvider`,
  `ProjectileVisual`.
- `WeaponInfo.guidance()` and `WeaponInfo.projectileEffects()`.

- HUD widgets: addons draw small panels in the in-vehicle HUD through the new client API,
  `McheliClientApi.get().hudWidgets().register(id, widget)` (package `com.ivomens.mcheli.api.client`).
  A widget picks an anchor (`HudAnchor`), may show only for some vehicle kinds or seats, and draws with
  pens in the modern HUD's style (text, lines, boxes, gauges, readouts) through `HudWidgetContext`. Widgets
  are laid out in slots the modern HUD leaves free, work in the legacy HUD too, and follow F1, third person
  and `HudScale`.
- Targets (`McheliApi.targets()`, package `api.target`): other mods register a `TargetProfile` for an entity
  type or predicate, and their entities show on MCHeli's radar, are locked by its seekers and AI gunners,
  draw its guided missiles and decoy them with flares or chaff, as described by a `TargetSignature` (air or
  ground, heat signature, radar cross-section, decoys). Profiles hear of locks and incoming missiles.
  Addons can also exclude any entity from radar, seekers and AI targeting.
- Wingmen (`McheliApi.wingmen()`, package `api.wingman`): list a player's wingmen, read their orders and
  status, and order them as the wingman panel does: follow, release, attack a target, auto attack, hold fire,
  weapon type, mission orders and abort.

### Added

- NeoForge 1.21.1 and Fabric 1.21.1 jars built from one codebase, with the same content and
  gameplay. The Fabric jar needs Fabric API; Team Reborn Energy is optional.
- Custom liveries: players upload a texture, share it on the server and apply it to the vehicle they
  fly. The server stores the uploads, and labelled UV guides help with painting
  ([docs/LIVERIES.md](docs/LIVERIES.md)).
- A modern HUD style and modern particle effects, both selectable in the config next to the classic
  ones, and an option to invert mouse pitch in vehicles.
- Optional Flywheel support: with Flywheel 1.0.7 or later installed, vehicle models are drawn as
  instances.
- Levels of detail for vehicle models, so distant vehicles cost much less to draw.
- HUD format 2, in which `If` blocks can nest and take an `Else`.
- Wingman settings in their own file, `mcheli-wingman.cfg`.
- Content errors name the file, line and key they come from.
- Every content load writes a report of its problems and replaced files to `logs/mcheli-content.log`,
  and `/mcheli validate [pack]` checks the loaded packs again without a restart.
- JSON Schema files for every content type in `schemas/`, for autocomplete and checks in editors, and
  a GitHub Action that validates an addon pack's YAML files
  ([docs/ADDON_AUTHORING.md](docs/ADDON_AUTHORING.md)).
- YAML settings files may name the format version they are written for with `SchemaVersion: 1`.
- Dedicated-server game tests that spawn, board and fire every vehicle on both loaders, plus server
  and client stress scenarios (`runStressTest`, `runClientStress`).
- Nightly builds of the default branch.
- A public API for addon mods, `mcheli-revived-api` 1.0.0, with its own version: vehicle and content
  lookups, loader-neutral events (vehicles spawned, removed and destroyed, seats boarded and left,
  weapons fired, which can be cancelled, projectile impacts, lock-ons and countermeasures), and content
  packs shipped inside a mod's jar, which load after the bundled content and before `mcheli_addons/`
  ([docs/API.md](docs/API.md)). The API jar comes with each GitHub release and from a public Maven
  repository, `https://ivomens.github.io/MCHeli-Revived/maven/`.
- Vehicle ownership and locking: a vehicle remembers the player who placed it, and its owner can lock
  it with `/mcheli lock` or by sneaking and using it with an empty hand. A locked vehicle can only be
  flown by its owner, their team and operators, and only its owner and operators can pick it up or
  open its cargo; it still takes damage as before. Configurable with `VehicleLocking`,
  `LockAllowsTeam` and `LockPassengerSeats` ([docs/SERVER_ADMIN.md](docs/SERVER_ADMIN.md)).
- Limits on placing vehicles: per player, per chunk, per dimension and per vehicle kind, which
  operators can bypass (`MaxVehiclesPerPlayer`, `MaxVehiclesPerChunk`, `MaxVehiclesPerLevel`,
  `Max<Kind>PerLevel`, `OpsBypassLimits`).
- Chaff on the bundled jets and military helicopters that carry chaff dispensers in reality, next to
  their flares: the A-10, AC-130, EMB 314, F-14D, F-15E, F-15 S/MTD, F-35A, F-35B, F-35C, F/A-18F,
  both Harriers, MiG-29, MV-22, Su-33, Su-37 and both Tornados, and the AH-1Z, AH-64, CH-47, EC665,
  Ka-50N, Ka-52, MH-60G, MH-60L DAP, Mi-24, Mi-28, NH90 and SH-60.
- `/mcheli perf` shows what MCHeli costs the server: vehicles and projectiles per dimension, their
  tick time over the last 30 seconds, the chunks kept loaded for UAVs, wingmen and projectiles, and
  the five most expensive vehicles with a position to teleport to.
- `/mcheli vehicles list [player]`, `remove <targets>`, `remove radius <blocks>` and
  `removeall <kind> confirm` for operators; removals eject the crew and are logged.
- `AdminCommandPermission` sets the operator level for the admin commands; on NeoForge a permission
  mod can grant `mcheli.admin` instead.
- A vehicle inspector for developers, addon authors and bug reports: a key (unbound by default) or
  `/mcheli inspect` opens it on the vehicle you are in or look at. It shows the vehicle's live state,
  its components in tick order, weapons, crew and definition values with their file lines, for
  operators the server's view beside the client's with mismatches highlighted, a compact overlay, and
  a JSON export for bug reports ([docs/INSPECTOR.md](docs/INSPECTOR.md)).
- Joystick and gamepad support, off by default: any device GLFW sees can fly pitch, roll, yaw and
  throttle (as a lever or a centring stick) and press the vehicle actions (fire, weapons,
  countermeasures, gear, camera, zoom, free look, hover). Devices can be plugged in and out at any
  time. A binding screen in the options captures axes and buttons, and shows live axis bars, the
  response curve with its dead zone and a stick test ([docs/CONTROLS.md](docs/CONTROLS.md)).
- Optional handling aids, all off by default: a helicopter assist that levels the airframe while the
  stick is centred and holds altitude once the throttle is let go, auto-rudder for coordinated turns
  in planes, mouse stick smoothing and return to centre for helicopters and planes, and stick keys
  for pitch, roll and yaw with their own rates and acceleration.
- The missile warning tells heat seekers from radar missiles. A digital RWR tags each missile on its
  scope IR or RDR, the modern HUD counts the inbound missiles by seeker, and both blink the decoy to
  drop (FLARE, CHAFF) when the vehicle carries one that would help. The seeker comes from the same
  rule that decides what flares and chaff decoy. Vehicles that carry flares or chaff are warned of
  missiles even without an RWR, the RWR lock tone and the flare dispenser's alert sound lower for a
  radar missile, and HUD scripts can read the counts as `msl_heat`, `msl_radar` and `msl_count`
  ([docs/CONTENT_FORMAT_NOTES.md](docs/CONTENT_FORMAT_NOTES.md)).

### Changed

- Vehicle physics moved into a standalone core with one motion model per vehicle type. Handling is
  checked against recorded trajectories of the old code and only differs where a change was
  deliberate.
- Aircraft attitude is computed in fixed substeps on the pilot's client instead of once per frame;
  the server owns the heading of ground vehicles.
- The vehicle entity was split into components (fuel, weapons, crew, parts, cameras, wreck and so
  on). Entity ids and NBT keys are unchanged.
- Weapons are composed from data: one projectile entity, weapon kinds, fire modes and weapon groups.
  Handheld launchers, the laser designator and throwables use the same system.
- HUD formulas are compiled by the mod itself; the Aviator library is no longer bundled.
- Vehicles, weapons, throwables and HUDs are read through one content schema for both YAML and TXT.
  Settings that nothing reads are ignored and listed in
  [docs/CONTENT_FORMAT_NOTES.md](docs/CONTENT_FORMAT_NOTES.md); `EnableAutoPilot` and `OpenParaAlt`
  were dropped from the bundled vehicles.
- `mcheli.cfg` is read into typed config sections; the file and its keys stay the same.
- The bundled content always loads first, so any addon can replace it; addons follow sorted by name
  ignoring case. A file that replaces another addon's file, or two files of one name in a pack, is
  reported.
- All packets are typed messages (network protocol 7). Client and server must run the same version.
- Network protocol 10: clients and servers must update together. Radar contacts go only to players
  aboard a vehicle and only as changes, seats no longer send their movement, and explosion effects are
  smaller; observers get 55-95 % fewer bytes about vehicles, pilots 75-96 %.
- Network protocol 11: radar contacts say what they are (helicopter, plane, ship, tank, ground
  vehicle, air-to-air missile, other missile, chaff, flare, other) as one byte instead of the entity's
  class name.
- Network protocol 13: the explosions of a tick reach each player as one message, and only clients
  with the legacy particle style, which draw explosions from the blocks they hit, are sent those
  blocks. An observer of a firefight gets 96-98 % fewer bytes of explosion effects; what
  players see and hear is unchanged.
- Parked vehicles stop ticking, and wingman and UAV chunk loading is opt-in and lease-based.
- The radar-guided missiles are radar missiles: the AIM-120, AIM-7 (and the Sea Sparrow), R-77, R-77-1,
  R-27AE, MIM-23 HAWK, SA-2, AGM-84 and Kh-35. McHeli treated every missile as a heat seeker, so flares
  decoyed all of them and chaff none; now flares decoy heat seekers and chaff decoys radar missiles, in
  flight and while the launcher locks on. A missile that is neither still falls for flares
  ([docs/CONTENT_FORMAT_NOTES.md](docs/CONTENT_FORMAT_NOTES.md)).
- Projectiles are no longer saved with the world, except laid mines. A projectile that waits in a
  chunk that ticks no entities for longer than `FrozenProjectileLifetime` seconds (30 by default, `0`
  turns it off) is removed without exploding, instead of waiting there until a player comes near
  ([docs/SERVER_ADMIN.md](docs/SERVER_ADMIN.md#stray-projectiles)).
- Relicensed under LGPL-3.0.

### Fixed

- The cockpit view: camera yaw conversion and back-face culling.
- Cameras are back at the height of the original files. YAML camera positions (third-person cameras,
  gunner cameras and rack loading points) sat 0.35 blocks lower than the same positions in TXT, so every
  bundled vehicle, and every pack converted by McHeli CE, looked from too low. The YAML files are
  unchanged; a file that had raised its cameras by 0.35 to make up for it should lower them again
  ([docs/CONTENT_FORMAT_NOTES.md](docs/CONTENT_FORMAT_NOTES.md)).
- A crash when firing unmanned weapons.
- The radar warning receiver works again: the server never ran it, so crews were not told of radars
  or missiles.
- Throwables honour their stack size again.
- Rounds that flew out of the area players keep ticking no longer pile up: in a long fight thousands
  sat in the loaded chunks around it, were written to every save, and flew on, able to hit, whenever
  someone came near, hours later.
- Aircraft are lit with their own brightness again, and the debug overlay shows in tanks again.
- Cycling seats past the last one moves to the first free seat.
- The air-to-surface proximity fuse bursts inside foliage.
- Smoke, splash and muzzle flash frames are drawn unmirrored.
- Team shuffle when a team has more than one player.
- Only the owner or an operator can control a wingman or its route, and an occupied aircraft can no
  longer be made someone else's wingman.
- The server refuses screenshot uploads it did not ask for.
- The drafting table counts ingredients correctly and only crafts the recipes it lists.
- The YAML and TXT parsers agree, YAML conversion keeps the rotor speed, and addon entries reload
  only from their own pack.
- A content file whose name is not a valid id is skipped with an error instead of stopping the game
  from starting, and TXT files saved with a byte order mark read their first line.
- Dispenser weapons use the item their file names (`DispenseItem`) where their container lands. The
  item was only looked up when a weapon was reloaded in game, so after a normal start the containers
  did nothing.
- The bundled dispensers (water, fire, seeds, bone meal, boat and the tilling water dispenser) have
  their dispensed item and range back. They were lost when McHeli CE converted its weapon files to
  YAML, so these dispensers dropped nothing; the values come from the original TXT files.
- More weapon values lost in the same conversion are back, taken from the original TXT files: 18
  weapons release their bomblets again (cluster bombs, the AGM-154, cluster rockets and shells, and
  five of the dispensers), the AN-M17, CBU-72 and S-8DM explode as fuel-air weapons, the nine
  targeting pods mark targets within their reach for the time they were made for (they had no
  reach, so they marked nothing), and 36 anti-tank, anti-ship and artillery weapons deal their extra
  damage to the vehicle kinds they were made for (`DamageFactor`).
- The bundled vehicles have back what the same conversion lost from their files, taken from the
  original TXT files: every vehicle and the M18 smoke grenades can be crafted on the drafting table
  again (none had a recipe; the CB90's is shapeless), 57 vehicles offer their extra paint schemes, 18
  helicopters and planes park nose up again (the AH-64 stood at +3.5 degrees instead of -3.5), the
  Growler, MXTMV and BNR32 have their pitch limits, the Mk.5 and Project 1204 turn their radars and the
  Stinger 390x its propellers, the F-35B's dummy rotor no longer tilts, six vehicles' search light beams are visible
  (their colours had lost the alpha), the M26 and T-84 gunner views have their HUD and the Macchi
  M.33 and MC.72 none, the racks of the MH-53E, MV-22 and Mk.5 face the right way, and the BNR32 is
  called Skyline GT-R BNR32.
- A vehicle can ride on several racks of the same carrier: YAML `RideRack` takes a list of rack
  numbers (`c5: [1, 2]`), and the AH-64, Mk.5 and five ground vehicles can board either rack of the
  C-5 again. The single number still reads.
- Incoming air-to-air missiles show on the BVR display again.
- Other players see a tank's tracks and steered wheels turn and hear its tracks while it steers: the
  server now receives the tank driver's steering keys.
- Vehicles behave the same at negative coordinates as at positive ones: the block checks below and
  around a vehicle (ground clearance, tank terrain following, flooding, wheels crushing snow, tank
  ramming, rope length, effects) rounded towards zero there and looked one block off.
- Wingmen lower their landing gear again: on the final approach of an autonomous landing, and when
  the leader they follow lowers its own. The gear was only ever raised, as in McHeli CE.
- A wingman on patrol that starts an orbiting attack carries on around the same circle instead of
  jumping to another point of it, and back. The patrol orbit counted its angle in radians and the
  attack orbit in degrees, as in McHeli CE; both now use radians and turn the same way.
- Autonomous wingmen keep at least 16 blocks above the ground while cruising. The cruise altitude is
  still the absolute Y the planner asks for (80 unless set), which is all McHeli CE used: a base above
  Y 75 counted its climb as done on the runway, and flights over hills steered into them.
- Wingmen fly into their formation slot and stay there instead of hanging back or drifting off in
  height. Helicopters used the throttle as their speed, which on a rotor is lift, so they climbed or
  sank away from the slot and pitched for height, which only moves them sideways; they now hold the
  slot's height with the collective and fly forward by pitching the nose. Planes trim their throttle
  by how far the slot lies ahead, so one that has overshot slows down, and the catch-up boost now
  carries a follower to the edge of its slot. With a stopped leader, a helicopter hovers at the slot's
  height instead of sinking at 0.35 throttle. McHeli CE flew formation the same way.

[Unreleased]: https://github.com/IVomens/MCHeli-Revived/commits/main
