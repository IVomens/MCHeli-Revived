package com.ivomens.mcheli.config;

/**
 * Server limits for custom liveries, read from the config.
 *
 * @param enabled        whether players may upload and apply liveries at all
 * @param maxBytes       largest accepted upload
 * @param maxDimension   largest accepted width or height in pixels
 * @param perPlayer      how many liveries one player may own
 * @param totalBytes     storage budget for all liveries on the server
 * @param operatorsOnly  restrict uploading to operators
 */
public record LiveryPolicy(boolean enabled, int maxBytes, int maxDimension, int perPlayer, long totalBytes,
                           boolean operatorsOnly) {

    public static final LiveryPolicy DEFAULT = new LiveryPolicy(true, 2 * 1024 * 1024, 2048, 10, 256L * 1024 * 1024, false);
}
