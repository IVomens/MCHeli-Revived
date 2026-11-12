# Benchmarks

Two benchmarks and one gate:

* the **server benchmark** measures what vehicles cost a server: tick time, heap allocation and
  network traffic, for four workloads from 1 to 500 vehicles (`./gradlew :neoforge:runBenchmarks`);
* the **benchmark gate** runs a small part of it in CI and fails when the server got slower or
  allocates more than a committed baseline (`./gradlew :neoforge:runBenchmarkGate`);
* the **client benchmark** is a command in the released mod, `/mcheli benchmark client`, that
  measures frame times on a player's own machine: the built-in renderer against Flywheel, and
  fixed-step attitude on against off ([PLAYTEST.md](PLAYTEST.md) is the guide for testers).

[PERFORMANCE_BASELINE.md](PERFORMANCE_BASELINE.md) keeps the older stage 0 and stage 8 numbers.

## Server benchmark

### Method

One game test (`gametest/bench/ServerBenchmark`) runs every tier in turn on the NeoForge (or
Fabric) game test server. A tier is a workload with a number of vehicles:

| Workload | What the vehicles do |
|---|---|
| `parked` | Every bundled kind (helicopters, planes, tanks, vehicles, ships), nobody aboard, 14 blocks apart: an airbase. |
| `flying` | Helicopters and planes 40 blocks up, each boarded by a player that holds full throttle (planes get a push so they keep airspeed). |
| `firefight` | Manned vehicles in rows facing each other; every player fires its current weapon each tick and switches weapon every 2 s. Rounds, rockets, missiles and bombs fill the level. |
| `mixed` | Helicopters, planes and tanks. Of every ten: three parked, two flown by players, three manned by AI gunners (two tanks, one helicopter), two helicopters flying as wingmen, in formations of four behind leaders that circle the field. |

For each tier the run spawns the vehicles (with seeded random generators, so every run rolls the
same numbers), adds an **observer**: a player 30 blocks above the middle with a 12-chunk view, runs
600 warm-up ticks, measures the next 600 ticks and removes everything. The test world around the field
is generated before the first tier, and mob griefing is off so explosions leave the ground as it was.

Per server tick, from the start to the end of the tick on the server thread:

* **ms/tick**: wall time; mean, p50, p95 and p99 over the measured ticks.
* **alloc/tick**: bytes the server thread allocated (`com.sun.management.ThreadMXBean#getCurrentThreadAllocatedBytes`).
  Other threads (chunk workers, the radar's virtual threads, Netty) are not in it.
* **net/tick/vehicle**: what the observer was sent about MCHeli, divided by the vehicles it tracks.
  That is every MCHeli message, plus the vanilla packets about entities of MCHeli's types (spawn,
  entity data, moves, teleports, motion, passengers, removal, events). Each packet is encoded with the
  play protocol's codec (vanilla packets) or the message's own codec (MCHeli's), and counted with its
  frame length prefix, before compression. The players are mock players whose packets pile up in an
  embedded channel; they are counted after the tick has been timed. Nothing in the networking code
  was changed for this: the game test layer opens MCHeli's channels on the observer's connection
  (`ModChannels`, one small class per loader), as a real client would by announcing them.

The report also counts the projectiles while a tier is measured (`ProjectileCensus`, `projectiles` in
the JSON): how many are alive after a tick, how many of those are frozen in chunks that tick no
entities, how many are fired or split off per tick, and how many ticks those that ended flew.

The markdown report breaks the observer's bytes down by kind (radar contacts, explosion effects, other
MCHeli messages, moves and rotations, entity data, teleports, motion, spawns and removals, passengers,
other vanilla packets), with the bytes and packets of each per tick. Its last column, **pilot B/tick**, is
everything the first crew member to board was sent about MCHeli; MCHeli's channels are open on its
connection too, so it gets the radar picture and the weapon messages a real pilot gets. The JSON
(`build/benchmarks/server-full.json`) has more: `netByKind` and `netCountByKind` per packet or message
type, `netEntityData` per synced value (`VehicleEntity#FUEL`), `netByEntityType` per entity type and
packet (`seat Pos`), and `pilotByKind`.

### Machine

* 4 vCPU Intel Xeon (Emerald Rapids) @ 2.10 GHz, KVM cloud VM, 15 GB RAM, Linux 6.18.
* OpenJDK 21.0.12 (Ubuntu build), `-Xmx3G`, NeoForge 21.1.265.
* Other builds shared the machine, so single numbers move by 10-30 %; compare runs from the
  same session, and prefer the gate's relative numbers over absolute milliseconds.

### Results

NeoForge, commit `e182aea` (after the network work below), 2026-11-08. Intel(R) Xeon(R) Processor @
2.10GHz, 4 threads, Ubuntu 21.0.12.1. Calibration loop 9.98 ms. 600 warm-up ticks, 600 measured ticks
per tier.

| Workload | Vehicles | Crew / gunners / wingmen | Entities | ms mean | p50 | p95 | p99 | alloc KiB/tick | net B/tick/vehicle | tracked |
|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| parked | 1 | 0 / 0 / 0 | 3 | 1.82 | 1.73 | 2.49 | 3.08 | 73.31 | 0.23 | 1.00 |
| parked | 25 | 0 / 0 / 0 | 138 | 1.76 | 1.71 | 2.02 | 2.61 | 82.33 | 0.23 | 25.00 |
| parked | 50 | 0 / 0 / 0 | 235 | 1.70 | 1.61 | 2.21 | 2.45 | 88.46 | 0.25 | 49.00 |
| parked | 100 | 0 / 0 / 0 | 375 | 2.20 | 2.07 | 2.87 | 4.75 | 106 | 0.48 | 99.00 |
| parked | 200 | 0 / 0 / 0 | 761 | 2.45 | 2.26 | 3.19 | 4.10 | 135 | 0.25 | 196 |
| parked | 500 | 0 / 0 / 0 | 1852 | 2.98 | 2.54 | 5.11 | 7.40 | 253 | 0.26 | 469 |
| flying | 1 | 1 / 0 / 0 | 4 | 3.12 | 2.91 | 4.12 | 6.87 | 240 | 11.05 | 1.00 |
| flying | 25 | 25 / 0 / 0 | 167 | 4.54 | 4.24 | 6.43 | 9.25 | 606 | 11.05 | 25.00 |
| flying | 50 | 50 / 0 / 0 | 288 | 4.66 | 4.45 | 6.42 | 8.01 | 833 | 11.05 | 30.00 |
| flying | 100 | 97 / 0 / 0 | 590 | 6.73 | 5.96 | 9.72 | 16.18 | 2002 | 11.06 | 60.00 |
| flying | 200 | 192 / 0 / 0 | 1118 | 8.92 | 8.10 | 13.99 | 22.85 | 3520 | 11.07 | 74.00 |
| flying | 500 | 455 / 0 / 0 | 2744 | 16.22 | 14.34 | 28.34 | 45.30 | 14844 | 11.04 | 95.50 |
| firefight | 1 | 1 / 0 / 0 | 88 | 2.44 | 2.32 | 3.00 | 4.35 | 285 | 49.53 | 1.00 |
| firefight | 25 | 25 / 0 / 0 | 1417 | 4.44 | 4.34 | 5.89 | 6.92 | 1064 | 41.16 | 25.00 |
| firefight | 50 | 50 / 0 / 0 | 2484 | 7.66 | 7.05 | 11.23 | 18.24 | 3032 | 56.29 | 50.00 |
| firefight | 100 | 100 / 0 / 0 | 3480 | 13.43 | 12.12 | 20.14 | 39.10 | 5663 | 45.71 | 100 |
| firefight | 200 | 200 / 0 / 0 | 6222 | 28.44 | 27.08 | 41.35 | 64.73 | 17554 | 47.74 | 200 |
| firefight | 500 | 500 / 0 / 0 | 12105 | 102 | 99.53 | 156 | 205 | 82211 | 48.10 | 498 |
| mixed | 1 | 0 / 0 / 0 | 3 | 3.16 | 3.08 | 4.03 | 4.56 | 189 | 0.82 | 1.00 |
| mixed | 25 | 6 / 6 / 4 | 112 | 5.77 | 5.58 | 8.05 | 10.34 | 1081 | 7.21 | 22.00 |
| mixed | 50 | 10 / 15 / 10 | 278 | 7.78 | 7.46 | 10.71 | 13.76 | 1725 | 7.90 | 45.00 |
| mixed | 100 | 20 / 30 / 20 | 543 | 10.37 | 9.89 | 14.46 | 18.27 | 2583 | 7.75 | 90.00 |
| mixed | 200 | 40 / 60 / 40 | 1067 | 14.55 | 14.17 | 20.07 | 25.34 | 4340 | 7.10 | 176 |
| mixed | 500 | 100 / 150 / 100 | 2672 | 32.79 | 30.85 | 47.01 | 61.49 | 10836 | 5.74 | 340 |

Network, bytes (packets) per tick the observer was sent about MCHeli, by kind; the pilot column is
everything one crew member was sent about MCHeli.

