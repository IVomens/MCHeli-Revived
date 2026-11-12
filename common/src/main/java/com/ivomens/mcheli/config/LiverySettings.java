package com.ivomens.mcheli.config;

/** Player-uploaded vehicle textures. Server side. */
public record LiverySettings(boolean enabled, boolean uploadOperatorsOnly, int maxKilobytes, int maxDimension,
                             int perPlayer, int storageMegabytes) {

    public static final BoolOption ENABLED = BoolOption.of("EnableLiveries", true)
            .describe("EnableLiveries = allow players to upload their own vehicle textures and apply them. Server"
                    + " side.");
    public static final BoolOption UPLOAD_OPERATORS_ONLY = BoolOption.of("LiveryUploadOperatorsOnly", false)
            .describe("LiveryUploadOperatorsOnly = only operators may upload liveries; everyone can still apply"
                    + " them.");
    public static final IntOption MAX_KILOBYTES = IntOption.of("LiveryMaxKilobytes", 2048).range(64, 16384)
            .describe("LiveryMaxKilobytes = largest accepted livery image in KB.");
    public static final IntOption MAX_DIMENSION = IntOption.of("LiveryMaxDimension", 2048).range(64, 8192)
            .describe("LiveryMaxDimension = largest accepted livery width or height in pixels.");
    public static final IntOption PER_PLAYER = IntOption.of("LiveriesPerPlayer", 10).range(0, 1000)
            .describe("LiveriesPerPlayer = how many liveries one player may have uploaded at a time.");
    public static final IntOption STORAGE_MEGABYTES = IntOption.of("LiveryStorageMegabytes", 256).range(1, 1_000_000)
            .describe("LiveryStorageMegabytes = total disk space all liveries on the server may use.");

    static LiverySettings from(ConfigValues v) {
        return new LiverySettings(v.get(ENABLED), v.get(UPLOAD_OPERATORS_ONLY), v.get(MAX_KILOBYTES),
                v.get(MAX_DIMENSION), v.get(PER_PLAYER), v.get(STORAGE_MEGABYTES));
    }

    public LiveryPolicy policy() {
        return new LiveryPolicy(this.enabled, this.maxKilobytes * 1024, this.maxDimension, this.perPlayer,
                this.storageMegabytes * 1024L * 1024L, this.uploadOperatorsOnly);
    }
}
