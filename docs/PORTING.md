# MCHeli Revived — 1.12.2 → NeoForge 1.21.1 porting conventions

This document is the contract every ported file follows, so packages ported independently fit
together without renames.

Target: **Minecraft 1.21.1, NeoForge 21.1.x, Java 21, Mojang mappings + Parchment parameter names.**

## 1. Layout and naming

* Root package `com.norwood.mcheli` → **`com.ivomens.mcheli`**. Sub-packages and class names are
  kept **exactly** (e.g. `aircraft/AircraftEntity`, `weapon/BaseWeapon`). Mod id stays
  `mcheli` so existing addon packs, textures and models resolve unchanged.
* Mod-defined methods and fields keep their upstream names and parameter order. Only the
  Minecraft/Forge *types* in their signatures change, using the table in §3. This makes every
  cross-package call predictable.
* A method that **overrode a vanilla/Forge method** (it had `@Override` against a Minecraft
  class) takes the 1.21.1 vanilla name/signature (`onUpdate` → `tick`, `attackEntityFrom` →
  `hurt`, `processInitialInteract(EntityPlayer, EnumHand)` → `interact(Player, InteractionHand)`,
  ...). Overloads that only exist in MCHeli (e.g. `processInitialInteract(Player, boolean, InteractionHand)`) keep their name.
* `@SideOnly(Side.CLIENT)` → `@OnlyIn(Dist.CLIENT)` on client-only classes. Common code never
  references client classes directly; it asks the client through a small interface found by
  `Services.sided` (`LocalClient`, `VehicleClientHooks`, `EffectStyle`).

## 2. Shared foundation (already written)

| Need | Use |
|---|---|
| Legacy GL matrix/texture/colour calls in renderers | `client.render.LegacyGL` (see §6) |
| Packets | `networking.packet.PacketBase` + `ClientToServerPacket` / `ServerToClientPacket`, registered in `networking.Packets` (§5) |
| Entity types | `AircraftEntityTypes.HELI.get()` etc., declared by the feature that owns them |
| Old stack NBT (`stack.getTagCompound()`) | `util.StackData.getTag/setTag/update/hasTag` |

## 3. Type mapping (1.12 MCP → 1.21.1 Mojang)

