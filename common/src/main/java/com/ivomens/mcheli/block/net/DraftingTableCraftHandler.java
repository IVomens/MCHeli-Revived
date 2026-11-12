package com.ivomens.mcheli.block.net;

import com.ivomens.mcheli.block.DraftingTableMenu;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Crafts a recipe the open drafting table lists; anything else is ignored. */
public final class DraftingTableCraftHandler implements ServerMessageHandler<DraftingTableCraftMessage> {

    @Override
    public void handle(DraftingTableCraftMessage message, ServerPlayer sender) {
        ResourceLocation id = ResourceLocation.tryParse(message.recipeId());
        if (id == null || !(sender.containerMenu instanceof DraftingTableMenu menu) || !menu.stillValid(sender)) {
            return;
        }
        RecipeHolder<?> recipe = DraftingTableMenu.findListedRecipe(id);
        if (recipe != null) {
            menu.createRecipeItem(recipe);
        }
    }
}
