package com.ivomens.mcheli.gametest.neoforge.client;

import com.ivomens.mcheli.gametest.client.ClientSceneHooks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Passes the client game events on to the client scenes, on the physical client only. */
@EventBusSubscriber(modid = "mcheli", value = Dist.CLIENT)
public final class NeoForgeClientScenes {

    private NeoForgeClientScenes() {
    }

    @SubscribeEvent
    public static void onServerTickEnd(ServerTickEvent.Post event) {
        ClientSceneHooks.onServerTickEnd(event.getServer());
    }

    @SubscribeEvent
    public static void onClientTickEnd(ClientTickEvent.Post event) {
        ClientSceneHooks.onClientTickEnd();
    }

    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        ClientSceneHooks.onFrameStart();
    }

    @SubscribeEvent
    public static void onFrameEnd(RenderFrameEvent.Post event) {
        ClientSceneHooks.onFrameEnd();
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        ClientSceneHooks.onRenderLiving(event.getEntity());
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            ClientSceneHooks.onEntitiesRendered(event.getCamera());
        }
    }
}
