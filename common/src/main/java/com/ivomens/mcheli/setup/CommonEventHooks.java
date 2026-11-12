package com.ivomens.mcheli.setup;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.admin.AdminCommands;
import com.ivomens.mcheli.bench.BenchmarkCommand;
import com.ivomens.mcheli.combat.explosion.ExplosionEffectSender;
import com.ivomens.mcheli.combat.projectile.ProjectileLifetime;
import com.ivomens.mcheli.combat.weapon.GPSPosition;
import com.ivomens.mcheli.command.McheliCommand;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.content.recipe.ContentRecipes;
import com.ivomens.mcheli.inspector.InspectorCommand;
import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.multiplay.OperatorRequests;
import com.ivomens.mcheli.multiplay.net.ServerSettingsSync;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.radar.RadarContactSync;
import com.ivomens.mcheli.vehicle.sensor.ThreatBoard;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import com.ivomens.mcheli.vehicle.uav.UAVTracker;
import com.ivomens.mcheli.world.uavstation.UavPairingDeviceItem;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.wingman.McHeliWingman;
import com.ivomens.mcheli.world.chain.ChainItem;
import com.ivomens.mcheli.world.uavstation.UavStationEntity;
import com.ivomens.mcheli.world.uavstation.UavTabletItem;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Game-bus hooks.
 * <p>
 * Damage handling only ever touches damage aimed at an entity that is actually seated in an MCHeli
 * aircraft (or flying one through a UAV station), never damage that bypasses invulnerability
 * ({@code /kill}, the void) and never an entity that is already dying. Death events are never
 * cancelled. This keeps mods that implement their own downed/revive state (e.g. PlayerRevive) in
 * full control of death: a player who was downed and respawned is no longer seated, so nothing here
 * applies to them, and no per-player state survives a respawn or logout.
 * <p>
 * The loader glue calls these methods from its own events; each one notes the event and phase it
 * stands for. Handlers of the same event run in the order they are called here.
 */
public final class CommonEventHooks {

    /** Aircraft damage taken for every hit on one of its crew, relative to the hit. */
    private static final float CREW_HIT_AIRCRAFT_DAMAGE_FACTOR = 2.0F;
    /** Returned by {@link #onLivingIncomingDamage} when the hit must be cancelled; test with {@code Float.isNaN}. */
    public static final float CANCEL_DAMAGE = Float.NaN;

    private CommonEventHooks() {
    }

    // Server lifecycle and ticks

    public static void onServerStarting(MinecraftServer server) {
        LiveryManager.onServerStarting(server);
    }

    public static void onServerStarted(MinecraftServer server) {
        ContentRecipes.resolve(server.getRecipeManager());
    }

    public static void onServerStopped(MinecraftServer server) {
        LiveryManager.onServerStopped(server);
        McHeliWingman.onServerStopped(server);
        RadarContactSync.of(server).close();
        ExplosionEffectSender.of(server).clear();
    }

    /** End of a server tick. */
    public static void onServerTick(MinecraftServer server) {
        McHeliWingman.onServerTick(server);
        LiveryManager.onServerTick(server);
        RadarContactSync.of(server).onServerTick(server);
        ThreatBoard.of(server).serverTick();
        ExplosionEffectSender.of(server).flush();
    }

    /** Start of a level tick, on both sides. */
    public static void onLevelTickStart(Level level) {
        McHeliWingman.onLevelTickStart(level);
    }

    /** End of a level tick, on both sides. */
    public static void onLevelTickEnd(Level level) {
        McHeliWingman.onLevelTickEnd(level);
        if (level instanceof ServerLevel serverLevel) {
            ProjectileLifetime.sweep(serverLevel);
        }
    }

    public static void onLevelUnload(LevelAccessor level) {
        McHeliWingman.onLevelUnload(level);
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        McheliCommand.register(dispatcher, context);
        com.ivomens.mcheli.content.command.ValidateCommand.register(dispatcher);
        McHeliWingman.registerCommands(dispatcher);
        BenchmarkCommand.register(dispatcher);
        AdminCommands.register(dispatcher);
        InspectorCommand.register(dispatcher);
    }

    /** Data packs were reloaded with {@code /reload} (not sent to one joining player). */
    public static void onDataPacksReloaded(MinecraftServer server) {
        ContentRecipes.resolve(server.getRecipeManager());
    }

    // Entities

    /** An entity is about to join a level, on both sides. */
    public static void onEntityJoinLevel(Entity entity, Level level) {

        if (entity instanceof AircraftEntity aircraft) {
            if (!aircraft.level().isClientSide && !aircraft.seats().areCreated()) {
                aircraft.seats().create(UUID.randomUUID().toString());
            }
            return;
        }

        if (entity instanceof Player player) {
            resetInvalidRotation(player);
            if (player instanceof ServerPlayer serverPlayer) {
                ServerSettingsSync.sendTo(serverPlayer);
            }
            return;
        }

        // The mob render distance is a global view scale, applied whenever a mob shows up on the client.
        if (entity instanceof LivingEntity && level.isClientSide()) {
            if (Services.PLATFORM.isClient()) {
                Entity.setViewScale(McheliConfig.render().mobRenderDistanceWeight());
            }
        }
    }

