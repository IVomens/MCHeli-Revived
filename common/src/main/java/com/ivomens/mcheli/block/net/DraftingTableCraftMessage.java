package com.ivomens.mcheli.block.net;

import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.networking.ServerboundMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Crafts the recipe with this id in the drafting table the sender has open. */
public record DraftingTableCraftMessage(String recipeId) implements ServerboundMessage {

    private static final int MAX_RECIPE_ID_LENGTH = 256;

    private static final StreamCodec<ByteBuf, DraftingTableCraftMessage> CODEC = StreamCodec.composite(
            Codecs.utf(MAX_RECIPE_ID_LENGTH), DraftingTableCraftMessage::recipeId,
            DraftingTableCraftMessage::new);

    public static final MessageType<DraftingTableCraftMessage> TYPE =
            MessageType.serverbound("block/drafting_table_craft", CODEC);

    public static DraftingTableCraftMessage of(RecipeHolder<?> recipe) {
        return new DraftingTableCraftMessage(recipe.id().toString());
    }

    @Override
    public MessageType<DraftingTableCraftMessage> messageType() {
        return TYPE;
    }
}
