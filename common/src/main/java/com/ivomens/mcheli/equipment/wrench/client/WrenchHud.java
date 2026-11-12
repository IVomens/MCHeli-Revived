package com.ivomens.mcheli.equipment.wrench.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftPicking;
import com.ivomens.mcheli.equipment.wrench.WrenchItem;
import com.ivomens.mcheli.client.overlay.HudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** HP bar of the aircraft under the crosshair while holding the wrench. */
public class WrenchHud extends HudOverlay {

    private static final int COLOR_HP_HEALTHY = 0xFF28A745;
    private static final int COLOR_HP_LOW = 0xFFDF4758;
    private static final int COLOR_BACKGROUND = 0xFF14161C;

    private static final float LOW_HEALTH_THRESHOLD = 0.3F;

    private static final int BAR_WIDTH = 40;
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_PADDING = 1;

    /** Offsets below the crosshair. */
    private static final int OFFSET_Y_BAR = 41;
    private static final int OFFSET_Y_TEXT = 50;

    public WrenchHud(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    public boolean isDrawGui(Player player) {
        return player != null && player.level() != null && !player.getMainHandItem().isEmpty() &&
                player.getMainHandItem().getItem() instanceof WrenchItem;
    }

    @Override
    public void drawGui(Player player, boolean isThirdPersonView) {
        if (isThirdPersonView) return;
        this.lineWidth(scaleFactor);
        if (!this.isDrawGui(player)) return;
        this.disableBlend();
        AircraftEntity ac = AircraftPicking.lookedAtAircraft(player);
        if (ac != null && ac.damage().maxHp() > 0) {
            float health = (float) ac.damage().hp() / ac.damage().maxHp();
            this.drawHP(health > LOW_HEALTH_THRESHOLD ? COLOR_HP_HEALTHY : COLOR_HP_LOW, ac.damage().hp(), ac.damage().maxHp());
        }
    }

    void drawHP(int color, int hp, int maxHp) {
        int barTop = this.centerY + OFFSET_Y_BAR;
        int barBottom = barTop + BAR_HEIGHT;
        int barLeft = this.centerX - BAR_WIDTH / 2;
        drawRect(barLeft, barTop, barLeft + BAR_WIDTH, barBottom, COLOR_BACKGROUND);

        hp = Math.min(hp, maxHp);
        float fraction = (float) hp / maxHp;
        int innerLeft = barLeft + BAR_PADDING;
        int innerWidth = (int) ((BAR_WIDTH - BAR_PADDING * 2) * fraction);
        drawRect(innerLeft, barTop + BAR_PADDING, innerLeft + innerWidth, barBottom - BAR_PADDING, color);

        // Never show 100 % while the aircraft still needs repair.
        int percent = (int) (fraction * 100.0F);
        if (hp < maxHp && percent >= 100) {
            percent = 99;
        }
        this.drawCenteredString(String.format("%d %%", percent), this.centerX, this.centerY + OFFSET_Y_TEXT, color);
    }
}
