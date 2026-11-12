package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.setup.CommonEventHooks;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus listeners for both sides; each forwards to {@link CommonEventHooks}. */
public final class NeoForgeCommonEvents {

    private NeoForgeCommonEvents() {
    }

    static void register(IEventBus bus) {
        bus.addListener((ServerStartingEvent e) -> CommonEventHooks.onServerStarting(e.getServer()));
        bus.addListener((ServerStartedEvent e) -> CommonEventHooks.onServerStarted(e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> CommonEventHooks.onServerStopped(e.getServer()));
        bus.addListener((ServerTickEvent.Post e) -> CommonEventHooks.onServerTick(e.getServer()));
        bus.addListener((LevelTickEvent.Pre e) -> CommonEventHooks.onLevelTickStart(e.getLevel()));
        bus.addListener((LevelTickEvent.Post e) -> CommonEventHooks.onLevelTickEnd(e.getLevel()));
        bus.addListener((LevelEvent.Unload e) -> CommonEventHooks.onLevelUnload(e.getLevel()));
        bus.addListener((RegisterCommandsEvent e) -> CommonEventHooks.registerCommands(e.getDispatcher(), e.getBuildContext()));
        bus.addListener(NeoForgeCommonEvents::onDatapackSync);

        bus.addListener((EntityJoinLevelEvent e) -> CommonEventHooks.onEntityJoinLevel(e.getEntity(), e.getLevel()));
        bus.addListener(EventPriority.LOWEST, NeoForgeCommonEvents::onEntityJoinedLevel);
        bus.addListener((EntityLeaveLevelEvent e) -> CommonEventHooks.onEntityLeaveLevel(e.getEntity(), e.getLevel()));
        bus.addListener(NeoForgeCommonEvents::onLivingIncomingDamage);
        bus.addListener(NeoForgeCommonEvents::onEntityInteract);
        bus.addListener(EventPriority.HIGHEST, NeoForgeCommonEvents::onAttackEntity);
        bus.addListener(EventPriority.HIGHEST, NeoForgeCommonEvents::onLeftClickBlock);
        bus.addListener(EventPriority.HIGHEST, (PlayerTickEvent.Pre e) -> CommonEventHooks.onPlayerTickStart(e.getEntity()));
        bus.addListener((LivingDeathEvent e) -> CommonEventHooks.onLivingDeath(e.getEntity()));
        bus.addListener(EventPriority.LOWEST, NeoForgeCommonEvents::onLivingDeathConfirmed);
        bus.addListener((PlayerEvent.Clone e) -> CommonEventHooks.onPlayerClone(e.getOriginal()));
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> CommonEventHooks.onPlayerRespawn(e.getEntity()));
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> CommonEventHooks.onPlayerLogout(e.getEntity()));
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        // A null player means a /reload for everyone; single-player joins need no re-resolve.
        if (event.getPlayer() == null) {
            CommonEventHooks.onDataPacksReloaded(event.getPlayerList().getServer());
        }
    }

    private static void onEntityJoinedLevel(EntityJoinLevelEvent event) {
        if (!event.isCanceled()) {
            CommonEventHooks.onEntityJoinedLevel(event.getEntity(), event.getLevel());
        }
    }

    private static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        float damage = CommonEventHooks.onLivingIncomingDamage(event.getEntity(), event.getSource(), event.getAmount());
        if (Float.isNaN(damage)) {
            event.setCanceled(true);
        } else if (damage != event.getAmount()) {
            event.setAmount(damage);
        }
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        InteractionResult result = CommonEventHooks.onEntityInteract(event.getEntity(), event.getTarget(), event.getHand(),
                event.getItemStack());
        if (result != null) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    private static void onAttackEntity(AttackEntityEvent event) {
        if (CommonEventHooks.shouldBlockMountedAttack(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (CommonEventHooks.shouldBlockMountedAttack(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onLivingDeathConfirmed(LivingDeathEvent event) {
        CommonEventHooks.onLivingDeathConfirmed(event.getEntity());
    }
}
