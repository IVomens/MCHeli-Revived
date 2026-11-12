package com.ivomens.mcheli.config;

/** UAV stations, the UAV tablet and keeping remote UAVs simulated. Server side. */
public record UavSettings(int maxPairedPerStation, boolean tabletControlsNormalUav, boolean chunkLoading,
                          int chunkLoadRadius) {

    public static final IntOption MAX_PAIRED_PER_STATION = IntOption.of("MaxPairedUavPerStation", 5)
            .describe("MaxPairedUavPerStation = maximum number of UAVs that can be paired to a single UAV station");
    public static final BoolOption TABLET_CONTROLS_NORMAL_UAV = BoolOption.of("UavTabletControlNormalUav", false)
            .describe("UavTabletControlNormalUav = true lets the handheld UAV tablet bind and control full-size UAVs;"
                    + " false (default) restricts the tablet to small UAVs only.");
    public static final BoolOption CHUNK_LOADING = BoolOption.of("EnableUavChunkLoading", false)
            .describe("EnableUavChunkLoading = true keeps the chunks around a UAV that is being remotely controlled"
                    + " loaded, so it keeps flying when it leaves the operator's view distance. false (default) = no"
                    + " chunk loading.");
    public static final IntOption CHUNK_LOAD_RADIUS = IntOption.of("UavChunkLoadRadius", 2).range(0, 8)
            .describe("UavChunkLoadRadius = radius in chunks loaded around a controlled UAV (0 = only the UAV's"
                    + " chunk, max 8). Only used when EnableUavChunkLoading = true.");

    static UavSettings from(ConfigValues v) {
        return new UavSettings(v.get(MAX_PAIRED_PER_STATION), v.get(TABLET_CONTROLS_NORMAL_UAV), v.get(CHUNK_LOADING),
                v.get(CHUNK_LOAD_RADIUS));
    }
}
