# Running a server with MCHeli: Revived

This page is for server owners: who owns which vehicle and how owners lock them, how many vehicles
players may place, how long stray projectiles last, and the operator commands that show what MCHeli
costs the server and remove vehicles. Every setting below lives in `config/mcheli.cfg` on the server, in the `[General]` section,
and takes effect after `/mcheli reconfig` or a restart.

## Ownership and locking

A vehicle remembers the player who placed it from its item (or launched it from a UAV station): their
UUID, and their name as it was then. Vehicles from worlds saved before this existed, and vehicles a
dispenser placed, have no owner and cannot be locked until someone claims them (see `/mcheli lock`
below). Picking a vehicle up and placing it again makes the player who places it the owner.

The owner locks or unlocks a vehicle with `/mcheli lock` and `/mcheli unlock` while aboard it or
looking at it, or by sneaking and using it with an empty hand. The vehicle's cargo then opens with an
item in hand, or for anyone else as before.

A locked vehicle:

* seats a pilot only if they are its owner, on the owner's scoreboard team (`LockAllowsTeam`) or an
  operator. Players see "Locked by *owner*" when they look at a seat they may not take;
* lets anyone take the passenger and gunner seats, unless `LockPassengerSeats = true`;
* can only be picked up (punched in creative, or broken down by hand) and have its cargo opened by its
  owner and operators. Anyone else's punch counts as an ordinary hit;
* takes damage exactly like an unlocked one. Locking is theft protection, not armour.

| Setting | Default | Meaning |
|---|---|---|
| `VehicleLocking` | `true` | `false` switches locking off: every vehicle behaves as unlocked (owners are still recorded). |
| `LockAllowsTeam` | `true` | Players on the owner's scoreboard team may fly a locked vehicle. |
| `LockPassengerSeats` | `false` | `true` locks the passenger and gunner seats too. |

Teams are vanilla scoreboard teams (`/team`). The owner does not need to be online.

## Placement limits

The limits apply when a player places a vehicle from its item, when a UAV station launches one and when
a dispenser places one (except the per-player limit, as a dispenser has no player). A refused player
is told which limit stopped them and keeps the item. `0` means unlimited, which is the default for all
of them.

| Setting | Default | Counts |
|---|---|---|
| `MaxVehiclesPerPlayer` | `0` | Vehicles the player owns, in every dimension. |
| `MaxVehiclesPerChunk` | `0` | Vehicles in the chunk the new one would stand in. |
| `MaxVehiclesPerLevel` | `0` | Vehicles in the dimension. |
| `MaxHelicoptersPerLevel` | `0` | Helicopters in the dimension. |
| `MaxPlanesPerLevel` | `0` | Planes (VTOLs included) in the dimension. |
| `MaxShipsPerLevel` | `0` | Ships in the dimension. |
| `MaxTanksPerLevel` | `0` | Tanks in the dimension. |
| `MaxGroundVehiclesPerLevel` | `0` | Vehicles of the `vehicles` kind (cars, guns) in the dimension. |
| `OpsBypassLimits` | `true` | Operators may place beyond every limit. |

The limits count vehicles in **loaded** chunks. A vehicle parked where no player is keeps no chunk
loaded, so it does not count until its chunk loads again. This keeps the check cheap (each dimension
keeps an index of its vehicles, so no entity search happens), but a player who leaves vehicles far
apart can own more than `MaxVehiclesPerPlayer` in total. Use `/mcheli vehicles list <player>` to see
what someone has near players.

## Who counts as an operator

`AdminCommandPermission` (default `2`, the vanilla level of `/gamemode` and `/tp`) is the operator level
needed for `/mcheli perf`, `/mcheli vehicles`, changing the lock on someone else's vehicle, flying any
locked vehicle and bypassing the limits.

* **NeoForge:** the check goes through NeoForge's permission API as the node `mcheli.admin`. Without a
  permission mod the node falls back to `AdminCommandPermission`; with one (LuckPerms, for example) you
  can grant the node to a group instead.
* **Fabric:** Fabric API has no permission API, and the community Fabric Permissions API would be a new
  dependency, so the operator level always decides.

A `CommandPermission = perf : name1, name2` (or `vehicles : ...`) line also lets the named players use
that command without being operators, as it does for the other `/mcheli` commands. With
`EnableCommand = false` the whole `/mcheli` command, these sub-commands included, is gone; locking by
sneak-use still works then.

## Commands

### `/mcheli lock` and `/mcheli unlock`

For every player. Acts on the vehicle you are aboard, or else the one you look at within reach. Only
the owner and operators may change a lock. A vehicle without an owner is claimed by the player who
locks it while flying it, or by an operator who locks it.

