# Releasing

Releases are cut from a tag. Pushing `v<version>` runs `.github/workflows/release.yml`, which
builds and tests both jars, creates the GitHub release and uploads the jars to Modrinth and
CurseForge. Nothing is published by hand.

## Versions

MCHeli: Revived uses [semantic versioning](https://semver.org/), `major.minor.patch`:

* **Major**: something existing breaks. Worlds or saved vehicles that no longer load the same way
  (entity ids, NBT keys, item data), content pack files that stop loading or behave differently,
  config keys that are removed or change meaning.
* **Minor**: new features and content that leave existing worlds, packs and configs working.
* **Patch**: bug fixes only.

The network protocol (`Network.PROTOCOL`, in `common/src/main/java/com/ivomens/mcheli/networking/Network.java`) goes up whenever a
message changes shape. After a bump, clients and servers on different versions cannot connect. That
is fine in a minor release; in a patch release avoid it unless the fix needs it. Either way, say so
in the changelog ("Network protocol 8: clients and servers must update together").

Pre-releases get a suffix: `2.1.0-beta.1`, `2.1.0-rc.1`. They become GitHub pre-releases and beta
versions on Modrinth and CurseForge (`-alpha` suffixes become alpha versions).

The version lives in one place, `mod_version` in `gradle.properties`, and the jars are named after
it (`mcheli-revived-<version>-neoforge.jar`, `-fabric.jar`).

The public API for addon mods has a version of its own, `api_version`, bumped when the API changes and not
on every release; [API.md](API.md#versioning) has its rules, which the build checks against `api/api.txt`.
The release carries its jar as well (`mcheli-revived-api-<api version>.jar` and `-sources.jar`), on the
GitHub release only; Modrinth and CurseForge get the two mod jars.

## The changelog

`CHANGELOG.md` follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Anything a player,
server owner or pack author would notice goes under `## [Unreleased]` in the same pull request that
makes the change, under Added, Changed, Deprecated, Removed, Fixed or Security. Write it for them,
not as a commit log.

## Cutting a release

1. Make sure the default branch is green in CI and the Unreleased section is complete.
2. Run the preparation script with the new version:

   ```
   tools/release/prepare-release.sh 2.1.0
   ```

   It sets `mod_version`, renames the Unreleased section to `## [2.1.0] - <date>`, opens a new
   empty Unreleased section and updates the compare links. It commits nothing.
3. Check the diff, then commit, tag and push. The script prints the exact commands:

   ```
   git commit -am "Release 2.1.0"
   git tag -a v2.1.0 -m "MCHeli: Revived 2.1.0"
   git push origin HEAD v2.1.0
   ```

4. Watch the Release workflow in the Actions tab. It fails early if the tag and `mod_version`
   disagree or the changelog has no section for the version.

`tools/release/changelog-section.sh 2.1.0` prints a version's changelog section; the workflow uses it
for the release notes.

To rerun a release for a tag that already exists (after fixing a secret, for example), start the
Release workflow by hand from the Actions tab and enter the tag. An existing GitHub release gets its
notes and jars replaced. If only the Modrinth or CurseForge upload failed, "Re-run failed jobs" on
the run repeats just that part.

## Setting up the publishing

Go to the repository's **Settings → Secrets and variables → Actions**. Tokens go on the **Secrets**
tab, project ids on the **Variables** tab.

| Name | Kind | Value |
|---|---|---|
| `MODRINTH_TOKEN` | secret | A Modrinth personal access token (modrinth.com → Settings → Personal access tokens) with the *Create versions* scope. |
| `MODRINTH_ID` | variable | The Modrinth project id, shown on the project page in its three-dot menu ("Copy ID"). |
| `CURSEFORGE_TOKEN` | secret | A CurseForge API token (CurseForge for Studios / authors site → Account → API tokens). |
| `CURSEFORGE_ID` | variable | The numeric CurseForge project id, shown in the "About Project" box on the project page. |

A site whose token or id is missing is skipped with a notice, so the GitHub release works before any
of this is set up. The GitHub release itself needs no setup: it uses the workflow's own token.

Each jar is uploaded as its own version: `<version>+neoforge` for NeoForge and `<version>+fabric`
for Fabric, both for Minecraft 1.21.1 and Java 21. The Fabric version requires Fabric API; both list
Flywheel as optional.
