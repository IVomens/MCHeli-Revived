# Roadmap: versions and loaders

The goal is one MCHeli codebase that ships for every Minecraft version people still play, on
NeoForge, Forge and Fabric, with the same content packs and the same server performance work in
every build.

## Where we are

| Version | Loader | State |
|---|---|---|
| 1.21.1 | NeoForge | **Primary.** Feature-complete port of McHeli CE, cleaned up, tested on a dedicated server. |

Everything below is planned work.

Fabric 1.21.1 is the first loader to add: findings and the step-by-step plan are in
[FABRIC_PORT.md](FABRIC_PORT.md).

## How the code has to be organised first

Supporting three loaders and ~10 Minecraft versions is only maintainable if most of the code does
not know which loader or version it runs on.

1. **`common` module** – vehicles, weapons, physics, the content parsers, HUD logic, networking
   payload classes. Written against vanilla Minecraft (Mojang mappings) only.
2. **Thin platform modules** – `neoforge`, `forge`, `fabric`. Each one implements a small set of
   platform services:
   * registration (items, blocks, entity types, menus, sounds),
   * networking channel and payload registration,
   * events (ticks, entity join/leave, damage, input, HUD layers, render stages),
   * config file location, mod file access (for built-in content and addon packs),
   * capabilities / transfer APIs (fluid tank on aircraft, FE charger),
   * chunk tickets for the opt-in UAV and projectile leases.
3. **Version branches** – one long-lived branch per Minecraft version family (`mc/1.20.1`,
   `mc/1.21.1`, `mc/26.x`, ...). A fix lands on the newest branch and is cherry-picked down.
   The multi-loader layout lives inside each branch.

Build tooling: the NeoForged MultiLoader template (ModDevGradle for NeoForge/Forge, Fabric Loom for
Fabric) gives one Gradle build that produces all loader jars for a version.

The port already has the right seams for this: `LegacyGL` isolates rendering, `StackData` isolates
item data storage, packets go through one registration point, and the content system is plain Java.

## Forward ports

| Target | Java | Loaders | Main work |
|---|---|---|---|
| 26.1 | 25 | NeoForge, Fabric, (Forge if maintained) | New rendering backend (GPU device / render pipeline API introduced in 1.21.5+): `LegacyGL`, model baking and HUD drawing move to render pipelines. Unobfuscated game jars simplify tooling. Item, entity and data-component changes since 1.21.1. |
| 26.2 | 25 | same | Incremental API drift. |
| 26.3 | 25 | same | Incremental API drift. |

Going straight from 1.21.1 to 26.1 is the big step; 26.2 and 26.3 should be small once 26.1 exists.

## Backports

| Target | Java | Loaders | Main work |
|---|---|---|---|
| 1.20.1 | 17 | Forge, NeoForge (1.20.1 fork), Fabric | Item NBT instead of data components (only `StackData` changes), `FriendlyByteBuf` channels instead of payloads, older `GuiGraphics`. **Highest value backport**: the biggest modpack population. |
| 1.19.2 / 1.19.4 | 17 | Forge, Fabric | No `GuiGraphics` (PoseStack-based screens), older damage source API, older registry access. |
| 1.18.2 | 17 | Forge, Fabric | World height and chunk API differences, older render types. |
| 1.17.1 | 16 | Forge, Fabric | First version with the modern shader-based renderer; otherwise close to 1.18. |
| 1.16.5 | 8 | Forge, Fabric | Fixed-function GL is still available: `LegacyGL` maps back onto `RenderSystem`/GL immediate mode. Java 8 means no records, switch expressions or `var` in `common`. |
| 1.15.2 | 8 | Forge, Fabric | Like 1.16.5 with older world APIs. |
| 1.14.4 | 8 | Forge, Fabric | Oldest target; Mojang mappings exist from here on, which is why it is the floor. |

Suggested order: **1.20.1 → 26.1 → 1.19.2 → 1.18.2 → 1.16.5 → 26.2/26.3 → 1.17.1 → 1.15.2 →
1.14.4**, i.e. by player count, with the forward ports slotted in as those versions mature.

## Things that need a decision

* **Java language level of `common`.** Backporting to 1.14–1.16 forces Java 8 syntax on the shared
  code (no records, `var`, switch expressions, text blocks, pattern matching). Options: keep
  `common` at Java 17 and maintain a separate Java 8 branch for 1.14–1.16, or downgrade with a tool
  such as Jabel. Recommendation: separate legacy branch, because the 1.14–1.16 renderer is
  different enough to need its own code anyway.
* **Forge on 26.x.** NeoForge and Fabric are the active loaders on modern versions; Forge builds for
  26.x only make sense if Forge keeps shipping for those versions.
* **Bundled libraries.** SnakeYAML is jar-in-jar on NeoForge/Forge and `include`d on Fabric. HUD
  formulas no longer need a bundled engine: the mod compiles them itself.

## Known issues to carry along

* Terrain streaming to far-away UAV operators was not ported to 1.21 (the client discards chunks
  outside the player's own view square). The UAV camera shows what the operator's client has
  loaded. A clean solution needs client and server mixins and is best designed once, in `common`.
* Every version needs the same performance guarantees: no unconditional chunk loading, tracking
  capped by view distance, culling-friendly render bounds. The game tests in `common/src/gametest`
  (vehicle smoke test, load test) should be ported to every branch so regressions show up in CI.
