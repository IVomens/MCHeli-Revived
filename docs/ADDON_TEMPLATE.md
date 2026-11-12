# The addon template

[`templates/addon-template`](../templates/addon-template) is a complete addon mod for MCHeli: Revived, ready to
copy: one Gradle project that builds a NeoForge jar and a Fabric jar (Minecraft 1.21.1, Java 21) against the
[API](API.md), with a sample content pack, a sample event listener, a game test, and GitHub workflows that
build it and check its content. This page goes from nothing to a running addon. [API.md](API.md) describes
the API itself, [ADDON_AUTHORING.md](ADDON_AUTHORING.md) the content format.

CI builds the template against every commit of MCHeli (the `addon-template` job in
`.github/workflows/ci.yml`), once as it is and once after renaming, so it keeps up with the API.

## 1. What you need

* A JDK 21 (Temurin, for example). If Gradle finds none it downloads one.
* Git, and an IDE: IntelliJ IDEA opens the folder as a Gradle project as it is; Eclipse and VS Code work
  with their Gradle support.
* A GitHub account, for the template and the workflows.

## 2. Get the template

* **On GitHub:** open the template repository (to be created, see [below](#for-the-mcheli-maintainers)) and
  click **Use this template → Create a new repository**. Clone the repository it creates.
* **Without GitHub:** copy `templates/addon-template` from a checkout or a source download of MCHeli to a
  new folder, hidden files (`.github`, `.gitignore`, `.gitattributes`) included, and `git init` it.

## 3. Make it yours

Pick a mod id (2 to 64 characters of `a-z`, `0-9` and `_`) and a Java package, and run once:

```
./gradlew renameTemplate -PmodId=myaddon -Ppackage=com.yourname.myaddon -PmodName="My Addon"
```

(`gradlew.bat` on Windows.) The template's placeholders are the mod id `exampleaddon`, the package
`com.example.exampleaddon` and the name `Example Addon`. The task replaces them in every source, resource,
metadata and workflow file and in `gradle.properties` and `settings.gradle`, moves the package folders, and
renames the files named after the mod id (`data/<mod id>/`, the sample weapon `<mod id>_cannon.yml`). It does
not need MCHeli or Minecraft, so it works offline, and it refuses to run on a template that was renamed
already. Without `-PmodName` the name is made from the mod id (`my_addon` becomes `My Addon`).

Then, by hand:

* replace `LICENSE` with the license you choose, and set `mod_license` in `gradle.properties` to its SPDX id;
* set `mod_authors` and `mod_description` in `gradle.properties`;
* rewrite `README.md` for your addon.

## 4. Build and run

```
./gradlew build
```

builds `neoforge/build/libs/<mod id>-neoforge-<version>.jar` and `fabric/build/libs/<mod id>-fabric-<version>.jar`.
These are what you ship; players put one of them next to the MCHeli jar for their loader.

```
./gradlew :neoforge:runClient          # a NeoForge client with MCHeli and your addon
./gradlew :fabric:runClient            # the same on Fabric
./gradlew :neoforge:runServer          # or :fabric:runServer
```

The runs load MCHeli as a mod of its own, the way a mods folder does. Their game folders are `neoforge/run`
and `fabric/run`. The sample weapon is in the game as `<mod id>_cannon`; add it to a vehicle's weapons in a
pack to fire it.

```
./gradlew :neoforge:runGameTestServer :fabric:runGameTestServer
```

starts a game test server per loader, runs the addon's game tests and stops; the task fails when a test fails.

## 5. What is in it

```
common/                     code and resources shared by both loaders (vanilla Minecraft + the MCHeli API)
  src/main/java/.../AddonCommon.java          setup, called by each loader's entry point
  src/main/java/.../AddonEvents.java          the sample MCHeli event listeners
  src/main/java/.../AddonPacks.java           registers the jar's content pack (ContentPackProvider)
  src/main/java/.../gametest/AddonGameTests   the game tests, shared by both loaders
  src/main/resources/mcheli_pack/             the content pack: pack.mcmeta and assets/mcheli/<type>/*.yml
  src/main/resources/META-INF/services/...    names AddonPacks for MCHeli's ServiceLoader
  src/main/resources/data/<mod id>/structure/empty.nbt   the game tests' empty 16x16x16 structure
neoforge/                   @Mod entry point, game test registration, neoforge.mods.toml
fabric/                     main entrypoint, fabric-gametest registration, fabric.mod.json
.github/workflows/          build.yml (builds both jars), content.yml (checks the pack)
gradle.properties           every version: your mod's, MCHeli's, Minecraft's and the loaders'
```

Each loader module compiles `common`'s sources and resources into its own jar, so code in `common` may use
only vanilla Minecraft, the MCHeli API and Java; loader-specific code goes in `neoforge` or `fabric`.

* **The content pack** is a normal MCHeli pack in the jar's `mcheli_pack/` folder. MCHeli finds `AddonPacks`
  through `META-INF/services/com.ivomens.mcheli.api.content.ContentPackProvider` while it loads its content,
  on both loaders, and loads the folder after its bundled content, under your mod id as the pack id
  ([API.md](API.md#content-packs-in-a-mods-jar)). Start the ids of your vehicles and weapons with your mod id
  so they cannot clash with other packs. The format: [ADDON_AUTHORING.md](ADDON_AUTHORING.md).
* **The listeners** in `AddonEvents` are registered on MCHeli's own event bus (`McheliApi.get().events()`),
  the same on both loaders ([API.md](API.md#events-package-comivomensmcheliapievent)).
* **The game test** checks that MCHeli loaded the pack's weapon under your pack id. Add tests to
  `AddonGameTests.all()`; NeoForge runs them for the namespace `<mod id>` and Fabric through the
  `fabric-gametest` entrypoint. They ship in the jar but only run on a game test server.
* **The metadata** declares MCHeli as a required dependency: `neoforge.mods.toml` (`mcheli`, `required`,
  `mcheli_version_range`) and `fabric.mod.json` (`"mcheli": mcheli_fabric_version_range`), filled in from
  `gradle.properties` by the build.

## 6. Where MCHeli comes from

`gradle.properties` names two MCHeli versions:

* `mcheli_api_version`: the API jar the code compiles against, `com.ivomens.mcheli:mcheli-revived-api`, from
  MCHeli's public Maven repository, `https://ivomens.github.io/MCHeli-Revived/maven/`. It is `compileOnly`; at
  run time the API's classes come from the MCHeli mod jar.
* `mcheli_version`: the MCHeli mod jar the development runs load, `com.ivomens.mcheli:mcheli-revived:<version>`
  with the classifier `neoforge` or `fabric`, downloaded from the assets of MCHeli's GitHub release
  `v<version>` (an Ivy repository in the root `build.gradle`). It never goes into your jar.

The API version and the mod version differ: [API.md](API.md#versioning) explains how. Use the API version that
the MCHeli release you require implements (its `gradle.properties`, or `McheliApi.get().apiVersion()` in game),
and set `mcheli_version_range` and `mcheli_fabric_version_range` to accept that release and newer.

**Building against an MCHeli that is not released yet** (a feature on MCHeli's `main`, or a fix of your own):

```
# in the MCHeli checkout
./gradlew publishToMavenLocal
# in your addon, with mcheli_api_version and mcheli_version set to the checkout's api_version and mod_version
./gradlew build -Pmcheli_repo=local
```

`publishToMavenLocal` puts the API and both mod jars into `~/.m2` under the same coordinates, and
`mcheli_repo=local` (on the command line, or in `gradle.properties` while you need it) makes the template look
there instead of online. Switch back once the release is out.

`-Pmcheli_maven_url=file:///some/folder/` points the build at a copy of the Maven repository, for example
MCHeli's `build/maven-repo` from `./gradlew publishMavenRepo`.

## 7. The workflows

* `build.yml` runs `./gradlew build` on every push and pull request and keeps both jars as a download on the
  run's page. Add the two `runGameTestServer` tasks to run the game tests in CI too.
* `content.yml` checks every YAML file under `common/src/main/resources/mcheli_pack` against the JSON Schemas
  that MCHeli publishes, with MCHeli's `validate-content` action, and marks each problem on its line in pull
  requests ([ADDON_AUTHORING.md](ADDON_AUTHORING.md#github-actions)). It uses `@main`; pin a release tag
  there and in `schemas` to check against the MCHeli version you target.

Both work in a fresh repository without setup.

## 8. Releasing your addon

Upload both jars (`neoforge/build/libs`, `fabric/build/libs`) to Modrinth or CurseForge, and declare MCHeli:
Revived as a required dependency there as well (and Fabric API for the Fabric jar). Bump `mod_version` in
`gradle.properties` for each release.

## For the MCHeli maintainers

The template is meant to be a GitHub template repository of its own; [RELEASING.md](RELEASING.md#the-addon-template-repository)
lists the steps to create it from this folder and to refresh it after a release. `tools/release/prepare-release.sh`
updates the template's MCHeli versions with each release. Until the template repository exists, developers copy
the folder (step 2).
