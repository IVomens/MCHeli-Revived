# Content schema

How content files are read, written and handed to the game since stages 3, 4 and 12 of
[ARCHITECTURE.md](ARCHITECTURE.md) Part 2 (§2.3). Every content type goes through it: weapons, throwables and vehicles through a schema,
HUD scripts through a command table built from the same pieces. [CONTENT_FORMAT_NOTES.md](CONTENT_FORMAT_NOTES.md)
describes the formats for pack authors.

## 1. What a schema is

A schema declares a content type's file format once. Both readers and the emitter walk that declaration,
so a setting's key, range and default can no longer drift apart between TXT, YAML and the converter.

```
core.content/              (in :core, no Minecraft)
├─ schema/   Field, Section, KeyAlias, EntrySchema, EntryList, TxtTuple, Ref, Schema, ValueCodec, Codecs,
│            Values, ArgbColor, LocalizedName
└─ def/      the definition records that need no Minecraft, with their schemas: throwable/, hud/

content/                   (in :common)
├─ schema/   Vectors (the codecs of Minecraft's Vec3)
├─ bind/     YamlReader, TxtReader (file → Values → definition), HudYamlReader, HudTxtReader,
│            SourceWarnings, UnsupportedSettings, YamlLineLocator
├─ emit/     SchemaEmitter (definition → YAML), HudEmitter
├─ def/      the definition records that use Minecraft types, with their schemas: weapon/, vehicle/, and
│            the vocabulary they use (VehicleKind, RadarType, RWRType, HitBoxType, SightType, MineFuze)
├─ source/   ContentFormat (per content type: a reader per file extension and the emitter), ContentLoader,
│            VehicleFiles
├─ registry/ ContentType, ContentId, ContentEntry, ContentRegistry, ContentRegistries, LoadedContent,
│            ContentBindings
└─ recipe/   generated recipes, recipe strings, legacy recipe conversion, legacy item names
```

* **`Field<S, T>`**: one setting. YAML key and aliases, codec, default (fixed, or derived from the file
  name), where the section record `S` keeps it, its legacy TXT keys and, where the legacy range differs,
  the TXT codec. `afterTxt` converts a finished TXT value (seconds to ticks, defaults included);
  `alwaysWritten` keeps a value in converted files even when it is the default.
* **`Section<P, S>`**: a YAML section with its members in emitter order, its aliases (`Bomblet` for
  `Submunition`), the keys older packs still write but the game ignores, and the rare section rules: what
  entering it sets (`Recoil` starts its buffer at 1), what leaving it checks (`Recoil` speed at most half
  the count), and a key it cannot do without (`DispenseItem.Location`).
* **`KeyAlias`**: a second key in another section for the same setting (`Sound.Name` and
  `Sound.Locations.Fire`), optionally with a spelling of its own.
* **`EntrySchema<E>`**: the keys of one entry of a YAML list (a seat in `Seats`, a hatch in
  `Components.Hatch`), declared like a section and built into a record. `Field.listOf` and `Field.entry`
  use it, so entries get key paths with lines (`Seats[2].Camera.Pos`), unknown-key reports and emission.
  An entry may have a short form, a plain value that sets one key (`Call: common_pilot`).
* **`TxtTuple<E>`**: a legacy TXT line that describes an entry by position, such as
  `AddSeat = x, y, z[, rotation...]`. It names the entry key each value sets (`value`, `pair`, `vector`,
  `rest`), which counts of values are read (lines that do not fit are ignored, as before), values fixed by
  the TXT key, and the rare legacy rule applied afterwards.
* **`Ref<D>`**: the name of other content (a weapon, a HUD), resolved by a link step after all types load.
* **`ValueCodec<T>`**: how a value is spelled in YAML, in TXT and in the emitter's output. Ranges clamp
  and report the clamp. Structured values (ammo items, muzzle flashes, potion effects, the NTM payload)
  are codecs too, reading their map entries through `YamlEntry`.
* **`Schema<D>`**: the root section, the build function from `Values` to the definition record, the
  legacy TXT rules (keys that set several values, append to lists, or change another value as a side
  effect, such as `Type = bomb` setting both gravities, applied in line order) and which TXT keys an
  in-game reload reads (`reloadableExcept`, which replaced `canReloadItem`).
