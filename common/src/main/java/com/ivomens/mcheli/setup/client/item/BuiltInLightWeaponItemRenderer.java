package com.ivomens.mcheli.setup.client.item;

import com.ivomens.mcheli.client.model.IItemModelRenderer;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import com.ivomens.mcheli.equipment.launcher.LightWeaponItem;
import com.ivomens.mcheli.platform.LocalClient;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class BuiltInLightWeaponItemRenderer implements IItemModelRenderer {

    @Override
    public boolean shouldRender(ItemStack stack, ItemDisplayContext displayContext) {
        return IItemModelRenderer.isFirstPerson(displayContext) || IItemModelRenderer.isThirdPerson(displayContext);
    }

    @Override
    public void renderItem(ItemStack stack, LivingEntity holder, ItemDisplayContext displayContext, float partialTicks) {
        if (!this.shouldRender(stack, displayContext)) {
            return;
        }
        // in first person the light weapon HUD replaces the held launcher
        if (holder instanceof Player player && LightWeaponItem.isHeld(player) && LocalClient.get().isFirstPerson()
                && LocalClient.get().isLocalPlayer(player)) {
            return;
        }
        this.renderLauncher(stack, IItemModelRenderer.isFirstPerson(displayContext), holder);
    }

    private void renderLauncher(ItemStack stack, boolean firstPerson, LivingEntity holder) {
        String name = LightWeaponItem.getLightWeaponName(stack);
        LegacyGL.pushMatrix();
        LegacyGL.enableCull();
        LegacyGL.bindTexture(McheliEntityRenderer.texture("textures/lweapon/", name));
        if (firstPerson && holder != null && holder.isUsingItem() && holder.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            LegacyGL.translate(0.13F, 0.27F, 0.01F);
        }

        ModelCache.render("lweapons", name);
        LegacyGL.popMatrix();
    }
}
