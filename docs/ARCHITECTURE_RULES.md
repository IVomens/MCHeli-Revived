# Architecture rules

The structure described in [ARCHITECTURE.md](ARCHITECTURE.md) §7 is checked on every build in two
ways.

**Module boundaries.** The build is split into Gradle subprojects, and what a module can compile
against is a rule in itself:

| Module | Compiles against | So it cannot |
|---|---|---|
| `:core` | Java 21, JOML and the `javax.annotation` annotations (JSR 305, compile only) | use Minecraft, Mojang's libraries, a loader, any other library, or the rest of the mod |
| `:api` | Java 21 and vanilla Minecraft (NeoForm, Mojang names) | use a loader, any library, or the rest of the mod |
| `:common` | `:core`, `:api`, vanilla Minecraft (NeoForm, Mojang names), SnakeYAML, Lombok, the mixin annotations | use NeoForge or Fabric, or reach the loader glue |
| `:neoforge` | NeoForge, plus the sources of `:core` and `:common` compiled in | be used by `:core`, `:common` or `:fabric` |
| `:fabric` | Fabric Loader and Fabric API (Loom), plus the sources of `:core` and `:common` compiled in | be used by `:core`, `:common` or `:neoforge` |

A NeoForge call in common code, or a Minecraft call in `:core`, is a compile error rather than a
test failure.

**Architecture tests.** The `:architecture` module checks the rest with three test classes in
`architecture/src/test/java/com/ivomens/mcheli/architecture`:

* `ArchitectureRulesTest` reads the compiled classes with ArchUnit (dependencies and fields).
* `ComponentRulesTest` reads them too, for the vehicle components (see "Components" below).
* `SourceSizeRulesTest` parses the sources with JavaParser (class and method length).

