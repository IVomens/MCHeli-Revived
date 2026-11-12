package com.ivomens.mcheli.gametest.wingman;

import com.ivomens.mcheli.gametest.golden.TestTerrain;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * One wingman flight: set the scene up on the test course, then let the wingman code fly for a
 * number of ticks while the scenario writes down what happens.
 */
interface WingmanScenario {

    String name();

    int ticks();

    /** Spawns the aircraft and registers the wingmen. */
    void setUp(ServerLevel level, TestTerrain terrain);

    /** Called after every level tick; adds lines to the recording. */
    void afterTick(int tick, List<String> lines);

    /** Removes the wingmen, markers and aircraft. */
    void tearDown(ServerLevel level);
}
