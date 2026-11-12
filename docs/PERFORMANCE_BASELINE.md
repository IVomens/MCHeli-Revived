# Performance baseline

Stage 0 numbers for the vehicle-core rewrite (see ARCHITECTURE.md §6 and §7: "server ms per tick
with 300 vehicles ≤ stage 0 baseline × 1.02"). Every later stage reruns the same commands on the
same kind of machine and compares.

The benchmarks that replaced these runs, with their CI gate, are in [BENCHMARKS.md](BENCHMARKS.md);
this file keeps the stage 0 and stage 8 numbers the rewrite was measured against.

Recorded on commit `b73d335` (the stage 0 harness; no gameplay code changed since `747afcf`).

## Machine

* 4 vCPU Intel Xeon @ 2.10 GHz (cloud VM), 15 GB RAM, Linux 6.18.
* OpenJDK 21.0.12, `-Xmx3G` from the run configuration.
* No GPU: the client numbers come from Mesa llvmpipe (software OpenGL 4.5) under Xvfb at 1280×720.
* Other builds were running on the same host (load average 2–3.7), so single runs vary by ±20 %.
  Take the median of three runs and compare medians.

## Server: StressTests

600 measured ticks after 100 warm-up ticks; times are wall-clock ms per server tick.

| Scenario | Load | Run | mean | p50 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|
| parked | 300 vehicles, nobody aboard | 1 | 1.28 | 1.10 | 2.31 | 3.55 | 8.72 |
| | | 2 | 1.04 | 0.99 | 1.59 | 2.10 | 4.27 |
| | | 3 | 1.22 | 1.11 | 1.81 | 3.11 | 17.61 |
| | | **median** | **1.22** | **1.10** | **1.81** | | |
| flying | 60 helicopters and planes, ~45 crewed, full throttle | 1 | 2.21 | 1.90 | 3.47 | 5.48 | 31.17 |
| | | 2 | 1.80 | 1.57 | 3.10 | 4.83 | 13.85 |
| | | 3 | 2.22 | 2.00 | 3.51 | 6.99 | 29.54 |
| | | **median** | **2.21** | **1.90** | **3.47** | | |
| firefight | 40 manned vehicles firing every weapon (~1,100 entities) | 1 | 4.10 | 3.16 | 6.65 | 17.57 | 138.38 |
| | | 2 | 4.19 | 3.21 | 6.51 | 14.08 | 166.62 |
| | | 3 | 4.17 | 3.19 | 7.38 | 17.54 | 125.63 |
| | | **median** | **4.17** | **3.19** | **6.65** | | |

In the parked scenario about 287 of the 300 vehicles are resting; the rest are target drones that
ran out of fuel and blew up, vehicles still settling, and a few with throttle left on.

The stress scenarios are not deterministic (unseeded randoms, boarding order, wingman and weapon
spread), so crew counts and entity counts move a little between runs. Use the golden trajectories,
not these numbers, to prove behaviour is unchanged.

## Client: ClientStress

128 vehicles parked in rows in front of the player in the `devscene` world, frames measured over
client ticks 300–600.

| Run | frames | mean | p50 | p95 | max | fps |
|---|---|---|---|---|---|---|
| 1 | 175 | 77.87 | 76.71 | 88.69 | 107.71 | 12.8 |
| 2 | 167 | 81.66 | 79.37 | 96.46 | 114.95 | 12.3 |

Software rendering makes these useful only as a relative baseline on this same setup; real GPU
numbers must be taken on a desktop before the rendering stages (ARCHITECTURE.md §5).

These and the stage 8 numbers below timed each frame from the start of the game renderer's frame
to its end. Since the orbit scene was added, `[clientstress]` times the interval between frame starts
as the client benchmark does (buffer swap and GPU wait included), and adds p99, max, 1 % low and the
spikes; compare new runs with new runs.

## Client: stage 8 (render snapshot, Flywheel backend)

`runClientStress -Pvehicles=200 -Pmoving=20`: 200 vehicles parked in rows of 20 plus 20 tanks and
helicopters driving back and forth in front of them, frames over client ticks 300-600, Mesa llvmpipe
under Xvfb (854x480 window), same machine as above. Runs were interleaved (baseline, new immediate,
new Flywheel, then again) because other builds shared the host (load average 3-5). Mean frame time
in ms:

| Loader | Build | Run 1 | Run 2 | Run 3 | **median** |
|---|---|---|---|---|---|
| NeoForge | `4d0fba1` (before stage 8) | 102.91 | 101.56 | 101.80 | **101.80** |
| | immediate backend | 99.44 | 96.49 | 104.26 | **99.44** |
| | Flywheel 1.0.7 (`-Pflywheel`, `VehicleRenderer = auto`) | 98.32 | 107.84 | 133.73 | **107.84** |
| Fabric | `4d0fba1` (before stage 8) | 102.19 | 98.27 | 105.03 | **102.19** |
| | immediate backend | 104.75 | 102.55 | 106.05 | **104.75** |
| | Flywheel 1.0.7 (`-Pflywheel`, `VehicleRenderer = auto`) | 106.14 | 104.76 | 110.66 | **106.14** |

* The immediate backend is within noise of the old renderers (-2 % on NeoForge, +2.5 % on Fabric).
* Flywheel runs under llvmpipe (it picks its indirect backend; the instancing backend was slower,
  about 230 ms) and draws every vehicle, but it is not faster here: llvmpipe rasterizes and runs the
  culling compute shaders on the same CPU cores, and the Java-side vertex streaming the instancing
  removes was not the bottleneck. In the steady state the render thread spends little time in Java.
  The 30 % target can only be judged on a real GPU, so `VehicleRenderer` defaults to `immediate`.
* Flywheel 1.0 uploads its whole mesh pool again whenever a mesh is added or freed. With vehicle
  meshes (about 2.5 million vertices for the content pack at all levels of detail) that costs several
  frames, so the backend keeps every mesh it has used resident; see [RENDERING.md](RENDERING.md).

Both stress runs also run the render equivalence check on the vehicles in the scene; all passed
(worst relative matrix error 4.3e-6).

## Client: render check

`runRenderCheck` draws all 115 vehicles of the content pack in four fixed views each (460 images,
see [RENDERING.md](RENDERING.md)). Two runs of the same build are byte-identical, except that the
far view of the three largest planes (C-5, E-767, MD-90) flips between two variants that differ in
3-4 pixels, in old and new builds alike. Stage 8 against `4d0fba1`: 457 of 460 images identical on
both loaders in every run, and all 460 in the runs where those three agree.

## Commands

```sh
# server, one scenario per run (each writes run/stress.jfr and logs a "[stress] <name>:" line)
./gradlew runStressTest -Pscenario=parked --offline -q
./gradlew runStressTest -Pscenario=flying --offline -q
./gradlew runStressTest -Pscenario=firefight --offline -q
grep "\[stress\]" run/logs/latest.log

# client, headless; needs a fresh copy of the devscene save every time, because the vehicles a
# run spawns are saved into the world and the next run would count them too
rm -rf run/saves/devscene && cp -r <pristine devscene save> run/saves/devscene
xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClientStress -Pvehicles=200 -Pmoving=20 --offline -q
grep "\[clientstress\]\|\[equivalence\]" run/logs/latest.log
# the same with Flywheel (set VehicleRenderer = flywheel in run/config/mcheli.cfg first)
xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClientStress -Pvehicles=200 -Pmoving=20 -Pflywheel
# the client benchmark's scene with an orbiting camera, three 20 s turns; with -Pflywheel it logs
# what the backend made per turn ([flywheelsteady], [flywheelspikes]) and every spike ([flywheelspike]);
# force Flywheel's backend with backend = "flywheel:instancing" in run/config/flywheel-client.toml
xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClientStress -Pvehicles=150 -Porbit=true -Pflywheel
grep "\[clientstress\]\|\[flywheelsteady\]\|\[flywheelspikes\]" run/logs/latest.log

# deterministic images of every vehicle, to compare two builds (run/renderchecks/*.png)
xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runRenderCheck --offline -q
```

The full game test suite, golden trajectories and save fixtures included, took 2 min 18 s of wall
time (`timeout 1800 ./gradlew runGameTestServer --offline -q`, 172 tests).
