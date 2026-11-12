# Fabric 1.21.1

MCHeli: Revived ships on Fabric next to NeoForge. Both jars come from one codebase; content packs,
configs and gameplay are identical. This page records how the Fabric build is put together, how each
NeoForge feature maps onto Fabric, and every behaviour that differs.

## Where things stand

Phases 1 to 3 are done, and the game test part of phase 4.

* `:core` (161 files): everything that needs no Minecraft, plain Java and JOML: the physics, the HUD
  expression compiler, the wingman brain, the content schema engine, ballistics and guidance math.
* `:common` (the shared code and resources): everything that is not loader glue. It compiles against
  plain Minecraft 1.21.1 (NeoForm with our access transformer, no loader). Its `gametest` source set
  holds the game tests both loaders run, compiled here against vanilla only.
* `:neoforge`: the `com.ivomens.mcheli.neoforge` package, `neoforge.mods.toml`, the
  `META-INF/services` registrations, the NeoForge registration of the game tests and the unit tests
  that need the mod loaded. Builds `mcheli-revived-<version>-neoforge.jar`.
* `:fabric`: the `com.ivomens.mcheli.fabric` package, `fabric.mod.json`, the access widener, the
  Fabric mixins, the services and the Fabric registration of the game tests. Builds
  `mcheli-revived-<version>-fabric.jar`.

Both loaders run the same 437 game tests, and they pass on both with the goldens exact: the vehicle
trajectories, the save fixtures, the definition dumps and the weapon snapshots compare equal to the
same committed files. The Fabric jar boots a production Fabric server (intermediary names, Fabric API,
no development mappings), and the headless client scene runs on Fabric and renders the vehicles, the
cockpit, the HUD, the effects and the livery screens the way the NeoForge client does.

## The build

* **Loom 1.13.6**, the newest release that runs on the build's Gradle 8.14 (Loom 1.14 and later need
  Gradle 9.2, Loom 1.19 also Java 25 for the Gradle daemon). Mojang names layered with the same
  Parchment mappings as NeoForm, so the shared sources compile unchanged.
* **Fabric Loader 0.20.1** to develop against; the jar asks for 0.16.10 or later (the first with the
  MixinExtras features the mixins use).
* **Fabric API 0.116.17+1.21.1**, required.
* **Team Reborn Energy 4.1.0**, compiled against, optional at runtime.
* SnakeYAML is bundled with `include`, as NeoForge bundles it with jar-in-jar. HUD formulas are
  compiled by the mod's own compiler (`core.hud.expr`), which generates no classes, so nothing depends on
  Knot's handling of runtime bytecode.
