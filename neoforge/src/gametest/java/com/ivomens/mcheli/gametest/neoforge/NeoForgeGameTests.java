package com.ivomens.mcheli.gametest.neoforge;

import com.ivomens.mcheli.gametest.GameTestHooks;
import com.ivomens.mcheli.gametest.McheliGameTests;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Registers the shared game tests with NeoForge's game test framework and passes the game events they
 * listen to on to {@link GameTestHooks}. The subscribers are added after the mod's own listeners, so
 * they run after the common hooks of the same event.
 */
@GameTestHolder("mcheli")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "mcheli")
public final class NeoForgeGameTests {

    @GameTestGenerator
    public static List<TestFunction> allTests() {
        return McheliGameTests.all();
    }

    @SubscribeEvent
    public static void onServerTickStart(ServerTickEvent.Pre event) {
        GameTestHooks.onServerTickStart();
    }

    @SubscribeEvent
    public static void onServerTickEnd(ServerTickEvent.Post event) {
        GameTestHooks.onServerTickEnd();
    }

    @SubscribeEvent
    public static void onLevelTickStart(LevelTickEvent.Pre event) {
        GameTestHooks.onLevelTickStart(event.getLevel());
    }

    @SubscribeEvent
    public static void onLevelTickEnd(LevelTickEvent.Post event) {
        GameTestHooks.onLevelTickEnd(event.getLevel());
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        GameTestHooks.onEntityJoinLevel(event.getEntity(), event.getLevel());
    }
}