| Tier | radar contacts | explosion effects | other MCHeli messages | move/rotation | entity data | teleport | motion | spawn/remove | passengers | other vanilla | total | B/tick/vehicle | pilot B/tick |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| parked/1 | - | - | - | 0.18 (0.02) | - | 0.05 (0.00) | - | - | - | - | 0.23 | 0.23 | 0.00 |
| parked/25 | - | - | - | 4.60 (0.46) | - | 1.25 (0.04) | - | - | - | - | 5.85 | 0.23 | 0.00 |
| parked/50 | - | - | - | 9.88 (0.90) | - | 2.53 (0.08) | - | - | - | - | 12.41 | 0.25 | 0.00 |
| parked/100 | - | - | - | 31.62 (2.80) | 0.30 (0.04) | 5.12 (0.17) | 10.23 (1.02) | - | - | - | 47.27 | 0.48 | 0.00 |
| parked/200 | - | - | - | 39.53 (3.59) | - | 10.13 (0.33) | - | - | - | - | 49.65 | 0.25 | 0.00 |
| parked/500 | - | - | - | 94.58 (8.60) | 0.33 (0.04) | 24.23 (0.78) | 1.35 (0.14) | - | - | - | 120 | 0.26 | 0.00 |
| flying/1 | - | - | - | 11.00 (1.00) | - | 0.05 (0.00) | - | - | - | - | 11.05 | 11.05 | 22.78 |
| flying/25 | - | - | - | 275 (25.00) | - | 1.29 (0.04) | - | - | - | - | 276 | 11.05 | 103 |
| flying/50 | - | - | - | 330 (30.00) | - | 1.55 (0.05) | - | - | - | - | 332 | 11.05 | 123 |
| flying/100 | - | - | - | 660 (60.00) | 0.60 (0.08) | 3.10 (0.10) | 0.05 (0.01) | - | - | - | 664 | 11.06 | 207 |
| flying/200 | - | - | - | 814 (74.00) | 1.37 (0.17) | 3.82 (0.12) | 0.23 (0.02) | - | - | - | 819 | 11.07 | 353 |
| flying/500 | - | - | - | 1046 (95.09) | 3.00 (0.38) | 4.91 (0.16) | 0.63 (0.06) | 0.05 (0.01) | - | - | 1055 | 11.04 | 841 |
| firefight/1 | - | - | 1.25 (0.03) | 9.22 (0.84) | 3.21 (0.25) | 27.23 (0.88) | 1.60 (0.16) | 6.58 (0.21) | - | 0.43 (0.11) | 49.53 | 49.53 | 19.03 |
| firefight/25 | - | 122 (0.20) | 28.57 (0.71) | 106 (9.60) | 109 (8.01) | 389 (12.56) | 57.00 (5.70) | 203 (6.74) | - | 13.29 (3.32) | 1029 | 41.16 | 333 |
| firefight/50 | - | 698 (0.69) | 56.91 (1.40) | 281 (24.06) | 276 (18.87) | 790 (24.69) | 210 (20.12) | 473 (15.07) | - | 30.03 (7.51) | 2814 | 56.29 | 1024 |
| firefight/100 | - | 1186 (1.56) | 116 (2.78) | 481 (39.79) | 501 (33.41) | 999 (31.22) | 399 (36.30) | 837 (26.58) | - | 53.10 (13.28) | 4571 | 45.71 | 1588 |
| firefight/200 | - | 2636 (3.39) | 230 (5.50) | 1083 (89.52) | 1013 (67.78) | 1860 (58.14) | 929 (84.49) | 1690 (53.67) | - | 107 (26.83) | 9549 | 47.74 | 3366 |
| firefight/500 | - | 7166 (8.93) | 500 (11.99) | 2627 (217) | 2585 (173) | 4160 (130) | 2342 (213) | 4300 (136) | - | 273 (68.27) | 23953 | 48.10 | 8547 |
| mixed/1 | - | - | - | 0.22 (0.02) | - | 0.05 (0.00) | 0.55 (0.05) | - | - | - | 0.82 | 0.82 | 0.00 |
| mixed/25 | - | - | - | 103 (8.61) | 2.14 (0.22) | 1.17 (0.04) | 52.31 (4.76) | - | - | - | 159 | 7.21 | 65.31 |
| mixed/50 | - | - | - | 228 (19.39) | 6.08 (0.63) | 2.61 (0.08) | 119 (10.84) | - | - | - | 356 | 7.90 | 68.02 |
| mixed/100 | - | - | - | 454 (39.49) | 10.24 (1.04) | 4.91 (0.15) | 228 (20.74) | - | - | - | 697 | 7.75 | 126 |
| mixed/200 | - | - | 0.37 (0.01) | 828 (72.33) | 18.23 (1.84) | 9.39 (0.29) | 391 (35.53) | 2.41 (0.10) | - | 0.14 (0.04) | 1249 | 7.10 | 184 |
| mixed/500 | - | - | 2.06 (0.03) | 1342 (117) | 24.50 (2.44) | 18.08 (0.57) | 552 (50.14) | 11.16 (0.39) | - | 0.69 (0.17) | 1950 | 5.74 | 465 |

Reproduce with `./gradlew :neoforge:runBenchmarks --offline` (about 10 minutes here; writes
`build/benchmarks/server-full.json` and `.md`). `-Pworkloads=parked,mixed` and `-Pvehicles=1,100`
narrow it, `-Pwarmup=` and `-Pmeasure=` change the windows, `-Pjfr` records `run/benchmarks.jfr`.
`./gradlew :fabric:runBenchmarks` runs the same on Fabric.

How to read it:

* The **1-vehicle rows** are what the test server costs with one observer and nothing to do: 2-3 ms
  a tick in this run (4-5 ms in a busier one earlier the same day) for the field's 600 loaded chunks
  and the observer. Subtract them for MCHeli's share.
* The game test server runs ticks back to back, without the 50 ms wait of a real server. Times per
  tick are the same either way; a 20 TPS server has the rest of each 50 ms free.
* **Parked vehicles are almost free**: 500 parked vehicles add about 1 ms and 180-220 KiB of garbage
  a tick, because resting vehicles skip their physics.
* **Crewed and firing vehicles cost**. A crewed, flying vehicle costs 0.02-0.05 ms and 15-30 KiB a
  tick; 500 of them take 15-30 ms. A firefight is dominated by its projectiles: 500 shooters keep about
  12,000 entities alive and take about 100 ms a tick, so a real server would drop to about 10 TPS.
* `mixed` scales almost linearly up to 500 vehicles (31-33 ms); its AI gunners and wingmen cost about as
  much as the crewed flyers.
* `flying` tracks fewer vehicles than it spawns: the aircraft fly out of the observer's view.
* What an observer is sent is now mostly vanilla: one move packet a tick for each flying vehicle in
  view (11 B/tick/vehicle in `flying`), velocity updates for wingmen, and in a firefight the
  projectiles (spawns, teleports, entity data) and the explosion effects. See "Network traffic" below
  for what changed.

## Network traffic

Measured with the breakdown above, before and after the changes that cut it (`faf892b` measures,
`554b30a`, `2305451` and `6f7b6de` cut; the after run also has the component wiring merged in).

### Before

NeoForge, game code of `7976d45` with the measurement of `faf892b`, same machine and session.

| Tier | radar contacts | explosion effects | other MCHeli messages | move/rotation | entity data | teleport | motion | spawn/remove | passengers | other vanilla | total | B/tick/vehicle | pilot B/tick |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| parked/1 | 33.40 (0.20) | - | - | 0.37 (0.04) | - | 0.10 (0.00) | - | - | - | - | 33.87 | 33.87 | 0.00 |
| parked/25 | 33.88 (0.20) | - | - | 25.36 (2.51) | - | 6.87 (0.23) | - | - | - | - | 66.11 | 2.64 | 0.00 |
| parked/50 | 33.60 (0.20) | - | - | 47.19 (4.29) | - | 12.09 (0.39) | - | - | - | - | 92.88 | 1.90 | 0.00 |
| parked/100 | 524 (0.20) | - | - | 87.08 (7.84) | 0.30 (0.04) | 19.32 (0.62) | 10.73 (1.07) | - | - | - | 642 | 6.48 | 0.00 |
| parked/200 | 986 (0.20) | - | - | 153 (13.90) | - | 39.16 (1.26) | - | - | - | - | 1178 | 6.01 | 0.00 |
| parked/500 | 3360 (0.20) | - | - | 355 (32.29) | 0.33 (0.04) | 90.99 (2.94) | 0.85 (0.09) | - | - | - | 3807 | 8.12 | 0.00 |
| flying/1 | 59.29 (0.20) | - | - | 16.50 (1.50) | - | 0.10 (0.00) | - | - | - | - | 75.89 | 75.89 | 75.37 |
| flying/25 | 667 (0.20) | - | - | 913 (83.00) | - | 7.29 (0.24) | - | - | - | - | 1587 | 63.48 | 727 |
| flying/50 | 1752 (0.20) | - | - | 1122 (102) | - | 8.99 (0.29) | - | - | - | - | 2883 | 96.11 | 1813 |
| flying/100 | 3499 (0.20) | - | - | 2244 (204) | 0.60 (0.08) | 17.98 (0.58) | 0.05 (0.01) | - | - | - | 5762 | 96.03 | 3604 |
| flying/200 | 7666 (0.20) | - | - | 2717 (247) | 1.37 (0.17) | 21.70 (0.70) | 0.23 (0.02) | - | - | - | 10406 | 141 | 7760 |
| flying/500 | 19400 (0.20) | - | - | 3474 (316) | 3.00 (0.38) | 27.69 (0.89) | 0.63 (0.06) | 0.05 (0.01) | - | - | 22906 | 240 | 19312 |
| firefight/1 | 59.20 (0.20) | - | 1.25 (0.03) | 9.42 (0.86) | 3.21 (0.25) | 27.28 (0.88) | 1.60 (0.16) | 6.58 (0.21) | - | 0.43 (0.11) | 109 | 109 | 76.82 |
| firefight/25 | 672 (0.20) | 312 (0.20) | 28.57 (0.71) | 146 (13.29) | 109 (8.01) | 395 (12.75) | 61.45 (6.15) | 203 (6.74) | - | 13.29 (3.32) | 1941 | 77.65 | 1184 |
| firefight/50 | 1323 (0.20) | 1780 (0.72) | 56.91 (1.40) | 487 (42.77) | 276 (18.87) | 775 (24.26) | 230 (22.15) | 473 (15.10) | - | 30.03 (7.51) | 5432 | 109 | 3374 |
| firefight/100 | 3333 (0.20) | 2985 (1.57) | 116 (2.78) | 766 (63.51) | 501 (33.41) | 983 (30.72) | 431 (39.14) | 837 (26.58) | - | 53.10 (13.28) | 10003 | 100 | 6660 |
| firefight/200 | 6703 (0.20) | 6726 (3.30) | 230 (5.51) | 1789 (148) | 1013 (67.77) | 1881 (58.77) | 994 (90.34) | 1689 (53.64) | - | 107 (26.80) | 21131 | 106 | 13829 |
| firefight/500 | 16585 (0.20) | 17839 (8.81) | 500 (11.99) | 4548 (377) | 2583 (173) | 4402 (138) | 2519 (229) | 4299 (136) | - | 273 (68.26) | 53547 | 108 | 34812 |
| mixed/1 | 34.00 (0.20) | - | - | 0.44 (0.04) | - | 0.11 (0.00) | 0.55 (0.05) | - | - | - | 35.10 | 35.10 | 0.00 |
| mixed/25 | 439 (0.20) | - | - | 253 (20.48) | 2.09 (0.22) | 4.64 (0.15) | 52.31 (4.76) | - | - | - | 751 | 34.16 | 529 |
| mixed/50 | 786 (0.20) | - | - | 818 (65.20) | 5.29 (0.54) | 11.63 (0.36) | 110 (10.02) | - | - | - | 1732 | 38.49 | 909 |
| mixed/100 | 1530 (0.20) | - | - | 1595 (128) | 10.36 (1.05) | 23.84 (0.75) | 227 (20.68) | - | - | - | 3386 | 37.63 | 1644 |
| mixed/200 | 2955 (0.20) | - | 0.37 (0.01) | 2983 (243) | 18.10 (1.83) | 45.65 (1.43) | 400 (36.38) | 2.41 (0.10) | - | 0.14 (0.04) | 6405 | 36.39 | 3096 |
| mixed/500 | 8142 (0.20) | - | 2.19 (0.03) | 4608 (379) | 24.69 (2.45) | 83.31 (2.60) | 556 (50.54) | 11.72 (0.40) | - | 0.73 (0.18) | 13429 | 39.50 | 8252 |

