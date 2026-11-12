package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.McheliMod;
import com.ivomens.mcheli.content.pack.AddonManager;
import com.ivomens.mcheli.setup.McheliCreativeTabs;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.wingman.McHeliWingman;
import com.ivomens.mcheli.wingman.handler.ChunkLoadHandler;
import com.ivomens.mcheli.wingman.handler.ProjectileChunkLoader;
import com.ivomens.mcheli.world.WorldEntityTypes;
import com.ivomens.mcheli.world.gunner.GunnerEntity;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** NeoForge entry point: runs the common init and wires the mod bus and game bus to the common hooks. */
@Mod(Ids.MOD_ID)
public final class McheliNeoForge {

    public McheliNeoForge(IEventBus modBus) {
        NeoForgeRegistryHelper.attach(modBus);
        McheliMod.init();
        NeoForgeFluids.init();

        modBus.addListener(McheliNeoForge::onRegister);
        modBus.addListener(NeoForgeCapabilities::register);
        modBus.addListener(McheliNeoForge::onEntityAttributes);
        modBus.addListener(McheliNeoForge::onLoadComplete);
        modBus.addListener(McheliNeoForge::onBuildCreativeTabs);
        modBus.addListener((AddPackFindersEvent e) -> e.addRepositorySource(AddonManager.packSource(e.getPackType())));
        modBus.addListener(McheliNeoForge::onRegisterTicketControllers);
        modBus.addListener((RegisterPayloadHandlersEvent e) -> NeoForgeNetworkHelper.onRegisterPayloads(e));

        NeoForgeCommonEvents.register(NeoForge.EVENT_BUS);
        NeoForgeAircraftEvents.register(NeoForge.EVENT_BUS);
        NeoForgePermissions.register(NeoForge.EVENT_BUS);
    }

    private static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            McheliMod.registerBlocks();
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            McheliMod.registerItems();
        }
    }

    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(WorldEntityTypes.GUNNER.get(), GunnerEntity.createAttributes().build());
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(McheliMod::onLoadComplete);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == McheliCreativeTabs.ITEMS.getTab()) {
            McHeliWingman.CREATIVE_TAB_ITEMS.forEach(i -> event.accept(i.get()));
            event.accept(NeoForgeFluids.MCH_FUEL_BUCKET.get());
        }
    }

    private static void onRegisterTicketControllers(RegisterTicketControllersEvent event) {
        event.register(NeoForgeChunkTickets.controller(ChunkLoadHandler.TICKETS));
        event.register(NeoForgeChunkTickets.controller(ProjectileChunkLoader.TICKETS));
    }
}
