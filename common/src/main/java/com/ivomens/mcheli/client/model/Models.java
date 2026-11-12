package com.ivomens.mcheli.client.model;

import com.ivomens.mcheli.client.model.loader.IVertexModelLoader;
import com.ivomens.mcheli.client.model.loader.MetasequoiaModelLoader;
import com.ivomens.mcheli.client.model.loader.WavefrontModelLoader;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Contract;

import java.io.FileNotFoundException;

/**
 * Loads .obj / .mqo models from the resource manager. Pure CPU work; safe to call from loader threads.
 */
public class Models {

    /** Tried in this order; the first format whose file exists wins. */
    private static final IVertexModelLoader[] LOADERS = {new WavefrontModelLoader(), new MetasequoiaModelLoader()};

    /**
     * Parses {@code models/<name>.obj} or {@code .mqo}. Returns null for a missing bullet model (the
     * weapon then falls back to the default bullet).
     */
    @Contract("_ -> new")
    public static IModelCustom loadModel(String name) throws ModelLoadException {
        ResourceLocation resource = Ids.of("models/" + name);
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        Throwable failure = null;
        int missing = 0;
        for (IVertexModelLoader loader : LOADERS) {
            try {
                return loader.load(resourceManager, resource);
            } catch (FileNotFoundException e) {
                ModLogger.get().debug("model file not found '{}' at .{}", resource, loader.getExtension());
                missing++;
            } catch (Throwable t) {
                ModLogger.get().error("load model error '{}' at .{}", resource, loader.getExtension());
                failure = t;
            }
        }
        if (missing == LOADERS.length) {
            if (resource.toString().contains("bullets")) {
                ModLogger.warn("Model for {} was not found. Will default to the normal bullet model", resource);
                return null;
            }
            throw new ModelLoadException(resource + " not found");
        }
        throw new ModelLoadException("model load error for " + resource, failure);
    }

    public static class ModelLoadException extends Exception {

        public ModelLoadException(String message) {
            super(message);
        }

        public ModelLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
