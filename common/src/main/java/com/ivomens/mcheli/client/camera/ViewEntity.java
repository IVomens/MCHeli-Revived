package com.ivomens.mcheli.client.camera;

import com.ivomens.mcheli.config.McheliConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;

/**
 * Which entity the camera views the world from, and whether the held item shows in first person. Vehicle
 * cameras switch the view to one of their stand-ins and hide the hand; leaving the vehicle switches back.
 *
 * <p>One per client, owned by the client session. The hand render hook reads it on the render thread.
 */
public final class ViewEntity {

    private final PostEffects postEffects;
    private volatile boolean heldItemHidden;

    public ViewEntity(PostEffects postEffects) {
        this.postEffects = postEffects;
    }

    /** Views from the entity, applying the camera mode's post effect to the new view. */
    public void switchTo(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != null && mc.getCameraEntity() != entity) {
            mc.setCameraEntity(entity);
            this.postEffects.applyWanted();
        }
    }

    /** Views from the entity when the config lets vehicles replace the view entity. */
    public void useIfConfigured(LivingEntity entity) {
        if (McheliConfig.render().replaceRenderViewEntity()) {
            Minecraft mc = Minecraft.getInstance();
            if (entity != null && entity != mc.getCameraEntity()) {
                mc.setCameraEntity(entity);
            }
        }
    }

    /** Whether the first person held item is hidden (an aircraft view); the hand render hook checks it. */
    public boolean isHeldItemHidden() {
        return this.heldItemHidden;
    }

    public void showHeldItem() {
        this.heldItemHidden = false;
    }

    /** Hides the held item, unless it is a map. */
    public void hideHeldItem(ItemStack held) {
        if (!held.isEmpty() || !(held.getItem() instanceof MapItem)) {
            hideHeldItem();
        }
    }

    /** Hides the held item unless the config keeps it; the hand render is then cancelled. */
    public void hideHeldItem() {
        if (McheliConfig.render().disableItemRender() != 0) {
            this.heldItemHidden = true;
        }
    }
}
