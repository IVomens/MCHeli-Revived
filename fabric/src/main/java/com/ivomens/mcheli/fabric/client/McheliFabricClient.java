package com.ivomens.mcheli.fabric.client;

import com.ivomens.mcheli.McheliMod;
import com.ivomens.mcheli.client.render.ShaderRegistry;
import com.ivomens.mcheli.fabric.FabricFluids;
import com.ivomens.mcheli.fabric.FabricNetworkHelper;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.setup.client.ClientEventHooks;
import com.ivomens.mcheli.setup.client.McheliClient;
import com.ivomens.mcheli.setup.client.MenuScreenBindings;
import com.ivomens.mcheli.setup.client.RenderRegistry;
import com.ivomens.mcheli.setup.client.item.ItemModelRenderers;
import com.ivomens.mcheli.util.Ids;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Client entry point: the counterpart of {@code NeoForgeClientSetup}. Registers the renderers, screens,
 * keys, models and the fuel's look, and the client events; client setup and load completion run once
 * the client has started, on the render thread.
 */
public final class McheliFabricClient implements ClientModInitializer {

    private static final ResourceLocation SOUND_RELOAD = Ids.of("sounds");

    @Override
    public void onInitializeClient() {
        McheliClient.init();
        ((FabricNetworkHelper) Services.NETWORK).registerClientReceivers();
        registerRenderers();
        MenuScreenBindings.register(MenuScreens::register);
        ClientEventHooks.keyMappings().forEach(KeyBindingHelper::registerKeyBinding);
        CoreShaderRegistrationCallback.EVENT.register(context -> ShaderRegistry.init());
        registerSoundReload(ClientEventHooks.soundReloadListener());
        ModelLoadingPlugin.register(context -> {
            ItemModelRenderers.onRegisterAdditionalModels();
            context.modifyModelAfterBake().register((model, bake) -> bake.topLevelId() == null ? model
                    : ItemModelRenderers.wrapInventoryModel(bake.topLevelId(), model, FabricItemBakedModel::new));
        });
        registerFuelLook();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            McheliClient.onClientSetup();
            McheliMod.onLoadComplete();
        });
        FabricClientEvents.register();
    }

    private static void registerRenderers() {
        RenderRegistry.RendererRegistrar registrar = new RenderRegistry.RendererRegistrar() {
            @Override
            public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                EntityRendererRegistry.register(type, provider);
            }

            @Override
            public <T extends BlockEntity> void registerBlockEntityRenderer(BlockEntityType<? extends T> type,
                                                                            BlockEntityRendererProvider<T> provider) {
                registerBlockEntity(type, provider);
            }
        };
        RenderRegistry.registerEntityRenderers(registrar);
        RenderRegistry.registerBlockEntityRenderers(registrar);
        RenderRegistry.registerItemRenderers((renderer, items) -> items.forEach(item ->
                BuiltinItemRendererRegistry.INSTANCE.register(item, (stack, context, poseStack, buffers, light, overlay) ->
                        renderer.get().renderByItem(stack, context, poseStack, buffers, light, overlay))));
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void registerBlockEntity(BlockEntityType<? extends T> type,
                                                                    BlockEntityRendererProvider<T> provider) {
        BlockEntityRenderers.register((BlockEntityType<T>) type, provider);
    }

    private static void registerSoundReload(ResourceManagerReloadListener listener) {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return SOUND_RELOAD;
            }

            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                listener.onResourceManagerReload(resourceManager);
            }
        });
    }

    /** Fuel renders with the water textures, tinted brown. */
    private static void registerFuelLook() {
        FluidRenderHandlerRegistry.INSTANCE.register(FabricFluids.MCH_FUEL.get(), FabricFluids.MCH_FUEL_FLOWING.get(),
                new SimpleFluidRenderHandler(ResourceLocation.withDefaultNamespace("block/water_still"),
                        ResourceLocation.withDefaultNamespace("block/water_flow"), FabricFluids.FUEL_COLOR));
    }
}