    /** Runs after every other join handler, and only when no mod cancelled the join. */
    public static void onEntityJoinedLevel(Entity entity, Level level) {
        McHeliWingman.onEntityJoined(entity, level);
    }

    private static void resetInvalidRotation(Entity entity) {
        if (!Float.isFinite(entity.getXRot()) || !Float.isFinite(entity.xRotO)) {
            ModLogger.log(entity, "Player joined with an invalid pitch (" + entity.getXRot() + "), reset to 0");
            entity.setXRot(0.0F);
            entity.xRotO = 0.0F;
        }
        if (!Float.isFinite(entity.getYRot()) || !Float.isFinite(entity.yRotO)) {
            ModLogger.log(entity, "Player joined with an invalid yaw (" + entity.getYRot() + "), reset to 0");
            entity.setYRot(0.0F);
            entity.yRotO = 0.0F;
        }
    }

    /**
     * Incoming damage to a seated crew member, adjusted before armor is applied:
     * <ul>
     *   <li>crew members and the aircraft cannot hurt their own crew;</li>
     *   <li>a fully armored aircraft ({@code damageFactor <= 0}) also blocks attacker-less damage
     *       (suffocation, fire, falling blocks...);</li>
     *   <li>every other hit is also applied, doubled, to the aircraft and scaled by
     *       {@code damageFactor} for the crew member.</li>
     * </ul>
     *
     * @return the damage to apply, or {@link #CANCEL_DAMAGE} to cancel the hit
     */
    public static float onLivingIncomingDamage(LivingEntity entity, DamageSource source, float damage) {
        if (entity.level().isClientSide || entity.isDeadOrDying() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return damage;
        }

        AircraftEntity aircraft = getRiddenAircraft(entity);
        if (aircraft == null || aircraft.isDestroyed() || aircraft.spec() == null) {
            return damage;
        }

        Entity attacker = source.getEntity();
        float factor = aircraft.spec().file().armor().damageFactor();

        boolean noOtherAttacker = attacker == null || attacker == entity;
        boolean fromOwnCrew = attacker != null
                && (aircraft.seats().isAboard(attacker) || getRiddenAircraft(attacker) == aircraft);
        boolean fullyArmored = !(factor > 0.0F);

        if (fromOwnCrew || (fullyArmored && noOtherAttacker)) {
            return CANCEL_DAMAGE;
        }
        aircraft.hurt(source, damage * CREW_HIT_AIRCRAFT_DAMAGE_FACTOR);
        return damage * Math.max(factor, 0.0F);
    }

    /**
     * The aircraft the entity is crewing: ridden directly, through a seat, or remotely through a UAV
     * station. Only real riding relations count, so a player that died and respawned (or was revived
     * by another mod) is never matched through stale seat data.
     */
    @Nullable
    private static AircraftEntity getRiddenAircraft(@Nullable Entity entity) {
        if (entity == null || entity.isRemoved()) return null;
        Entity ridden = entity.getVehicle();
        if (ridden == null) {
            return null;
        }
        AircraftEntity ac = null;
        if (ridden instanceof AircraftEntity a) {
            ac = a;
        } else if (ridden instanceof SeatEntity seat) {
            ac = seat.getParent();
        } else if (ridden instanceof IUavStation station) {
            ac = station.getControlled();
        }
        if (ac != null && ac.isRemoved()) {
            return null;
        }
        return ac;
    }

    /**
     * Item interactions with entities that are not living entities, so {@code Item#interactLivingEntity}
     * never fires.
     *
     * @return the result to cancel the interaction with, or null to let it go on
     */
    @Nullable
    public static InteractionResult onEntityInteract(Player player, Entity target, InteractionHand hand, ItemStack stack) {
        if (hand != InteractionHand.MAIN_HAND) {
            return null;
        }
        return switch (stack.getItem()) {
            case ChainItem ignored -> {
                ChainItem.interactEntity(stack, target, player, player.level());
                yield InteractionResult.SUCCESS;
            }
            case AircraftItem aircraftItem -> {
                aircraftItem.rideEntity(stack, target, player);
                yield null;
            }
            case UavPairingDeviceItem ignored -> onUavPairingInteract(player, target, stack);
            case UavTabletItem ignored -> onUavTabletInteract(player, target, stack);
            default -> null;
        };
    }

    /** Crew members cannot attack entities or start breaking blocks; true cancels the action. Runs first. */
    public static boolean shouldBlockMountedAttack(Player player) {
        return getRiddenAircraft(player) != null;
    }

    /** Start of a player tick, on both sides. Runs first. */
    public static void onPlayerTickStart(Player player) {
        if (player.level().isClientSide) {
            Entity riding = player.getVehicle();
            if ((riding instanceof AircraftEntity || riding instanceof SeatEntity) && player.swinging) {
                player.swinging = false;
                player.swingTime = 0;
                player.attackAnim = 0.0F;
            }
        }
    }