Three things made up most of it:

* **Radar contacts** (`combat/radar_contacts`): every five ticks, every player within 2,000 blocks got
  every radar contact in full, with the entity's class name, type name and dimension as strings and two
  positions as doubles (about 120 bytes a contact), whether or not the player could show a radar. In
  `flying/100` that was 3.5 KB a tick to the observer and to each pilot.
* **Seat moves** (`seat Pos`, `seat PosRot`): every seat of every vehicle sent a move packet every other
  tick, which the client ignores, since the vehicle places its seats itself (`SeatEntity#lerpTo`). In
  `flying/100` that was 1.55 KB of the 2.24 KB of move packets.
* **Explosion effects** (`combat/explosion_effect`): each affected block as an 8-byte absolute position.

### What changed

1. **Radar contacts go to crew only, as changes** (protocol 10). The radar is drawn only by a vehicle's
   instruments (`MortarRadar`, `BvrLockMarkers`), so only players aboard a vehicle (as pilot, in a seat
   or at a UAV station) get contacts. Each of them has a `RadarReceiver` on the server that remembers what
   their client was told: a snapshot carries the contacts the client has not seen (with their names, once),
   the contacts that moved (fixed point, 1/256 block, relative to a block near the receiver, with the
   motion since the last tick), and only the ids of the ones that stayed put. Nothing is sent while
   nothing changes, except a heartbeat every second (the client drops contacts after five silent
   seconds) and a full picture every ten seconds. Contacts still disappear as before: a contact that
   leaves the radar is no longer mentioned and expires on the client. Per pilot in `flying/100` the radar
   went from 3,499 to 174 B/tick.
   Protocol 11 replaced the contact's class name with a one-byte kind (`RadarContactKind`), so a newly
   introduced contact is the length of its class name smaller, 30-50 bytes (the codec sample went from 109 to 91 bytes); the gate's
   network figures moved by 1-3 %, within the baseline.
2. **Seats send no movement.** Seats are registered with an update interval that never comes round
   (`EntityTypeRegistration.placedByParent`); they are still sent when they spawn and when their riders
   or synced data change. Nothing the client draws depended on the packets.
3. **Explosion blocks are written relative to the blast** (protocol 10): three zigzag VarInts, usually
   one byte each, instead of a long. The decoded message is identical.

Checked and left alone: vehicle moves (one 11-byte packet per vehicle and tick; the client already
predicts between updates, and a lower rate for far observers would need a teleport, 33 bytes, per
skipped delta, as all observers share one delta base), velocity packets (the client's prediction runs
on them), synced entity data (under 1 % of the traffic: `FUEL`, `THROTTLE`, `DAMAGE` change rarely and
are only sent on change), projectiles (vanilla spawns and teleports; a fast round outruns the 8-block
range of a move packet).

### Before and after

Observer bytes per tick, per tracked vehicle, the pilot's bytes, and the server thread's allocation and
median tick (the two runs were on a shared machine; time and allocation are within its noise, see the
gate below for the controlled comparison).

| Tier | observer B/tick before | after | change | B/tick/vehicle before | after | pilot B/tick before | after | change | alloc KiB/tick before | after | p50 ms before | after |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| parked/1 | 33.87 | 0.23 | -99 % | 33.87 | 0.23 | 0.00 | 0.00 | - | 129 | 73.31 | 2.15 | 1.73 |
| parked/25 | 66.11 | 5.85 | -91 % | 2.64 | 0.23 | 0.00 | 0.00 | - | 138 | 82.33 | 1.83 | 1.71 |
| parked/50 | 92.88 | 12.41 | -87 % | 1.90 | 0.25 | 0.00 | 0.00 | - | 146 | 88.46 | 2.11 | 1.61 |
| parked/100 | 642 | 47.27 | -93 % | 6.48 | 0.48 | 0.00 | 0.00 | - | 170 | 106 | 2.50 | 2.07 |
| parked/200 | 1178 | 49.65 | -96 % | 6.01 | 0.25 | 0.00 | 0.00 | - | 201 | 135 | 2.53 | 2.26 |
| parked/500 | 3807 | 120 | -97 % | 8.12 | 0.26 | 0.00 | 0.00 | - | 324 | 253 | 3.08 | 2.54 |
| flying/1 | 75.89 | 11.05 | -85 % | 75.89 | 11.05 | 75.37 | 22.78 | -70 % | 149 | 240 | 3.20 | 2.91 |
| flying/25 | 1587 | 276 | -83 % | 63.48 | 11.05 | 727 | 103 | -86 % | 474 | 606 | 4.59 | 4.24 |
| flying/50 | 2883 | 332 | -89 % | 96.11 | 11.05 | 1813 | 123 | -93 % | 1275 | 833 | 5.73 | 4.45 |
| flying/100 | 5762 | 664 | -88 % | 96.03 | 11.06 | 3604 | 207 | -94 % | 1932 | 2002 | 7.87 | 5.96 |
| flying/200 | 10406 | 819 | -92 % | 141 | 11.07 | 7760 | 353 | -95 % | 3754 | 3520 | 8.20 | 8.10 |
| flying/500 | 22906 | 1055 | -95 % | 240 | 11.04 | 19312 | 841 | -96 % | 14862 | 14844 | 16.44 | 14.34 |
| firefight/1 | 109 | 49.52 | -55 % | 109 | 49.52 | 76.82 | 19.03 | -75 % | 289 | 285 | 3.02 | 2.32 |
| firefight/25 | 1941 | 1029 | -47 % | 77.65 | 41.16 | 1184 | 333 | -72 % | 1101 | 1064 | 4.98 | 4.34 |
| firefight/50 | 5432 | 2814 | -48 % | 109 | 56.28 | 3374 | 1024 | -70 % | 2601 | 3032 | 7.43 | 7.05 |
| firefight/100 | 10003 | 4571 | -54 % | 100 | 45.71 | 6660 | 1588 | -76 % | 6154 | 5663 | 13.90 | 12.12 |
| firefight/200 | 21131 | 9549 | -55 % | 106 | 47.74 | 13829 | 3366 | -76 % | 17631 | 17554 | 30.98 | 27.08 |
| firefight/500 | 53547 | 23953 | -55 % | 108 | 48.10 | 34812 | 8547 | -75 % | 83156 | 82211 | 106 | 99.53 |
| mixed/1 | 35.10 | 0.82 | -98 % | 35.10 | 0.82 | 0.00 | 0.00 | - | 211 | 189 | 3.51 | 3.08 |
| mixed/25 | 751 | 159 | -79 % | 34.16 | 7.21 | 529 | 65.31 | -88 % | 872 | 1081 | 5.26 | 5.58 |
| mixed/50 | 1732 | 356 | -79 % | 38.49 | 7.90 | 909 | 68.02 | -93 % | 1463 | 1725 | 6.30 | 7.46 |
| mixed/100 | 3386 | 697 | -79 % | 37.63 | 7.75 | 1644 | 126 | -92 % | 2493 | 2583 | 9.21 | 9.89 |
| mixed/200 | 6405 | 1249 | -80 % | 36.39 | 7.10 | 3096 | 184 | -94 % | 4380 | 4340 | 14.75 | 14.17 |
| mixed/500 | 13429 | 1950 | -85 % | 39.50 | 5.74 | 8252 | 465 | -94 % | 10387 | 10836 | 30.14 | 30.85 |

At 100 and more vehicles the observer gets 88-95 % fewer bytes in `flying`, 54-55 % in `firefight` and
79-85 % in `mixed`; a pilot 94-96 %, 75-76 % and 92-94 %. In a firefight the projectiles are what is left:
their spawns, teleports and entity data are vanilla's ([Projectiles in a firefight](#projectiles-in-a-firefight)
takes them on).

## Projectiles in a firefight

What a big firefight costs once the radar and seat traffic were gone (see above) is the projectiles:
their flight, their hits and blasts, the server tracking them for every player, and their packets.
This section measures that and what was done about it (`3b9c26a` to `23511c3`). Gameplay did not
change: the 234 weapon firing snapshots, the countermeasure shots, lock-on, the NBT fixtures and the
golden trajectories are byte-identical, on both loaders.

### Profile

JFR (`-Pjfr`) of `firefight/500` on `eead4cc`: share of the server thread's samples and the
milliseconds of the mean tick (107 ms) they stand for; after, the same on `6d049db` (82 ms), which has
everything below but the last iterator fix.

