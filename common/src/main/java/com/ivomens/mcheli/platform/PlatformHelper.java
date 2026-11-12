package com.ivomens.mcheli.platform;

import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;

/** Loader services with no vanilla equivalent: paths, mod lookups and the running server. */
public interface PlatformHelper {

    /** Short loader name for logs, e.g. {@code "NeoForge"}. */
    String getLoaderName();

    /** True on the physical client, including the integrated server running inside it. */
    boolean isClient();

    /** True when running from a development environment rather than an installed jar. */
    boolean isDevelopment();

    Path getGameDir();

    Path getConfigDir();

    boolean isModLoaded(String modId);

    /** The running server (integrated or dedicated), or null while none is running. */
    @Nullable
    MinecraftServer getServer();

    /** MCHeli's own version string, or {@code "unknown"} when it cannot be read. */
    String getModVersion();

    /**
     * MCHeli's own jar, or the build output folder in a development environment. Null when the
     * loader has no mod list (unit tests).
     */
    @Nullable
    Path getModSource();

    /**
     * Root of MCHeli's bundled resources as an nio path (inside the jar file system in production).
     * Relative paths such as {@code "assets/mcheli/..."} resolve against it.
     */
    @Nullable
    Path getModRoot();

    /**
     * A file or folder among a loaded mod's files ({@code ""} for their root): inside its jar in production,
     * in its build output in a development environment. Null when the mod is not loaded or has no such file.
     */
    @Nullable
    Path findModFile(String modId, String path);

    /** Every loaded mod, for the operator mod list request. */
    List<LoadedMod> getLoadedMods();

    record LoadedMod(String id, String displayName, String version, String fileName) {
    }
}
