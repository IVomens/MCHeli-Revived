# Vehicle rendering

How vehicles are drawn since stage 8 of the vehicle-core rewrite (see [ARCHITECTURE.md](ARCHITECTURE.md)
Part 1 §5). Everything here is client code in `com.ivomens.mcheli.vehicle.client.render`.

## The frame of one vehicle

```
VehicleEntityRenderer (one for every vehicle type)
  ├─ VehicleFrameUpdates   per-frame client state: ground vehicle aim, search light aim, flame flip-books
  ├─ VehicleSnapshots      entity + components  ->  VehicleRenderSnapshot (immutable)
  ├─ VehicleDebugViews     test mode only: hit boxes, pilot seat, tank wheels
  ├─ model                 ImmediateVehicleRenderer, unless a Flywheel visual drew it this frame
  ├─ VehicleEffectsRenderer search light cones, ropes, exhaust flames, track outlines (always immediate)
  └─ markers               target marker, launcher sight, lock-on box, test-mode weapon points
```

* **`VehicleRenderSnapshot`** holds everything the model needs for one frame: the interpolated hull
  pose, the texture (livery, definition texture or thermal), tint, light, the level of detail and
  `PartPoses`, the interpolated state of every moving part (weapons, rotors, gear, hatches, canopies,
  wheels, tracks, bays, throttles, camera turrets, the ground vehicle part tree). Taking a snapshot
  only reads the entity, so it is safe on Flywheel's worker threads.
* **`VehicleModelPoser`** walks the definition's parts in drawing order and sends each one, with its
  transform relative to the airframe, to a **`PartSink`**. It is the only place that knows how parts
  move. The transforms use the fixed-function vocabulary the vehicle packs are written for
  (`rotate(angle, axis)` with axis-aligned axes counting only by sign, `rotateAround`, `translate`).
* **Backends** implement `VehicleModelRenderer`:
  * `ImmediateVehicleRenderer` sends the poser into the `LegacyGL` frame (`LegacyGLPartSink`), which
    streams every vertex through the vanilla buffers. It is the reference, the shader-pack path and
    the base for the 26.x rendering API, and it is what the mod always drew.
  * `FlywheelVehicleVisual` (package `flywheel`) records the poser into a `PoseRecorder` and keeps one
    Flywheel instance per part in instancers shared by all vehicles (see below).

## Level of detail

* A part smaller than 1.5 pixels on screen is skipped (`PartDrawer`).
* Each baked group has reduced meshes made by vertex clustering (`MeshClustering`), used once a cell
  of the level is smaller than a pixel (`MeshDetail`). The immediate renderer picks the level per
  group as it emits it; Flywheel uses the full mesh or the coarsest level for the whole vehicle and
  swaps the part models (see below).
* Crawler tracks are one static mesh when the vehicle is parked or farther than 256 blocks.

## Vertex emission (immediate backend)

Every baked group goes through `LegacyGL.emitBaked` and `BakedEmitter`, which turns the baked floats
(position, texture coordinates, normal) into `NEW_ENTITY` vertices through the current pose. That is
most of what a vehicle costs the render thread, so it has two fast paths. Both write exactly the bytes
the vanilla `BufferBuilder.addVertex` call writes (only the unused padding byte after the normal is
zero instead of left as it was), so images do not change.

* **One block per mesh** (`EntityVertexWriter`). When the buffer is a plain vanilla `BufferBuilder`
  building `NEW_ENTITY` quads, the space for the whole mesh is reserved at once and filled with plain
  memory writes (the access transformer opens the builder's fields for this). Anything else (a
  subclass or wrapping consumer such as the outline or decal buffers, a vertex half written, another
  format) takes the per-vertex path. Iris gives entity buffers its extended format while a shader pack
  is on, so with shaders everything is drawn per vertex as before and gets every attribute Iris adds.
* **Kept blocks** (`MeshBlockCache`, `VertexTransform`). A vertex position is
  `((m00 x + m10 y) + m20 z) + m30`: with the same rotation and scale, everything but the last addition
  is the same. A mesh drawn twice with the same linear part of the pose, colour, light and overlay gets
  its finished bytes kept in native memory, with its positions before the translation. Drawing it
  again copies the block; when only the translation moved (the camera moved, or two identical parked
  vehicles face the same way), the positions are redone by one addition each first. A parked vehicle
  therefore costs a memory copy per part instead of a matrix transform per vertex; moving vehicles,
  turning rotors and parts under 24 vertices are computed as before. Blocks live up to 64 MiB; when
  that is full a new block only replaces one not drawn for half a second, so a scene bigger than the
  budget keeps its blocks instead of churning, and blocks not drawn for 10 s are freed. Leaving the
  world frees all of them.

