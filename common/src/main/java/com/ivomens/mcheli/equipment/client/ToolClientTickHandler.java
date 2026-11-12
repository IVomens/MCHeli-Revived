package com.ivomens.mcheli.equipment.client;

import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.config.client.KeyNames;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.equipment.rangefinder.RangeFinderItem;
import com.ivomens.mcheli.equipment.rangefinder.client.RangeFinderScope;
import com.ivomens.mcheli.equipment.wrench.WrenchItem;
import com.ivomens.mcheli.client.input.ClientTickHandler;
import com.ivomens.mcheli.sound.client.UiSounds;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Client input of the hand-held tools: range finder scope keys and the wrench hand animation. */
public class ToolClientTickHandler extends ClientTickHandler {

    /** Legacy key codes below zero are mouse buttons, offset by this much. */
    private static final int LEGACY_MOUSE_OFFSET = 100;
    /** Each zoom step changes the zoom by this fraction of its current value. */
    private static final float ZOOM_STEP = 0.1F;

    private final RangeFinderScope scope;
    private InputKey keyUseItem;
    private InputKey keyZoomIn;
    private InputKey keyZoomOut;
    private InputKey keySwitchMode;
    private InputKey[] keys;

    public ToolClientTickHandler(Minecraft minecraft, KeySettings bindings, RangeFinderScope scope) {
        super(minecraft);
        this.scope = scope;
        this.updateKeybind(bindings);
    }

    /** Display name of a legacy (LWJGL2-numbered, mouse buttons from -100) key code. */
    public static String keyName(int legacy) {
        try {
            if (legacy < 0) {
                return InputConstants.Type.MOUSE.getOrCreate(legacy + LEGACY_MOUSE_OFFSET).getDisplayName().getString();
            }
            return InputConstants.Type.KEYSYM.getOrCreate(KeyNames.toGlfw(legacy)).getDisplayName().getString();
        } catch (Exception e) {
            return String.valueOf(legacy);
        }
    }

    @Override
    public void updateKeybind(KeySettings bindings) {
        this.keyUseItem = new InputKey(KeySettings.ATTACK);
        this.keyZoomIn = new InputKey(bindings.zoom());
        this.keyZoomOut = new InputKey(bindings.switchWeaponMode());
        this.keySwitchMode = new InputKey(bindings.flare());
        this.keys = new InputKey[]{this.keyUseItem, this.keyZoomIn, this.keyZoomOut, this.keySwitchMode};
    }

    @Override
    public void onTick(boolean inGUI) {
        for (InputKey key : this.keys) {
            key.update();
        }
        Player player = this.mc.player;
        this.keepWrenchFromReequipping(player);
        this.tickRangeFinder(player);
    }

    private void tickRangeFinder(Player player) {
        this.scope.tickCooldown();

        ItemStack held = player != null ? player.getMainHandItem() : ItemStack.EMPTY;
        if (held.getItem() instanceof RangeFinderItem rangeFinder) {
            boolean scoped = player.getTicksUsingItem() > RangeFinderItem.SCOPE_DELAY_TICKS && RangeFinderItem.canUse(player);
            if (scoped) {
                if (!this.scope.isRaised()) {
                    this.scope.raise();
                }
                this.handleScopeKeys(player, held, rangeFinder);
            }
        } else if (this.scope.isRaised()) {
            this.scope.lower();
        }
    }

    private void handleScopeKeys(Player player, ItemStack held, RangeFinderItem rangeFinder) {
        if (this.keyUseItem.isKeyDown()) {
            this.scope.spot(player, held);
        }
        if (this.keyZoomIn.isKeyPress() && this.scope.zoom() < RangeFinderScope.MAX_ZOOM) {
            setZoom(Math.min(this.scope.zoom() * (1.0F + ZOOM_STEP), RangeFinderScope.MAX_ZOOM));
            UiSounds.play("zoom", 0.05F, 1.0F);
        }
        if (this.keyZoomOut.isKeyPress() && this.scope.zoom() > RangeFinderScope.MIN_ZOOM) {
            setZoom(Math.max(this.scope.zoom() * (1.0F - ZOOM_STEP), RangeFinderScope.MIN_ZOOM));
            UiSounds.play("zoom", 0.05F, 0.9F);
        }
        if (this.keySwitchMode.isKeyDown()) {
            UiSounds.play("lockon", 1.0F, 0.9F);
            this.scope.nextMode(this.mc.isSingleplayer());
        }
    }

    private void setZoom(float zoom) {
        this.scope.setZoom(zoom);
    }

    /** The wrench's damage changes while it animates; keep the hand from playing the re-equip animation. */
    private void keepWrenchFromReequipping(Player player) {
        if (player == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof WrenchItem && held.getDamageValue() <= held.getMaxDamage()) {
            ItemInHandRenderer handRenderer = this.mc.gameRenderer.itemInHandRenderer;
            if (handRenderer.mainHandItem.isEmpty() || held.getItem() == handRenderer.mainHandItem.getItem()) {
                handRenderer.mainHandItem = player.getInventory().getSelected();
            }
        }
    }
}