* **Definitions** are records (`WeaponDefinition`, `ThrowableDefinition`, `VehicleFile`, `HudProgram`)
  whose nested records mirror the YAML sections. They hold the YAML spelling: TXT codecs normalise legacy
  spellings into it (a rack entry raised, a sign flipped), so both formats read into equal records. They
  hold what the file says, in game units. Values the game derives from others (a destruction power that
  follows the explosion power when unset) are resolved by the feature's run-time value (§4), so a converted
  file keeps what its author wrote. `content.def` names no run-time feature type.
* **`ContentFormat<D>`**: one row per `ContentType`: the reader for `yml` and for `txt`, and the emitter.
  The loader, the `/mcheli dumpyaml` command and the tests look formats up there; there is no parser or
  emitter class per format any more.

The schema files read top to bottom like the YAML they describe; `WeaponSchema` lists the top level and
has one class per YAML section.

### Messages

Every problem names the file, the line and the key:

```
WARN  assets/mcheli/weapons/foo.yml:11 Ammo.MagSize: 0 is outside 1 to 30000, 1 is used
WARN  assets/mcheli/weapons/foo.yml:6 unknown key 'Ballistics.Acceleraton', ignored (did you mean 'Acceleration'?)
ERROR Failed to load 'assets/mcheli/weapons/foo.yml' from addon 'mcheli' at line 6, key 'Explosion.Flaming':
      expected true/false but got the whole number 1
```

Which files load, and the values they load with, are the same as before: a value of the wrong type still
skips the file, a value out of range is still clamped.

### Tests

* `ContentDumpTest`: every value run-time code reads, for all bundled files and the TXT corpus, exact. The
  dumps were recorded from the info adapters that stage 12 removed; the test-side `LegacyViews` and
  `LegacyVehicleView` list today's values under the old class and field names, so the goldens did not change.
* `SchemaRoundTripTest`: definition → YAML → definition gives an equal record, for the TXT corpus and
  every bundled file.
* `EmitterRoundTripTest`: the emitted YAML of the TXT corpus is a golden file; the dump lines a round trip
  changes are listed. None are left for bundled files; for the TXT corpus only the format-dependent
  derivations listed under the vehicle and HUD limits remain.
* `TxtKeyCoverageTest`: every TXT key of a schema, and every TXT HUD command, appears in a TXT fixture.
* `HudTraceTest`, `HudExpressionCorpusTest`: what the HUD scripts draw, frame by frame, exact.
* `DiagnosticsTest`: what an author sees for each broken file.

### Known limits

* A legacy TXT weapon that sets recoil but keeps the default recoil buffer (count 2, speed 3) has a buffer
  speed YAML cannot express, because a YAML `Recoil` section limits the speed to half the count. The
  emitter leaves the section's buffer keys to the reader in that case; no bundled or fixture file hits it.
* YAML holds one bullet decay curve; a TXT weapon with several `BulletDecay` lines converts its first.
* A sound written as a YAML map (a client sound definition) is converted to its id only.

## 2. Vehicles

```
content/def/vehicle/
├─ VehicleFile                 everything a vehicle file says: physics, crew, seats, racks, hit boxes,
│                              aircraft features, kind features, parts and components
├─ VehicleSchema               forKind(kind): the shared top level plus the kind's features and parts
├─ PhysicsSchema, FuelSchema, ViewSchemas, CrewSchema, SeatSchemas, HitBoxSchema, FeaturesSchema,
│  KindFeaturesSchema, PartSchemas, WeaponPartSchemas, KindPartSchemas, ComponentsSchema
├─ KindFields, KindProfile     what differs per kind: defaults, ranges, the kind's own keys
├─ VehicleTxtRules, CrewTxtRules, PartTxtRules, GearTxtRules, KindTxtRules   the legacy TXT lines
├─ VehicleCodecs, Placement    shared spellings: names, categories, part position/rotation/name
├─ VehicleRules                derivations on top of the file: config-scaled speeds, camera and mount
│                              positions (cameras sit 0.7 above the file, mounts 0.35), UAV and seat
│                              rules, weapon bay links
└─ VehicleLinks                resolves the weapon and HUD names a file mounts
```

* The schema produces a `VehicleFile`. When the file loads, the core builds its `VehicleSpec` (§4): the
  file, the core's frozen `VehicleDefinition`, and the layouts run-time code reads.
* **TXT normalisation.** Legacy spellings are turned into the YAML form by the TXT codecs and rules: rack
  entries are raised, `OnGroundPitch` and `FloatOffset` are negated in the codec (exactly, so the
  emitter writes the sign the author meant), HUD names become each seat's `Hud` with the kind's default,
  a one-seat vehicle's second HUD becomes `GunnerHud`, and `ExclusionSeat` pairs become the owner's
  `ExcludeWith` (groups of three or more stay `ExclusiveSeats`).