### `/mcheli perf`

For operators. Shows, for each dimension:

```
MCHeli server cost over the last 30 seconds
minecraft:overworld: 12 vehicles, 40 projectiles, 9 chunks kept loaded by leases. helicopters: 5 planes: 4 tanks: 3
  Tick time: vehicles 0.412 ms mean, 0.901 ms p95; projectiles 0.120 ms mean, 0.540 ms p95 (600 ticks)
The 5 most expensive vehicles:
  0.083 ms per tick ah-64 (helicopters, owner Steve) [locked] minecraft:overworld 120 70 -340
  ...
```

* **vehicles** are those in loaded chunks; **projectiles** are the MCHeli bullets, rockets and
  missiles that ticked in the last tick.
* **Tick time** is the time the server thread spent inside MCHeli vehicle and projectile ticks, per
  tick, as a mean and a 95th percentile over the last 600 ticks (ticks in which none ran count as 0).
  It is measured with two clock reads per vehicle and projectile tick; nothing else is timed.
* **chunks kept loaded by leases** are the chunks MCHeli forces for UAVs flown from a station, wingmen
  on autonomous missions (`EnableUavChunkLoading`, the wingman `forceChunkload`) and fast projectiles
  (`ProjectileChunkLoad`).
* The **most expensive vehicles** are averaged over the ticks each one ticked in the last 30 to 60
  seconds. Click a position to teleport there.

Network traffic is not counted.

### `/mcheli vehicles`

For operators. Vehicles in unloaded chunks are neither listed nor removed.

| Command | Does |
|---|---|
| `/mcheli vehicles list` | Lists every vehicle in loaded chunks (the first 50), with kind, owner, lock and a position to click. |
| `/mcheli vehicles list <player>` | The same for one owner (an online player, or one the server has seen). |
| `/mcheli vehicles remove <targets>` | Removes the vehicles an entity selector picks, for example `@e[type=mcheli:heli,distance=..20]` or the vehicle's UUID. Selecting a seat or hit box removes its vehicle. |
| `/mcheli vehicles remove radius <blocks>` | Removes the vehicles within that many blocks of you (at most 512). |
| `/mcheli vehicles removeall <kind> confirm` | Removes every loaded vehicle of a kind: `helicopter`, `plane`, `ship`, `tank`, `vehicle` or `all`. Without `confirm` nothing happens. |

A removal first gets everyone out (pilot, passengers and gunners land next to the vehicle), then
removes the vehicle with its seats. It drops nothing, unless `RemoveDropsItem = true`, in which case
the vehicle's item and its cargo drop where it stood. Every removal is written to the server log with
who ran the command, the vehicle, its owner and its position.

| Setting | Default | Meaning |
|---|---|---|
| `RemoveDropsItem` | `false` | `true` drops the vehicle's item and cargo on removal. |

## Stray projectiles

A projectile that flies into a chunk that is loaded but ticks no entities (no player is near enough)
stops there, as vanilla's arrows do, and flies on when the chunk ticks again. In a long fight thousands
of rounds can collect at the edge of the area the players keep ticking. MCHeli removes them:

* A projectile that has not ticked for `FrozenProjectileLifetime` seconds of game time is removed
  without exploding, by a check that looks at every loaded projectile once a second. One that waited
  less flies on as before; one in flight ticks every tick, so the setting never touches it. Game time
  stands still while the server is stopped or `/tick freeze` holds it.
* Projectiles are not saved with the world. One in a chunk that unloads is gone; one saved by an
  earlier version is removed within a second of its chunk loading.
* Laid mines (bombs whose weapon has a mine fuze) are the exception to both: they are saved and wait
  as long as it takes.

| Setting | Default | Meaning |
|---|---|---|
| `FrozenProjectileLifetime` | `30` | Seconds a projectile may wait in a chunk that ticks no entities before it is removed. `0` keeps them until the chunk ticks again or unloads. |

## Example

A survival server where everyone may keep three vehicles, nobody parks more than six in one chunk,
the dimension holds at most four tanks, and squads share their vehicles:

```
VehicleLocking = true
LockAllowsTeam = true
LockPassengerSeats = false
MaxVehiclesPerPlayer = 3
MaxVehiclesPerChunk = 6
MaxTanksPerLevel = 4
OpsBypassLimits = true
AdminCommandPermission = 3
```

Then `/team add alpha`, `/team join alpha Steve Alex`: Alex may fly Steve's locked helicopter, everyone
else may ride in its gunner seat but not fly it, and `/mcheli vehicles removeall tank confirm` clears
the tanks after an event.
