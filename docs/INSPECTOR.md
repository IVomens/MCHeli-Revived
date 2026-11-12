# Vehicle inspector

The vehicle inspector shows what a vehicle is doing right now: where its definition came from, its health,
fuel and throttle, every component's state in tick order, each weapon and seat, and every value of its
definition. For an operator it also shows the **server's** view of the same vehicle beside the client's,
with the differences highlighted, which is what most desync bugs come down to. Its export is the most
useful thing to attach to a bug report about a vehicle.

## Opening it

* **Key.** Bind a key to **Vehicle Inspector** (Options → Controls → Key Binds, category *MCHeli*). It is
  unbound by default. Press it while in a vehicle, or while looking at one up to 64 blocks away. Press it
  again (or Escape) to close the inspector.
* **Command.** `/mcheli inspect [vehicle]` (operators only) opens it on the named entity, else the vehicle
  you are in, else the one you look at. A seat or a hit box of a vehicle counts as the vehicle.

Anyone can open the inspector; it only reads what the client already has. The server's view is for
operators (permission level 2, or *Allow cheats* in single player).

## The screen

The header names the vehicle type and says where the server comparison stands:

| Header | Meaning |
|---|---|
| `Server: 0.2 s old, 0 mismatches` | The server's snapshot arrived 0.2 s ago and agrees with the client. Green with no mismatches, red with some. |
| `Server: waiting...` | The request is on its way. |
| `Server: operators only` | You are not an operator, so only the client's view is shown. |
| `Server: vehicle not found` | The server has no vehicle with that id in your level (it was removed, or the client is behind). |

Every tab is a table. **Key** names the value, **Client** is what your game has, **Server** what the server
has (operators only). The values refresh every tick; the server's four times a second while the inspector
is open. A cell too long for its column ends in `...`; hover it to read it whole.

* A row tinted **red** is a mismatch: both sides have the value and they differ.
* A server value in **grey** differs too, but for a value that changes every tick (position, attitude, age,
  counters, cooldowns), so a difference of a few ticks is expected while the server's snapshot travels,
  or for a value each side keeps for itself and never syncs (a seat's entity, the unique id the server
  gives a vehicle, the client's pending requests). Numbers agree within 2% (at least 0.05). The legend
  under the table says *expected to differ*.
* A value in **orange** with an orange bar on the left is a definition value that differs from the schema's
  default.

### Overview

* **Identity**: the type id (`kind/name`), display name, kind, the pack it came from and the file inside
  that pack, its format and author, the owner (when the server tracks owners), the entity id and UUID.
* **State**: health, fuel (amount / capacity), throttle and the throttle the server last synced, whether it
  is destroyed, the rest state (`resting`, `settling`, or what keeps it awake: `throttle`, `aboard`, `air`,
  `damaged`, ...), and its age in ticks.
* **Motion**: position, velocity (blocks per tick), speed in km/h, yaw, pitch and roll from the vehicle's
  simulation body, and whether it is on the ground.
* **Tick**: how many steps the vehicle tick has and how many run in each phase (`input`, `lifecycle`,
  `systems`, `physics`, `follow_hull`, `bookkeeping`). Phase timings are not measured.

### Components

On the left, every component of the vehicle: first the ones with a tick of their own, in the order they
tick, with their phase; then the ones without. Select one to see the state it describes. The section header
names the phase and the tick step (`SYSTEMS  step 18: fuel: burn and refill`). Components that describe
nothing show only their header.

### Weapons

One section per weapon, in weapon id order (`#0 gau8`): the weapon id, the name the HUD shows, ammunition
in the magazine and in reserve, heat, cooldown and reload ticks, the current mount and fire mode, who is
using it, and for lock-on weapons the lock (`searching`, `locking 12 / 20`, `locked`), its target and range.

### Crew

One section per seat, seat 0 being the pilot: who sits there, the weapon the seat has selected, the seat
entity, its position and pitch limits. The last section counts everyone aboard.

### Definition

Every value of the vehicle file, read through the schema the way `/mcheli dumpyaml` writes it, one section
per YAML section (`General` holds the top-level keys). **Source / default** gives the line the file sets
the value on, when the file can be read again from its pack (`line 42`), and for a value that differs from
the default, the default (`default 1.0`). Lists of entries (seats, weapons, parts) are counted.

The server sends only the values that differ from the defaults. A value the client has changed but the
server has at its default (`(default)` in red) means the two sides loaded different files: a pack that is
installed on one side only, or edited without a reload.

## Overlay

**Overlay** closes the inspector and shows a small box in the top right corner while you fly: the type, HP,
fuel, throttle, speed, altitude, rest state and the server comparison. Press the inspector key to get the
full screen back (which turns the overlay off).

## Exporting for a bug report

* **Export JSON** writes both views to `<minecraft folder>/mcheli/inspector/<kind>-<name>-<date>-<time>.json`
  and says so at the bottom of the screen.
* **Copy summary** copies a few lines to the clipboard: the mod, loader and Minecraft versions, the vehicle
  and its pack and file, its key numbers and the number of mismatches.

When you report a bug about a vehicle (it handles wrongly, jitters, shows the wrong ammunition, or behaves
differently for different players), open the inspector on it while the problem shows, paste the summary
into the report and attach the JSON file. If you are an operator, wait until the header shows the server
view first, so the export has both sides.

The JSON file has this layout (`format` is bumped when it changes):

```json
{
  "format": 1,
  "exported": "2026-11-08T14:03:12.511",
  "mod": "2.0.0", "loader": "NeoForge", "minecraft": "1.21.1",
  "type": "helicopter/ah-64", "entityId": 412, "singlePlayer": true,
  "client": { "gameTime": 18843, "truncated": false, "sections": [
    { "tab": "overview", "title": "State", "detail": "", "values": { "health": "80 / 100", "...": "..." } }
  ] },
  "definition": { "sections": [ { "tab": "definition", "title": "Physics", "values": { "Speed": "1.4" },
                                  "details": { "Speed": "changed; line 31, default 1.0" } } ] },
  "server": { "status": "OK", "ageTicks": 3, "snapshot": { "...": "..." } },
  "mismatches": [ { "where": "Components / FlightModesComponent / gunner mode", "client": "yes", "server": "no" } ]
}
```

## For mod developers

* A component shows its state by overriding `VehicleComponent.describe(StateSink)`: one `put(key, value)`
  per value. It must only read. A component that tracks an owner and describes it under the key `owner`
  shows up in the Overview's Identity section.
* The snapshot (`inspector.InspectorSnapshot`) is the same record on both sides: sections of key and value
  entries, built by `SnapshotBuilder` within `InspectorSnapshot.MAX_BYTES` (48 KiB). Past that, entries are
  left out and the snapshot is marked truncated; every string and list has a bound in the codec.
* `inspector/request` asks the server for its snapshot of an entity id; `inspector/reply` carries the status
  (`OK`, `DENIED`, `NOT_FOUND`) and the snapshot, empty unless `OK`. The server answers a player at most once
  every 5 ticks and refuses players below operator level. `inspector/open` is what `/mcheli inspect` sends.
* `InspectorSnapshotCodecTest` covers the codec and the size bound, the game test
  `mcheli_inspector.inspector_server_snapshot` the server's values and the refusal, and the dev scene
  (`runDevScene`) photographs every tab and the overlay (`inspector_*.png` in the run folder).