| 1.12 | 1.21.1 |
|---|---|
| `World` / `WorldServer` / `WorldClient` | `Level` / `ServerLevel` / `ClientLevel` |
| `world.isRemote` | `level.isClientSide()` (field `level().isClientSide` on entities) |
| `EntityPlayer` / `EntityPlayerMP` / `EntityPlayerSP` | `Player` / `ServerPlayer` / `LocalPlayer` |
| `EntityLivingBase` / `EntityLiving` | `LivingEntity` / `Mob` |
| `EntityItem` / `EntityFallingBlock` / `EntityThrowable` | `ItemEntity` / `FallingBlockEntity` / `ThrowableProjectile` |
| `NBTTagCompound` / `NBTTagList` / `NBTBase` | `CompoundTag` / `ListTag` / `Tag` |
| `Vec3d` / `AxisAlignedBB` / `RayTraceResult` | `Vec3` / `AABB` / `HitResult` (`BlockHitResult`, `EntityHitResult`) |
| `MathHelper` | `Mth` |
| `EnumHand` / `EnumFacing` / `EnumActionResult` | `InteractionHand` / `Direction` / `InteractionResult` |
| `IBlockState` / `TileEntity` | `BlockState` / `BlockEntity` |
| `ResourceLocation(ns, path)` | `ResourceLocation.fromNamespaceAndPath(ns, path)`; `ResourceLocation.parse(str)` |
| `ITextComponent` / `TextComponentString` / `TextComponentTranslation` | `Component` / `Component.literal` / `Component.translatable` |
| `TextFormatting` | `ChatFormatting` |
| `DamageSource` (static fields) | `level.damageSources().xxx()`; custom: `DamageSource` built from a `ResourceKey<DamageType>` |
| `PotionEffect` / `Potion` / `MobEffects` | `MobEffectInstance` / `Holder<MobEffect>` / `MobEffects` |
| `SoundEvent` (registered) | `SoundEvent.createVariableRangeEvent(rl)` wrapped in `Holder.direct(...)` when no registry entry exists |
| `DataParameter<T>` / `EntityDataManager` | `EntityDataAccessor<T>` / `SynchedEntityData` (`defineSynchedData(SynchedEntityData.Builder)`) |
| `IEntityAdditionalSpawnData` | `IEntityWithComplexSpawn` (`writeSpawnData/readSpawnData(RegistryFriendlyByteBuf)`) |
| `ByteBuf` in packets | `RegistryFriendlyByteBuf` |
| `GuiScreen` / `GuiContainer` / `GuiButton` / `FontRenderer` | `Screen` / `AbstractContainerScreen` / `Button` / `Font` |
| `Container` / `Slot` | `AbstractContainerMenu` / `Slot` (with a `MenuType`) |
| `ScaledResolution` | `mc.getWindow().getGuiScaledWidth()/Height()` |
| `Tessellator`/`BufferBuilder` (2D) | `Tesselator.getInstance().begin(mode, format)` → `BufferUploader.drawWithShader(buf.buildOrThrow())` |
| `KeyBinding` / `Keyboard.isKeyDown` / `Mouse` | `KeyMapping` / `InputConstants.isKeyDown(window, key)` / `MouseHandler` |
| `ItemStack.EMPTY`, `stack.getCount()` | unchanged |
| `item.setMaxDamage/setMaxStackSize/setCreativeTab` | `Item.Properties#durability/stacksTo`; tabs via `BuildCreativeModeTabContentsEvent` |
| `Item.getIdFromItem` numeric ids | `BuiltInRegistries.ITEM.getKey(item)` / `getId(item)` |
| `ForgeRegistries.X` | `BuiltInRegistries.X` |
| `FMLCommonHandler.instance().getMinecraftServerInstance()` | `ServerLifecycleHooks.getCurrentServer()` |
| `MinecraftForge.EVENT_BUS` | `NeoForge.EVENT_BUS` |
| `TickEvent.ServerTickEvent` / `WorldTickEvent` / `ClientTickEvent` / `PlayerTickEvent` / `RenderTickEvent` | `ServerTickEvent.Pre/Post` / `LevelTickEvent.Pre/Post` / `ClientTickEvent.Pre/Post` / `PlayerTickEvent.Pre/Post` / `RenderFrameEvent.Pre/Post` |
| `RenderGameOverlayEvent` | `RenderGuiEvent.Pre/Post`, `RenderGuiLayerEvent.Pre/Post` (`VanillaGuiLayers`) |
| `RenderWorldLastEvent` | `RenderLevelStageEvent` (`Stage.AFTER_TRANSLUCENT_BLOCKS` etc.) |
| `EntityViewRenderEvent.CameraSetup/FOVModifier` | `ViewportEvent.ComputeCameraAngles` / `ViewportEvent.ComputeFov` |
| `LivingAttackEvent` / `LivingHurtEvent` | `LivingIncomingDamageEvent` / `LivingDamageEvent.Pre` |
| `EntityJoinWorldEvent` | `EntityJoinLevelEvent` |
| `ICommand` / `CommandBase` | Brigadier, registered in `RegisterCommandsEvent` |
| `ForgeChunkManager` | `TicketController` (registered in `RegisterTicketControllersEvent`) |

### Common Entity method renames

