# Making an addon pack

An addon pack adds or replaces vehicles, weapons, throwables and HUDs. This page covers how to lay a pack
out, how to get autocomplete and checks in your editor, how to validate a pack in CI and in game, and what
happens when two packs define the same thing. [CONTENT_FORMAT_NOTES.md](CONTENT_FORMAT_NOTES.md) covers the
file formats themselves and the settings older packs still contain.

## 1. Folder layout

Packs live in the `mcheli_addons` folder of the game directory, next to `config/`. A pack is a folder, or the
same folder zipped (`.zip` or `.jar`):

```
mcheli_addons/
├─ default/                      the bundled content, extracted by the mod (do not edit, it is replaced)
└─ desert_pack/
   ├─ pack.mcmeta
   └─ assets/mcheli/
      ├─ helicopters/  planes/  ships/  tanks/  vehicles/     one file per vehicle
      ├─ weapons/                                            one file per weapon
      ├─ throwable/                                          grenades and smoke
      ├─ hud/                                                HUD scripts (only read by clients)
      └─ models/  textures/  sounds/  sounds.json  lang/      client resources, loaded as a resource pack
```

`pack.mcmeta` names the pack. The `addon` object is optional but recommended:

```json
{
  "pack": { "pack_format": 34, "description": "Desert Storm vehicles" },
  "addon": {
    "domain": "desert_pack",
    "version": "1.2",
    "authors": ["Your Name"],
    "credits": "Models by ...",
    "loader_version": "1"
  }
}
```

* **`domain`** identifies the pack in messages, reports and `/mcheli validate`. Use lower case and keep it
  unique: two packs with one domain are reported, and an in-game reload would read the wrong pack's files.
  Without it, the folder name is used and a warning says so.
* **`loader_version`** `"1"` (the default with a `pack.mcmeta`) reads content only below `assets/`; `"2"` (the
  default without one) searches the whole pack for `assets/mcheli/<type folder>/`.

A file's **name** (without extension) is its id: `assets/mcheli/weapons/gau8.yml` defines the weapon `gau8`,
which vehicles mount by that name. Names may only use lower-case letters, digits, `_`, `-` and `.`; a file
with any other name is skipped with an error. Content files end in `.yml` (YAML) or `.txt` (the legacy
format); `.yaml` files are not read.

Write new files in YAML. The legacy TXT format still loads but is deprecated; `/mcheli dumpyaml` converts
every loaded TXT file to YAML with the same values.

## 2. Schemas and editor setup

The repository's [`schemas/`](../schemas) folder has a JSON Schema (draft 2020-12) for each content type,
generated from the same declarations the game reads files with:

| Folder | Schema |
|---|---|
| `weapons/` | `weapon.schema.json` |
| `helicopters/`, `planes/`, `ships/`, `tanks/`, `vehicles/` | `helicopter.schema.json`, `plane.schema.json`, `ship.schema.json`, `tank.schema.json`, `vehicle.schema.json` |
| `throwable/` | `throwable.schema.json` |
| `hud/` | `hud.schema.json` |

A schema knows every key with its other spellings, the value type, the range the game clamps to, the
default, the words a setting accepts, the keys a section cannot do without, and the keys the game no longer
reads (marked deprecated, so editors strike them through). Each key's description says what it does where the
declaration says so, and which legacy TXT key it replaces.

**VS Code**: install the *YAML* extension (Red Hat). Then either put a modeline at the top of a file:

```yaml
# yaml-language-server: $schema=https://raw.githubusercontent.com/IVomens/MCHeli-Revived/main/schemas/weapon.schema.json
DisplayName: GAU-8 Avenger
Type: machinegun2
```

or map the folders once in `.vscode/settings.json` of your pack:

