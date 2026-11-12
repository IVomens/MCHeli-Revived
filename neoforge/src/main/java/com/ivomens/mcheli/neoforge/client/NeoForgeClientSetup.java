package com.ivomens.mcheli.neoforge.client;

import com.ivomens.mcheli.client.render.ShaderRegistry;
import com.ivomens.mcheli.setup.client.HudLayers;
import com.ivomens.mcheli.neoforge.NeoForgeFluids;
import com.ivomens.mcheli.setup.client.ClientEventHooks;
import com.ivomens.mcheli.setup.client.McheliClient;
import com.ivomens.mcheli.setup.client.MenuScreenBindings;
import com.ivomens.mcheli.setup.client.RenderRegistry;
import com.ivomens.mcheli.setup.client.item.ItemColorRegistration;
import com.ivomens.mcheli.setup.client.item.ItemModelRenderers;
import com.ivomens.mcheli.setup.client.item.LegacyModelLoader;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.NotNull;

/** Client-only wiring: mod-bus registration events plus the client game-bus listeners. */
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    /** Called by the client entry point, on the physical client only. */
    public static void register(IEventBus modBus) {
        McheliClient.init();
        modBus.addListener(NeoForgeClientSetup::onRegisterRenderers);
        modBus.addListener(NeoForgeClientSetup::onRegisterItemRenderers);
        modBus.addListener(NeoForgeClientSetup::onRegisterClientExtensions);
        modBus.addListener((RegisterMenuScreensEvent e) -> MenuScreenBindings.register(e::register));
        modBus.addListener((RegisterShadersEvent e) -> ShaderRegistry.init());
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(McheliClient::onClientSetup));
        modBus.addListener(NeoForgeClientSetup::onRegisterGuiLayers);
        modBus.addListener((RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener(ClientEventHooks.soundReloadListener()));
        modBus.addListener((RegisterKeyMappingsEvent e) -> ClientEventHooks.keyMappings().forEach(e::register));
        modBus.addListener((RegisterColorHandlersEvent.Item e) -> ItemColorRegistration.register(e.getItemColors()));
        modBus.addListener((ModelEvent.RegisterAdditional e) -> ItemModelRenderers.onRegisterAdditionalModels());
        modBus.addListener((ModelEvent.ModifyBakingResult e) -> ItemModelRenderers.onModelsBaked(e.getModels(),
                NeoForgeItemBakedModel::new));
        modBus.addListener((AddPackFindersEvent e) -> {
            if (e.getPackType() == PackType.CLIENT_RESOURCES) {
                e.addRepositorySource(LegacyModelLoader.packSource());
            }
        });

        NeoForgeClientEvents.register(NeoForge.EVENT_BUS);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        RenderRegistry.RendererRegistrar registrar = new RenderRegistry.RendererRegistrar() {
            @Override
            public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                event.registerEntityRenderer(type, provider);
            }

            @Override
            public <T extends BlockEntity> void registerBlockEntityRenderer(BlockEntityType<? extends T> type,
                                                                            BlockEntityRendererProvider<T> provider) {
                event.registerBlockEntityRenderer(type, provider);
            }
        };
        RenderRegistry.registerEntityRenderers(registrar);
        RenderRegistry.registerBlockEntityRenderers(registrar);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, HudLayers.HUD, HudLayers::renderHud);
        event.registerAbove(VanillaGuiLayers.CHAT, HudLayers.HUD_TOP, HudLayers::renderTop);
    }

    private static void onRegisterItemRenderers(RegisterClientExtensionsEvent event) {
        RenderRegistry.registerItemRenderers((renderer, items) -> event.registerItem(new IClientItemExtensions() {
            @Override
            public @NotNull BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer.get();
            }
        }, items.toArray(new Item[0])));
    }

    /** Fuel renders with the water textures, tinted brown. */
    private static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public @NotNull ResourceLocation getStillTexture() {
                return ResourceLocation.withDefaultNamespace("block/water_still");
            }

            @Override
            public @NotNull ResourceLocation getFlowingTexture() {
                return ResourceLocation.withDefaultNamespace("block/water_flow");
            }

            @Override
            public int getTintColor() {
                return NeoForgeFluids.FUEL_COLOR;
            }
        }, NeoForgeFluids.MCH_FUEL_TYPE.get());
    }
}
