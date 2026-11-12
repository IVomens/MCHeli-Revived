package com.ivomens.mcheli.client.model.loader;

import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.MetasequoiaModel;
import com.ivomens.mcheli.client.model.ModelFormatException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;

public class MetasequoiaModelLoader implements IVertexModelLoader {

    @Override
    public IModelCustom load(ResourceManager resourceManager, ResourceLocation location) throws IOException, ModelFormatException {
        ResourceLocation modelLocation = this.withExtension(location);
        Resource resource = resourceManager.getResourceOrThrow(modelLocation);
        return new MetasequoiaModel(modelLocation, resource);
    }

    @Override
    public String getExtension() {
        return "mqo";
    }
}
