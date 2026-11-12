package com.ivomens.mcheli.config;

/** Content extraction, model loading and entity tracking at start-up. */
public record LoadingSettings(boolean extractDefaultContentPack, boolean removeClientTrackingRestrictions,
                              boolean asyncOnDemandModelLoading, int onDemandModelLifetimeSeconds,
                              int onDemandModelLoaderThreads) {

    /** Set once the bundled content pack has been copied out, so it is not copied again. */
    public static final BoolOption EXTRACT_DEFAULT_CONTENT_PACK = BoolOption.of("ExtractDefaultContentPack", false);
    public static final BoolOption REMOVE_CLIENT_TRACKING_RESTRICTIONS = BoolOption
            .of("ExperimentalRemoveClientTrackingRestrictions", true)
            .describe("Experimental: remove client tracking restrictions - keeps MCHeli entities (e.g. UAVs) tracked,"
                    + " loaded and rendered far beyond the normal view distance by enabling the long-distance ASM"
                    + " patches. Read at startup by the coremod; CHANGING THIS REQUIRES A GAME RESTART.");
    public static final BoolOption ASYNC_ON_DEMAND_MODEL_LOADING = BoolOption
            .of("ExperimentalAsyncOnDemandModelLoading", true)
            .describe("Experimental: parse vehicle models + upload their VBOs on demand (when first rendered) on a"
                    + " background thread, instead of parsing/uploading every model at startup. Rendering is skipped"
                    + " for a model until its buffer is ready (no main-thread stall). Models unused for"
                    + " ExperimentalOnDemandModelLifetimeSeconds are deleted to reclaim VRAM and reloaded on"
                    + " demand.");
    public static final IntOption ON_DEMAND_MODEL_LIFETIME_SECONDS = IntOption
            .of("ExperimentalOnDemandModelLifetimeSeconds", 720)
            .describe("Seconds an on-demand model stays resident after it was last rendered before its VBO is"
                    + " deleted; -1 disables eviction (load once, keep). Only used when"
                    + " ExperimentalAsyncOnDemandModelLoading = true.");
    public static final IntOption ON_DEMAND_MODEL_LOADER_THREADS = IntOption
            .of("ExperimentalOnDemandModelLoaderThreads", 0)
            .describe("Worker threads for on-demand model parsing. On a JDK with virtual threads (21+) this is"
                    + " ignored and one virtual thread is used per load. On Java 8 it sizes the fallback fixed thread"
                    + " pool; 0 = auto (CPU cores - 1, min 2).");

    static LoadingSettings from(ConfigValues v) {
        return new LoadingSettings(v.get(EXTRACT_DEFAULT_CONTENT_PACK), v.get(REMOVE_CLIENT_TRACKING_RESTRICTIONS),
                v.get(ASYNC_ON_DEMAND_MODEL_LOADING), v.get(ON_DEMAND_MODEL_LIFETIME_SECONDS),
                v.get(ON_DEMAND_MODEL_LOADER_THREADS));
    }
}
