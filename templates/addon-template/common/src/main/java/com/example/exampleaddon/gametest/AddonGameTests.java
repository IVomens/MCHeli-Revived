package com.example.exampleaddon.gametest;

import com.example.exampleaddon.AddonCommon;
import com.example.exampleaddon.AddonEvents;
import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.content.WeaponInfo;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;

import java.util.List;

/**
 * The addon's game tests, built here and registered by each loader ({@code AddonNeoForgeGameTests},
 * {@code AddonFabricGameTests}). They run on a game test server with MCHeli loaded next to the addon:
 * {@code ./gradlew :neoforge:runGameTestServer} or {@code ./gradlew :fabric:runGameTestServer}.
 * <p>
 * Every test uses the empty 16x16x16 structure in {@code data/<mod id>/structure/empty.nbt}. Add your own
 * tests to {@link #all()}.
 */
public final class AddonGameTests {

    private static final String BATCH = AddonCommon.MOD_ID;
    private static final String EMPTY = AddonCommon.MOD_ID + ":empty";

    private AddonGameTests() {
    }

    /** Every test of the addon. */
    public static List<TestFunction> all() {
        return List.of(
                new TestFunction(BATCH, AddonCommon.MOD_ID + ".contentpackloads", EMPTY, 20, 0, true,
                        AddonGameTests::contentPackLoads));
    }

    /** MCHeli found the pack in the addon's jar and loaded its weapon under the addon's pack id. */
    private static void contentPackLoads(GameTestHelper helper) {
        WeaponInfo cannon = McheliApi.get().content().weapon(AddonEvents.CANNON)
                .orElseThrow(() -> new GameTestAssertException(AddonEvents.CANNON + " did not load"));
        if (!AddonCommon.MOD_ID.equals(cannon.packId())) {
            throw new GameTestAssertException(AddonEvents.CANNON + " came from pack " + cannon.packId());
        }
        helper.succeed();
    }
}
