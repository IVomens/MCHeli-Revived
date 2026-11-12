package com.ivomens.mcheli.fabric;

import com.ivomens.mcheli.McheliMod;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.setup.McheliCreativeTabs;
import com.ivomens.mcheli.wingman.McHeliWingman;
import com.ivomens.mcheli.world.WorldEntityTypes;
import com.ivomens.mcheli.world.gunner.GunnerEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Fabric entry point: the counterpart of {@code McheliNeoForge}. Runs the common init, fills the
 * registries in NeoForge's order and wires the game events to the common hooks. The client and the
 * dedicated server finish in {@code McheliFabricClient} and {@link McheliFabricServer}.
 */
public final class McheliFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ((FabricPlatformHelper) Services.PLATFORM).trackRunningServer();
        McheliMod.init();
        FabricFluids.init();

        ((FabricRegistryHelper) Services.REGISTRY).registerAll(registry -> {
            if (registry.equals(Registries.BLOCK)) {
                McheliMod.registerBlocks();
            } else if (registry.equals(Registries.ITEM)) {
                McheliMod.registerItems();
            }
        });
        FabricDefaultAttributeRegistry.register(WorldEntityTypes.GUNNER.get(), GunnerEntity.createAttributes());
        FabricFluids.registerAttributes();
        ((FabricEnergyHelper) Services.ENERGY).registerBlockEnergy();
        ItemGroupEvents.MODIFY_ENTRIES_ALL.register(McheliFabric::addTabExtras);

        FabricCommonEvents.register();
    }

    /** The wingman items and the fuel bucket go into the items tab, after its own entries. */
    private static void addTabExtras(CreativeModeTab tab, FabricItemGroupEntries entries) {
        if (tab == McheliCreativeTabs.ITEMS.getTab()) {
            McHeliWingman.CREATIVE_TAB_ITEMS.forEach(item -> entries.accept(item.get()));
            entries.accept(FabricFluids.MCH_FUEL_BUCKET.get());
        }
    }
}
