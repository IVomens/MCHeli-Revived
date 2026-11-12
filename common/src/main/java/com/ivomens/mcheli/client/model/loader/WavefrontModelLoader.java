package com.ivomens.mcheli.client.model.loader;

import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.ModelFormatException;
import com.ivomens.mcheli.client.model.WavefrontModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;

public class WavefrontModelLoader implements IVertexModelLoader {

    @Override
    public IModelCustom load(ResourceManager resourceManager, ResourceLocation location) throws IOException, ModelFormatException {
        ResourceLocation modelLocation = this.withExtension(location);
        Resource resource = resourceManager.getResourceOrThrow(modelLocation);
        return new WavefrontModel(modelLocation, resource);
    }

    @Override
    public String getExtension() {
        return "obj";
    }
}
