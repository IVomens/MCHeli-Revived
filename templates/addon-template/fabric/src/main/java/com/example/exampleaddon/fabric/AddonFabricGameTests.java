package com.example.exampleaddon.fabric;

import com.example.exampleaddon.gametest.AddonGameTests;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;

import java.util.List;

/**
 * Registers the addon's game tests with Fabric's game test API ({@code fabric-gametest} in fabric.mod.json). They
 * run only on a game test server ({@code ./gradlew :fabric:runGameTestServer}).
 */
public final class AddonFabricGameTests {

    @GameTestGenerator
    public List<TestFunction> tests() {
        return AddonGameTests.all();
    }
}