| Where the tick goes | before | after |
|---|---:|---:|
| projectile ticks (`ProjectileEntity.tick`) | 22.0 % (23.6 ms) | 23.4 % (19.1 ms) |
| of those, entity hit test (`findEntityOnPath`) | 5.9 % (6.3 ms) | 6.1 % (5.0 ms) |
| of those, explosions (`McheliExplosion.detonate`) | 8.2 % (8.8 ms) | 9.2 % (7.5 ms) |
| of those, blast rays to entities (`Explosion.getSeenPercent`) | 3.8 % (4.1 ms) | 2.9 % (2.4 ms) |
| vehicle ticks (`AircraftEntity.tick`) | 24.7 % (26.4 ms) | 27.0 % (22.1 ms) |
| part box tests of entity searches (`AircraftBoundingBox.intersects`) | 5.9 % (6.3 ms) | 0.6 % (0.5 ms) |
| missile warning searches (`MissileWarning.getMissilesAround`) | 1.7 % (1.8 ms) | < 0.1 % |
| entity tracking (`ChunkMap.tick`) | 18.6 % (19.9 ms) | 13.7 % (11.2 ms) |
| of that, who sees what (`TrackedEntity.updatePlayers`) | 15.6 % (16.7 ms) | 8.8 % (7.2 ms) |
| of that, passenger streams (`TrackedEntity.getEffectiveRange`) | 6.2 % (6.6 ms) | 0.8 % (0.7 ms) |
| of that, entity updates (`ServerEntity.sendChanges`) | 4.9 % (5.2 ms) | 2.6 % (2.1 ms) |
| writing packets to the connections | 11.4 % (12.2 ms) | 9.7 % (7.9 ms) |

Allocation was 70 MB a tick, 74 % of it the empty passenger streams of `getEffectiveRange`. Block ray
traces of projectiles in the air cost 0.6 %, homing target searches under 1 %, chunk leases and smoke
bookkeeping nothing measurable (smoke is client side).

