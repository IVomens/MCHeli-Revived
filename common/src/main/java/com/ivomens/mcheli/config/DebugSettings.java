package com.ivomens.mcheli.config;

/** Developer switches. */
public record DebugSettings(boolean debugLog, boolean testMode, double textureAlpha, boolean debugBoundingBox,
                            int hitBoxDelayTick) {

    public static final int MAX_HIT_BOX_DELAY = 50;

    /** A bare {@code McHeliOutputDebugLog} line turns on extra logging. */
    public static final BoolOption DEBUG_LOG = BoolOption.of("McHeliOutputDebugLog", false);
    public static final BoolOption TEST_MODE = BoolOption.of("TestMode", false);
    public static final DoubleOption TEXTURE_ALPHA = DoubleOption.of("__TextureAlphaDebug", 1.0);
    /** Server side: lets players see hit boxes with F3 + B. */
    public static final BoolOption DEBUG_BOUNDING_BOX = BoolOption.of("EnableDebugBoundingBox", true);
    public static final IntOption HIT_BOX_DELAY_TICK = IntOption.of("HitBoxDelayTick", 0).range(0, MAX_HIT_BOX_DELAY);

    static DebugSettings from(ConfigValues v) {
        return new DebugSettings(v.get(DEBUG_LOG), v.get(TEST_MODE), v.get(TEXTURE_ALPHA), v.get(DEBUG_BOUNDING_BOX),
                v.get(HIT_BOX_DELAY_TICK));
    }
}
