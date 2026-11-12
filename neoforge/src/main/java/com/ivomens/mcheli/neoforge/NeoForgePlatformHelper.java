package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.platform.PlatformHelper;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class NeoForgePlatformHelper implements PlatformHelper {

    @Override
    public String getLoaderName() {
        return "NeoForge";
    }

    @Override
    public boolean isClient() {
        try {
            return FMLEnvironment.dist.isClient();
        } catch (Throwable t) {
            // FML is not bootstrapped (unit tests)
            return false;
        }
    }

    @Override
    public boolean isDevelopment() {
        return !FMLEnvironment.production;
    }

    @Override
    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get() != null && ModList.get().isLoaded(modId);
    }

    @Nullable
    @Override
    public MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    @Override
    public String getModVersion() {
        try {
            return ModList.get().getModContainerById(Ids.MOD_ID)
                    .map(c -> c.getModInfo().getVersion().toString()).orElse("unknown");
        } catch (Throwable t) {
            return "unknown";
        }
    }

    @Nullable
    @Override
    public Path getModSource() {
        IModFile file = getModFile();
        return file == null ? null : file.getFilePath();
    }

    @Nullable
    @Override
    public Path getModRoot() {
        IModFile file = getModFile();
        if (file == null) {
            return null;
        }
        Path assets = file.findResource("assets");
        if (assets != null && Files.exists(assets)) {
            Path parent = assets.getParent();
            return parent != null ? parent : assets.getFileSystem().getPath("/");
        }
        return file.findResource("");
    }

    @Nullable
    @Override
    public Path findModFile(String modId, String path) {
        var info = ModList.get().getModFileById(modId);
        if (info == null) {
            return null;
        }
        Path found = info.getFile().findResource(path);
        return Files.exists(found) ? found : null;
    }

    @Override
    public List<LoadedMod> getLoadedMods() {
        List<LoadedMod> mods = new ArrayList<>();
        for (IModInfo mod : ModList.get().getMods()) {
            mods.add(new LoadedMod(mod.getModId(), mod.getDisplayName(), mod.getVersion().toString(),
                    mod.getOwningFile().getFile().getFileName()));
        }
        return mods;
    }

    @Nullable
    private static IModFile getModFile() {
        ModList list;
        try {
            list = ModList.get();
        } catch (Throwable t) {
            return null;
        }
        if (list == null) {
            return null;
        }
        var info = list.getModFileById(Ids.MOD_ID);
        return info == null ? null : info.getFile();
    }
}
