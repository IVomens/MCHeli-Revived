package com.ivomens.mcheli.block.client;

import com.ivomens.mcheli.block.DraftingTableBlock;
import com.ivomens.mcheli.block.DraftingTableBlockEntity;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.util.Ids;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/** Renders the drafting table's .mqo model (the block itself has no baked model). */
public class DraftingTableRenderer implements BlockEntityRenderer<DraftingTableBlockEntity> {

    public static final ResourceLocation TEXTURE = Ids.of("textures/blocks/drafting_table.png");

    public DraftingTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull DraftingTableBlockEntity tile, float partialTicks, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffers, int packedLight, int packedOverlay) {
        LegacyGL.begin(poseStack, buffers, packedLight, packedOverlay);
        try {
            LegacyGL.pushMatrix();
            LegacyGL.enableCull();
            LegacyGL.translate(0.5, 0.0, 0.5);
            LegacyGL.rotate(getYawAngle(tile), 0.0F, 1.0F, 0.0F);
            LegacyGL.color(0.75F, 0.75F, 0.75F, 1.0F);
            LegacyGL.enableBlend();
            LegacyGL.bindTexture(TEXTURE);
            ModelCache.render("blocks/drafting_table");
            LegacyGL.disableBlend();
            LegacyGL.popMatrix();
        } finally {
            LegacyGL.end();
        }
    }

    private static float getYawAngle(DraftingTableBlockEntity tile) {
        if (!tile.hasLevel()) {
            return 0.0F;
        }
        BlockState state = tile.getBlockState();
        if (!state.hasProperty(DraftingTableBlock.DIRECTION8)) {
            return 0.0F;
        }
        return -state.getValue(DraftingTableBlock.DIRECTION8).getIndex() * 45.0F + 180.0F;
    }
}