```json
{
  "yaml.schemas": {
    "https://raw.githubusercontent.com/IVomens/MCHeli-Revived/main/schemas/weapon.schema.json": "**/assets/mcheli/weapons/**/*.yml",
    "https://raw.githubusercontent.com/IVomens/MCHeli-Revived/main/schemas/helicopter.schema.json": "**/assets/mcheli/helicopters/**/*.yml",
    "https://raw.githubusercontent.com/IVomens/MCHeli-Revived/main/schemas/plane.schema.json": "**/assets/mcheli/planes/**/*.yml",
    "https://raw.githubusercontent.com/IVomens/MCHeli-Revived/main/schemas/ship.schema.json": "**/assets/mcheli/ships/**/*.yml",
    "https://raw.githubusercontent.com/IVomens/MCHeli-Revived/main/schemas/tank.schema.json": "**/assets/mcheli/tanks/**/*.yml",
    "https://raw.githubusercontent.com/IVomens/MCHeli-Revived/main/schemas/vehicle.schema.json": "**/assets/mcheli/vehicles/**/*.yml",
    "https://raw.githubusercontent.com/IVomens/MCHeli-Revived/main/schemas/throwable.schema.json": "**/assets/mcheli/throwable/**/*.yml",
    "https://raw.githubusercontent.com/IVomens/MCHeli-Revived/main/schemas/hud.schema.json": "**/assets/mcheli/hud/**/*.yml"
  }
}
```

You get completion of keys and words, hover descriptions and squiggles under unknown keys and values out of
range. **IntelliJ IDEA**: Settings → Languages & Frameworks → Schemas and DTDs → JSON Schema Mappings, one
mapping per folder with the same URLs.

The URLs above follow the mod's `main` branch. To check against the version of the mod your pack targets,
use that release's tag instead of `main` in the URL.

A file may say which schema version it is written for with `SchemaVersion: 1` at the top. Version 1 is the
only one so far; a file without the key is version 1. If a later version of the mod changes the format, it
migrates older files when it reads them, and the schemas say which version they describe
(`x-mcheli-schema-version`).

A format change that adds a spelling keeps the old one working. A vehicle that rides on several racks of
one carrier, for example, now lists them, and the single number still reads:

```yaml
RideRack:
  c5: [1, 2]    # either rack of the C-5
  mv-22: 1
```

## 3. Validating a pack outside the game

### GitHub Actions

Add a workflow to your pack's repository, for example `.github/workflows/content.yml`:

```yaml
name: Content
on: [push, pull_request]
jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: IVomens/MCHeli-Revived/.github/actions/validate-content@main
        with:
          path: .            # the folder that holds assets/mcheli (or several packs)
          # schemas: https://raw.githubusercontent.com/IVomens/MCHeli-Revived/<tag>/schemas/
```