| 1.12 | 1.21.1 |
|---|---|
| `onUpdate()` | `tick()` |
| `entityInit()` | `defineSynchedData(SynchedEntityData.Builder)` |
| `readEntityFromNBT` / `writeEntityToNBT` | `readAdditionalSaveData` / `addAdditionalSaveData` |
| `attackEntityFrom(ds, amt)` | `hurt(ds, amt)` |
| `setDead()` / `isDead` | `discard()` (or `remove(RemovalReason)`) / `isRemoved()` (`!isAlive()` for living) |
| `posX/posY/posZ`, `prevPosX…`, `lastTickPosX…` | `getX()/getY()/getZ()`, `xo/yo/zo`, `xOld/yOld/zOld` |
| `motionX/Y/Z` | `getDeltaMovement()` / `setDeltaMovement(...)` |
| `rotationYaw/Pitch`, `prevRotationYaw/Pitch` | `getYRot()/setYRot()`, `getXRot()/setXRot()`, `yRotO/xRotO` |
| `setPosition`, `setLocationAndAngles`, `setPositionAndRotation` | `setPos`, `moveTo(x,y,z,yaw,pitch)`, `absMoveTo` |
| `setPositionAndRotationDirect(...)` (client interp) | `lerpTo(x,y,z,yRot,xRot,steps)` |
| `move(MoverType, x, y, z)` | `move(MoverType, Vec3)` |
| `getEntityBoundingBox()` / `setEntityBoundingBox` | `getBoundingBox()` / `setBoundingBox` |
| `getRidingEntity()` / `dismountRidingEntity()` / `startRiding` | `getVehicle()` / `stopRiding()` / `startRiding` |
| `updatePassenger(Entity)` | `positionRider(Entity, MoveFunction)` |
| `getMountedYOffset()` / `getYOffset()` | `getPassengerAttachmentPoint(...)` / `getMyRidingOffset` replaced by `getVehicleAttachmentPoint` |
| `canBeCollidedWith()` / `canBePushed()` | `isPickable()` / `isPushable()` |
| `getCollisionBoundingBox` | `canBeCollidedWith()` (boolean) |
| `processInitialInteract(EntityPlayer, EnumHand)` | `interact(Player, InteractionHand)` → `InteractionResult` |
| `getEntityId()` / `getUniqueID()` | `getId()` / `getUUID()` |
| `world` field | `level()` |
| `ticksExisted` | `tickCount` |
| `isInWater()`, `isBurning()` | `isInWater()`, `isOnFire()` |
| `setSize(w, h)` | sized in `EntityType`; dynamic: override `getDimensions(Pose)` + `refreshDimensions()` |
| `getRenderBoundingBox` / `ignoreFrustumCheck` | `getBoundingBoxForCulling()` (do **not** disable culling, see §8) |
| `isInRangeToRenderDist` | `shouldRenderAtSqrDistance(double)` |
| `getDistance(e)` / `getDistanceSq` | `distanceTo(e)` / `distanceToSqr` |
| `world.spawnEntity(e)` | `level.addFreshEntity(e)` |
| `world.getEntitiesWithinAABBExcludingEntity` | `level.getEntities(entity, aabb, predicate)` |
| `world.rayTraceBlocks(a, b, …)` | `level.clip(new ClipContext(a, b, Block/COLLIDER, Fluid.NONE/ANY, entity))` |
| `world.createExplosion / newExplosion` | `level.explode(source, x, y, z, power, fire, Level.ExplosionInteraction.X)` |
| `world.playSound(null, …)` | `level.playSound(null, x, y, z, soundEvent, SoundSource.X, vol, pitch)` |
| `world.spawnParticle(EnumParticleTypes.X, …)` | `level.addParticle(ParticleTypes.X, …)` (client) / `serverLevel.sendParticles` |

## 4. Entities

* Every entity has `public X(EntityType<? extends X> type, Level level)`; the matching
  `EntityType` lives in `ModEntities`. Keep a convenience `public X(Level level)` that delegates
  with `ModEntities.X.get()` where upstream code calls `new X(world)`.
* Abstract bases (`BaseEntity`, `AircraftEntity`, `BaseBulletEntity`, ...) take the same
  `(EntityType<? extends X>, Level)` constructor.