* **Ride racks.** YAML `RideRack` maps a carrier's name to a rack number, or to a list of numbers when the
  vehicle rides on several racks of that carrier (`c5: [1, 2]`). The emitter writes the list only for those
  carriers; the TXT rule keeps a carrier's racks together, the order the YAML map gives them.
* **Cursors.** Lines that add to the line before them (`AddPartWeaponChild`, `AddCrawlerTrack` points)
  use `Values.updateLast`; nothing of the cursor is stored in the definition.
* **Links.** After loading, weapon names are checked against the loaded weapons: a YAML file that mounts
  an unknown weapon fails as before, a TXT file loads without that mount and now says so. HUD names are
  checked on the client only; an unknown HUD leaves the seat without one.

### Known limits

The definition → YAML → definition round trip is exact for every file. `VehicleLayouts` still derives a
few values differently by source format, as the old parsers did, so the TXT corpus keeps these dump
differences after conversion: marker bounds (`bbZ`, `markerWidth`), landing gear `rot2` (zero for TXT,
none for YAML), `lastWeaponPart`, crawler track point spacing, and `VPart.child` (null for TXT without
children). A crawler track with fewer than two points converts to YAML that does not load.

* The rotor speed trim uses float arithmetic for both formats now; TXT files can differ from before by one
  unit in the last place.
* YAML `BoundingBoxes: []` gives the default marker bounds.
* Exclusion pairs are written on the seat that owns them, in seat order.

## 3. HUDs

A HUD file is a program, not a settings record (Part 2 §2.3, "two kinds of files"), so it has no
`Schema`. It is built from the same pieces:

```
core.content.def.hud (in :core)
├─ HudProgram      name, ignoreAutoScale, the ops in file order, the format version
├─ HudOp           sealed: If (holding its body and, in format 2, its Else), Exit, Call, Color,
│                  DrawString, DrawRect, DrawLine, DrawTexture, DrawRadar, DrawGraduation, CameraRotation;
│                  each with its line
├─ HudCommands     the command table
└─ HudCommand      YAML key and aliases, the entry schema of the arguments, the TXT keys and their layouts
```

* **The command table.** Each command's arguments are an `EntrySchema` (YAML keys, codecs, defaults) and
  each TXT key a `TxtTuple` over the same fields, so `DrawRect = x, y, w, h` and
  `DrawRectangle: {Position: [x, y], Size: [w, h]}` read into the same record. The readers and the
  emitter look commands up in the table, never in a switch. `If`/`Do` and `if`/`endif` are the only
  grammar outside it.
* **Two front ends, one program.** `HudTxtReader` and `HudYamlReader` both produce an immutable
  `HudProgram`. Block errors (`endif` without `if`, a nested `if`, a missing `endif`) fail the file with
  their line, as they always failed it.
* **Formulas keep their source text.** The program stores what the file wrote, and `HudEmitter` writes
  it back unchanged; formulas are normalised (`Formula.normalise`) and compiled only when the HUD is built.
* **`Hud.of` compiles its program.** When a HUD loads, `HudScriptCompiler` turns the program into an immutable
  `HudScript` (Part 2 §2.4): formulas compiled by `core.hud.expr` against the HUD variables, blocks lowered to
  gates (format 1) or jumps (format 2). Unknown variables, formulas that cannot be read, unknown
  `DrawString` values and formats that do not fit them are reported then, with the file, line and command.
  `Hud` holds the script and nothing that changes while drawing.
* **Two formats.** Files without a version, and every TXT file, are format 1: blocks do not nest. A YAML
  file whose first item is `- SchemaVersion: 2` is format 2: `If` takes `Then` (or `Do`) and an optional
  `Else`, and blocks nest. The emitter writes each program in its own format.

### Known limits

* A TXT `endif` keeps the empty condition it always had where YAML has `0`; neither is evaluated, but the
  converted file's dump shows the difference.
* Format 1 YAML `If` blocks do not nest, as before: an `If` inside `Do` is reported and skipped. Nested
  blocks need format 2.
* The content dumps show a HUD as the element classes before stage 7 held it, through the test-side
  `LegacyHudView`; they list YAML commands without the lines the reader now keeps.

## 4. Registries and run-time values