The action installs [check-jsonschema](https://github.com/python-jsonschema/check-jsonschema), checks every
`assets/mcheli/<type folder>/**/*.yml` below `path` against the schema of its folder, marks each problem on the
file in the pull request, writes a table to the job summary, and fails the job when a file has a problem. It
warns about `.yaml` files (which the game does not read) and counts the TXT files it cannot check.

### On your computer

```
pipx install check-jsonschema
check-jsonschema --schemafile https://raw.githubusercontent.com/IVomens/MCHeli-Revived/main/schemas/weapon.schema.json assets/mcheli/weapons/*.yml
```

or run the action's script, which checks every folder at once:
`python validate.py <pack folder> https://raw.githubusercontent.com/IVomens/MCHeli-Revived/main/schemas/`.

### What a schema cannot check

A schema checks each file on its own: keys, types, ranges and words. Only the game checks:

* that a weapon or HUD a vehicle names exists (`/mcheli validate` does this);
* a weapon's `Type`, which the combat code reads;
* HUD formulas and the variables they use;
* rules across files and sections, such as a vehicle needing at least one seat;
* the legacy TXT format.

The game parses YAML 1.1 (SnakeYAML) and check-jsonschema parses YAML 1.2. They read almost everything the same
way; write `true`/`false` rather than `yes`/`no` or `on`/`off`, quote hex colours (`'#FF8800'`), and do not
repeat a key in one map (YAML 1.1 keeps the last one silently, the validator rejects the file).

### Why there is no stand-alone validator program

Reading a file the way the game does means binding it to the game's registries: weapons register their
sounds, ammo items and potion effects are looked up in Minecraft's registries, vehicles are linked to loaded
weapons. That needs Minecraft itself, bootstrapped, on the classpath, which is what the game and the mod's
own unit tests have but a small command-line tool should not. So the out-of-game check is the schema, which
covers everything a single file can get wrong, and `/mcheli validate` runs the real pipeline in game.

## 4. Validating in game

Every content load writes a full report to **`logs/mcheli-content.log`** and one line to the main log:

```
Content load: 412 of 414 files loaded from 3 packs, 2 errors, 17 warnings, 5 files replaced. Full report: logs/mcheli-content.log
```

The report lists the packs in load order, every file that replaced another, and every problem grouped by
pack and file:

```
[ru_patch] assets/mcheli/weapons/gau8.yml
  WARN  assets/mcheli/weapons/gau8.yml:7 unknown key 'Ballistics.Acceleraton', ignored (did you mean 'Acceleration'?)
  WARN  assets/mcheli/weapons/gau8.yml:9 Ammo.MagSize: 50000 is outside 1 to 30000, 30000 is used
```

**`/mcheli validate [pack]`** (operators only) reads the loaded packs again from disk, or only the pack with
that domain, without changing anything that is loaded. Fix a file, run the command, and see the result
without restarting. It prints the counts per severity and the worst few messages in chat, and writes the full
report to `logs/mcheli-validate.log` (click the path in chat to copy it). Links to weapons and HUDs are checked
against the content loaded now.

To see what the game made of a vehicle file, open the vehicle inspector on the vehicle
([INSPECTOR.md](INSPECTOR.md)): its Definition tab lists every value with the line of the file that sets it,
marks the values that differ from the defaults, and its Overview names the pack and file the vehicle came from.

## 5. Overriding content

Packs load in this order:

1. the bundled content (`mcheli_addons/default`);
2. the packs other mods ship in their jars, by mod id (see [API.md](API.md#content-packs-in-a-mods-jar));
   they are copied to `mcheli_mod_packs/` at each start, so do not edit them there;
3. every other pack in `mcheli_addons/`, sorted by its file or folder name, **ignoring case** (`a_pack`,
   `B_pack`, `zz_patch`). So your pack can replace a mod's content too.

When two files define content of the same type and name, **the one loaded later wins, and it replaces the
earlier file whole**. There are no partial overrides: a patch that changes one value of a bundled weapon is a
copy of the whole weapon file with that value changed. The only thing carried over is the author credit: a
replacement that names no `Author` keeps the original one.

Every replacement is listed in the report. Replacing bundled content is what addons are for, so it is not a
warning. Replacing a file of another addon is a warning that names both files, because usually one of the
two pack authors did not expect it. Two files with the same name inside one pack (`gau8.txt` next to
`gau8.yml`, or two subfolders) are a warning too; the one whose path sorts last is used (`.yml` sorts after
`.txt`).

To make a pack win over another on purpose, give it a name that sorts later, such as `zz_ru_patch`.

Why whole files: a file is what an author tests. Merging keys from several packs would make the result
depend on every pack installed, keys that only make sense together (a missile's guidance settings) could
come from different authors, and a file that removes a setting could not say so. Replacing whole files keeps
every loaded definition equal to one file someone wrote, which the report can name.

Earlier builds differed in two ways (see [CHANGELOG.md](../CHANGELOG.md)): the bundled pack was sorted among
the addons by name, so a pack named `a_pack` could not replace bundled content, and neither could one named
`Desert_pack`, since upper case sorted first; and the files of one pack were read in the order the file system
listed them, so which of two same-named files won could differ between computers.

## 6. Common errors

| Message | What to do |
|---|---|
| `unknown key 'Ballistics.Acceleraton', ignored (did you mean 'Acceleration'?)` | Fix the spelling; keys are case sensitive in YAML. |
| `Ammo.MagSize: 50000 is outside 1 to 30000, 30000 is used` | Use a value in the range; the schema shows it. |
| `Setting 'SwitchCooldown' is no longer supported and is ignored` | Delete the line; see CONTENT_FORMAT_NOTES.md. |
| `expected true/false but got the whole number 1` | YAML flags are `true` or `false`. |
| `YAML syntax error: found character '\t(TAB)' that cannot start any token` | YAML indents with spaces, never tabs. |
| `a content file name may only use lower-case letters, digits, '_', '-' and '.'` | Rename the file; the name is the id. |
| `Unknown weapon type 'x' (no file with that name in a weapons folder)` | A YAML vehicle names a weapon no pack defines; check the name or add the weapon. |
| `weapon 'x' is not loaded, so its AddWeapon line is left out` | The same for a TXT vehicle, which still loads. |
| `Skipping '...' in addon '...': no reader for '.yaml' files` | Rename to `.yml`. |
| `Loading legacy .txt content ...: the TXT format is deprecated` | Convert the file with `/mcheli dumpyaml`. |
| `... in pack 'b' replaces ... in pack 'a'` | Two addons define the same id; rename one, or remove one pack. |
| `Addon '...' has no domain metadata, using fallback domain '...'` | Add `addon.domain` to `pack.mcmeta`. |

## 7. For mod developers

* A mod can ship a pack inside its jar, and listen to vehicle and weapon events, through the public API:
  see [API.md](API.md).

* The schemas are generated, never edited: change a schema declaration in `content/def`, then run
  `./gradlew generateSchemas` and commit `schemas/`. The build fails while the files are out of date
  (`JsonSchemaFilesTest`), and every bundled YAML file must be valid under them (`BundledContentSchemaTest`).
  Descriptions come from the Javadoc of the declaring constant; a codec states its value's shape with
  `ValueCodec.shaped(ValueShape)`.
* The CI job `content` runs the validation action on the bundled content.
* `CommunityPacksTest` loads the community-style fixtures in `neoforge/src/test/resources/content/community`
  (CRLF, BOMs, tabs, legacy keys, overrides) and compares the load report with
  `golden/report/community.txt`.

## 8. Behaviours and movement models from mods

Mods built on the API ([API.md](API.md#vehicle-extensions-package-comivomensmcheliapivehicleextension-since-api-11))
can add **behaviours** to vehicles (a winch, a beacon, a siren) and **movement models** that replace a ground
vehicle's physics (a hovercraft). A vehicle file opts in by naming them; without the mod installed the vehicle
still loads, as it would without the lines.

```yaml
Behaviours:
  mymod:winch: { Length: 20 }      # namespace:path, then the settings that mod reads
  mymod:beacon: {}                 # {} when it needs none
Movement: mymod:hovercraft         # vehicles/ only
```

or, for a model that takes settings:

```yaml
Movement:
  Model: mymod:hovercraft
  Settings: { HoverHeight: 1.0, Thrust: 0.02 }
```

* **`Behaviours`** works in every vehicle folder. Each key is the id the mod registered (lower case,
  `namespace:path`); each value is that behaviour's settings map. Behaviours run in the order the file lists
  them.
* **`Movement`** works in `vehicles/` files (emplaced guns and light ground vehicles) only; elsewhere it is an
  unknown key. The model moves the vehicle instead of the ground vehicle physics and the key-driven driving
  (`VehicleFeatures.CanMove`/`CanRotate` no longer push or steer it); seats, weapons, camera, HUD, hit boxes
  and everything else keep working. `PhysicalProperties.Gravity` and `GravityInWater` are handed to the model,
  which may use them.
* What goes in a settings map is up to the mod: read its documentation. The schemas accept any map there, so
  your editor cannot check those keys; the game checks them when it loads the file.

Problems are warnings in the content report, with the file and line, and the vehicle still loads:

| Message | What to do |
|---|---|
| `unknown behaviour 'mymod:winch' (no installed mod registers any), it is ignored` | Install the mod that adds it, or check the spelling; the message lists the ids that are registered. |
| `unknown movement model 'mymod:hovercraft' (...), the vehicle moves as its kind does` | The same for `Movement`. |
| `'winch' is not a behaviour id; write it as namespace:path, ...` | Ids name their mod: `mymod:winch`. |
| `behaviour 'mymod:winch' rejected its settings (...), it is left out` | The mod's own message says which setting is wrong. |
| `unknown key 'Movement'` | Only `vehicles/` files take a movement model. |
