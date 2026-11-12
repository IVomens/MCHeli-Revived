# Playtest: the in-game benchmark

Thank you for testing! This takes about 15 minutes, 10 of which the game runs on its own. At the end
you send back two small files. The benchmark measures how fast vehicles are drawn on your machine,
with the built-in renderer and with Flywheel, which we cannot measure on our own machines (they have
no real graphics card).

## 1. Install

1. Install Minecraft **1.21.1** with **NeoForge** (or Fabric with Fabric API) in your launcher, as
   for any other mod.
2. Put the MCHeli: Revived jar from the release or nightly page in the `mods` folder.
3. Also put **Flywheel 1.0.7** for 1.21.1 (same loader) in `mods`. It is optional for players, but
   the benchmark compares with and without it, so please install it for the test. Flywheel 1.0 is
   not listed on Modrinth as a mod of its own (the "Flywheel" page there is the old 1.18/1.19 line),
   so take the jar from its developers' Maven:
   [NeoForge](https://maven.createmod.net/dev/engine-room/flywheel/flywheel-neoforge-1.21.1/1.0.7/flywheel-neoforge-1.21.1-1.0.7.jar)
   or
   [Fabric](https://maven.createmod.net/dev/engine-room/flywheel/flywheel-fabric-1.21.1/1.0.7/flywheel-fabric-1.21.1-1.0.7.jar).
   Don't use Vanillin for this: it bundles an older Flywheel and also changes how vanilla entities
   are drawn, which muddies the comparison.
4. No shader packs (Iris/Oculus) for this test: Flywheel switches itself off under shaders.

## 2. Before you start the game

**MacBook Pro 16" (2019):** make sure the Radeon is used, not the built-in Intel graphics.

1. Plug in the power adapter (on battery macOS prefers the Intel GPU and slows the Radeon down).
2. System Settings → Battery (or Energy Saver on older macOS) → Options: switch **Automatic graphics
   switching** off.
3. Close other heavy apps (browsers with many tabs, video calls).

**HP EliteBook 840 G5:**

1. Plug in the power adapter, and set Windows to **Best performance** (Settings → System → Power &
   battery → Power mode).
2. Update the Intel graphics driver if Windows offers it (optional).
3. Close other heavy apps.

On both: give Minecraft at least 4 GB of memory in the launcher (Java arguments `-Xmx4G`) if you can.

## 3. Make a test world

1. Start the game. Single player → Create New World.
2. Game Mode **Creative**, **Allow Cheats: ON**.
3. More/World options → World Type **Superflat** (the default preset is fine).
4. Create the world and wait until the chunks around you have loaded (5–10 seconds).

Use a throwaway world: the benchmark spawns and removes vehicles and changes the time and weather
for the duration (it puts them back afterwards).

## 4. Run it

1. Open chat (`T`) and type:

   ```
   /mcheli benchmark client
   ```

2. Let go of the mouse and keyboard. A box in the top left shows the step and the time left. The
   camera circles a field of parked and moving helicopters, planes and tanks, then sits in a
   helicopter's cockpit; this repeats with 0, 25, 50, 100 and 150 vehicles. The frame rate cap and
   vsync are switched off on purpose while it runs.
3. It takes about 10 minutes (about 7 without Flywheel). To stop early press **Backspace** (or Esc);
   what was measured so far is still saved.
4. When it is done, chat says "Benchmark finished" and shows the path of the results. **Click the
   path** to copy it.

Everything it changed is put back: your render distance, frame rate limit, vsync, camera, the
`VehicleRenderer` and `FixedStepAttitude` settings, the time, the weather and where you stood.

**If your machine cannot keep up.** A step that runs under 5 FPS for its first two seconds, or has a
single frame longer than 2 seconds, is given up and the benchmark moves on to the next step. The
results show it as `aborted: too slow (0.5 FPS)` instead of frame times; that is a result too, please
send it. (Its first two frames are not counted, since switching the renderer can compile shaders.)

**If the game crashed or you had to force-quit it.** Start Minecraft again: the benchmark notes your
render distance, frame rate limit, vsync, pause-on-lost-focus, `VehicleRenderer` and
`FixedStepAttitude` in `.minecraft/mcheli/benchmarks/benchmark-in-progress.properties` when it starts,
and if that file is still there on the next start it puts those settings back and deletes it (the
game log says "A client benchmark run did not finish last time"). The vehicles it spawned are removed
the next time you open the world.

Want to try it first? `/mcheli benchmark client quick` runs a 1-minute version.

## 5. Send back

In the folder `.minecraft/mcheli/benchmarks/` (the path chat showed) there are two files with the
same name:

* `mcheli-benchmark-<date>-<time>.json`
* `mcheli-benchmark-<date>-<time>.txt`

Please send both. The `.txt` is readable; have a look if you are curious. Nothing personal is in
them: the graphics card, processor, memory, operating system, Java and mod versions, and frame times.

Opening the folder: on the Mac, Finder → Go → Go to Folder… and paste the path. On Windows, paste
the path into the Explorer address bar (without the file name), or `Win+R` → `%appdata%\.minecraft\mcheli\benchmarks`.

## 6. What a benchmark cannot judge (5 minutes, optional but very helpful)

A few things only a person can tell. Please write one line per item: fine / slightly off / bad, and
what felt wrong.

**How to cap the frame rate:** Options → Video Settings → **Max Framerate** slider (set 30, 60 or
144; "Unlimited" is the far right) and switch **VSync** off for the test.

1. **Fixed-step attitude, at 30, 60 and 144 FPS.** Spawn a helicopter (e.g. AH-64) and a plane
   (e.g. F-35A) from the creative tab, fly each for a minute at each cap. Do roll, pitch and yaw
   respond the same at all three caps? Any stutter or jitter of the aircraft or the horizon?
2. **The same with the old behaviour.** Set `FixedStepAttitude = false` in
   `.minecraft/config/mcheli.cfg` (or the in-game MCHeli config screen), restart or `/mcheli reconfig`,
   and repeat 1 at 30 and 144 FPS. Is one of the two clearly better?
3. **Server terrain follow.** Set `ServerTerrainFollow = true`, `/mcheli reconfig`, park a tank on a
   slope (a hill in a normal world works better than superflat), get out and walk around it. Does it
   settle onto the slope naturally? Then `false` (the default) and compare. Any jumping or sinking?
4. **Flywheel look.** With `VehicleRenderer = flywheel` (the benchmark switches it back to what you
   had), do vehicles look exactly the same as with `immediate`? Flickering, missing parts, wrong
   colours? (`auto` only uses Flywheel on its `indirect` backend, which Macs do not have; `flywheel`
   uses it on any backend.)

Thanks again!
