package com.ivomens.mcheli.fabric;

import com.ivomens.mcheli.platform.PlatformHelper;
import com.ivomens.mcheli.util.Ids;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Paths, mods and sides from Fabric Loader. Fabric has no global handle on the running server, so this
 * keeps one from the server lifecycle events, set before the levels load and cleared after the
 * server stopped, as NeoForge does.
 */
public class FabricPlatformHelper implements PlatformHelper {

    private final AtomicReference<MinecraftServer> server = new AtomicReference<>();

    /** Called once by {@link McheliFabric} during mod init. */
    void trackRunningServer() {
        ServerLifecycleEvents.SERVER_STARTING.register(this.server::set);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> this.server.compareAndSet(stopped, null));
    }

    @Override
    public String getLoaderName() {
        return "Fabric";
    }

    @Override
    public boolean isClient() {
        try {
            return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
        } catch (Throwable t) {
            // Fabric Loader is not running (unit tests)
            return false;
        }
    }

    @Override
    public boolean isDevelopment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Nullable
    @Override
    public MinecraftServer getServer() {
        return this.server.get();
    }

    @Override
    public String getModVersion() {
        return ownContainer().map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }

    @Nullable
    @Override
    public Path getModSource() {
        return ownContainer().map(c -> firstPath(c.getOrigin())).orElse(null);
    }

    /** The root that holds {@code assets/}: the jar's root in production, the resources output in development. */
    @Nullable
    @Override
    public Path getModRoot() {
        return ownContainer().flatMap(c -> c.findPath("assets").map(FabricPlatformHelper::parentOrSelf)
                        .or(() -> c.getRootPaths().stream().findFirst()))
                .orElse(null);
    }

    @Nullable
    @Override
    public Path findModFile(String modId, String path) {
        return FabricLoader.getInstance().getModContainer(modId)
                .flatMap(mod -> path.isEmpty() ? mod.getRootPaths().stream().findFirst() : mod.findPath(path))
                .orElse(null);
    }

    @Override
    public List<LoadedMod> getLoadedMods() {
        List<LoadedMod> mods = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            Path source = firstPath(mod.getOrigin());
            mods.add(new LoadedMod(mod.getMetadata().getId(), mod.getMetadata().getName(),
                    mod.getMetadata().getVersion().getFriendlyString(),
                    source == null || source.getFileName() == null ? "" : source.getFileName().toString()));
        }
        return mods;
    }

    private static Optional<ModContainer> ownContainer() {
        try {
            return FabricLoader.getInstance().getModContainer(Ids.MOD_ID);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    @Nullable
    private static Path firstPath(ModOrigin origin) {
        return switch (origin.getKind()) {
            case PATH -> origin.getPaths().isEmpty() ? null : origin.getPaths().getFirst();
            case NESTED -> Path.of(origin.getParentSubLocation());
            default -> null;
        };
    }

    private static Path parentOrSelf(Path path) {
        Path parent = path.getParent();
        return parent != null ? parent : path.getFileSystem().getPath("/");
    }
}