The module needs no Minecraft: it reads class files and sources from disk. The classes are those
of the `:neoforge` and `:fabric` main source sets, each of which holds the whole mod as it ships
(`:core`, `:api` and `:common` compiled together with that loader's glue). The sources are the
`src/main/java` folders of `:core`, `:api`, `:common`, `:neoforge` and `:fabric`. Only main code is checked. Game tests and
unit tests are not.

## The rules are absolute

Every rule holds for all of the code, and none has exceptions. Until stage 12 each rule carried an
allow-list of the violations that existed when it was added, a ratchet that could only shrink. Stage
12 emptied the last of them and removed the mechanism: there is no list to add an entry to, and no
switch that records violations instead of failing. A violation fails the build and is fixed in the
code. Changing a rule itself (a package set, a limit) is a change to the test classes and to this
document, and needs a reviewer who agrees.

## The rules

| Rule | What it protects |
|---|---|
| Only `neoforge` uses `net.neoforged`, and only `fabric` uses `net.fabricmc` | Common code stays loader-free, and each loader's glue only knows its own loader. Also enforced by the module boundaries: `:common` has no loader on its classpath, and each loader module compiles against its own loader only. |
| Only packages named `client` use `net.minecraft.client` or our own `..client..` packages | A dedicated server never loads a client class. |
| No public non-final fields | State has one owner that controls how it changes. |
| No non-final static fields, except fields annotated `@RegistryHolder` or `@StaticCache` (see below) | No hidden global state: it leaks between worlds, between integrated server and client, and between tests. |
| No `java.util.Random`, `ThreadLocalRandom`, `Math.random`, `System.nanoTime` or `System.currentTimeMillis` in `aircraft`, `helicopter`, `plane`, `tank`, `vehicle`, `ship`, `combat`, `equipment`, outside their `client` packages | Physics and weapons must replay identically, which the golden-trajectory harness depends on. Client code only draws frames and may time them. |
| Classes ≤ 500 lines | Classes small enough to understand. Measured from the declaration to the closing brace; nested classes count towards their outer class. |
| Methods and constructors ≤ 50 lines | Methods that fit on a screen. Methods of anonymous classes and lambdas count towards the method around them. |
| `platform` does not depend on gameplay packages | The platform seam stays a thin service layer that a new loader can implement. |
| `content` (definitions, packs, parsing, registries) does not depend on client rendering | Content packs load on a dedicated server. Rendering means `net.minecraft.client`, `com.mojang.blaze3d` and our `..client..` packages. |
| The feature packages are free of cycles | Each feature can be read, tested and replaced on its own, and dependencies point one way (see ARCHITECTURE.md Part 2 §3.1). A slice is a top-level package below `com.ivomens.mcheli`; the vehicle core (`aircraft`, `helicopter`, `plane`, `ship`, `tank`, `vehicle`) is one slice, `vehicles`; its read-only views, `api` (the public API) and `entityview`, are slices of their own below it. The `:core` module (`com.ivomens.mcheli.core`) is one slice, `core`, which depends on no other. |
| Common code does not depend on the `neoforge` or `fabric` packages | Dependencies point from the loader glue to common code, never back. Also enforced by the module boundary: `:common` cannot see the loader modules. |
| `core` uses only `java..`, `javax.annotation..`, `org.joml..` and itself | Everything in the `:core` module (the physics, the HUD expression compiler, the wingman brain, the content schema engine, ballistics and guidance math) stays pure Java and unit-testable. Also enforced by the module boundary: `:core` compiles against Java, JOML and the JSR 305 annotations alone. |
| `core` uses no `net.minecraft`, `com.mojang`, `net.neoforged`, `net.fabricmc`, `cpw.mods` or `org.spongepowered` | The same promise named directly: no game, no Mojang library, no loader and no mixin, reached in any way. |
| The packages of `core` (`core.sim`, `core.hud`, `core.wingman`, ...) are free of cycles | Each part of the module can be read and tested on its own. A slice is a package directly below `com.ivomens.mcheli.core`. |
| `api` uses only `java..`, `javax.annotation..`, `net.minecraft..`, `com.mojang.serialization..` (vanilla's `Codec`, for addon vehicle data) and itself, and no client class | The public API other mods compile against (docs/API.md) needs nothing else and exposes no internal type, so the mod's internals can change without breaking addons. Also enforced by the module boundary. |
| Every class in `com.ivomens.mcheli.api` comes from the `:api` module | Common code cannot slip a class into the API's packages past the signature check of `api/api.txt`. |
| `core.sim` uses only `java..`, `org.joml..` and itself | The physics stays self-contained inside the module, so the vehicle models are read and tested on their own. |

### Components

A vehicle component is a class implementing `vehicle.component.VehicleComponent`; its helpers are the
other classes of `vehicle.component` (the weapon selection and aiming, the shot direction, the
countermeasure dispensers, ...). ARCHITECTURE.md Part 1 §2.6 describes the model.

| Rule | What it protects |
|---|---|
| Components and their helpers call no method of a vehicle entity (`VehicleEntity`, `AircraftEntity` and its kinds, or the `Vehicle` and `ArmedVehicle` views of one) that returns a component or an interface a component implements | Every collaborator comes in through the constructor, so the constructor shows everything a component depends on. A component that works with another vehicle goes through `aircraft.VehiclePeers`. |
| The components each vehicle kind builds (`AircraftComponents` plus the kind's entity classes) depend on each other in no cycle, following constructor parameters | The components can be built, read and tested from the bottom up. A lower component that has to tell a higher one something offers a subscription instead of taking it as a dependency. |
| `@TickOrder(after = ...)` names only components the constructor takes, and a component that overrides `serverTick` or `clientTick` declares `@TickOrder` | An order constraint is always a visible dependency, and every component's tick says which phase it belongs to. The tick pipeline checks the order itself when it is built. |
| Component code, `AircraftPhysics`, `AircraftComponents`, `VehicleTickPipeline` and `TickPlan`, the pilot assists (`aircraft.assist`) and `core` depend on nothing in `config` and not on `vehicle.settings.VehicleSettingsHolder` | Settings are handed in as immutable snapshot records (`vehicle.settings`), so a component shows in its constructor which settings it follows, a reload is never seen half-way, and a test can pin a vehicle's settings. UI, commands, client input and rendering may still read `McheliConfig` (ARCHITECTURE.md Part 2 §2.6). |
| A component's constructor takes at most 7 collaborators | No component becomes a hub that knows the whole vehicle. A collaborator is a parameter whose type is one of the mod's classes or interfaces (another component, or a port such as `AircraftCamera`), or a generic type with such a type argument (`Supplier<WeaponComponent>`). The host entity (anything that is a Minecraft `Entity`), records, enums, `SyncedValue`, the settings (`vehicle.settings`), `core` types and Java or Minecraft types (callbacks, entity data keys) do not count. The constructor that takes the most counts. |
| `docs/COMPONENTS.md` is what `./gradlew :architecture:writeComponentGraph` makes of the classes | The dependency graph in the docs is the real one. |

A failure of the first rule names the component and the accessor (`FoldingWings AbstractPlaneEntity.engine()`)
with the line that calls it; of the second, the vehicle kind and the cycle (`Tank: A -> B -> A`); of the
settings rule, the class and the config type it uses; of the collaborator limit, the component, its
collaborators and their count.

### Allowed static state

Two annotations in `com.ivomens.mcheli.util.state` exempt a static field from the static-field rule:

* `@RegistryHolder` marks a field assigned once while the game registers its content or constructs
  the mod, and only read afterwards: a registry handle, the loader's payload registrar, a value the
  set-up code hands a feature at start-up. Never use it for state that changes while the game runs.
* `@StaticCache(reset = "name")` marks a cache whose loss only costs time. The field's class must
  declare a static method without parameters with that name which drops the cache, and whoever owns
  the cache's lifecycle calls it. The rule fails when the method is missing.

Everything else that changes at run time belongs to an instance: a per-client service built by
`setup.client.ClientSession`, or a per-server service held by the server or level (ARCHITECTURE.md
Part 2 §3.2).

A failure of a dependency rule names the top-level class (nested, anonymous and lambda classes count
towards it); a field rule names `Owner#field`; the determinism rule names the class and the API it
uses.

## Reading a failure

A failure names the rule and lists every violation with one piece of evidence:

```
Architecture rule broken: Gameplay packages use no java.util.Random, Math.random, System.nanoTime or currentTimeMillis

Violations (1). The rules have no exceptions; fix the code (see docs/ARCHITECTURE_RULES.md):
  com.ivomens.mcheli.tank.ScratchTank java.util.Random
      Method <com.ivomens.mcheli.tank.ScratchTank.roll()> calls method <java.util.Random.nextInt(int)> in (ScratchTank.java:7)
```

The indented line shows where. The cycle rule reports in ArchUnit's own format, with the slices of
each cycle and the dependencies between them. The full report is in
`architecture/build/reports/tests/test/index.html`.

The rule definitions (package sets and limits) are constants at the top of the two test classes.
