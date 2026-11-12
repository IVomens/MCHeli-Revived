package com.ivomens.mcheli.client.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Loads models into the {@link ModelCache}: either reusing a model that is cached already, or parsing every
 * file again so that edited models show without a restart.
 */
public final class ModelLoader {

    /** Uses a cached model when there is one. */
    public static final ModelLoader CACHED = new ModelLoader(false);
    /** Parses every model again and replaces the cached one. */
    public static final ModelLoader REPARSING = new ModelLoader(true);

    private final boolean reparse;

    private ModelLoader(boolean reparse) {
        this.reparse = reparse;
    }

    /** {@link #REPARSING} when {@code reparse}, otherwise {@link #CACHED}. */
    public static ModelLoader of(boolean reparse) {
        return reparse ? REPARSING : CACHED;
    }

    /** The model {@code path/name}; a missing or broken model is logged and returns null. */
    @Nullable
    public IModelCustom load(@NotNull String path, @NotNull String name) {
        return this.load(path, name, false);
    }

    /** The model {@code path/name}; with {@code noThrow} a missing or broken model only returns null. */
    @Nullable
    public IModelCustom load(@NotNull String path, @NotNull String name, boolean noThrow) {
        return ModelCache.load(ModelCache.checkedKey(path, name), noThrow, this.reparse);
    }

    /** The model {@code name}; a missing or broken model is logged and returns null. */
    @Nullable
    public IModelCustom load(String name) {
        return ModelCache.load(name, false, this.reparse);
    }
}
