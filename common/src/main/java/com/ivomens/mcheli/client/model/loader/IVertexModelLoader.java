package com.ivomens.mcheli.client.model.loader;

import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.ModelFormatException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Contract;

import java.io.IOException;

/** Parses one model file format. */
public interface IVertexModelLoader {

    /** File extension without the dot, such as {@code obj}. */
    String getExtension();

    /** @throws java.io.FileNotFoundException when no resource with this loader's extension exists */
    @Contract("_, _ -> new")
    IModelCustom load(ResourceManager resourceManager, ResourceLocation location) throws IOException, ModelFormatException;

    default ResourceLocation withExtension(ResourceLocation location) {
        return ResourceLocation.fromNamespaceAndPath(location.getNamespace(), location.getPath() + "." + this.getExtension());
    }
}
