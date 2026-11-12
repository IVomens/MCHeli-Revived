package com.ivomens.mcheli.setup.client.item;

import com.ivomens.mcheli.client.model.IItemModelRenderer;
import com.ivomens.mcheli.client.model.ItemBakedModel;
import com.ivomens.mcheli.setup.client.ClientModels;
import com.ivomens.mcheli.setup.client.McheliClient;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.util.state.RegistryHolder;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Built-in (Java) item renderers: the item's normal sprite model is wrapped in an
 * {@link ItemBakedModel} that switches to the renderer for the contexts it handles.
 */
public class ItemModelRenderers {

    private static final Map<Item, IItemModelRenderer> RENDERERS = new LinkedHashMap<>();
    /** Set once the item models have been registered with the model event. */
    @RegistryHolder
    private static boolean modelsRegistered;

    /** Called when the model loader asks for extra models, before baking. */
    public static void onRegisterAdditionalModels() {
        // the .obj/.mqo models are parsed once, during the first resource load
        if (!modelsRegistered) {
            modelsRegistered = true;
            ModLogger.log("Register models");
            ClientModels.registerModels();
        }
    }

    /**
     * Wraps the baked inventory models of items with a built-in renderer; {@code models} is modified in
     * place. {@code wrapper} creates the loader's {@link ItemBakedModel}.
     */
    public static void onModelsBaked(Map<ModelResourceLocation, BakedModel> models, ItemBakedModel.Factory wrapper) {
        for (Map.Entry<Item, IItemModelRenderer> entry : RENDERERS.entrySet()) {
            ModelResourceLocation key = getInventoryModel(entry.getKey());
            BakedModel bakedModel = models.get(key);
            if (bakedModel != null && !(bakedModel instanceof ItemBakedModel)) {
                models.put(key, wrapper.create(bakedModel, entry.getValue(), McheliClient.itemRenderContext()));
            }
        }
    }

    /**
     * The same wrapping for a loader that hands over baked models one at a time: {@code model} wrapped by
     * {@code wrapper} when {@code id} is the inventory model of an item with a built-in renderer, else
     * {@code model} itself.
     */
    public static BakedModel wrapInventoryModel(ModelResourceLocation id, BakedModel model,
                                                ItemBakedModel.Factory wrapper) {
        if (!ModelResourceLocation.INVENTORY_VARIANT.equals(id.variant()) || model instanceof ItemBakedModel) {
            return model;
        }
        IItemModelRenderer renderer = RENDERERS.get(BuiltInRegistries.ITEM.get(id.id()));
        return renderer != null ? wrapper.create(model, renderer, McheliClient.itemRenderContext()) : model;
    }

    public static void registerRenderer(Item item, IItemModelRenderer renderer) {
        if (item != null) {
            RENDERERS.put(item, renderer);
        }
    }

    /** Accepts an {@link Item}, a block / any {@link ItemLike}, or a registry supplier of one. */
    @Nullable
    public static Item asItem(@Nullable Object o) {
        if (o instanceof ItemLike like) {
            Item item = like.asItem();
            return item == Items.AIR ? null : item;
        }
        if (o instanceof Supplier<?> s) {
            return asItem(s.get());
        }
        return null;
    }

    public static Map<Item, IItemModelRenderer> getRenderers() {
        return RENDERERS;
    }

    private static ModelResourceLocation getInventoryModel(Item item) {
        return ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(item));
    }

    @Nullable
    public static IItemModelRenderer getRenderer(Item item) {
        return RENDERERS.get(item);
    }
}
