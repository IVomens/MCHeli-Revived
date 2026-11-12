package com.ivomens.mcheli.example;

import com.ivomens.mcheli.api.client.McheliClientApi;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The addon's client half, constructed only on a physical client: it adds {@link ExampleClockWidget} to MCHeli's
 * vehicle HUD through the client API.
 */
@Mod(value = ExampleAddon.MOD_ID, dist = Dist.CLIENT)
public final class ExampleAddonClient {

    /** The clock widget's id. */
    public static final ResourceLocation CLOCK = ResourceLocation.fromNamespaceAndPath(ExampleAddon.MOD_ID, "clock");

    public ExampleAddonClient(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class,
                event -> McheliClientApi.get().hudWidgets().register(CLOCK, new ExampleClockWidget()));
    }
}