The content pipeline knows no feature. Each feature owns the `ContentRegistry<D, T>` of its content types
and hands it to `ContentRegistries.register` at start-up (`McheliMod.registerContentTypes`); the pipeline
reads every file into a `ContentEntry<D>` (where it came from, its format, its author, its definition) and
has the registry's binder build the run-time value `T` once, right after the file is read. A binder may
reject the file by throwing; the loader logs it with the file name, as it logs a parse error.

| Type | Owner | Run-time value |
|---|---|---|
| HUD | `hud.HudManager` | `Hud`: the compiled `HudScript` |
| weapon | `combat.weapon.Weapons` | `WeaponSpec`: the definition, the objects built from it (damage factors by entity class, potion effects, decay curves, muzzle flashes, tracer colours), its sections as accessors, and the resolved values |
| helicopter … vehicle | `aircraft.VehicleTypes` | `VehicleSpec`: the `VehicleFile`, the core's `VehicleDefinition`, the speed after the server setting, and the layouts `CrewLayout`, `Armament`, `DrawnParts`, `KindParts`, `HitBoxes` with the part classes of `VehicleParts` |
| throwable | `equipment.throwable.Throwables` | the `ThrowableDefinition` itself |

Lookups return `Optional`. A reload (`ContentRegistry.reload`, `reloadAll`) reads the file again, builds a new
value, calls the registry's reload hook (a vehicle's models load again) and tells its listeners.

**Run-time bindings** are kept apart from the values, keyed by `ContentId` (type and name):
`ContentBindings` holds the item registered for each vehicle and throwable, the item stacks each weapon is
reloaded with, and a dispenser weapon's item. Client caches live in client maps keyed by name:
`ProjectileModels` (bullet and bomblet models), `SpentCasings` (casings whose models loaded),
`VehicleHuds` (the HUD of each seat), `ExhaustFlameTextures`.

Two bindings keep the timing they had: round stacks are bound once every item exists, and a dispenser
weapon's item is bound only when the weapon is read again in game, never at start-up.

## 5. Reports, JSON Schemas and pack order

```
content/
├─ report/   ReportCollector, ContentReport, Diagnostic, FileOverride, ContentLog
├─ command/  ValidateCommand (/mcheli validate)
├─ emit/     JsonSchemaEmitter, ShapeWriter, JsonSchemas (the schemas/ files)
└─ pack/     PackOrder
```

* **Reports** (Part 2 §3.3). `ContentRegistries.loadContents` opens a `ReportCollector` for the whole load.
  Problems are still logged where they are found, as one line each in the main log; while the collector is
  open it also receives every warning and error the mod logs on the loading thread and files it under the
  content file being read (`ReportCollector.enterFile`, opened by `PackFile.parse`). The finished
  `ContentReport` goes to `logs/mcheli-content.log`, with one summary line in the main log.
  `ContentValidation` (`/mcheli validate [pack]`) runs the same read and bind steps over the loaded packs
  without filling the registries and writes `logs/mcheli-validate.log`.
* **Pack order and replaced files.** `PackOrder`: the bundled pack (`mcheli_addons/default`) first, then the
  other packs by name ignoring case; files of one pack in path order. A later file of the same type and name
  replaces the earlier one whole (`ContentRegistry.fill`). `Overrides` lists every replacement in the report;
  replacing another addon's file, or a second file of one name in a pack, is also a warning.
* **JSON Schemas.** A codec states the shape of its YAML value (`ValueCodec.shape()`, set with
  `shaped(ValueShape)`; a codec that states none accepts anything). `JsonSchemaEmitter` walks a `Schema` like
  the emitter does and writes draft 2020-12: keys and aliases, `KeyAlias` keys, required keys under any
  spelling, entry schemas as `$defs`, defaults, ranges, words, and the unsupported keys as `deprecated`.
  `JsonSchemas` lists the files; `./gradlew generateSchemas` writes them to `schemas/` with descriptions
  from the Javadoc of the declaring constants, `JsonSchemaFilesTest` fails while they are out of date, and
  `BundledContentSchemaTest` checks every bundled YAML file against them. A schema is never stricter than
  the game: what the game reads without a message is valid.
* **`SchemaVersion`.** A YAML settings file may start with `SchemaVersion: N` (`Schema.VERSION`, now 1); the
  reader checks it before binding, which is where a later version will migrate older files.

## 6. Not done yet

* HUD `Call` names are still looked up while drawing, not linked at load time.
* Problems still reach the report through the log rather than through a `DiagnosticSink` the readers write
  to; the report's file attribution relies on the loader marking the file being read.
