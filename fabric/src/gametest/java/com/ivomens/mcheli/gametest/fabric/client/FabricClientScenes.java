package com.ivomens.mcheli.gametest.fabric.client;

import com.ivomens.mcheli.fabric.client.event.McheliClientEvents;
import com.ivomens.mcheli.gametest.client.ClientSceneHooks;
import com.ivomens.mcheli.gametest.fabric.FabricGameTests;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Registers the game tests on the client and passes the client game events on to the client scenes. */
public final class FabricClientScenes implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FabricGameTests.registerTests();
        ServerTickEvents.END_SERVER_TICK.register(ClientSceneHooks::onServerTickEnd);
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientSceneHooks.onClientTickEnd());
        McheliClientEvents.FRAME_START.register(delta -> ClientSceneHooks.onFrameStart());
        McheliClientEvents.FRAME_END.register(delta -> ClientSceneHooks.onFrameEnd());
        McheliClientEvents.RENDER_LIVING_START.register(ClientSceneHooks::onRenderLiving);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> ClientSceneHooks.onEntitiesRendered(context.camera()));
    }
}