    public static void onLivingDeath(LivingEntity entity) {
        McHeliWingman.onLivingDeath(entity);
    }

    /**
     * A crew member that really died (the death was not cancelled by a revive mod) leaves the seat
     * right away instead of riding it as a corpse. Runs after every other death handler.
     */
    public static void onLivingDeathConfirmed(LivingEntity entity) {
        if (!entity.level().isClientSide && entity.getVehicle() instanceof SeatEntity) {
            entity.stopRiding();
        }
    }

    /** A player was copied after death or returning from the End; {@code original} is the old entity. */
    public static void onPlayerClone(Player original) {
        clearPlayerState(original);
    }

    public static void onPlayerRespawn(Player player) {
        clearPlayerState(player);
    }

    public static void onPlayerLogout(Player player) {
        McHeliWingman.onPlayerLogout(player);
        clearPlayerState(player);
        LiveryManager.onPlayerLogout(player);
        if (player.getServer() != null) {
            ExplosionEffectSender.of(player.getServer()).forget(player);
        }
    }

    /** Drops every bit of MCHeli state keyed to this player entity. */
    private static void clearPlayerState(Player player) {
        if (player == null || player.level().isClientSide) return;
        GPSPosition.currentGPSPositions.remove(player.getId());
        if (player.getServer() != null) {
            OperatorRequests.of(player.getServer()).forget(player);
        }
    }

    /** UAVs remember where they were unloaded so a station can find them again. */
    public static void onEntityLeaveLevel(Entity entity, Level level) {
        McHeliWingman.onEntityLeave(entity, level);
        if (!level.isClientSide && entity instanceof AircraftEntity ac && ac.isUAV()) {
            Entity.RemovalReason reason = ac.getRemovalReason();
            if (reason != null && !reason.shouldDestroy()) {
                UAVTracker.saveUAVPos(level, ac);
            }
        }
    }

    /** Right-clicking a UAV stores its UUID on the pairing device; right-clicking a station pairs that UAV. */
    @Nullable
    private static InteractionResult onUavPairingInteract(Player player, Entity target, ItemStack stack) {
        boolean isUav = target instanceof AircraftEntity ac && ac.isUAV();
        boolean isStation = target instanceof UavStationEntity;
        if (!isUav && !isStation) {
            return null;
        }

        // The result suppresses the default interaction (mount UAV / open station GUI) while the device is held.
        if (!player.level().isClientSide) {
            pairUav(player, target, stack, isUav);
        }
        return InteractionResult.SUCCESS;
    }

    private static void pairUav(Player player, Entity target, ItemStack stack, boolean isUav) {
        if (isUav) {
            UavPairingDeviceItem.setBoundUav(stack, target.getUUID());
            player.sendSystemMessage(Component.literal("Bound UAV to pairing device."));
            return;
        }

        UavStationEntity station = (UavStationEntity) target;
        UUID boundId = UavPairingDeviceItem.getBoundUav(stack);
        if (boundId == null) {
            player.sendSystemMessage(Component.literal("No UAV bound. Right-click a UAV first."));
            return;
        }

        // Resolve the UAV so the single-station constraint can be enforced and its owning station recorded.
        AircraftEntity uav = UAVTracker.locateUAV(player.level(), boundId);
        if (uav == null) {
            player.sendSystemMessage(Component.literal("Bound UAV could not be located."));
            return;
        }

        UUID currentStation = uav.uavLink().pairedStation();
        if (currentStation != null && !currentStation.equals(station.getUUID())) {
            player.sendSystemMessage(Component.literal("That UAV is already paired to another station."));
            return;
        }

        if (!station.pairUav(boundId)) {
            player.sendSystemMessage(Component.literal("Station is full (max " + station.getMaxPairedUavs() + " UAVs)."));
            return;
        }

        uav.uavLink().setPairedStation(station.getUUID());
        UavPairingDeviceItem.setBoundUav(stack, null);
        player.sendSystemMessage(Component.literal("Paired UAV to station."));
    }

    @Nullable
    private static InteractionResult onUavTabletInteract(Player player, Entity target, ItemStack stack) {
        if (!(target instanceof AircraftEntity uav) || !uav.isUAV()) {
            return null;
        }
        if (!player.level().isClientSide) {
            bindUavToTablet(player, uav, stack);
        }
        return InteractionResult.SUCCESS;
    }

    private static void bindUavToTablet(Player player, AircraftEntity uav, ItemStack stack) {
        if (uav.isDestroyed()) {
            player.sendSystemMessage(Component.literal("Cannot bind a destroyed UAV."));
            return;
        }
        if (!UavTabletItem.allowsNormalUav() && !uav.uavLink().isSmall()) {
            player.sendSystemMessage(Component.literal("This tablet can only bind small UAVs."));
            return;
        }
        boolean added = UavTabletItem.addPairedUav(stack, uav.getUUID());
        player.sendSystemMessage(Component.literal(added ? "Bound UAV to tablet." : "UAV is already bound."));
    }
}
