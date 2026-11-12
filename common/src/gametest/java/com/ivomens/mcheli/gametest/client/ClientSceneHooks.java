package com.ivomens.mcheli.gametest.client;

import net.minecraft.client.Camera;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The game events the client scenes ({@link DevScene}, {@link ClientStress},
 * {@link VehicleRenderCheck}, {@link ClientBenchmarkCheck}) listen to. Each loader's game test layer calls these on the physical
 * client only; every scene does nothing unless its system property is set.
 */
public final class ClientSceneHooks {

    private ClientSceneHooks() {
    }

    /** End of a tick of the integrated server. */
    public static void onServerTickEnd(MinecraftServer server) {
        DevScene.onServerTick(server);
        ClientStress.onServerTick(server);
        VehicleRenderCheck.onServerTick(server);
    }

    /** End of a client tick. */
    public static void onClientTickEnd() {
        DevScene.onClientTick();
        ClientStress.onClientTick();
        VehicleRenderCheck.onClientTick();
        ClientBenchmarkCheck.onClientTick();
    }

    /** Before the game renderer draws a frame. */
    public static void onFrameStart() {
        ClientStress.onFrameStart();
    }

    /** After the game renderer drew a frame. */
    public static void onFrameEnd() {
        ClientStress.onFrameEnd();
        FlywheelProbe.onFrameEnd();
    }

    /** A living entity is about to be drawn. */
    public static void onRenderLiving(LivingEntity entity) {
        DevScene.onRenderLiving(entity);
    }

    /** The level's entities have been drawn. */
    public static void onEntitiesRendered(Camera camera) {
        DevScene.onEntitiesRendered(camera);
    }
}