* `mcheli.loader-conventions` compiles `:core` and `:common` (and common's game tests) into the
  module, as for NeoForge. Common's access transformer is left out of the jar.
* The mixins need no refmap: Loom rewrites their targets to intermediary names when it remaps the jar.
* Fabric's runs use `fabric/run`, so the two loaders' configs and saves never mix. The game tests run
  in `fabric/build/gametest`.

### Access widener

`fabric/src/main/resources/mcheli.accesswidener` holds every entry of common's
`META-INF/accesstransformer.cfg`, including those NeoForge's own transformer also opens, plus the few
the Fabric glue needs (the creative tab constructor, the entity tracker's player set).
`AccessWidenerCoverageTest` fails the build when a transformer entry has no widener entry.

Loom can only set up one Minecraft per project, and `:common` gets its Minecraft from NeoForm through
ModDevGradle, so the widener does not apply to common's own compilation; it does not need to, the
transformer covers that. This is the MultiLoader template's arrangement.

### The game tests

The shared game tests live in `common/src/gametest`. `McheliGameTests.all()` lists every test, each
built as a `TestFunction` with its own batch and the `mcheli:empty` structure, so names, batches and
order are the same on both loaders (the stress scenarios, once `@GameTest` methods, became generated
tests with the names and settings NeoForge gave them). `GameTestHooks` and `ClientSceneHooks` are the
events the tests listen to; `ExposedStorage` is how a test reaches an entity's storage through the
loader's API.

* NeoForge: `NeoForgeGameTests` is a `@GameTestHolder` with one generator, and subscribes the hooks.
* Fabric: a development-only mod (`mcheli_gametest`, in `fabric/src/gametest`) registers the tests from
  its `server` and `client` entrypoints, which run after every mod's main initializer, and adds the
  hooks in an event phase after the default one, so they run after the common hooks of the same event
  as on NeoForge. Fabric's own `fabric-gametest` entrypoint runs during the main initializers, before
  MCHeli has loaded its content. A mixin puts the test server's world spawn where NeoForge's is.

Run them with `./gradlew :fabric:runGameTestServer`; `-Pgolden=record` writes the same files as on
NeoForge.

## Platform services

Interfaces live in `com.ivomens.mcheli.platform` and are found with `ServiceLoader` (`Services`,
listed in `META-INF/services`). Client-only ones go through `ClientServices` so a dedicated server
never loads them.

| Service | NeoForge | Fabric |
|---|---|---|
| `PlatformHelper` | `FMLEnvironment`, `FMLPaths`, `ModList`, `ServerLifecycleHooks` | `FabricLoader`; the running server is tracked from the server lifecycle events |
| `RegistryHelper` | one `DeferredRegister` per registry | entries are collected and registered after common init, one registry at a time in the game's registry order (the order of NeoForge's `RegisterEvent`); the creative tabs subclass `CreativeModeTab` for the live icon |
| `NetworkHelper` | `RegisterPayloadHandlersEvent`, `PacketDistributor`, `hasChannel` | `PayloadTypeRegistry`, `ServerPlayNetworking`/`ClientPlayNetworking` (client receivers registered by the client initializer), `canSend` as the channel check; the tracking players are read from the entity tracker in NeoForge's order |
| `MenuHelper` | `IMenuTypeExtension` | `ExtendedScreenHandlerType` carrying the raw extra-data bytes |
| `ChunkTickets` | `TicketController` | a vanilla `TicketType` per set, with per-level bookkeeping |
| `GameplayHooks` | `EventHooks`, block and item extensions | vanilla behaviour; "no repair" through mixins |
| `PackHelper` | hidden `Pack.Metadata` | vanilla metadata remembered as hidden; mixins hide those packs |
| `FluidHelper` | fluid handler item capability | Transfer API `FluidStorage.ITEM`, droplets converted to millibuckets |
| `EnergyHelper` | energy item capability | Team Reborn Energy `EnergyStorage.ITEM` when installed, else no energy items |
| `EntityDataHelper` | `Entity.getPersistentData` | a compound added to `Entity` by a mixin, saved under the same `NeoForgeData` key |
| `ClientPlatformHelper` | `isActiveAndMatches` | the key equals the mapping's key |

## How the NeoForge glue maps onto Fabric

Fabric API events are used where one fires at the same point as NeoForge's event. Where none does,
a mixin hooks the game where NeoForge's patch fires its event. Mixins call the common hooks directly,
unless something else needs the same point (the game tests do), in which case they fire a small event
from `fabric.event.McheliEvents` or `fabric.client.event.McheliClientEvents`. `FabricCommonEvents` and
`FabricClientEvents` read like their NeoForge counterparts.

| NeoForge | Fabric |
|---|---|
| `ServerStartingEvent`, `ServerStartedEvent`, `ServerStoppedEvent` | `ServerLifecycleEvents` |
| `ServerTickEvent.Post` | `ServerTickEvents.END_SERVER_TICK` |
| `LevelTickEvent.Pre`/`Post` (server) | `ServerTickEvents.START/END_WORLD_TICK` (same point) |
| `LevelTickEvent.Pre`/`Post` (client) | mixin around `ClientLevel.tick` in `Minecraft.tick` (Fabric's events wrap the entity tick) |
| `LevelEvent.Unload` | `ServerWorldEvents.UNLOAD`; client: mixin in `Minecraft` |
| `RegisterCommandsEvent` | `CommandRegistrationCallback` |
| `OnDatapackSync` (reload) | `ServerLifecycleEvents.END_DATA_PACK_RELOAD` |
| `EntityJoinLevelEvent` (normal, lowest) | `McheliEvents.ENTITY_JOIN` (default, last phase) from mixins in `PersistentEntitySectionManager.addEntity`, `ServerLevel.addPlayer`, `ClientLevel.addEntity` |
| `EntityLeaveLevelEvent` | `McheliEvents.ENTITY_LEAVE` at the end of the levels' tracking callbacks (Fabric's `ENTITY_UNLOAD` fires at their start) |
| `LivingIncomingDamageEvent` | `McheliEvents.LIVING_INCOMING_DAMAGE` from a mixin in `LivingEntity.hurt`, which can change the amount |
| `LivingDeathEvent` (normal, lowest) | `McheliEvents.LIVING_DEATH` from mixins in `LivingEntity.die`, `Player.die`, `ServerPlayer.die` |
| `PlayerTickEvent.Pre` | `McheliEvents.PLAYER_TICK_START` from a mixin in `Player.tick` |
| `PlayerInteractEvent.EntityInteract` | `UseEntityCallback` without a hit result |
| `AttackEntityEvent`, `LeftClickBlock` (highest) | `AttackEntityCallback`, `AttackBlockCallback` in a phase before the default one |
| `PlayerEvent.Clone`, `PlayerRespawnEvent`, `PlayerLoggedOutEvent` | `ServerPlayerEvents.COPY_FROM`, `AFTER_RESPAWN`, `LEAVE` |
| `RegisterEvent` | `FabricRegistryHelper.registerAll` |
| `EntityAttributeCreationEvent` | `FabricDefaultAttributeRegistry` |
| `RegisterCapabilitiesEvent` | charger: Team Reborn Energy `EnergyStorage.SIDED`; entities: none (see below) |
| `FMLCommonSetupEvent` | end of the main initializer |
| `FMLLoadCompleteEvent` | dedicated server: `server` entrypoint; client: `ClientLifecycleEvents.CLIENT_STARTED` |
| `BuildCreativeModeTabContentsEvent` | `ItemGroupEvents.MODIFY_ENTRIES_ALL` |
| `AddPackFindersEvent` | mixins on the `PackRepository` constructor, recognising the client and the data pack repositories by their vanilla sources, as Fabric API does |
| `FMLClientSetupEvent` | `ClientLifecycleEvents.CLIENT_STARTED` |
| `EntityRenderersEvent.RegisterRenderers` | `EntityRendererRegistry`, `BlockEntityRenderers` |
| `RegisterClientExtensionsEvent` (item renderers, fuel textures) | `BuiltinItemRendererRegistry`, `FluidRenderHandlerRegistry` |
| `RegisterMenuScreensEvent` | `MenuScreens.register` |
| `RegisterShadersEvent` | `CoreShaderRegistrationCallback` |
| `RegisterClientReloadListenersEvent` | `ResourceManagerHelper` |
| `RegisterKeyMappingsEvent` | `KeyBindingHelper` |
| `RegisterColorHandlersEvent.Item` | mixin at the end of `ItemColors.createDefault` |
| `ModelEvent.RegisterAdditional`, `ModifyBakingResult` | `ModelLoadingPlugin`, its after-bake modifier |
| `BakedModel.applyTransform` | mixin at the start of `ItemRenderer.render` |
| `RegisterGuiLayersEvent`, `RenderGuiLayerEvent` | `GuiMixin`: the HUD after the camera overlays and after the chat, one layer step deeper each; hidden layers skipped |
| `RenderGuiEvent.Pre`/`Post` | mixin around the layers in `Gui.render` |
| `ClientTickEvent.Pre`/`Post` | `ClientTickEvents.START/END_CLIENT_TICK` |
| `RenderFrameEvent.Pre`/`Post` | `McheliClientEvents.FRAME_START/END` around the game renderer |
| `RenderLevelStageEvent` `AFTER_SKY`, `AFTER_PARTICLES` | mixin in `LevelRenderer.renderLevel` |
| `RenderLivingEvent`, `RenderPlayerEvent`, `RenderHandEvent`, `RenderNameTagEvent` | mixins in the living, player, hand and entity renderers |
| `ViewportEvent.ComputeCameraAngles`, `CalculateDetachedCameraDistanceEvent` | `CameraMixin`, including the camera roll vanilla lacks |
| `ViewportEvent.ComputeFov` | mixin on `GameRenderer.getFov` |
| `CalculatePlayerTurnEvent` | mixin in `MouseHandler.turnPlayer` |
| `InputEvent.MouseScrollingEvent` | mixin in `MouseHandler.onScroll` |
| `InputEvent.InteractionKeyMappingTriggered` (attack) | `ClientPreAttackCallback` |
| `ClientPlayerNetworkEvent.LoggingOut` | `ClientPlayConnectionEvents.DISCONNECT` |
| `RecipesUpdatedEvent` | mixin at the end of `ClientPacketListener.handleUpdateRecipes` |
| custom particle render types (NeoForge draws them all) | mixin in `ParticleEngine.render` |
| `Item.onEntitySwing` | mixin in `LivingEntity.swing` |

The fuel fluid (`mcheli:mch_fuel`, `mch_fuel_flowing`, the block and the bucket) is registered with
the same ids and properties as on NeoForge; `FuelFluid` reproduces `BaseFlowingFluid` with MCHeli's
settings.

## Behaviour that differs on Fabric

Exact parity was the goal; these are the differences that remain, all of them where Fabric has no
counterpart for a NeoForge feature.

| Area | NeoForge | Fabric |
|---|---|---|
| Entity storage for other mods | aircraft, containers and UAV stations expose their inventory as an item handler, aircraft their fuel tank as a fluid handler | Fabric's Transfer API has no entity lookup that other mods query, so nothing is exposed; MCHeli's own fuel slots and buckets work the same |
| Energy | the charger takes FE from cables; battery items through the energy capability | the same through Team Reborn Energy (1 E counted as 1 FE) when it is installed; without it, no energy interop |
| Fuel fluid physics | the fluid type lets entities swim and drown in fuel and boats float on it, and it does not put out fire | vanilla only does this for fluids tagged as water or lava, so entities pass through fuel as through an air block; viscosity and name are set for other mods' fluid displays |
| Chunk tickets with `ticking` | forced chunks also get random ticks and mob spawning without players | vanilla region tickets give entity ticking only. Tickets are not saved on either loader |
| Damage and death events | other mods' listeners can run before or after MCHeli's | MCHeli's mixins fire its own events at NeoForge's points; other Fabric mods' damage and death hooks run wherever they hook |
| Entity join | other mods can cancel a join before MCHeli's "joined" hooks run | nothing can cancel the join through MCHeli's event |
| Explosions | other mods can edit an MCHeli explosion's entity list (`ExplosionEvent.Detonate`) and the block a fluid makes (`FluidPlaceBlockEvent`) | neither event exists; vanilla behaviour |
| Per-block explosion resistance | position-aware `getExplosionResistance` of other mods' blocks | the block's fixed resistance, as in vanilla |
| `canRiderInteract`, `onItemUseFirst` | other mods' entities and items can opt in | vanilla behaviour; no MCHeli entity or item uses them |
| Enchanting MCHeli items | `supportsEnchantment`/`isPrimaryItemFor` overrides refuse every enchantment | vanilla's enchantment item tags list none of our items, so the result is the same unless a data pack adds them |
| Key conflict contexts | key checks (the drafting table closing on the inventory key) also honour the mapping's conflict context and modifier | plain key match |
| Opaque effect sprites | drawn before translucent blocks | all particles are drawn in vanilla's single pass after translucent blocks, so an opaque sprite behind water is hidden by it |
| Payload protocol version | negotiated per channel | Fabric checks channels only; a client and server of the same jar always agree |
| Persistent player data | `PlayerPersisted` survives death | not copied (MCHeli does not use it) |
| Game test server | spawn at (0, 60, 0); not a dedicated server | Fabric API reports it as dedicated (MCHeli never asks); the spawn is moved to NeoForge's |

## Decisions made

* One mod id (`mcheli`) and one network protocol on both loaders. A Fabric client joins a Fabric
  server and a NeoForge client a NeoForge server. Mixing loaders between client and server is not
  a goal.
* Fabric API is a required dependency of the Fabric jar.
* Energy interop on Fabric uses Team Reborn Energy when installed and is otherwise off.
* Entity persistent data is saved under NeoForge's own key, `NeoForgeData`, so entities save the same
  NBT on both loaders (the save fixtures check it) and worlds move between them.
* Menu types made by `MenuHelper` are always opened through it, even without extra data, since Fabric
  refuses to open them with the vanilla call.
* Jars carry the loader as a suffix: `mcheli-revived-<version>-neoforge.jar` and
  `mcheli-revived-<version>-fabric.jar`. The nightly release publishes both.

## What remains

* **Unit tests on Fabric.** The unit tests that need the mod loaded (content, config, HUD and network
  goldens) run on NeoForge only. They exercise common code, so running them on Fabric would mostly
  prove the service wiring, which the game tests already cover; Fabric Loader's JUnit support could run
  them if wanted.
* **A production Fabric client** was not launched; the client was checked in the development
  environment and the production jar on a dedicated server.
