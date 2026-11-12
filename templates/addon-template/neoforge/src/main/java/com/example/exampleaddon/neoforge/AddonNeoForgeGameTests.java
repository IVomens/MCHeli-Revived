package com.example.exampleaddon.neoforge;

import com.example.exampleaddon.AddonCommon;
import com.example.exampleaddon.gametest.AddonGameTests;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.List;

/**
 * Registers the addon's game tests with NeoForge. NeoForge collects them only when the mod id is in the
 * {@code neoforge.enabledGameTestNamespaces} system property, which the development runs set; in a player's
 * game this class does nothing.
 */
@GameTestHolder(AddonCommon.MOD_ID)
public final class AddonNeoForgeGameTests {

    private AddonNeoForgeGameTests() {
    }

    @GameTestGenerator
    public static List<TestFunction> tests() {
        return AddonGameTests.all();
    }
}
