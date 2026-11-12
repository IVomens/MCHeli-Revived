package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftPicking;
import com.ivomens.mcheli.aircraft.HitBoxEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.VehicleTool;
import com.ivomens.mcheli.client.overlay.HudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** "Press use to enter seat N" prompt while the player looks at a free seat of a vehicle. */
public class GuiInteractSeat extends HudOverlay {

    private static final int TEXT_COLOR = 0xFF28A745;
    private static final int BACKGROUND_COLOR = 0xFF14161C;
    /** The prompt sits this far below the crosshair. */
    private static final int OFFSET_Y = 50;
    private static final int PROMPT_PADDING_X = 6;
    private static final int PROMPT_PADDING_Y = 4;
    private static final int LOCKED_TEXT_COLOR = 0xFFDC3545;
    private static final String TRANSLATION_ENTER_SEAT = "gui.mcheli.interact_seat.enter";
    private static final String TRANSLATION_LOCKED = "gui.mcheli.interact_seat.locked";

    /** Seat found by {@link #isDrawGui}, reused by {@link #drawGui} in the same frame. */
    private int targetSeatId = -1;
    /** The owner who locked that seat against the player, or null when the player may take it. */
    @Nullable
    private String lockedBy;

    public GuiInteractSeat(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    public boolean isDrawGui(Player player) {
        this.targetSeatId = getTargetSeatId(player);
        return this.targetSeatId >= 0;
    }

    @Override
    public void drawGui(Player player, boolean isThirdPersonView) {
        if (isThirdPersonView || this.targetSeatId < 0) return;
        this.lineWidth(scaleFactor);
        String keyName = this.mc.options.keyUse.getTranslatedKeyMessage().getString();
        if (this.lockedBy != null) {
            drawPrompt(I18n.get(TRANSLATION_LOCKED, this.lockedBy), LOCKED_TEXT_COLOR);
        } else {
            drawPrompt(I18n.get(TRANSLATION_ENTER_SEAT, keyName, this.targetSeatId), TEXT_COLOR);
        }
    }

    /** The owner's name when the vehicle is locked against the player for that seat. */
    @Nullable
    private static String lockedBy(@Nullable AircraftEntity aircraft, Player player, int seatId) {
        if (aircraft == null || seatId < 0 || aircraft.ownership().mayBoard(player, seatId)) {
            return null;
        }
        return aircraft.ownership().ownerName();
    }

    private int getTargetSeatId(Player player) {
        if (player == null || player.level() == null || player.getVehicle() != null || player.isShiftKeyDown()) {
            return -1;
        }

        if (isBlockedHeldItem(player.getMainHandItem())) {
            return -1;
        }

        Entity target = AircraftPicking.lookedAt(player);
        AircraftEntity aircraft = switch (target) {
            case SeatEntity seat -> seat.getParent();
            case HitBoxEntity hitBox -> hitBox.owner();
            case AircraftEntity vehicle -> vehicle;
            case null, default -> null;
        };
        int seatId = switch (target) {
            case SeatEntity seat -> getTargetSeatId(seat, player);
            case HitBoxEntity hitBox -> getTargetSeatId(hitBox.owner(), player);
            case AircraftEntity vehicle -> getTargetSeatId(vehicle, player);
            case null, default -> -1;
        };
        this.lockedBy = lockedBy(aircraft, player, seatId);
        return seatId;
    }

    /** Displayed seat numbers start at 1 for the first passenger seat; 0 is the pilot. */
    private int getTargetSeatId(SeatEntity seat, Player player) {
        AircraftEntity parent = seat.getParent();
        if (parent == null || parent.isDestroyed() || parent.seats().hasEnemyOf(player)) {
            return -1;
        }

        if (seat.getRiddenByEntity() != null || !seat.canRideMob(player)) {
            return -1;
        }

        return seat.seatID() + 1;
    }

    private int getTargetSeatId(AircraftEntity aircraft, Player player) {
        if (aircraft == null || aircraft.isDestroyed() || aircraft.spec() == null ||
                aircraft.seats().hasEnemyOf(player) || !aircraft.spec().file().canRide()) {
            return -1;
        }

        if (aircraft.getRiddenByEntity() != null || aircraft.isUAV()) {
            return getFirstAvailablePassengerSeatId(aircraft, player);
        }

        if (!aircraft.seats().canTake(0)) {
            return -1;
        }

        if (aircraft.spec().parts().haveCanopy() && aircraft.parts().isCanopyClosed()) {
            return -1;
        }

        if (aircraft.parts().modeSwitchCooldown() > 0) {
            return -1;
        }

        return 0;
    }

    private int getFirstAvailablePassengerSeatId(AircraftEntity aircraft, Player player) {
        int seatId = 1;
        for (SeatEntity seat : aircraft.seats().all()) {
            if (seat != null && seat.getRiddenByEntity() == null && !aircraft.seats().isAboard(player) &&
                    aircraft.seats().canTake(seatId)) {
                return seatId;
            }

            seatId++;
        }

        return -1;
    }

    private boolean isBlockedHeldItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof VehicleTool;
    }

    private void drawPrompt(String text, int color) {
        int textY = this.centerY + OFFSET_Y;
        int halfWidth = this.mc.font.width(text) / 2;
        drawRect(this.centerX - halfWidth - PROMPT_PADDING_X, textY - PROMPT_PADDING_Y,
                this.centerX + halfWidth + PROMPT_PADDING_X, textY + this.mc.font.lineHeight + PROMPT_PADDING_Y,
                BACKGROUND_COLOR);
        this.drawCenteredString(text, this.centerX, textY, color);
    }
}
