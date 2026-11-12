package com.ivomens.mcheli.setup.client.item;

import com.ivomens.mcheli.client.model.IItemModelRenderer;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import com.ivomens.mcheli.equipment.rangefinder.client.RangeFinderScope;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class BuiltInRangeFinderItemRenderer implements IItemModelRenderer {

    private static final ResourceLocation TEX = McheliEntityRenderer.texture("textures/rangefinder.png");

    private final RangeFinderScope scope;

    public BuiltInRangeFinderItemRenderer(RangeFinderScope scope) {
        this.scope = scope;
    }

    @Override
    public boolean shouldRender(ItemStack stack, ItemDisplayContext displayContext) {
        return IItemModelRenderer.isFirstPerson(displayContext) || IItemModelRenderer.isThirdPerson(displayContext) ||
                displayContext == ItemDisplayContext.GROUND;
    }

    @Override
    public void renderItem(ItemStack stack, LivingEntity holder, ItemDisplayContext displayContext, float partialTicks) {
        LegacyGL.pushMatrix();
        LegacyGL.bindTexture(TEX);
        boolean visible = true;
        if (IItemModelRenderer.isFirstPerson(displayContext)) {
            // looking through the scope hides the device
            visible = holder instanceof Player player && !this.scope.isUsingScope(player);
            if (holder != null && holder.isUsingItem() && holder.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                LegacyGL.translate(0.6563F, 0.3438F, 0.01F);
            }
        }

        if (visible) {
            ModelCache.render("rangefinder");
        }

        LegacyGL.popMatrix();
    }
}
