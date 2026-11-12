# MCHeli Revived

MCHeli (MC Helicopter) for **Minecraft 1.21.1**, on **NeoForge** and **Fabric** — helicopters, planes,
tanks, vehicles, ships, UAVs and their weapons — rebuilt with dedicated-server performance as a
first-class goal.

Revived is a port of [McHeli: Community Edition](https://github.com/Warfactory-Official/McHeliCE)
(1.12.2), done with the permission of the Warfactory team. McHeli CE itself continues Ragex's
MCHeli-O-Extended, which builds on EMB4's original MCHeli.

## Building

```
./gradlew build
```
This compiles every module and runs the unit and architecture tests. It builds one jar per loader:

| Jar | Loader | Needs |
|---|---|---|
| `neoforge/build/libs/mcheli-revived-<version>-neoforge.jar` | NeoForge 21.1 | nothing else |
| `fabric/build/libs/mcheli-revived-<version>-fabric.jar` | Fabric Loader 0.16.10 or later | Fabric API; Team Reborn Energy is optional |

Both jars hold the same content and gameplay; a client joins a server running the same loader.

The build is split into Gradle subprojects:

| Module | Contents |
|---|---|
| `core` | Everything that needs no Minecraft: the vehicle physics, the HUD expression compiler, the wingman brain, the content schema engine, ballistics and guidance math. Plain Java and JOML, tested on JUnit alone. |
| `api` | The public API other mods build against, with its own version: interfaces and enums for vehicles, content and events ([docs/API.md](docs/API.md)). |
| `common` | Everything that is not loader glue, compiled against vanilla Minecraft, plus the assets and data, and the game tests both loaders run. |
| `neoforge` | The NeoForge glue, `neoforge.mods.toml` and the NeoForge registration of the game tests. Builds the NeoForge jar. |
| `fabric` | The Fabric glue, `fabric.mod.json`, the access widener and the Fabric registration of the game tests. Builds the Fabric jar. |
| `architecture` | The architecture rule checks over all modules ([docs/ARCHITECTURE_RULES.md](docs/ARCHITECTURE_RULES.md)). |
| `examples/example-addon` | An example NeoForge addon built against the API alone; built with the rest. |

Development runs and tests:

| Command | What it does |
|---|---|
| `./gradlew :neoforge:runClient` | Starts the game with the mod. |
| `./gradlew :neoforge:runServer` | Starts a dedicated server. |
| `./gradlew :neoforge:runGameTestServer` | Runs the game tests; `-Pgolden=record` re-records the golden files. |
| `./gradlew :neoforge:runDevScene` | Client render check: loads the `devscene` save and takes screenshots. |
| `./gradlew :neoforge:runStressTest -Pscenario=flying` | Server load scenario (`parked`, `flying`, `firefight`, `crowd`). |
| `./gradlew :neoforge:runClientStress -Pvehicles=120` | Client render load test. |
| `./gradlew test -Pgolden=record` | Re-records the golden files of the unit tests. |
| `./gradlew generateSchemas` | Rewrites the JSON Schema files in `schemas/` from the content schema declarations. |
| `./gradlew :fabric:runClient` | Starts the game with the mod on Fabric. |
| `./gradlew :fabric:runServer` | Starts a Fabric dedicated server. |
| `./gradlew :fabric:runGameTestServer` | Runs the same game tests on Fabric; the goldens must match exactly. |
| `./gradlew :fabric:runDevScene` | The client render check on Fabric (copy the `devscene` save into `fabric/run/saves`). |
| `./gradlew :fabric:runStressTest -Pscenario=flying`, `:fabric:runClientStress` | The load tests on Fabric. |

The NeoForge runs share the game directory `run/` at the repository root (configs, saves, logs and
the profiler recordings); the Fabric runs use `fabric/run/`, so the two loaders' configs and saves
never mix. The Fabric game tests run in `fabric/build/gametest/`.

## Nightly builds

Every night the default branch is built, tested (unit tests plus the dedicated-server game tests,
which spawn, board and fire every vehicle, on both loaders) and published, both jars, as the
[`nightly` pre-release](https://github.com/IVomens/MCHeli-Revived/releases/tag/nightly). Nights
without new commits are skipped. A build can also be started by hand from the Actions tab
("Nightly build" → "Run workflow"). Nightlies are for testing: back up your world first.

## Releases

Versioned releases are on the [releases page](https://github.com/IVomens/MCHeli-Revived/releases),
with what changed in each in [CHANGELOG.md](CHANGELOG.md). Every push and pull request runs the same
build and tests as the nightly. [docs/RELEASING.md](docs/RELEASING.md) explains how a release is cut.

## Flying

The mouse flies as a virtual stick, as it always did. Joysticks, gamepads and HOTAS setups, extra
stick keys and optional handling aids (a helicopter assist with altitude hold, auto-rudder for
planes) can be set up in the options; [docs/CONTROLS.md](docs/CONTROLS.md) explains every control
and recommended settings.

## Running a server

Vehicles remember who placed them, and owners can lock theirs. `mcheli.cfg` can limit how many
vehicles players may place, and operators get `/mcheli perf` (what MCHeli costs the server) and
`/mcheli vehicles` (list and remove vehicles). [docs/SERVER_ADMIN.md](docs/SERVER_ADMIN.md) describes
the settings and commands.

## Content and addons

Vehicles, weapons and HUDs are data-driven. The stock definitions live in
`assets/mcheli/{helicopters,planes,tanks,vehicles,ships,weapons,hud,throwable}`; addon packs go in the
`mcheli_addons` folder next to `config/`. [docs/ADDON_AUTHORING.md](docs/ADDON_AUTHORING.md) explains
how to make a pack, set up your editor with the JSON Schemas in [`schemas/`](schemas), validate a pack
in CI or with `/mcheli validate`, and which pack wins when two define the same thing.
`docs/CONTENT_FORMAT_NOTES.md` lists the settings older packs may still contain that are now ignored.

Mods can build on MCHeli through its API: look up vehicles and content, listen to vehicle and weapon
events (and cancel shots), and ship content packs inside their own jar. [docs/API.md](docs/API.md)
explains how to get the API jar (from a public Maven repository), its versioning rules and every type,
with the example addon in `examples/example-addon`. To start an addon of your own, copy the addon template
([docs/ADDON_TEMPLATE.md](docs/ADDON_TEMPLATE.md)).

The in-game vehicle inspector shows a vehicle's live state, its definition with the file line of each
value, and for operators the server's view beside the client's; its export belongs in bug reports. See
[docs/INSPECTOR.md](docs/INSPECTOR.md).

See `docs/PORTING.md` for the code conventions used by the port.

## License

MCHeli: Revived is released under the GNU Lesser General Public License v3.0 (see `LICENSE`, which
builds on the GNU GPL v3.0 in `LICENSE-GPL`). It is based on McHeli CE by the Warfactory Team,
MCHeli-O-Extended by Ragex and the original MCHeli by EMB4.
