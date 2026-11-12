# Releasing

Releases are cut from a tag. Pushing `v<version>` runs `.github/workflows/release.yml`, which
builds and tests both jars, creates the GitHub release, adds the API to the public Maven repository
and uploads the jars to Modrinth and CurseForge. Nothing is published by hand.

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
The release carries its jar as well (`mcheli-revived-api-<api version>.jar` and `-sources.jar`) and adds it
to the [Maven repository](#the-maven-repository); Modrinth and CurseForge get the two mod jars.

Addon builds download the mod jars straight from the GitHub release by their file names
(`v<version>/mcheli-revived-<version>-neoforge.jar`, `-fabric.jar`), so keep those names and keep the
assets on the release.

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
   empty Unreleased section and updates the compare links. It also points the addon template
   (`templates/addon-template/gradle.properties`) at the new release: `mcheli_version`, the current
   `api_version` and the accepted MCHeli version range. It commits nothing.
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

## The Maven repository

Addon developers get the API from a static Maven repository at
`https://ivomens.github.io/MCHeli-Revived/maven/`: the API jar, its `-sources` and `-javadoc` jars, the POM,
`maven-metadata.xml` and `.md5`/`.sha1`/`.sha256`/`.sha512` checksums for each file, under
`com/ivomens/mcheli/mcheli-revived-api/<api version>/`. It needs no credentials to read.

It lives on the `maven` branch of this repository, which GitHub Pages serves. The release workflow's
"Publish to the Maven repository" step clones that branch (or starts it, the first time), runs
`./gradlew publishMavenRepo -PmavenRepoDir=maven-branch/maven`, which adds the release's API version and
merges `maven-metadata.xml` with the versions already there, and pushes a commit. An API version that is
on the branch already (an `api_version` unchanged since the previous release) is left exactly as it was
published, so a published file never changes; nightly versions are refused. The branch root also gets
`.nojekyll` (serve the files as they are) and an `index.html` that explains the repository.

The two mod jars are not put there: at about 80 MB each they would outgrow GitHub Pages' 1 GB site limit
within a few releases. Addon builds take them from the GitHub release assets instead, with an Ivy
repository ([API.md](API.md#getting-the-jar)).

### Setting it up (once, by hand)

The branch appears with the first release. Then, in the repository on GitHub:

1. **Settings → Pages**: under *Build and deployment*, set *Source* to **Deploy from a branch**, then
   *Branch* to **`maven`** and the folder to **`/ (root)`**, and click **Save**.
2. Wait for the *pages build and deployment* run in the Actions tab to finish, then open
   `https://ivomens.github.io/MCHeli-Revived/maven/com/ivomens/mcheli/mcheli-revived-api/maven-metadata.xml`.
   It should list the released API version.
3. If the repository is private, Pages needs a plan that allows Pages on private repositories, and the
   site is public anyway; addon developers need nothing else.

Later releases update the site by themselves. If a release's push to `maven` does not show up on the site,
check that the *pages build and deployment* workflow ran for it (Actions tab), and run it again from there
if needed. Do not use **Settings → Pages → Source: GitHub Actions**: the workflow publishes through the
branch.

To try the publishing without GitHub, run

```
./gradlew publishMavenRepo
```

which writes the same layout to `build/maven-repo` (or to `-PmavenRepoDir=<folder>`), and point a build at
it with `maven { url = uri("file:///.../build/maven-repo/") }`. The addon template takes such a folder with
`-Pmcheli_maven_url=file:///.../build/maven-repo/`.

### The addon template repository

`templates/addon-template` is the starting point for addon developers ([ADDON_TEMPLATE.md](ADDON_TEMPLATE.md)).
CI builds it against every commit. It is most useful as a GitHub template repository of its own, so that
"Use this template" creates a fresh addon repository from it. After the first release (so the versions in
its `gradle.properties` are on the Maven repository and the release), set it up once:

1. Create an empty public repository, for example `IVomens/mcheli-addon-template` (no README, license or
   .gitignore).
2. Copy the folder's contents to it as its root, including the hidden `.github`, `.gitignore` and
   `.gitattributes`:

   ```
   git clone https://github.com/IVomens/mcheli-addon-template.git
   cp -r templates/addon-template/. mcheli-addon-template/
   cd mcheli-addon-template
   git add . && git commit -m "Addon template for MCHeli: Revived <version>" && git push
   ```

3. In that repository's **Settings → General**, tick **Template repository**.
4. Put its URL into [ADDON_TEMPLATE.md](ADDON_TEMPLATE.md) (the "Use this template" link).

After each release that changes the template (`prepare-release.sh` updates its versions), copy the folder
over again the same way and push.
