# Example Addon (an MCHeli: Revived addon template)

A starting point for a mod that builds on [MCHeli: Revived](https://github.com/IVomens/MCHeli-Revived) through
its public API, on NeoForge and Fabric for Minecraft 1.21.1 (Java 21). Out of the box it

* ships a content pack inside its jar (one sample weapon, `exampleaddon_cannon`), found by MCHeli through a
  `ContentPackProvider`;
* listens to MCHeli's events (`AddonEvents`: logs vehicles that spawn, counts shots of the sample weapon);
* has a game test that checks the pack loaded, on a NeoForge and on a Fabric game test server;
* has two GitHub workflows: `build.yml` builds both jars, `content.yml` checks the pack's YAML files against
  MCHeli's JSON Schemas.

The full walkthrough is MCHeli's
[docs/ADDON_TEMPLATE.md](https://github.com/IVomens/MCHeli-Revived/blob/main/docs/ADDON_TEMPLATE.md); the API
itself is described in [docs/API.md](https://github.com/IVomens/MCHeli-Revived/blob/main/docs/API.md).

## Start your addon

1. Click **Use this template → Create a new repository** on GitHub, and clone the new repository. (Or copy
   this folder from the MCHeli repository's `templates/addon-template`.)
2. Give it your own mod id, Java package and name, once:

   ```
   ./gradlew renameTemplate -PmodId=myaddon -Ppackage=com.yourname.myaddon -PmodName="My Addon"
   ```

   On Windows use `gradlew.bat` in place of `./gradlew`. The mod id is 2 to 64 characters of `a-z`, `0-9`
   and `_`. The task rewrites the sources, resources, metadata, `gradle.properties` and `settings.gradle`
   and moves the package folders; it refuses to run a second time.
3. Pick a license: replace `LICENSE` with its text and set `mod_license` in `gradle.properties`. Fill in
   `mod_authors` and `mod_description` there too, and rewrite this README.
4. Build and try it:

   ```
   ./gradlew build                      # both jars, in neoforge/build/libs and fabric/build/libs
   ./gradlew :neoforge:runClient        # or :fabric:runClient
   ./gradlew :neoforge:runGameTestServer :fabric:runGameTestServer
   ```

   The first build downloads Minecraft, the loaders and MCHeli, which takes a few minutes.

## Layout

| Folder | What is in it |
|---|---|
| `common/` | Code and resources for both loaders, against vanilla Minecraft and the MCHeli API only: `AddonCommon` (setup), `AddonEvents` (listeners), `AddonPacks` (the content pack), `gametest/AddonGameTests`, and `src/main/resources/mcheli_pack/` (the pack). |
| `neoforge/` | `AddonNeoForge` (`@Mod`), the game test registration and `neoforge.mods.toml`. |
| `fabric/` | `AddonFabric` (`main` entrypoint), the game test registration and `fabric.mod.json`. |

Each loader module compiles `common`'s sources into its own jar. Versions (yours, MCHeli's, Minecraft's and
the loaders') are all in `gradle.properties`.

## Where MCHeli comes from

* The API jar, `com.ivomens.mcheli:mcheli-revived-api`, from MCHeli's public Maven repository
  (`https://ivomens.github.io/MCHeli-Revived/maven/`). It is `compileOnly`: never put it in your jar.
* The MCHeli mod jar for the development runs, `com.ivomens.mcheli:mcheli-revived:<version>:neoforge` or
  `:fabric`, from the assets of MCHeli's GitHub releases.

To build against an MCHeli that is not released yet, run `./gradlew publishToMavenLocal` in an MCHeli checkout
and build this project with `-Pmcheli_repo=local` (or set `mcheli_repo=local` in `gradle.properties`), with
`mcheli_api_version` and `mcheli_version` set to the checkout's `api_version` and `mod_version`.

To move to a newer MCHeli, change `mcheli_api_version`, `mcheli_version` and the two version ranges in
`gradle.properties`.