`-Dmcheli.render.bulkVertices=false` turns both off and `-Dmcheli.render.meshBlocks=false` only the
kept blocks. `EntityVertexWriterTest` compares the bytes of both paths with the per-vertex path for
rotated, scaled, mirrored and moving poses, and the render check takes its images from the third draw
of every view, so the kept blocks are in the images (see below).

### Where a vehicle's frame time goes

Real hardware first: a MacBook Pro 16" 2019 (Radeon Pro 5300M, macOS OpenGL 4.1, 3584x2240,
render distance 12, NeoForge, no Flywheel, no Sodium), `/mcheli benchmark client` on `e85bc3a`:

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

The frame grows by about 0.2 ms per vehicle in view, linearly (3.7 ms empty, 8.1 ms with 25, 14.1 ms
with 50, 24.7 ms with 100), which at that resolution points at work per vehicle on the CPU, not fill
rate. (The cockpit rows with fixed-step attitude on and off differ by the order they were measured in,
not by the setting; see [BENCHMARKS.md](BENCHMARKS.md#fixed-step-attitude).)

Profiled with JFR in `runClientStress -Pvehicles=100 -Pmoving=10` (Mesa llvmpipe, 854x480, about 108
vehicles drawn, 630,000 vertices and 3,300 `emitBaked` calls a frame), and timed around each stage on
the render thread, per frame:

| Stage | `e85bc3a`, per frame |
|---|---|
| Snapshot and effects capture (`VehicleSnapshots`, `VehicleEffects`) | 1.0 ms |
| Posing the parts (`VehicleModelPoser`, `CommonPartPoser`, `WeaponPartPoser`, part lookups) | 2.7 ms |
| Vertex emission (`BakedEmitter`: transform and write 630,000 vertices, about 20 ns each) | 13 ms |
| Effects and markers | 0.8 ms |
| Batch switches: every vehicle has its own texture, so asking for its buffer draws the previous vehicle (upload and `glDrawElements`, which llvmpipe runs on the render thread) | 31 ms |
| Client ticks of the vehicles (wheels, effects, components), two ticks a frame at 10 FPS | 8 % of the render thread's samples |
| Particles (tick and render) | 7 % of the samples |

Render types and textures cost nothing measurable on their own (one translucent type per texture, a
lookup per `emitBaked`); translucency sorting is already off for vehicles (`McheliRenderTypes`). On a
real GPU the batch switch is an upload of the vehicle's vertices and one draw call, much cheaper than
llvmpipe's rasterization, which leaves the emission as the biggest part a vehicle costs.

What changed, measured in the same scene (100 parked vehicles, interleaved runs of the old per-vertex
path, `-Dmcheli.render.bulkVertices=false`, and the new one, two rounds each, on a shared 4-core VM):

| Per frame, 95 vehicles drawn | per-vertex (`e85bc3a`) | now | change |
|---|---|---|---|
| Vertex emission | 11.9, 10.2 ms | 4.6, 5.5 ms | -55 % |
| The vehicles' render-thread work, without the batch switches | 15.3, 13.4 ms | 8.0, 9.4 ms | -40 % |
| ... per vehicle | 0.151 ms | 0.091 ms | -0.06 ms |

* Writing a mesh as one block alone (`d68165c`) was within noise: a micro-benchmark wrote 14 ns per
  vertex either way. The time is the arithmetic and memory traffic per vertex, not the builder's
  bookkeeping.
* The kept blocks (`f077008`) did it: with 10 of 108 vehicles moving, 81 % of the vertices came from a
  block, 6 % had their translation redone, 10 % were computed (moving vehicles) and 9 % were in parts
  too small to keep.
* Part lookups by exact name before the case-insensitive index (`1db02a1`) took
  `BakedModelBase.findPrefixedPart` from 2.1 % of the render thread's samples to 1.1 %.

On that MacBook this should take roughly 0.05 ms of the 0.2 ms a parked vehicle costs (the
CPU is faster than this VM's, so a little less in absolute terms): about 5 ms at 100 vehicles, 24.7
to roughly 20 ms in the orbit, 40 to about 50 FPS. Moving vehicles and the upload and draw call per
vehicle are unchanged. A `VertexBuffer` per part drawn with the vehicle's pose would remove the
emission and the upload too, but it bypasses the vertex format Iris extends and the batching of the
vanilla buffers; that is what the Flywheel backend already does, behind `VehicleRenderer = auto` or
`flywheel`.

### Batching vehicles by texture (tried, not shipped)

Each vehicle asks vanilla's buffer source for a render type keyed by its own texture, so a frame with
100 vehicles of mixed types ends about 100 batches, one upload and draw each. Vehicles drawn one
after another with the same texture already share a batch. Collecting every vehicle's geometry per
texture during the entity pass and drawing each texture once was built and measured:

- **Pixels.** Every part, hull and glass alike, is drawn with one depth-writing translucent type per
  texture, and 80 % of the bundled textures contain glass. Reordering is therefore only exact
  between pieces that cover no common pixel, so a piece may only join an earlier batch when its
  screen rectangle touches none of the batches drawn after it. With that rule the 460 render-check
  images and three overlapping fleet scenes were byte-identical.
- **Gain.** Draws per frame fell from 100 to 59 with four types in turn, 100 to 93 with spawn
  paints, and not at all with every vehicle of a different type (the client benchmark's case). On
  llvmpipe the time did not change: its "draw" cost is software vertex processing, which is the same
  split into 100 draws or 59. On a real GPU the saving is the driver's per-draw work, estimated at
  1-2 ms per frame at 100 mixed vehicles (0.01-0.02 ms of the 0.13 ms a vehicle costs on the
  MacBook).
- **Cost.** Two mixins in vanilla's entity render path, a reflection check for Iris, an access
  widener on `ByteBufferBuilder`, and a screen-space overlap rule that has to stay right forever.

The gain was too small and too uncertain for the risk to other mods that hook the same render path,
so it was left out. If a real-GPU profile ever shows per-draw overhead dominating, this is the
design to return to; Flywheel already removes per-vehicle draws entirely for players who want that.

## The Flywheel backend

Flywheel 1.0.7 for 1.21.1 (`dev.engine-room.flywheel`, maven.createmod.net) is an optional dependency.
Common code compiles against `flywheel-common-mojmap-api-1.21.1`; the loader jars
(`flywheel-neoforge-1.21.1`, `flywheel-fabric-1.21.1`) are declared optional in `neoforge.mods.toml`
and `fabric.mod.json`. Development runs load Flywheel with `-Pflywheel`.

* `VehicleRenderer` in `mcheli.cfg` decides whether it draws:
  * `immediate` (the default): never.
  * `auto`: only while Flywheel runs its **`indirect`** backend (OpenGL 4.6), which draws all instances
    of a material with one indirect draw however many part models there are. Its **`instancing`**
    backend (every Mac, whose OpenGL stops at 4.1, and older drivers) issues one draw call per
    instancer, each with its own program, uniforms, material state, instance texture buffer and buffer
    upload; a mixed fleet has hundreds of part models, and on a MacBook Pro (Radeon Pro 5300M) the
    first version of this backend ran at 0.5 FPS where the built-in renderer drew 40-115
    ([BENCHMARKS.md](BENCHMARKS.md)). Until the playtests show `indirect` (and the fixed `instancing`)
    beating the built-in renderer on real cards, `auto` stays a cautious choice and the default stays
    `immediate`. The proposal for `instancing` and its evidence are under
    [frame pacing](#frame-pacing-on-the-instancing-backend).
  * `flywheel`: on any backend, for testing; the client benchmark's Flywheel steps use it.
* `VehicleRenderBackend` touches Flywheel classes only when Flywheel is installed. A vehicle's model
  is left to Flywheel only when `VisualizationManager.supportsVisualization(level)` is true (Flywheel
  switches itself off under shader packs and on hardware without a usable backend) and its visual
  reported that it drew the vehicle this frame; every other vehicle is drawn immediately. While the
  config does not let Flywheel draw, the visuals hold no instances, so Flywheel draws nothing for them.
* Every part is a `TransformedInstance` placed from the render origin with the hull and part matrices
  multiplied (`VehicleInstances`). There are no per-vehicle embeddings: Flywheel keys instancers by
  embedding, so an embedding per vehicle gave every vehicle its own instancers, a draw call per part
  and vehicle. Now all vehicles share one instancer per part model. An instance is rewritten only when
  its matrix, tint or light changes, so parked vehicles cost nothing per frame beyond taking the
  snapshot; a moving vehicle rewrites its parts' matrices.
* Meshes are built once per part and level and shared, and one model per mesh and texture
  (`FlywheelModels`). Flywheel draws a vehicle with the full meshes, or with the coarsest reduced level
  once its cells are under a pixel; each level in use is another draw call per part, and every new
  mesh makes Flywheel upload its whole mesh pool, so it skips the levels in between. A vehicle looks
  its parts' models up once (again only when a part's mesh or the texture changes), and the far
  level's only when it first needs them.
* Parts a frame does not need are parked with a zero matrix and reused, so a vehicle crossing the far
  level back and forth only moves instances. `ModelKeepers` keeps every model in use resident: one
  empty instance in its instancer keeps the mesh, while any vehicle uses the model and for 60 s after
  the last one went (Flywheel frees a mesh as soon as its instancer is empty, and uploads its whole
  mesh pool whenever a mesh is added or freed, which with vehicle-sized meshes costs far more than a
  frame). Expired keepers are freed together, once a second at most, so the pool is uploaded once for
  all of them.
* New models are added in batches: models asked for within 50 ms of each other share one pool
  upload, and the next batch waits two seconds. A vehicle whose models are not resident yet (a type
  that just came into tracking range, or the far level reached for the first time) is drawn by the
  immediate renderer until then.
* A vehicle that goes (out of tracking range, destroyed, or recreated by Flywheel) gives its instances
  back parked and the next vehicle using the model takes them; spares nobody took for 10 s are deleted
  together. Deleting an instance makes Flywheel move every later instance of the instancer down and
  upload them again, and creating one grows and re-uploads the instancer.
* When the camera gets 256 blocks from Flywheel's render origin, Flywheel moves the origin, clears every
  instancer and recreates every visual. The keepers (and as many instances as the vehicles had) move to
  the new origin in that same frame, and the recreated visuals draw at once, before Flywheel would drop
  the instancers left empty; so no mesh leaves the pool and no vehicle falls back to the immediate
  renderer for that frame. See [frame pacing](#frame-pacing-on-the-instancing-backend).
* `FlywheelVehicleStats` counts visuals, meshes, models, keepers, instances created and deleted,
  instance rewrites (and the runs of neighbouring ones, which the instancing backend uploads with a
  call each), render origin moves and the vertices Flywheel copied from our meshes. Development runs
  with `-Pflywheel` log them every second (`[flywheelstats]`, with Flywheel's instancer count and
  backend) and log every frame that took over twice the median of the 101 before it with what
  happened in it (`[flywheelspike]`: the counters, instancers made or dropped, bytes allocated by all
  threads, garbage collections). `runClientStress` checks that a parked scene's measured frames create
  nothing (`[flywheelsteady]`) and sums the spikes of each window (`[flywheelspikes]`).
  `VehicleInstancesTest` checks the same against fakes of Flywheel's instancers, and that an orbiting
  camera over a steady scene (levels of detail, vehicles leaving and coming back, origin moves) creates
  nothing once every vehicle has been seen and never leaves an instancer empty.
* Effects and markers stay on the entity renderer, which Flywheel keeps running
  (`skipVanillaRender` is false).

### Frame pacing on the instancing backend

On the MacBook (Flywheel's `instancing` backend, see [BENCHMARKS.md](BENCHMARKS.md#results-on-real-hardware))
Flywheel beat the built-in renderer by 29-53 % average FPS from 25 vehicles up, but not on 1 % lows:
33.0 against 33.9 at 100 vehicles, 21.8 against 28.8 at 150 (p99 34 ms against 31 ms). The benchmark's
orbit was rebuilt as `runClientStress -Porbit=true` and run on `instancing` (forced in
`run/config/flywheel-client.toml`) with the per-frame counters above. llvmpipe's frame times are too
uniform and too noisy on the shared VM to show the spikes themselves (every frame is slow, and the same
build varies by 30-50 % between runs), so the counters are the evidence. What the backend made per 20 s
turn of the orbit, before (`a157a8d`, the backend as the MacBook ran it, with the counters) and after:

| 150 vehicles (126-138 loaded), per turn | before, turn 1 | turn 2 | turn 3 | after, turn 1 | turn 2 | turn 3 |
|---|---:|---:|---:|---:|---:|---:|
| Render origin moves | 2 | 2 | 2 | 2 | 2 | 2 |
| Mesh pool copies (meshes copied, every one an upload of the whole pool) | 70,352 | 72,138 | 65,929 | 7,456 | 7,929 | 1,035 |
| ... uploaded, MB per frame on average | 14.5 | 13.4 | 14.4 | 2.9 | 1.7 | 0.2 |
| Instancer requests (before) / keepers made (after) | 4,406 | 3,788 | 3,686 | 2,509 | 2,702 | 2,679 |
| Instances created / deleted | 8,191 / 4,541 | 6,917 / 4,720 | 6,868 / 4,623 | 7,249 / 0 | 8,100 / 0 | 8,039 / 0 |

| 100 vehicles (107-112 loaded), per turn | before, turn 1 | turn 2 | turn 3 | after, turn 1 | turn 2 | turn 3 |
|---|---:|---:|---:|---:|---:|---:|
| Render origin moves | 0 | 0 | 0 | 0 | 0 | 0 |
| Mesh pool copies | 37,226 | 0 | 0 | 7,258 | 3,769 | 0 |
| Instancers requested / keepers made | 646 | 322 | 289 | 181 | 9 | 0 |
| Instances created / deleted | 919 / 230 | 326 / 334 | 293 / 318 | 512 / 6 | 32 / 36 | 13 / 22 |

What caused the spikes, in order of cost:

1. **The render origin.** Flywheel moves its render origin when the camera gets 256 blocks from it,
   clears every instancer and recreates every visual, and runs no frame for the new visuals in that
   frame. The orbit at 150 vehicles is 280 blocks across (100 vehicles: 226), so the origin moved twice
   per turn. In that frame the backend's visuals drew nothing, so the immediate renderer drew all 136
   vehicles on top of the frame's other work, and every instancer was empty at Flywheel's draw, which
   deletes empty instancers and frees their meshes. The keepers lived with the old origin and were
   lost. From then on every vehicle that came back into range, and every model it needed, brought its
   instancer back and its mesh into the pool, and each frame with a new mesh uploads the whole pool
   again (about a million vertices, 36 MB): 50 to 60 pool uploads per turn, in a third of the frames,
   for as long as the camera kept moving. That is the 150-vehicle 1 % low. Now the keepers, with as many
   instances as the vehicles had, move to the new origin in the frame Flywheel clears them, and the
   recreated visuals draw at once; the pool is never touched. The keepers and instances still have to
   be made again (Flywheel cleared them), about 1,300 keepers and 4,000 instances, in one frame twice
   per turn; that is the remaining cost of a 280-block orbit and is Flywheel's to change.
2. **New meshes during the first turn.** Vehicles of types not yet seen come into tracking range all
   through the first turn (the benchmark measures that turn), and vehicles reach the far level for the
   first time; each frame with a new model uploaded the whole pool, 35-55 times a turn. New models are
   now added in batches every two seconds at most (the vehicles waiting are drawn by the immediate
   renderer meanwhile, which looks the same): 5 times fewer pool copies in the first turn.
3. **Vehicles leaving and coming back.** At 150 vehicles 17 visuals a second are deleted and made as
   vehicles leave and enter tracking range. Each deleted vehicle deleted its instances (Flywheel then
   moves every later instance of each instancer down and uploads it again) and each new one created
   them (growing and re-uploading the instancer). Instances now go back to their model's keeper parked,
   and the next vehicle takes them: at 100 vehicles a steady turn creates 13 instances instead of 300
   and requests no instancer.
4. **Not the cause:** the moving vehicles rewrite only their changed parts, 250-300 instances a frame
   at 150 vehicles (20-25 KB in about 150 runs, a `glBufferSubData` each), the same before and after;
   rotor blades are among them. Composing the hull in the shader, so static parts would not be
   rewritten when the hull moves, would need an instance type of our own with a second matrix, and
   is not worth it for 25 KB a frame. Allocation: the client allocates 8-15 MB a frame in the orbit,
   but Flywheel's worker threads (where the visuals run) account for under 1 % of it in a JFR
   profile; the integrated server and chunk loading make most of it. Of the frames over twice the
   median on llvmpipe, nearly all had backend work in them, and a collection alone explained none.

Per-frame lookups that went with it: a vehicle looks its parts' models up once instead of every frame
(each lookup took the lock of a map every Flywheel worker shares), and tells the entity renderer
whether Flywheel drew it only when that changes (another shared lock).

On llvmpipe the frame times move with the machine's load more than with the build (interleaved runs,
NeoForge, 854x480):

| Scene, run | Renderer | Turn 1: FPS / 1 % low / p99 ms | Turn 2 | Turn 3 |
|---|---|---|---|---|
| 150, `instancing`, round 1 | Flywheel before | 3.8 / 1.3 / 788 | 6.4 / 1.4 / 310 | 6.3 / 3.5 / 243 |
| | Flywheel after | 3.1 / 1.1 / 928 | 6.0 / 1.8 / 530 | 7.5 / 4.6 / 208 |
| | built-in | 5.6 / 1.9 / 526 | 8.4 / 2.4 / 390 | 10.6 / 7.0 / 138 |
| 150, `instancing`, round 2 | Flywheel before | 3.8 / 1.8 / 562 | 5.6 / 1.7 / 402 | 7.0 / 3.2 / 235 |
| | Flywheel after | 6.5 / 2.7 / 282 | 7.6 / 4.0 / 205 | 7.7 / 4.6 / 182 |
| | built-in | 7.0 / 2.2 / 441 | 9.5 / 3.7 / 243 | 9.2 / 4.6 / 189 |
| 100, `instancing` | Flywheel before | 5.3 / 2.1 / 374 | 6.7 / 2.1 / 304 | 8.1 / 4.3 / 216 |
| | Flywheel after | 7.1 / 2.5 / 314 | 8.5 / 4.5 / 214 | 8.7 / 4.5 / 193 |
| | built-in | 4.8 / 2.0 / 500 | 6.6 / 1.7 / 436 | 6.7 / 1.6 / 479 |
| 150, `indirect` | Flywheel before | 8.0 / 1.7 / 397 | 9.2 / 2.2 / 401 | 9.7 / 2.4 / 310 |
| | Flywheel after | 8.5 / 2.6 / 334 | 10.5 / 3.6 / 239 | 11.5 / 6.2 / 161 |

Flywheel's 1 % low after the change is above the version before it in every turn but one, and in the
last two turns level with or above the built-in renderer's in two of the three `instancing`
comparisons; the built-in renderer is still ahead on average FPS here, as before, because llvmpipe
rasterizes on the CPU and gains nothing from fewer draw calls (see [BENCHMARKS.md](BENCHMARKS.md)).
The same build differs by up to a factor of two between rounds, so read these as direction only.

**`auto` on `instancing`.** The causes found are gone in the steady orbit and much smaller in the
first turn, and nothing in the counters is left that the built-in renderer does not also pay for
(it draws the waiting vehicles). That is a reason to measure it, not to switch: llvmpipe cannot show
the 1 % low of a real GPU. The proposal is to let `auto` pick Flywheel on `instancing` too if the
MacBook, with this build, shows Flywheel's orbit 1 % low at least equal to the built-in
renderer's at 100 and 150 vehicles (it already leads on average FPS). Until that run, `auto` keeps
`indirect` only and the default stays `immediate`.

## Checks

* `./gradlew runRenderCheck` (and `:fabric:runRenderCheck`) draws every vehicle of the content pack
  off screen, in four fixed views each (near, middle, far, and near with the hull turned and the crew
  aiming sideways), and writes `run/renderchecks/*.png`. Comparing the images of two builds shows
  whether the immediate renderer's output changed. The run also logs `[equivalence]` lines: for
  every vehicle and several poses, the matrices Flywheel would draw each part with must equal the
  `LegacyGL` matrix stack of the immediate renderer.
* `./gradlew runClientStress -Pvehicles=200 -Pmoving=20 [-Pflywheel]` measures frame times with
  parked and moving vehicles and runs the same equivalence check on the vehicles in the scene. See
  [PERFORMANCE_BASELINE.md](PERFORMANCE_BASELINE.md). `-Porbit=true` builds the client benchmark's
  scene instead (`-Pvehicles` of them, one in ten moving with rotors running, damaged helicopters
  smoking) and circles the camera around it three times, 20 s a turn, logging each turn: frame time
  percentiles, 1 % low and spikes, and with Flywheel what the backend made.
* `/mcheli benchmark client` measures the same comparison on a player's own graphics card, in the
  released jar: immediate against Flywheel (`VehicleRenderer = flywheel`), and fixed-step attitude
  on against off, at 0 to 150 vehicles (see [BENCHMARKS.md](BENCHMARKS.md) and
  [PLAYTEST.md](PLAYTEST.md)). Its results decide what `auto` may pick and whether it can become the
  default.
* `./gradlew :neoforge:test --tests '*EntityVertexWriterTest*'` checks that the fast emission paths
  write the bytes of the per-vertex path.