* Spawn data: implement `IEntityWithComplexSpawn`. Synced data: `EntityDataAccessor` created with
  `SynchedEntityData.defineId(X.class, EntityDataSerializers.Y)`.
* Seats/passengers keep the upstream seat-entity design (`SeatEntity` rides the aircraft and the
  player rides the seat).

## 5. Networking

```java
public class PacketFoo extends PacketBase implements ClientToServerPacket {
    public int entityId;                       // public fields as upstream
    public PacketFoo() {}                      // required no-arg constructor
    @Override public void write(RegistryFriendlyByteBuf buf) { buf.writeInt(entityId); }
    @Override public void read(RegistryFriendlyByteBuf buf)  { entityId = buf.readInt(); }
    @Override public void onReceive(ServerPlayer player) { ... }   // server thread
}
```
`ServerToClientPacket#onReceive()` runs on the client thread and may use `Minecraft.getInstance()`.
Sending helpers on `PacketBase`: `sendToServer()`, `sendToPlayer(ServerPlayer)`, `sendToClients()`,
`sendToDimension(ServerLevel)`, `sendToAllAround(level, x, y, z, r)`, `sendToTracking(entity)`,
`sendToTrackingAndSelf(entity)`. Prefer the *tracking* variants over dimension-wide broadcasts.
Every packet class is listed once in `networking/Packets.registerAll()`.

## 6. Rendering

All world-space rendering goes through vanilla's `EntityRenderer#render(entity, yaw, partialTicks,
PoseStack, MultiBufferSource, packedLight)`. Legacy GL-style code is kept almost line for line by
wrapping it in an `LegacyGL` frame:

```java
LegacyGL.begin(poseStack, buffers, packedLight);
try {
    LegacyGL.pushMatrix();
    LegacyGL.translate(x, y, z);
    LegacyGL.rotate(yaw, 0, 1, 0);           // glRotatef semantics
    LegacyGL.bindTexture(texture);
    LegacyGL.color(1, 1, 1, 1);
    model.renderPart("rotor");             // models emit through LegacyGL.buffer()
    LegacyGL.popMatrix();
} finally {
    LegacyGL.end();
}
```
`GlStateManager.enableBlend/disableBlend/disableLighting/disableTexture/color` map to the
`LegacyGL` methods of the same name. Models (`IModelCustom`, `WavefrontModel`,
`MetasequoiaModel`) are parsed once into baked vertex arrays and emitted per frame through the
current `LegacyGL` frame — no VAO/VBO/display lists.

2D (HUD, screens) uses `GuiGraphics`. Raw shapes use `Tesselator` with
`RenderSystem.setShader(GameRenderer::getPositionColorShader)`; text via `guiGraphics.drawString`.

## 7. Data and resources

* Vehicle/weapon/HUD definitions stay in `assets/mcheli/{helicopters,planes,tanks,vehicles,ships,weapons,hud,throwable}`
  and the external `mcheli_addons` folder, parsed by `content.source` into `content.registry`.
* Language files are JSON (`lang/en_us.json`). Item models stay under `models/item`.
* Recipes live in `data/mcheli/recipe/*.json` (1.21 format).
* Sounds are declared in `assets/mcheli/sounds.json`.

## 8. Performance rules

* **No unconditional chunk loading.** Vehicles, UAVs and projectiles never hold force-load tickets by
  default. Opt-in loading uses a `TicketController` with a small radius, is lease-based and is
  released as soon as it is not needed.
* Never call `level.getChunk(...)`/`getChunkSource().getChunk(..., true)` to *load* terrain from
  game logic; check `level.hasChunkAt(pos)` / `isLoaded` instead.
* Tracking ranges come from `ModEntities` and are capped by the server view distance.
* Do not disable frustum culling; give large entities a correct `getBoundingBoxForCulling()`.
* Avoid per-tick large-AABB entity scans; cache results for a few ticks or narrow the box.
* Avoid allocation in per-frame/per-tick hot paths; models are baked once.
* Prefer `sendToTracking` over global broadcasts.
