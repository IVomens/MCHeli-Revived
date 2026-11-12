package com.ivomens.mcheli.setup.client.item;

import com.ivomens.mcheli.client.model.IItemModelRenderer;
import com.ivomens.mcheli.client.model.ItemRenderContext;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.setup.client.McheliClient;
import com.ivomens.mcheli.util.state.StaticCache;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Shared built-in item renderer that dispatches to the {@link IItemModelRenderer} registered for the item. */
public class CustomItemStackRenderer extends BlockEntityWithoutLevelRenderer {

    /** Created the first time an item is drawn, once the client's renderers exist. */
    @StaticCache(reset = "reset")
    private static CustomItemStackRenderer instance;

    private final ItemRenderContext context;

    private CustomItemStackRenderer(ItemRenderContext context) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.context = context;
    }

    public static CustomItemStackRenderer getInstance() {
        if (instance == null) {
            instance = new CustomItemStackRenderer(McheliClient.itemRenderContext());
        }

        return instance;
    }

    /** Drops the renderer; the next item drawn creates a new one. */
    public static void reset() {
        instance = null;
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext displayContext, @NotNull PoseStack poseStack,
                             @NotNull MultiBufferSource buffers, int packedLight, int packedOverlay) {
        IItemModelRenderer renderer = ItemModelRenderers.getRenderer(stack.getItem());
        if (renderer != null) {
            LegacyGL.begin(poseStack, buffers, packedLight, packedOverlay);
            try {
                renderer.renderItem(stack, this.context.holder(), displayContext,
                        Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
            } finally {
                LegacyGL.end();
            }
        }
    }
}
