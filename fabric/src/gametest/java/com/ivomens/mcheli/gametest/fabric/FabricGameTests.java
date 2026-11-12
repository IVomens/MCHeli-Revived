package com.ivomens.mcheli.gametest.fabric;

import com.ivomens.mcheli.fabric.event.McheliEvents;
import com.ivomens.mcheli.gametest.GameTestHooks;
import com.ivomens.mcheli.gametest.McheliGameTests;
import com.ivomens.mcheli.util.Ids;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Registers the shared game tests and passes the game events they listen to on to {@link GameTestHooks}.
 * <p>
 * The tests are built from the loaded content, so they are registered from the {@code server} entrypoint
 * (and, in {@code FabricClientScenes}, the {@code client} one), which Fabric runs after every mod's main
 * initializer. Fabric's own {@code fabric-gametest} entrypoint runs during the main initializers, in no
 * set order with MCHeli's. NeoForge likewise collects the tests once every mod is constructed.
 * <p>
 * The event listeners sit in a phase after the default one, so they run after the common hooks of the
 * same event whichever mod initialises first, as on NeoForge.
 */
public final class FabricGameTests implements ModInitializer, DedicatedServerModInitializer {

    /** Runs after the default phase of each event. */
    static final ResourceLocation PHASE = Ids.of("gametest");

    @GameTestGenerator
    public List<TestFunction> allTests() {
        return McheliGameTests.all();
    }

    /** Adds the tests to the game's test registry, which the test server and the test command read. */
    public static void registerTests() {
        GameTestRegistry.register(FabricGameTests.class);
    }

    @Override
    public void onInitializeServer() {
        registerTests();
    }

    @Override
    public void onInitialize() {
        ServerTickEvents.START_SERVER_TICK.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
        ServerTickEvents.START_SERVER_TICK.register(PHASE, server -> GameTestHooks.onServerTickStart());
        ServerTickEvents.END_SERVER_TICK.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
        ServerTickEvents.END_SERVER_TICK.register(PHASE, server -> GameTestHooks.onServerTickEnd());
        ServerTickEvents.START_WORLD_TICK.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
        ServerTickEvents.START_WORLD_TICK.register(PHASE, GameTestHooks::onLevelTickStart);
        ServerTickEvents.END_WORLD_TICK.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
        ServerTickEvents.END_WORLD_TICK.register(PHASE, GameTestHooks::onLevelTickEnd);
        // between the common join hooks and the "joined" hooks, where NeoForge's normal priority falls
        McheliEvents.ENTITY_JOIN.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
        McheliEvents.ENTITY_JOIN.addPhaseOrdering(PHASE, McheliEvents.LAST);
        McheliEvents.ENTITY_JOIN.register(PHASE, GameTestHooks::onEntityJoinLevel);
    }
}
