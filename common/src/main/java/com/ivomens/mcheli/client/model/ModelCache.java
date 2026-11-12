package com.ivomens.mcheli.client.model;

import com.ivomens.mcheli.util.ModLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache of parsed models keyed by their path below {@code assets/mcheli/models/} without the file
 * extension, e.g. {@code helicopters/ah-64}. Loading is pure CPU work (no GL context needed), so it
 * may run on loader threads; rendering happens on the render thread.
 */
public final class ModelCache {

    private static final Map<String, IModelCustom> MODELS = new ConcurrentHashMap<>();
    /** {@code path + "/" + name} per path and name, so renderers do not build the key every frame. */
    private static final Map<String, Map<String, String>> KEYS = new ConcurrentHashMap<>();

    private ModelCache() {
    }

    static String key(String path, String name) {
        return KEYS.computeIfAbsent(path, p -> new ConcurrentHashMap<>()).computeIfAbsent(name, n -> path + "/" + n);
    }

    public static IModelCustom load(@NotNull String path, @NotNull String name, boolean noThrow) {
        return load(checkedKey(path, name), noThrow, false);
    }

    static String checkedKey(@NotNull String path, @NotNull String name) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(name);
        if (path.isEmpty() || name.isEmpty()) {
            throw new IllegalArgumentException();
        }
        return key(path, name);
    }

    public static IModelCustom load(@NotNull String path, @NotNull String name) {
        return load(path, name, false);
    }

    public static IModelCustom load(String name) {
        return load(name, false);
    }

    /**
     * Returns the cached model or parses it.
     *
     * @param noThrow when true a missing or broken model only returns null; otherwise the error is logged too
     */
    @Nullable
    public static IModelCustom load(String name, boolean noThrow) {
        return load(name, noThrow, false);
    }

    /**
     * Returns the cached model or parses it; with {@code reparse} the file is parsed again even when the model
     * is cached (see {@link ModelLoader#REPARSING}).
     */
    @Nullable
    static IModelCustom load(String name, boolean noThrow, boolean reparse) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException();
        }
        IModelCustom cached = MODELS.get(name);
        if (cached != null) {
            if (!reparse) {
                return cached;
            }
            remove(name);
        }

        IModelCustom model;
        try {
            model = Models.loadModel(name);
        } catch (Models.ModelLoadException e) {
            if (!noThrow) {
                ModLogger.get().catching(e);
            }
            model = null;
        }
        if (model != null) {
            MODELS.put(name, model);
        }
        return model;
    }

    public static void remove(String name) {
        MODELS.remove(name);
    }

    public static void render(String path, String name) {
        render(key(path, name));
    }

    public static void render(String name) {
        IModelCustom model = MODELS.get(name);
        if (model != null) {
            model.renderAll();
        }
    }

    public static void renderPart(String name, String partName) {
        IModelCustom model = MODELS.get(name);
        if (model != null) {
            model.renderPart(partName);
        }
    }

    /** The cached model loaded by {@link #load(String)}, or null when it is not loaded. */
    @Nullable
    public static IModelCustom find(String name) {
        return MODELS.get(name);
    }

    /** The cached model, or null when it is not loaded. */
    @Nullable
    public static IModelCustom find(String path, String name) {
        return MODELS.get(key(path, name));
    }

    @Nullable
    public static BakedModelBase get(String path, String name) {
        return MODELS.get(key(path, name)) instanceof BakedModelBase model ? model : null;
    }
}
