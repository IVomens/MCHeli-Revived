package com.ivomens.mcheli.fabric;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.content.def.vehicle.VehicleKinds;
import com.ivomens.mcheli.fabric.event.McheliEvents;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.setup.CommonEventHooks;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The game events of both sides, each forwarded to {@link CommonEventHooks}: the counterpart of
 * {@code NeoForgeCommonEvents} and {@code NeoForgeAircraftEvents}. Fabric API events are used where
 * one fires at the same point as NeoForge's; the others come from {@link McheliEvents}, which mixins
 * fire where NeoForge's patches do.
 */
final class FabricCommonEvents {

    private FabricCommonEvents() {
    }

    static void register() {
        registerServerEvents();
        registerEntityEvents();
        registerPlayerEvents();
    }

    private static void registerServerEvents() {
        ServerLifecycleEvents.SERVER_STARTING.register(CommonEventHooks::onServerStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(CommonEventHooks::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(CommonEventHooks::onServerStopped);
        ServerTickEvents.END_SERVER_TICK.register(CommonEventHooks::onServerTick);
        ServerTickEvents.START_WORLD_TICK.register(CommonEventHooks::onLevelTickStart);
        ServerTickEvents.END_WORLD_TICK.register(CommonEventHooks::onLevelTickEnd);
        ServerWorldEvents.UNLOAD.register((server, level) -> CommonEventHooks.onLevelUnload(level));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                CommonEventHooks.registerCommands(dispatcher, context));
        // NeoForge: OnDatapackSync for everyone, which only a /reload sends.
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) {
                CommonEventHooks.onDataPacksReloaded(server);
            }
        });
    }

    private static void registerEntityEvents() {
        McheliEvents.ENTITY_JOIN.register(CommonEventHooks::onEntityJoinLevel);
        McheliEvents.ENTITY_JOIN.register(FabricCommonEvents::markAircraftKind);
        McheliEvents.ENTITY_JOIN.register(McheliEvents.LAST, CommonEventHooks::onEntityJoinedLevel);
        McheliEvents.ENTITY_LEAVE.register(CommonEventHooks::onEntityLeaveLevel);
        McheliEvents.LIVING_INCOMING_DAMAGE.register(CommonEventHooks::onLivingIncomingDamage);
        McheliEvents.LIVING_DEATH.register((entity, source) -> CommonEventHooks.onLivingDeath(entity));
        McheliEvents.LIVING_DEATH.register(McheliEvents.LAST, (entity, source) -> CommonEventHooks.onLivingDeathConfirmed(entity));
    }

    private static void registerPlayerEvents() {
        // Fabric also fires this for a click on a precise spot of the entity (NeoForge's
        // EntityInteractSpecific); only the plain interaction, without a hit result, is NeoForge's EntityInteract.
        UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (hit != null) {
                return InteractionResult.PASS;
            }
            InteractionResult result = CommonEventHooks.onEntityInteract(player, target, hand, player.getItemInHand(hand));
            return result != null ? result : InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.addPhaseOrdering(McheliEvents.FIRST, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.register(McheliEvents.FIRST, (player, level, hand, target, hit) -> blockWhileMounted(player));
        AttackBlockCallback.EVENT.addPhaseOrdering(McheliEvents.FIRST, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(McheliEvents.FIRST, (player, level, hand, pos, direction) -> blockWhileMounted(player));

        McheliEvents.PLAYER_TICK_START.register(McheliEvents.FIRST, CommonEventHooks::onPlayerTickStart);
        ServerPlayerEvents.COPY_FROM.register((original, copy, alive) -> CommonEventHooks.onPlayerClone(original));
        ServerPlayerEvents.AFTER_RESPAWN.register((original, respawned, alive) -> CommonEventHooks.onPlayerRespawn(respawned));
        ServerPlayerEvents.LEAVE.register(CommonEventHooks::onPlayerLogout);
    }

    private static InteractionResult blockWhileMounted(Player player) {
        return CommonEventHooks.shouldBlockMountedAttack(player) ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    /** Aircraft carry their kind ("Plane", "Tank", ...) in the persistent data, as they always have. */
    private static void markAircraftKind(Entity entity, Level level) {
        if (entity instanceof AircraftEntity aircraft) {
            Services.ENTITY_DATA.getPersistentData(aircraft).putString("EntityType", VehicleKinds.legacyEntityType(aircraft.kind()));
        }
    }
}
