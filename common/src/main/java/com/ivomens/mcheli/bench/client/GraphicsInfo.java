package com.ivomens.mcheli.bench.client;

import com.ivomens.mcheli.platform.PlatformHelper.LoadedMod;
import com.ivomens.mcheli.platform.Services;
import com.mojang.blaze3d.platform.GlUtil;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** The graphics side of the machine and the game, for the benchmark report. Render thread only. */
final class GraphicsInfo {

    static final String FLYWHEEL = "flywheel";

    private GraphicsInfo() {
    }

    static Map<String, String> describe(Minecraft mc) {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("glRenderer", GlUtil.getRenderer());
        info.put("glVendor", GlUtil.getVendor());
        info.put("glVersion", GlUtil.getOpenGLVersion());
        info.put("window", mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
        info.put("graphicsMode", mc.options.graphicsMode().get().name().toLowerCase(Locale.ROOT));
        info.put("minecraft", SharedConstants.getCurrentVersion().getName());
        info.put("loader", Services.PLATFORM.getLoaderName());
        info.put("mcheli", Services.PLATFORM.getModVersion());
        info.put("flywheel", modVersion(FLYWHEEL));
        info.put("flywheelBackend", flywheelBackend());
        info.put("iris", modVersion("iris"));
        info.put("sodium", modVersion("sodium"));
        return info;
    }

    /** The backend Flywheel is using right now (instancing, indirect, off), or "not installed". */
    static String flywheelBackend() {
        return Services.PLATFORM.isModLoaded(FLYWHEEL) ? Probe.backend() : "not installed";
    }

    private static String modVersion(String id) {
        for (LoadedMod mod : Services.PLATFORM.getLoadedMods()) {
            if (mod.id().equals(id)) {
                return mod.version();
            }
        }
        return "not installed";
    }

    /** Touches Flywheel's classes, so it is only loaded when Flywheel is installed. */
    private static final class Probe {

        static String backend() {
            try {
                Backend backend = BackendManager.currentBackend();
                ResourceLocation id = Backend.REGISTRY.getId(backend);
                return (id == null ? backend.getClass().getSimpleName() : id.toString())
                        + (BackendManager.isBackendOn() ? "" : " (off)");
            } catch (RuntimeException | LinkageError e) {
                return "unknown (" + e.getClass().getSimpleName() + ")";
            }
        }
    }
}
