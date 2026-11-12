package com.ivomens.mcheli.equipment.rangefinder.client;

import com.ivomens.mcheli.client.render.ModTextures;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.equipment.client.ToolClientTickHandler;
import com.ivomens.mcheli.equipment.rangefinder.RangeFinderItem;
import com.ivomens.mcheli.client.overlay.HudOverlay;
import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Range finder scope: durability, measured distance, zoom and spotting mode. */
public class RangeFinderHud extends HudOverlay {

    private static final int OK_COLOR = 0xFF10FF20;
    private static final int ERROR_COLOR = 0xFFFF1010;
    private static final int WARNING_COLOR = 0xFFFF0000;
    private static final int INACTIVE_COLOR = 0xFF404040;
    private static final int WHITE = -1;
    private static final double RANGE = 300.0;
    private static final int DIGIT_SPACING = 13;

    private final RangeFinderScope scope;

    public RangeFinderHud(Minecraft minecraft, RangeFinderScope scope) {
        super(minecraft);
        this.scope = scope;
    }

    @Override
    public boolean isDrawGui(Player player) {
        return RangeFinderItem.canUse(player);
    }

    @Override
    public void drawGui(Player player, boolean isThirdPersonView) {
        if (isThirdPersonView) return;
        this.lineWidth(scaleFactor);
        if (this.isDrawGui(player)) {
            this.disableBlend();
            if (this.scope.isUsingScope(player)) {
                this.drawScope(player);
            }
        }
    }

    private void drawScope(Player player) {
        this.enableBlend();
        this.color(0.0F, 0.0F, 0.0F, 1.0F);
        ModTextures.bind("textures/gui/rangefinder.png");
        double size = 512.0;

        while (size < this.width || size < this.height) {
            size *= 2.0;
        }

        this.drawTexturedModalRectRotate(-(size - this.width) / 2.0, -(size - this.height) / 2.0, size, size, 0.0, 0.0,
                256.0, 256.0, 0.0F);
        this.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.disableBlend();
        // The readouts are laid out in physical pixels of the scope texture, scaled with it.
        double factor = size / 512.0;
        double pixelScale = scaleFactor * factor;
        double centerX = (double) this.mc.getWindow().getWidth() / 2;
        double centerY = (double) this.mc.getWindow().getHeight() / 2;
        double x = (centerX - 80.0 * pixelScale) / pixelScale;
        double y = (centerY + 55.0 * pixelScale) / pixelScale;
        this.pushMatrix();
        this.scale((float) factor, (float) factor, (float) factor);
        ItemStack item = player.getMainHandItem();
        int durability = item.getMaxDamage() > 0
                ? (int) ((double) (item.getMaxDamage() - item.getDamageValue()) / item.getMaxDamage() * 100.0) : 0;
        this.drawDigit(String.format("%3d", durability), (int) x, (int) y, DIGIT_SPACING, durability > 0 ? OK_COLOR : ERROR_COLOR);
        if (durability <= 0) {
            this.drawString("Please craft", (int) x + 40, (int) y, WARNING_COLOR);
            this.drawString("redstone", (int) x + 40, (int) y + 10, WARNING_COLOR);
        }

        x = (centerX - 20.0 * pixelScale) / pixelScale;
        if (durability > 0) {
            drawMeasuredRange(player, (int) x, (int) y);
        }

        drawScopeSettings(x - 80.0, y - 4.0);
        this.popMatrix();
        drawKeyHints(centerX, centerY, pixelScale);
    }

    /** The spot cooldown bar, the zoom and the spotting mode, from the scope's lower left. */
    private void drawScopeSettings(double x, double y) {
        drawRect((int) x, (int) y, (int) x + 30, (int) y + 2, OK_COLOR);
        drawRect((int) x, (int) y, (int) x + this.scope.spotCooldown() / 2, (int) y + 2, ERROR_COLOR);
        this.drawString(String.format("x%.1f", this.scope.zoom()), (int) x, (int) y - 20, WHITE);
        x += 130.0;
        int mode = this.scope.mode();
        this.drawString(">", (int) x, (int) y - 30 + mode * 10, WHITE);
        x += 10.0;
        this.drawString("Players/Vehicles", (int) x, (int) y - 30, mode == RangeFinderScope.MODE_SPOT_PLAYERS ? WHITE : INACTIVE_COLOR);
        this.drawString("Monsters/Mobs", (int) x, (int) y - 20, mode == RangeFinderScope.MODE_SPOT_MOBS ? WHITE : INACTIVE_COLOR);
        this.drawString("Mark Point", (int) x, (int) y - 10, mode == RangeFinderScope.MODE_MARK_POINT ? WHITE : INACTIVE_COLOR);
    }

    /** The keys of the scope, at the upper left of the screen. */
    private void drawKeyHints(double centerX, double centerY, double pixelScale) {
        int hintX = (int) Math.max((centerX - 160.0 * pixelScale) / scaleFactor, 10.0);
        int hintY = (int) Math.max((centerY - 100.0 * pixelScale) / scaleFactor, 10.0);
        this.drawString("Spot      : " + ToolClientTickHandler.keyName(KeySettings.ATTACK), hintX, hintY, WHITE);
        this.drawString("Zoom in   : " + ToolClientTickHandler.keyName(McheliConfig.keys().zoom()), hintX, hintY + 10,
                this.scope.zoom() < RangeFinderScope.MAX_ZOOM ? WHITE : INACTIVE_COLOR);
        this.drawString("Zoom out : " + ToolClientTickHandler.keyName(McheliConfig.keys().switchWeaponMode()), hintX, hintY + 20,
                this.scope.zoom() > RangeFinderScope.MIN_ZOOM ? WHITE : INACTIVE_COLOR);
        this.drawString("Mode      : " + ToolClientTickHandler.keyName(McheliConfig.keys().flare()), hintX, hintY + 30, WHITE);
    }

    private void drawMeasuredRange(Player player, int x, int y) {
        Vec3 from = player.getEyePosition();
        Vec3 look = Rotations.direction(player.getYRot(), player.getXRot());
        Vec3 to = from.add(look.x * RANGE, look.y * RANGE, look.z * RANGE);
        BlockHitResult hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.MISS) {
            int range = (int) Math.sqrt(player.distanceToSqr(hit.getLocation()));
            this.drawDigit(String.format("%4d", range), x, y, DIGIT_SPACING, OK_COLOR);
        } else {
            this.drawDigit("----", x, y, DIGIT_SPACING, ERROR_COLOR);
        }
    }
}