The projectiles themselves (the benchmark's new projectile table, `ProjectileCensus`, on `23511c3`):

| Tier | alive after a tick | of those frozen | fired or split off per tick | ticks flown |
|---|---:|---:|---:|---:|
| firefight/100 | 3,874 | 3,734 | 16.2 | 3.9 |
| firefight/200 | 6,540 | 6,256 | 33.0 | 4.7 |
| firefight/500 | 11,138 | 10,586 | 80.5 | 4.7 |

Rounds live about five ticks: most hit the next row of vehicles or the ground. Nearly all projectiles
alive at a time (95 %) are **frozen**: rounds that flew out of the simulated area into chunks that are
loaded but tick no entities, where they wait, as vanilla's arrows do, until the chunk ticks again or
unloads. They cost no tick of their own, only room in the entity sections that searches walk and in the
chunks that are saved. They used to stay until a player came near; since then they have a lifetime
([Frozen projectiles](#frozen-projectiles)), which these numbers, measured before it, do not show.

### What changed

1. **No passenger stream for entities nobody rides** (`622f9ca`, `23511c3`). For every entity that
   crosses a chunk section the server asks, for every player, how far its passengers are tracked
   (`getEffectiveRange`), and vanilla builds a stream of the passengers to answer. `McheliEntity` now
   answers with the shared empty list when there are none; a round crosses a section almost every tick,
   and the server has 500 players in this tier.
2. **The envelope of a vehicle's part boxes** (`d5cb92f`). An entity search tests every vehicle in the
   sections it walks against the search box, part box by part box when the main box misses. The
   vehicle now keeps the box around all its parts (dropped whenever a part moves) and tests that
   first; nothing outside it can touch a part.
3. **No blast rays to projectiles that cannot use them** (`96039f2`). An explosion casts rays at every
   entity in reach to see how much of it is exposed. A projectile is neither hurt by a blast nor pushed,
   and with a damage power of zero or more what it sees changes no outcome unless cover rules can cut
   the damage to nothing, so those rays are skipped.
4. **Incoming missiles from the projectiles that have a target** (`d351d50`). The missile warning of
   every vehicle with flares or chaff searched the 120-block box around it for projectiles homing on it,
   walking every round of the firefight; only a projectile with a target can home, and each level now
   keeps those in a set (`HomingProjectiles`).
5. **The path before the filter** (`792c094`). A round's entity hit test visits the same entities in
   the same order as before and takes the nearest hit the same way, but tests whether its path reaches
   an entity before asking whether it may hit it (shooter, crew, same shooter's rounds, ignore list),
   and collects nothing.
6. **The weapon name once** (`0c0a1eb`). A projectile is named after its weapon before it enters the
   level, so the name is in the spawn packets; vanilla sent it again on the first update as changed
   data. The first update now sends only what changed after the spawn packets were made.
7. **Clients fly rounds and rockets** (`84346f8`, `4505644`, `ProjectileSync`). Every client flies the
   projectiles it knows with the server's code; rounds and rockets are steered by nothing else, so the
   server no longer sends their position every other tick. A client gets the projectile where it is when
   it starts tracking it, with its motion (in the spawn packet, without a motion packet) and, in the
   spawn packet's data, the ticks flown (so a rocket motor burns on time) and whether the server's
   projectile flies once more before the packet leaves, which the copy catches up. The server sends a
   teleport and a motion packet only after something only it knows changed the flight (launch boost from
   a moving carrier, a bounce, a stop at unloaded chunks), every other tick while the motion is too fast
   for the packets (over 3.9 blocks a tick on an axis), and every 40 ticks. Missiles, bombs, torpedoes
   and containers keep vanilla's updates: guidance and drag that only the server applies steer them. On
   the client a rocket's step factor is now worked out as the server does, and a copy that starts in
   mid-flight looks its weapon up before giving it up as unknown.

Each change on its own, `firefight` at 200 and 500 vehicles, one run per commit in a row (the machine
was shared with other game runs, so single tick times move by 10-20 %; allocation and network bytes
are steady):

| Change | commit | 200: p50 / mean ms | 200: alloc MiB | 200: B/tick/vehicle | 500: p50 / mean ms | 500: alloc MiB | 500: B/tick/vehicle |
|---|---|---:|---:|---:|---:|---:|---:|
| before | `eead4cc` | 30.1 / 36.3 | 18.2 | 58.9 | 110.9 / 118.9 | 70.6 | 56.1 |
| passenger stream | `622f9ca` | 29.2 / 30.5 | 7.5 | 59.2 | 81.9 / 88.4 | 22.4 | 54.9 |
| part envelope | `d5cb92f` | 27.2 / 29.8 | 7.8 | 58.3 | 91.1 / 107.7 | 22.8 | 55.3 |
| blast rays | `96039f2` | 26.2 / 28.5 | 7.3 | 57.5 | 83.1 / 97.0 | 21.5 | 55.0 |
| missile lookup | `d351d50` | 24.6 / 26.3 | 7.2 | 58.2 | 77.3 / 81.9 | 21.6 | 55.4 |
| path first | `792c094` | 26.2 / 35.1 | 8.0 | 57.5 | 73.4 / 78.1 | 21.6 | 55.8 |
| weapon name once | `0c0a1eb` | 26.9 / 31.5 | 7.2 | 54.8 | 76.9 / 80.6 | 20.4 | 51.8 |
| clients fly rounds | `84346f8` | 28.7 / 36.8 | 7.0 | 40.4 | 86.7 / 96.7 | 20.1 | 41.2 |
| shared empty iterator | `23511c3` | 23.5 / 24.4 | 6.2 | 39.4 | 75.3 / 78.2 | 17.4 | 40.5 |

The tick times of the `84346f8` row fell in a busy stretch of the machine: three interleaved runs of
`0c0a1eb` and `84346f8` at 500 vehicles gave median ticks of 80.3, 78.2 and 73.1 ms against 92.7 (a run
whose calibration loop was 25 % slow), 72.9 and 71.1 ms, with the same allocation; that change saves the
tracker's work and costs nothing measurable. The last row is the after run below.

### Before and after

NeoForge, `firefight` with the measurement of `3b9c26a`: before is `eead4cc`, after `23511c3`, the same
machine and day, 600 warm-up and 600 measured ticks.

| Tier | ms mean before | after | p95 before | after | alloc KiB/tick before | after | B/tick/vehicle before | after | pilot B/tick before | after |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| firefight/100 | 13.40 | 11.50 | 19.32 | 16.26 | 5,889 | 2,957 (-50 %) | 54.75 | 35.88 (-34 %) | 1,884 | 1,709 |
| firefight/200 | 29.45 | 24.43 | 48.52 | 36.38 | 17,741 | 6,298 (-65 %) | 58.72 | 39.35 (-33 %) | 4,041 | 3,666 |
| firefight/500 | 107.08 | 78.20 (-27 %) | 178.92 | 123.82 | 70,654 | 17,827 (-75 %) | 55.11 | 40.45 (-27 %) | 9,109 | 9,038 |

Network, bytes (packets) per tick the observer was sent about MCHeli:

| Tier | explosion effects | other MCHeli messages | move/rotation | entity data | teleport | motion | spawn/remove | other vanilla | total |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| firefight/100 before | 1442 (2.4) | 112 (2.8) | 619 (53.7) | 628 (39.3) | 1182 (37.5) | 441 (43.1) | 986 (28.8) | 65 (16.2) | 5475 |
| firefight/100 after | 1382 (2.3) | 112 (2.8) | 322 (28.2) | 342 (23.1) | 87 (2.8) | 295 (29.2) | 983 (28.5) | 65 (16.2) | 3588 |
| firefight/200 before | 3060 (5.3) | 230 (5.5) | 1434 (117.6) | 1331 (80.7) | 2398 (74.9) | 1094 (99.5) | 2035 (58.5) | 132 (33.0) | 11715 |
| firefight/200 after | 2998 (5.0) | 230 (5.5) | 822 (67.3) | 739 (47.9) | 155 (4.8) | 760 (69.1) | 2034 (58.2) | 132 (33.0) | 7871 |
| firefight/500 before | 7966 (13.8) | 490 (11.8) | 3187 (261.1) | 3232 (195.6) | 4240 (132.5) | 2680 (243.7) | 4996 (150.7) | 321 (80.2) | 27113 |
| firefight/500 after | 8093 (14.1) | 492 (11.8) | 1945 (159.1) | 1786 (115.9) | 414 (12.9) | 1863 (169.4) | 5007 (151.4) | 321 (80.4) | 19922 |

The rounds' teleports are gone (4,240 to 414 B/tick at 500; what is left are rockets' boost phases and
corrections), and their moves and motion packets with them; `ProjectileEntity#WEAPON_NAME` went from
1,365 to 721 B/tick. What is left is mostly the vehicles (moves and motion of the flying ones), the
projectiles' spawns and removals (one `AddEntity` of about 60 bytes per projectile and player, which no
projectile can do without), and the explosion effects, now the largest part: about 600 bytes per
explosion, the sample of affected blocks the client's particles need. The pilot's bytes hardly moved: a
pilot with a 2-chunk view is sent few projectiles, and most of its bytes are explosion effects.

`network/ProjectileSyncGameTests` checks the client side of change 7: for a heavy round with gravity, a
fast round and a rocket, a copy rebuilt from what an observer was sent, and flown the way a client flies
it, stays within 0.05 blocks of the server's projectile for 60 ticks (it stays within 0.009), for an
observer that saw the shot and for one that started tracking the projectile 12 ticks into its flight,
and gets at most one position packet per 40 ticks plus two.

Not changed: tracking ranges (12 chunks for every projectile; a client draws a round from up to 256
blocks), the explosion effect sample (the client's particles need it), and the frozen projectiles (see
the next section). Vanilla's `TrackedEntity.updatePlayers` still looks at every player whenever a round crosses a
chunk section; with 500 players that is the largest piece of tracking left.

### Frozen projectiles

The frozen rounds were not a steady number but a pile that grew as long as the fight went on: the
600-tick window above caught it 30 to 60 seconds in. Every round in it was also written to the region
files with each save, and flew on (and could hit) whenever a player came near, hours later. Now
(`ProjectileLifetime`, see [SERVER_ADMIN.md](SERVER_ADMIN.md#stray-projectiles)):

* a projectile that has not ticked for `FrozenProjectileLifetime` seconds of game time (30 by default)
  is removed without exploding, when it would tick next or by a sweep of the level once a second. One
  in flight ticks every tick, so nothing changed for a flight: the weapon firing snapshots, the
  countermeasure shots, lock-on, the NBT fixtures and the golden trajectories are byte-identical;
* projectiles are no longer written to saves (before, they were written and then dropped on their first
  tick after loading); laid mines are, and the lifetime never removes them.

The benchmark logs a census every 20 s of a tier's warm-up (alive, frozen, and how many a save would
write). NeoForge, `firefight/200` with 4,000 warm-up and 600 measured ticks, `FrozenProjectileLifetime`
0 (which keeps them, as before) and 30, the same machine and day; another build shared the machine, so
the times are rough:

| Seconds in | alive, kept | of those frozen | alive, 30 s lifetime | of those frozen |
|---:|---:|---:|---:|---:|
| 20 | 3,062 | 2,820 | 3,141 | 2,874 |
| 40 | 6,034 | 5,792 | 5,107 | 4,855 |
| 60 | 9,204 | 8,939 | 5,446 | 5,188 |
| 100 | 16,232 | 15,849 | 5,193 | 4,789 |
| 140 | 23,190 | 22,845 | 5,610 | 5,196 |
| 200 | 33,687 | 33,257 | 5,510 | 5,036 |
| measured, 200-230 s | 36,178 | 35,829 | 5,360 | 4,998 |

Kept, the pile grows by about 170 rounds a second for as long as the fight lasts, and each of them would
go into the next save; with the lifetime it levels off at what 30 seconds of fighting leave behind, and
a save writes none of them. The measured window went from 58.3 ms mean (52.7 p50) and 6,915 KiB a tick
to 39.7 ms (33.9 p50) and 6,256 KiB. In the gate's 600 + 600 ticks the sweep starts half-way through the
measurement: `firefight/100` averaged 2,464 projectiles alive (2,337 frozen), against 3,874 (3,734)
before.

## Explosion effects

After the projectile work the explosion effects were the largest single part of what an observer of a
firefight is sent: 8.0 KB a tick at 500 vehicles, 40 % of the total. This section measures them and
what was done about them (`8f01178`, network protocol 13).

### What they were made of

Measured on `b050e1c` with a temporary count of every explosion message the observer and one pilot
were sent (`firefight`, 600 measured ticks, NeoForge):

| Tier | explosions / tick | bytes / explosion | blocks / explosion | of the bytes, the blocks | same place and tick as another | 128 to 255 blocks from the observer |
|---|---:|---:|---:|---:|---:|---:|
| firefight/100 | 2.34 | 598 | 177 | 89 % | 5.6 % | 1.4 % |
| firefight/200 | 5.09 | 594 | 176 | 89 % | 7.5 % | 2.2 % |
| firefight/500 | 13.45 | 596 | 176 | 89 % | 15.5 % | 28.6 % |

* Each explosion was a message of its own: 33 bytes of payload id and frame
  (`mcheli:combat/explosion_effect`), 24 bytes of position (three doubles), the size, the id of the
  exploding entity (which no client reads) and the blocks.
* The blocks, about 3 bytes each (offsets of 1 to 7 blocks from the centre, one byte an axis), were
  nine tenths of the bytes. Half the explosions had fewer than 32 blocks; one in five had 256 or more,
  and the 4,096-block cap was reached by the biggest (sizes 10 and 15).
* Only the legacy particle style reads them: it throws debris, smoke, dust and burning flares from the
  blown blocks, in the order the server's rays found them. The modern style, the default, draws the
  whole explosion from its position and size (`ModernFx.explosion`), and the entity push the client
  replays does not look at blocks either.
* Several explosions a tick (up to 78 at 500 vehicles) and some at the same spot in the same tick
  (rounds hitting the same block); every one is drawn, so none can be left out.
* Every explosion was within 256 blocks of the observer, inside its 12-chunk view, and within 512 of the
  pilot, all well inside the 1,000-block range.

### What changed

1. **One message a tick.** The explosions of a tick are collected and sent at its end
   (`ExplosionEffectSender`, one per server), each player getting every explosion within 1,000 blocks
   of them in the order they went off, in one `combat/explosions` message. A client handles them in
   that order, as it handled the single messages; they now arrive at the end of the server tick in
   which they went off instead of during it, at most one tick (usually a few milliseconds) later. The
   explosion sound is a vanilla sound packet sent as before.
2. **Blocks only for clients that draw them.** A client tells the server, when it joins and whenever its
   particle style changes, whether it draws explosions from their blocks (`combat/explosion_detail`,
   one byte). The server sends the blocks only to those clients and takes the default style, modern,
   until told otherwise. A legacy client gets the same blocks in the same order as before.
3. **A smaller explosion.** The position is kept on a grid of 1/4096 block (the record puts it there, so
   the server's message and the client's copy are equal) and written in grid steps as zigzag VarLongs:
   the first explosion of the message in full, every other relative to the one before, about three bytes
   an axis. The size is left out when it repeats, the exploding entity's id is gone, and the under-water
   and has-blocks flags share a byte. An explosion without blocks is about 11 bytes; a message of one,
   about 45.

Left alone: the range (1,000 blocks, see above; the view of a remote-controlled UAV can be far from its
operator, so a range tied to the view distance could hide explosions a player sees), duplicates (each is
drawn) and the encoding of the blocks for legacy clients (the client's debris depends on their order,
which the rays' hash set decides, so a bitset of the blast's box would change it).

**The same picture.** `network/ExplosionEffectGameTests` sets eight real explosions off (sizes 1 to 15 in
stone, dirt, planks, glass, sand and obsidian, and one under water), sends each the way protocol 12 did
and the way it is sent now, and runs the client's drawing code (`ExplosionEffects.draw`) on both with the
same random numbers, for the legacy style with MCHeli debris and with vanilla debris and for the modern
style (sent no blocks). The 27,600 calls it makes (particles, block dust, flares, the modern explosion)
are the same calls in the same order with the same whole numbers; positions and motions differ by at
most 0.0004 blocks (the grid moves a position by at most 1/8192 of a block an axis; a pixel of a block's
texture is 1/16). The modern style's calls were identical before, as they only take the position and
size.

### Before and after

NeoForge, `firefight`, 600 warm-up and 600 measured ticks; before is `b050e1c`, after `8f01178`, the same
machine and day (other builds shared the machine, so the tick times are noisy; the network bytes are
not).

| Tier | explosion effects B/tick before | after | change | B/tick/vehicle before | after | change | pilot B/tick before | after | change |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| firefight/100 | 1,399 (2.34 messages) | 52.8 (0.79) | -96 % | 36.22 | 22.41 | -38 % | 1,734 | 384 | -78 % |
| firefight/200 | 3,027 (5.09) | 85.9 (0.92) | -97 % | 39.72 | 24.88 | -37 % | 3,693 | 762 | -79 % |
| firefight/500 | 8,011 (13.45) | 160 (0.96) | -98 % | 40.25 | 24.31 | -40 % | 9,014 | 1,234 | -86 % |

The observer's total went from 3,622 to 2,241 B/tick at 100 vehicles, 7,944 to 4,976 at 200 and 19,862
to 11,973 at 500. A pilot's bytes were mostly explosion effects and fell the most. A client with the
legacy particle style still gets the blocks; for it each explosion is about 55 bytes smaller (the id
and frame of its own message, the doubles and the entity id), some 9 % of the explosion bytes.

## Vehicle ticks

With the projectiles and the network cut down, the vehicles' own ticks were the largest part of the
server's time: a quarter of it in a firefight, half of it in `mixed`. This section measures where a
vehicle tick goes and what was done about it (`e51d907` to `77c1249`). Nothing a vehicle does changed:
the golden trajectories (20 and 60 FPS), the weapon firing snapshots, the NBT fixtures and the wingman
scenarios are byte-identical, and all 474 game tests pass on both loaders.

### Profile

JFR (`-Pjfr`) of the 500-vehicle tiers: share of the server thread's samples, and the milliseconds of
that run's mean tick they stand for. Before is `b050e1c`; after is `a5c94ac`, which still had a cache of
the blocks around a tank's wheel probes that was then dropped (below).

| Where the tick goes | flying/500 before | after | mixed/500 before | after | firefight/500 before | after |
|---|---:|---:|---:|---:|---:|---:|
| vehicle ticks (`AircraftEntity.tick`) | 29.9 % (5.4 ms) | 23.2 % (3.5 ms) | 54.9 % (16.0 ms) | 50.2 % (11.9 ms) | 27.2 % (27.1 ms) | 23.0 % (21.2 ms) |
| a tank breaking blocks (`TankRamming.breakBlocks`) | - | - | 15.7 % (4.6 ms) | 4.0 % (1.0 ms) | 3.1 % (3.1 ms) | 0.5 % (0.5 ms) |
| a tank's wheel probes (`WheelProbe.move`) | - | - | 10.7 % (3.1 ms) | 13.0 % (3.1 ms) | 3.6 % (3.6 ms) | 2.9 % (2.7 ms) |
| the vehicle's own move (`VehicleEntity.move`) | 5.0 % (0.9 ms) | 4.0 % (0.6 ms) | 8.4 % (2.4 ms) | 9.0 % (2.1 ms) | 7.4 % (7.4 ms) | 7.1 % (6.6 ms) |
| part boxes following the hull (`ExtraBoundingBoxTracker.tick`) | 8.4 % (1.5 ms) | 4.1 % (0.6 ms) | 2.5 % (0.7 ms) | 3.4 % (0.8 ms) | 3.8 % (3.8 ms) | 3.0 % (2.8 ms) |
| of that, the players at the part boxes (`collectPlayers`) | 6.0 % (1.1 ms) | 1.5 % (0.2 ms) | 1.1 % (0.3 ms) | 1.4 % (0.3 ms) | 2.4 % (2.4 ms) | 1.7 % (1.5 ms) |
| terrain column scans (`Terrain.firstBlockY`) | 2.2 % (0.4 ms) | 1.3 % (0.2 ms) | 1.3 % (0.4 ms) | 0.7 % (0.2 ms) | 0.2 % | 0.1 % |
| the search for a rack to ride (`RackComponent.findRideableRack`) | 1.0 % (0.2 ms) | 0.5 % (0.1 ms) | 1.1 % (0.3 ms) | 1.5 % (0.3 ms) | 1.3 % (1.3 ms) | 0.9 % (0.9 ms) |
| weapons (`WeaponComponent.serverTick`) | 3.3 % (0.6 ms) | 2.2 % (0.3 ms) | 2.1 % (0.6 ms) | 2.6 % (0.6 ms) | 1.6 % (1.6 ms) | 1.8 % (1.7 ms) |

Allocation, by the same profile: 66 % of what the server thread allocated in `mixed/500` was in vehicle
ticks, two fifths of that the streams of the block-break rules and a quarter the collision of the wheel
probes; in `flying/500` three quarters of it (5.8 MB a tick) were the passenger streams the entity
tracker builds for every crewed vehicle and seat and every player (`TrackedEntity.getEffectiveRange`).

The rest of a vehicle tick is spread thin. In `flying/500` the seats placed on the hull took 0.8 %, the
missile warning 0.4 % (since `d351d50`), the countermeasures and the synced-data writes 0.2 % each, the
rest-state bookkeeping less than one sample. A seat entity's own tick (vanilla's base tick: fluid and
fire checks) is another 5 % of `flying/500`, mostly chunk lookups for its fluid check; it was left
alone, as skipping it would change the seat's state.

### What changed

1. **The break rules once per block state and tick** (`e51d907`). A moving tank breaks soft blocks in a
   box around every hull part; for each block it checked the break lists and up to ten material
   predicates (tag lookups), through a stream. The verdict of a block state is now worked out once per
   tick and kept in a map that each tick starts empty (so a reload or a tag change is seen on the next
   tick, as before), the material check is a plain loop, and each column's chunk is looked up once
   instead of once per block. The same blocks break in the same order.
2. **Players at the part boxes from the entity sections** (`00fad94`). Every moving vehicle looks for the
   players touching its part boxes (to stop their fall damage and to know whether it may cache the
   boxes). It walked the level's whole player list, so with 500 crewed vehicles the cost grew with the
   square of the players. With more than 16 players in the level it now asks the entity sections around
   the boxes, which hold the same players; the order of the answer changes, which nothing reads.
3. **One chunk lookup per column in the terrain scans** (`e5f148b`). `Terrain.firstBlockY` (clear below,
   above water, the landing gear's ground check) looked every block up through the level, chunk lookup
   included; it now keeps the chunk of the column it is scanning, and asks it what the level would.
4. **The passenger list for vehicles nobody aboard carries anyone** (`2d8280b`). The entity tracker
   asks every tracked entity, for every player, how far its passengers are seen, and vanilla builds a
   stream of the passengers for it. Since `622f9ca` an MCHeli entity without passengers answers with the
   shared empty list; one whose passengers carry nobody (a pilot, a gunner in a seat) now answers with
   its passenger list itself, which is the same entities in the same order.
5. **No block search for a vehicle's move in open air** (`4645a3b`). Vanilla's collision of a moving
   entity collects the shapes of every block around the move, block by block. For a vehicle,
   `MixinEntity` first checks the chunk sections the search would read (with the margin of one block it
   adds): when all of them hold only air, or lie in chunks that are not loaded, the search can find
   nothing and is skipped. Flying vehicles spend their time there; anywhere near blocks the search runs
   as before.
6. **A rack search only near racks that fit** (`8b0b0ea`, `77c1249`). Every half second the pilot's HUD
   hint looks for a rack to ride, an entity search of the 120-block box around the vehicle. It now asks
   the level's `AircraftIndex` first whether any vehicle there has a rack that names this vehicle (or
   that this vehicle names), the first thing a rack must do to be ridden, and skips the search when none
   is near.

Tried and dropped: a per-tick cache of the blocks around a tank's wheel probes, with vanilla's block
collision rebuilt over it (`d6d4cfe`, reverted in `114c783`). It reproduced vanilla exactly (the tank
goldens passed) and cut 7 % of `mixed/500`'s allocation, but two interleaved pairs of runs put it at 19.6
against 19.2 ms a tick: reading a block from the chunk is about as cheap as reading it from a cache, and
the cost is in the shapes, which the cache did not save.

### Before and after

NeoForge, 600 warm-up and 600 measured ticks, the mean of two runs each, run in turn on the same
machine and day: before is `f6394e7` (the branch with the explosion and frozen projectile work), after
`114c783` (other builds shared the machine; single runs moved by up to 10 %).

| Tier | ms mean before | after | change | p95 before | after | change | alloc KiB/tick before | after | change |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| flying/200 | 7.47 | 6.68 | -11 % | 10.52 | 9.14 | -13 % | 1,942 | 1,344 | -31 % |
| flying/500 | 18.43 | 14.66 | -20 % | 31.50 | 21.71 | -31 % | 7,661 | 3,772 | -51 % |
| mixed/200 | 11.11 | 9.12 | -18 % | 15.14 | 11.48 | -24 % | 2,924 | 2,024 | -31 % |
| mixed/500 | 26.89 | 21.20 | -21 % | 36.17 | 29.47 | -19 % | 7,145 | 4,899 | -31 % |
| firefight/200 | 29.69 | 29.14 | -2 % | 46.59 | 43.87 | -6 % | 6,318 | 5,913 | -6 % |
| firefight/500 | 87.67 | 86.48 | -1 % | 135.0 | 133.5 | -1 % | 17,817 | 16,610 | -7 % |

The network bytes did not change. A firefight's vehicles stand still and are on the ground, so the open
air, the players at moving part boxes and the tanks driving through blocks hardly come up there; its tick
is still mostly projectiles and tracking, and the vehicles' share of it (about 23 %) is now mostly the
vanilla move against the ground and the entities around (7 %) and the wheel probes (3 %). In `flying` the
largest allocation left is the passenger streams vanilla builds for the players themselves, which MCHeli
does not own.

## Benchmark gate (CI)

`./gradlew :neoforge:runBenchmarkGate` runs `parked`, `flying` and `firefight` with 100 vehicles each
(600 warm-up and 600 measured ticks, about one minute) and compares them with
[`common/src/gametest/resources/bench/server-gate-baseline.json`](../common/src/gametest/resources/bench/server-gate-baseline.json).
The `benchmark` job of `.github/workflows/ci.yml` runs it on every push and uploads the results
(`build/benchmarks/server-gate.json` and `.md`) whether it passes or not.

A tier fails when

* its **network bytes per tick and tracked vehicle** are more than **10 %** above the baseline (plus
  1 byte), or
* its **allocation per tick** (mean) is more than **10 %** above the baseline (plus 64 KiB, so a tier
  that allocates almost nothing cannot fail on noise), or
* its **median tick time, divided by a calibration loop** timed in the same JVM, is more than **35 %**
  above the baseline's (plus 0.5 ms). The calibration loop (`Calibration`) is fixed work that looks
  like a tick: vector maths with trigonometry, hash map churn with boxed keys and sorting; its fastest
  of 15 rounds is taken, before and after the tiers. A CI machine that is uniformly slower than the
  one that recorded the baseline therefore does not fail.

A tier that fails is measured once more and the better run counts (time and allocation each). The
gate JVM runs with `-XX:-DoEscapeAnalysis`: with escape analysis, how much the JIT inlines decides
which allocations disappear, and the allocation of the same tier moved by up to 40 % between runs;
without it, repeated runs agreed within about 8 %. The gate's milliseconds are therefore a little
higher than the full benchmark's.

The network bytes depend on the code, not the machine, so they are compared directly; a retried tier
counts the network bytes of its faster run. The baseline's time and allocation are those of `526f487`;
its network numbers are from the gate run after the network work (`e182aea`), when time and
allocation did not move beyond noise (flying -9 %, firefight +6 % allocation) and so were not re-recorded.
The firefight tier's network numbers, allocation and entity count were taken again after the bundled
weapons got back the bomblets McHeli CE's YAML conversion had dropped: their rockets and dispensed items
add about 580 entities, 11 % more bytes per vehicle (43.3 to 47.9 B/tick) and about 6 % more allocation.
They were recorded on a fresh `run/world`: a world left over from an earlier benchmark run keeps its
projectiles and fails every tier, so `runBenchmarks` and `runBenchmarkGate` now delete the world (and the
extracted copy of the bundled content) before they start, on both loaders.
They were taken once more after the bundled vehicles got back the values the same conversion had
dropped: 18 helicopters and planes now park nose up, so the guns of the parked firefight rows fire
above the ground instead of into it, and their bullets live longer (about 5,600 entities instead of
4,300, 12 % more bytes per vehicle, 47.9 to 53.9 B/tick, and about 16 % more allocation).
After the projectile work ([Projectiles in a firefight](#projectiles-in-a-firefight)) the gate passed
against that baseline (firefight -33 % bytes per vehicle and -42 % allocation, flying -29 % allocation),
and the network numbers, allocation and entity counts were recorded again (`6d049db`); the parked tier
keeps its allocation, which the new run put 8 % higher, within noise. The time numbers and the
calibration stay those of `526f487`: the recording run's calibration loop ran during other builds and
would have made the time limits tighter than the machine's noise.
After the explosion effects work ([Explosion effects](#explosion-effects)) the gate passed (firefight
-35 % bytes per vehicle, 36.0 to 23.6 B/tick), and only the firefight tier's network numbers were
recorded again (`8f01178`); its time, allocation and entity count, and the other tiers, stay as they were.
After the vehicle tick work ([Vehicle ticks](#vehicle-ticks)) the gate passed twice (flying -25 and -21 %
allocation, -26 and -20 % median tick in calibrations; firefight -13 and -14 % allocation, -11 and +7 %
median tick), and the allocation of the flying and firefight tiers was recorded again, from the gate run
of `114c783`; everything else stays as it was.

**Updating the baseline.** After a change that is meant to cost more (or that made things cheaper):

```sh
./gradlew :neoforge:runBenchmarkGate -Pbench=record --offline
git add common/src/gametest/resources/bench/server-gate-baseline.json
```

Record on a quiet machine; the time numbers are relative to the calibration loop, so the machine
does not have to be the one CI uses. If CI keeps failing on time alone, take `server-gate.json`
from the CI run's artifact, check the numbers, and commit it as the baseline.

## Client benchmark

`/mcheli benchmark client` (single player, cheats allowed; `ClientBenchmark = false` in mcheli.cfg
switches it off) runs on the player's machine:

1. It saves what it will change (render distance, frame rate limit, vsync, pause on lost focus,
   camera, `VehicleRenderer`, `FixedStepAttitude`, the time, weather, game rules, game mode and the
   player's place), then sets render distance 12, no frame cap, no vsync, first person, a still clear
   noon, and creative flight.
2. For 0, 25, 50, 100 and 150 vehicles it spawns rows of parked helicopters, planes and tanks south of
   the player (every fifth helicopter damaged and trailing smoke), one in ten driving back and forth in
   a lane in front of them (helicopters with rotors running, kicking up dust), plus an AH-64 for the
   cockpit. Every vehicle is tagged `mcheli_benchmark`.
3. Per size: the camera **orbits** the rows once in 20 s with `VehicleRenderer = immediate`, then again
   with `flywheel` (Flywheel on whatever backend it runs, which `auto` may not pick) when Flywheel is
   installed; then the player sits in the AH-64's **cockpit** looking at the rows, with
   `FixedStepAttitude` on, off, off and on again, 10 s each (the cockpit is the only place that
   setting does work). The two halves of a setting are one result, so whatever the first cockpit step
   pays for does not land on one setting (see [Fixed-step attitude](#fixed-step-attitude)). Each step
   settles for 3 s first, and the log gets a line per step.
4. Frame time is the time between the starts of two frames. Each step records frames, average FPS,
   1 % low (average FPS of the slowest 1 % of frames), p50/p95/p99/max frame ms, how many vehicles
   the client had, and whether Flywheel drew any.
5. A step that averages under 5 FPS over its first 2 s, or has one frame over 2 s, is **aborted**
   (`StepWatchdog`; the step's first two frames are not judged, as a renderer switch may compile
   shaders) and recorded as `aborted: too slow (x FPS)`; the run goes on with the next step. At a
   frame every few seconds the game barely reads input, so the player could not cancel either.
6. Backspace or the pause menu cancels; leaving the world aborts. Either way everything is put back,
   the vehicles are removed, and what was measured is written. The player's render distance, frame
   cap, vsync, pause on lost focus, `VehicleRenderer` and `FixedStepAttitude` are also noted in
   `mcheli/benchmarks/benchmark-in-progress.properties` for the length of the run
   (`BenchmarkRecovery`): if the game dies mid-run, the next start puts them back and saves them.

The JSON is schema 2: Flywheel steps have `renderer` `flywheel` (schema 1 said `auto`), and every
step has `aborted`, empty unless the step was given up.

It writes `<game dir>/mcheli/benchmarks/mcheli-benchmark-<yyyyMMdd-HHmmss>.json` and a `.txt` summary
of the same name, and prints the path in chat (click copies it). Both hold the GPU and driver
(`GL_RENDERER`, `GL_VENDOR`, `GL_VERSION`), OS, CPU, RAM, Java, window size, graphics mode, render
distance (the benchmark's and the player's), mod and Minecraft versions, and Flywheel's version and
backend. `/mcheli benchmark client quick` runs 0 and 25 vehicles with 4 s steps.

`./gradlew :neoforge:runBenchmarkCheck` (and `:fabric:`, under `xvfb-run` on a headless machine) plays
it in the `devscene` world: it types the command, waits for the files, then checks that render
distance, frame cap, vsync, pause on lost focus, camera and mcheli.cfg settings are back, the player is
back where it stood and out of the vehicle, and no tagged vehicle is left; it logs `[benchcheck] PASS`.
`-PbenchCheck=full` runs the full benchmark, `-Pflywheel` adds Flywheel. The run needs
`run/saves/devscene` and `run/options.txt` (see [PERFORMANCE_BASELINE.md](PERFORMANCE_BASELINE.md)).

### Results on this machine (software rendering)

The full run of `./gradlew :neoforge:runBenchmarkCheck -PbenchCheck=full -Pflywheel` (8 minutes,
NeoForge, Flywheel 1.0.7 on its `indirect` backend, Mesa llvmpipe 25.2.8 under Xvfb, 854x480,
render distance 12). "Loaded" counts the vehicles the client had, including the 11 the `devscene`
save already holds; at 100 and 150 the far rows are out of tracking range from parts of the orbit.

```
Veh.  Camera   Renderer FixedStep  Loaded  Avg FPS   1% low   p50 ms   p95 ms   p99 ms
0     orbit    immediate on         12         31.9      9.1    29.32    43.00    54.65
0     orbit    auto     on         12         29.1     17.6    33.52    42.74    52.74
0     cockpit  immediate on         12         35.1     23.5    27.92    35.49    40.14
0     cockpit  immediate off        12         34.8     21.8    28.22    36.30    40.97
25    orbit    immediate on         37         19.9      9.2    49.08    68.31    88.27
25    orbit    auto     on         37         13.4      5.3    74.75   108.00   160.28
25    cockpit  immediate on         37         17.6      8.3    55.66    80.88   103.37
25    cockpit  immediate off        37         17.6      6.9    54.63    86.87   113.68
50    orbit    immediate on         62         13.8      7.3    70.29    97.56   133.59
50    orbit    auto     on         60         12.7      7.0    74.08   114.06   130.36
50    cockpit  immediate on         60         17.1     12.3    57.39    71.43    75.73
50    cockpit  immediate off        60         17.4     10.9    57.53    65.88    76.65
100   orbit    immediate on         112        10.0      6.0    97.29   123.22   145.80
100   orbit    auto     on         108         6.8      4.2   142.29   213.78   234.83
100   cockpit  immediate on         107         8.3      5.8   119.03   156.53   172.19
100   cockpit  immediate off        106         9.9      6.4    95.61   140.74   148.15
150   orbit    immediate on         138         6.6      3.7   151.06   214.28   239.31
150   orbit    auto     on         126         5.4      2.8   185.72   276.65   334.64
150   cockpit  immediate on         145         6.8      3.4   142.80   196.84   238.09
150   cockpit  immediate off        145         8.4      4.8   115.93   158.57   209.32

0 vehicles: Flywheel -8.5% average FPS, +93.6% 1% low against the built-in renderer
0 vehicles: fixed-step attitude on +1.0% average FPS against off
25 vehicles: Flywheel -32.9% average FPS, -42.7% 1% low against the built-in renderer
25 vehicles: fixed-step attitude on -0.2% average FPS against off
50 vehicles: Flywheel -8.0% average FPS, -4.1% 1% low against the built-in renderer
50 vehicles: fixed-step attitude on -1.5% average FPS against off
100 vehicles: Flywheel -32.0% average FPS, -28.9% 1% low against the built-in renderer
100 vehicles: fixed-step attitude on -15.6% average FPS against off
150 vehicles: Flywheel -17.6% average FPS, -24.1% 1% low against the built-in renderer
150 vehicles: fixed-step attitude on -18.8% average FPS against off
```

Flywheel was 8-33 % slower than the built-in renderer here, as in the stage 8 measurements, and
fixed-step attitude cost nothing measurable up to 50 vehicles and 16-19 % at 100 and 150 (one run;
steps of the same configuration differ by 10-20 % on this machine, so treat that as a lead to check
on real hardware, not a result; it was the order of the steps, see [Fixed-step attitude](#fixed-step-attitude)). These come from Mesa llvmpipe, which rasterizes on the same four CPU cores the game runs on, at
854x480 under Xvfb. They show that the benchmark runs and what it records, not how a graphics card
behaves; Flywheel picks its `indirect` backend here, while the MacBook's OpenGL 4.1 will get
`instancing`. The real numbers come from the playtest machines.

The table above predates the Flywheel fix below (its Flywheel steps ran with `auto`, which then meant
Flywheel on any backend).

### The Flywheel backend on the MacBook (0.5 FPS) and its fix

The first real-hardware run (MacBook Pro 16" 2019, Radeon Pro 5300M, macOS OpenGL 4.1, so Flywheel's
`instancing` backend, 3584x2240) drew about 250 / 115 / 70 / 40 FPS at 0 / 25 / 50 / 100 vehicles with
the built-in renderer and about 0.5 FPS with Flywheel. Counting what the backend asked of Flywheel
(`FlywheelVehicleStats`, logged once a second as `[flywheelstats]` by development runs with
`-Pflywheel`, together with Flywheel's own instancer count) showed why:

* **An instancer per vehicle and part.** Every vehicle was an embedding of its own, and Flywheel keys
  instancers by embedding, so no two vehicles shared one. The instancing backend issues a draw call
  per instancer, each with a program bind, uniforms, material state, a re-pointed instance texture
  buffer and an instance buffer upload when something moved. `runClientStress -Pvehicles=100`: 1,868
  instancers; the quick benchmark's orbit with 37 vehicles: 1,806.
* **An instancer per level of detail.** Each of the six levels a moving camera reached was a model
  of its own, and every new level's mesh made Flywheel upload its whole mesh pool again: up to
  3,500 mesh copies (2 million vertices) a second while the camera orbited.
* **Empty instancers kept drawing.** Parked instances and the mesh keepers stayed after a switch back
  to `immediate`, so the cockpit steps after a Flywheel step still issued 1,806 draw calls of zero-sized
  instances.

The fix: parts are instanced from the render origin with the hull and part matrices multiplied, so
every vehicle adds to one shared instancer per part model; Flywheel draws the full mesh or the
coarsest level only; a vehicle deletes its instances when Flywheel may not draw; mesh keepers live in
the shared instancers and are freed 60 s after their model's last user. Steady frames of a parked
scene create nothing (`[flywheelsteady] PASS` in `runClientStress`, and `VehicleInstancesTest`).

Mesa llvmpipe under Xvfb, NeoForge, `runClientStress -Pvehicles=100 -Pflywheel` (108 vehicles with
the `devscene` ones), the backend forced in `run/config/flywheel-client.toml`; all runs of a row within
20 minutes of each other (the machine is shared, so runs further apart differ by up to 50 %):

| Scene, Flywheel backend | Instancers before → after | FPS, built-in | Flywheel before | Flywheel after |
|---|---:|---:|---:|---:|
| 100 parked, `indirect` | 1,864 → 879 | 11.2-12.2 | 8.7 | **18.6** |
| 100 parked, `instancing` | 1,868 → 876 | 11.2-12.2 | 8.7 | 7.7 |
| 100 parked + 20 moving, `indirect` | | 9.5 | | 16.3 |
| 100 parked + 20 moving, `instancing` | 2,787 → 1,105 | 9.5 | 5.3 | 7.8 |
| quick benchmark, 25 vehicles orbit, `instancing` | 1,806 → 451 | 6.4-8.4 | 7.3 | 6.1-8.8 |

On `indirect` the fix doubled Flywheel's frame rate and made it 50-70 % faster than the built-in
renderer here: that backend batches by embedding too, so per-vehicle embeddings had split its
indirect draws per vehicle. On `instancing` llvmpipe hardly notices draw calls (it rasterizes on the
CPU, where the full meshes now drawn at middle distances cost more), so the instancer count is the
result that matters there; 876 draw calls for 100 vehicles of mostly different types are still far
more than the built-in renderer's few. So `VehicleRenderer = auto` uses Flywheel on `indirect` only
([RENDERING.md](RENDERING.md)); whether the fixed `instancing` is usable on the MacBook, and how
`indirect` does on a real card, is for the playtest machines (`VehicleRenderer = flywheel` and the
client benchmark's Flywheel steps measure it, and abort it if it is still unusable).

### Results on real hardware

A MacBook Pro 16" 2019 (Radeon Pro 5300M, macOS OpenGL 4.1, 3584x2240, render distance 12,
NeoForge, no Flywheel, no Sodium), on `e85bc3a`, before the cockpit steps were interleaved. Flywheel
was not installed, so the empty Flywheel instancers described above did not draw in these steps:

```
Veh.  Camera   Renderer FixedStep  Loaded  Avg FPS   1% low   p50 ms   p95 ms   p99 ms
0     orbit    immediate on         1         251.7     94.9     3.67     5.72     7.12
0     cockpit  immediate on         1         201.1    105.2     4.53     7.50     8.58
0     cockpit  immediate off        1         240.5    131.9     3.93     5.68     6.56
25    orbit    immediate on         26        117.9     54.1     8.10    10.96    13.26
25    cockpit  immediate on         26        103.1     76.8     9.38    11.31    11.89
25    cockpit  immediate off        24        111.3     80.5     8.71    10.43    10.94
50    orbit    immediate on         51         69.7     41.1    14.06    16.93    21.64
50    cockpit  immediate on         47         66.4     51.5    14.78    17.02    17.51
50    cockpit  immediate off        46         71.2     54.4    13.62    15.46    16.48
100   orbit    immediate on         101        39.8     26.4    24.74    30.16    36.50
100   cockpit  immediate on         96         37.9     29.0    26.48    29.03    33.07
100   cockpit  immediate off        94         40.8     29.3    24.48    26.30    27.00
150   orbit    immediate on         118        33.3     23.0    29.47    36.73    42.18
150   cockpit  immediate on         138        30.1     24.0    33.03    37.88    40.72
150   cockpit  immediate off        138        30.1     24.1    32.95    36.99    39.47
```

About 0.2 ms of frame time per vehicle in view, linear in the count; [RENDERING.md](RENDERING.md#where-a-vehicles-frame-time-goes)
breaks it down and has what was cut (about 0.06 ms of it per parked vehicle on the 4-core VM).

The same MacBook again on the nightly `2.0.0-nightly.20261111+d50366b6`, with the vertex block cache
in the built-in renderer, the shared Flywheel instancers and Flywheel 1.0.7 installed (it can only
run its `instancing` backend under macOS; the Flywheel rows use `VehicleRenderer = flywheel`):

```
Veh.  Camera   Renderer FixedStep  Loaded  Avg FPS   1% low   p50 ms   p95 ms   p99 ms
0     orbit    immediate on         1         206.9     84.7     4.37     7.67     9.11
0     orbit    flywheel on         1         161.4     91.5     6.06     8.13     9.23
0     cockpit  immediate on         1         199.7    117.0     4.79     6.98     7.81
0     cockpit  immediate off        1         209.0    130.1     4.53     6.49     6.74
25    orbit    immediate on         26        151.9     59.9     6.12     8.91    11.94
25    orbit    flywheel on         24        196.4     89.4     4.78     6.92     9.40
25    cockpit  immediate on         24        147.9    105.7     6.64     8.12     8.62
25    cockpit  immediate off        24        150.9    105.1     6.47     7.82     8.14
50    orbit    immediate on         51        103.9     56.1     9.10    11.90    13.90
50    orbit    flywheel on         47        142.9     74.3     6.55     9.31    11.57
50    cockpit  immediate on         46        103.3     67.1     9.34    11.48    12.07
50    cockpit  immediate off        46        103.8     73.8     9.34    11.38    11.82
100   orbit    immediate on         101        62.1     33.9    15.64    19.94    26.82
100   orbit    flywheel on         96         92.5     33.0    10.00    14.32    20.37
100   cockpit  immediate on         94         65.2     49.7    14.70    17.61    18.16
100   cockpit  immediate off        94         71.1     52.0    14.17    17.07    17.70
150   orbit    immediate on         118        52.2     28.8    18.52    25.01    31.18
150   orbit    flywheel on         116        80.0     21.8    11.46    16.51    34.21
150   cockpit  immediate on         138        55.8     30.9    17.23    21.48    25.24
150   cockpit  immediate off        138        46.5     17.7    19.27    38.76    48.71
```

- **Built-in renderer, orbit:** 25 vehicles +29%, 50 +49%, 100 +56%, 150 +57% average FPS against the
  first run. The 0-vehicle row is lower (252 to 207), which is the cost of having Flywheel loaded at
  all plus run-to-run variation; it is far above any frame cap.
- **Flywheel (`instancing`):** from about 0.5 FPS before the fix to faster than the built-in renderer
  from 25 vehicles up (+29% to +53% average FPS). Its 1% lows are no better at 100 vehicles and worse
  at 150 (21.8 against 28.8), so frame pacing on this backend still has spikes; `auto` keeps choosing
  the built-in renderer on `instancing` for that reason, and `flywheel` remains an explicit choice.
  The spikes were traced afterwards to Flywheel's render origin moving twice per orbit at 150
  vehicles (which lost the mesh keepers, so the whole mesh pool was uploaded again in about a third
  of the frames from then on), to new part models arriving one frame at a time, and to vehicles
  leaving and entering tracking range; all three are fixed, see
  [RENDERING.md](RENDERING.md#frame-pacing-on-the-instancing-backend). Whether `auto` may then pick
  Flywheel on `instancing` waits for the next run on this MacBook.
- **Fixed-step attitude:** the interleaved on/off steps differ by -8% to +20% with both signs; the
  150-vehicle "off" step had a 90 ms frame and the worst 1% low of the run, which looks like heat or
  a garbage collection rather than the setting.

### Fixed-step attitude

In the table above, fixed-step attitude on looks 7-16 % slower in the cockpit (0.6 ms at 0 vehicles,
2 ms at 100). It is not the setting:

* It only runs for the vehicle the player flies: `VehicleAttitude.advance` is called from the pilot's
  mouse and stick input (`PilotMouse`), never for other vehicles. With it on, the model runs 60 times
  a second however fast the frames come; with it off, once a frame, which at 240 FPS is four times as
  often. A substep is a few microseconds.
* The benchmark always measured on first, right after the camera left the orbit and the player was
  seated: the HUD's first frames, the chunks around the new view and, on a laptop, the clock and
  thermal state all fall on that step. Run on, off, off, on (the order the benchmark uses now), the
  steps get faster in the order they run, whatever the setting:

```
0 vehicles, cockpit:   on 15.9 FPS, off 18.8, off 22.3, on 25.7  ->  on 20.8, off 20.5 (+1.4 %)
150 vehicles, cockpit: on  6.7 FPS, off  7.0, off  7.4, on  7.9  ->  on  7.3, off  7.2 (+1.0 %)
```

(`./gradlew :neoforge:runBenchmarkCheck -PbenchCheck=full`, llvmpipe under Xvfb on the 4-core VM while
other builds ran; the pooled results over all sizes were within -4 % to +3 %, both signs, inside this
machine's noise.) The `on` slowdown in the first MacBook run is the first-step penalty; the interleaved
order removes it from the comparison.

## Caveats

* The radar picture depends on wall-clock time (heartbeat and full picture), so its bytes vary a
  little with the tick rate; the benchmark runs ticks back to back, so it sees fewer heartbeats per tick
  than a 20 TPS server would.
* Mock players are not network clients: nothing is compressed, nothing is acknowledged except chunk
  batches, and the crew of `flying` and `firefight` ask for a 2-chunk view, so the server tracks far
  fewer entities for them than for real players. With 500 crewed vehicles a real server would also
  have 500 connections to serve.
* Network bytes are uncompressed; a server with the default compression threshold (256 bytes) sends
  less for large packets and the same for the small ones that make up most vehicle traffic.
* Allocation counts only the server thread. The radar picture is built on virtual threads and its
  garbage is not in the numbers.
* `firefight` changes as vehicles get destroyed and projectiles hit; seeded random generators make
  runs similar, not identical, since boarding order and thread timing still differ.
* The client benchmark measures what the player sees, including the GPU wait, at the window size the
  player uses; results of two machines compare only as far as their windows and drivers do.
