package com.ivomens.mcheli.vehicle.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

/** How many flip-book frames each exhaust flame texture prefix has, counted once when plane models load. */
public final class ExhaustFlameTextures {

    private static final Map<String, Integer> FRAMES = new HashMap<>();

    private ExhaustFlameTextures() {
    }

    /** Counts the frames of {@code prefix} unless they were counted before; planes load in parallel. */
    public static synchronized void countOnce(String prefix, ToIntFunction<String> count) {
        FRAMES.computeIfAbsent(prefix, count::applyAsInt);
    }

    /** The number of frames of {@code prefix}, 0 when they were never counted. */
    public static synchronized int frames(String prefix) {
        return FRAMES.getOrDefault(prefix, 0);
    }
}
