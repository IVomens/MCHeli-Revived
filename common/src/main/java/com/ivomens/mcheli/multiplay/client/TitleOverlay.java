package com.ivomens.mcheli.multiplay.client;

import com.ivomens.mcheli.client.overlay.HudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** Large centred message shown by {@code /mcheli title}. */
public class TitleOverlay extends HudOverlay {

    private static final int MAX_LINES = 100;
    private static final int LINE_HEIGHT = 9;
    /** Fade in and out over this many ticks. */
    private static final int FADE_TICKS = 5;

    private final List<FormattedCharSequence> chatLines = new ArrayList<>();
    private int prevPlayerTick = 0;
    private int restShowTick = 0;
    private int showTick = 0;
    private float colorAlpha = 0.0F;
    private int position = 0;

    public TitleOverlay(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    public boolean isDrawGui(Player player) {
        if (this.restShowTick > 0 && !this.chatLines.isEmpty() && player != null && player.level() != null) {
            if (this.prevPlayerTick != player.tickCount) {
                this.showTick++;
                this.restShowTick--;
            }

            this.prevPlayerTick = player.tickCount;
        }

        return this.restShowTick > 0;
    }

    @Override
    public void drawGui(Player player, boolean isThirdPersonView) {
        this.lineWidth(scaleFactor * 2);
        this.disableBlend();
        this.colorAlpha = 1.0F;
        if (this.restShowTick > 20 && this.showTick < FADE_TICKS) {
            this.colorAlpha = this.showTick / (float) FADE_TICKS;
        }
        if (this.showTick > 0 && this.restShowTick < FADE_TICKS) {
            this.colorAlpha = this.restShowTick / (float) FADE_TICKS;
        }

        this.drawChat();
    }

    /** Same width formula as the vanilla chat box. */
    private int calculateChatboxWidth() {
        return Mth.floor(this.mc.options.chatWidth().get() * 280 + 40);
    }

    public void setupTitle(Component chatComponent, int showTime, int pos) {
        this.chatLines.clear();
        this.position = pos;
        this.showTick = 0;
        this.restShowTick = showTime;
        int wrapWidth = Math.max(Mth.floor(this.calculateChatboxWidth() / this.mc.options.chatScale().get()), 1);
        Component text = chatComponent;
        if (!this.mc.options.chatColors().get()) {
            text = Component.literal(chatComponent.getString().replaceAll("§.", ""));
        }

        // Explicit line breaks first, then wrap each line to the chat width.
        String[] lines = text.getString().split("\n");
        if (lines.length > 1) {
            for (String line : lines) {
                MutableComponent component = Component.literal(line).withStyle(text.getStyle());
                this.chatLines.addAll(this.mc.font.split(component, wrapWidth));
            }
        } else {
            this.chatLines.addAll(this.mc.font.split(text, wrapWidth));
        }

        while (this.chatLines.size() > MAX_LINES) {
            this.chatLines.remove(this.chatLines.size() - 1);
        }
    }

    private void drawChat() {
        if (this.graphics == null) return;
        float charAlpha = (float) (this.mc.options.chatOpacity().get() * 0.9F + 0.1F);
        float scale = (float) (this.mc.options.chatScale().get() * 2.0F);
        float screenHeight = this.height;
        float blockHeight = this.chatLines.size() * LINE_HEIGHT * scale;
        // Positions: 1 top, 2 bottom, 3 upper third, 4 lower third, otherwise centred.
        float posY = switch (this.position) {
            case 1 -> 0.0F;
            case 2 -> screenHeight - blockHeight;
            case 3 -> screenHeight / 3 - blockHeight / 2.0F;
            case 4 -> screenHeight * 2 / 3 - blockHeight / 2.0F;
            default -> screenHeight / 2 - blockHeight / 2.0F;
        };

        this.graphics.pose().pushPose();
        this.graphics.pose().translate(0.0F, posY, 0.0F);
        this.graphics.pose().scale(scale, scale, 1.0F);

        for (int i = 0; i < this.chatLines.size(); i++) {
            FormattedCharSequence line = this.chatLines.get(i);
            int alpha = (int) (255.0F * charAlpha * this.colorAlpha);
            if (alpha <= 3) continue;
            int y = i * LINE_HEIGHT;
            this.graphics.fill(0, y, (int) (this.width / scale) + 1, y + LINE_HEIGHT, (alpha / 2) << 24);
            int textX = (int) ((this.width / 2.0F - this.mc.font.width(line) * scale / 2.0F) / scale);
            this.graphics.drawString(this.mc.font, line, textX, y + 1, 0xFFFFFF | (alpha << 24), true);
        }

        this.graphics.pose().popPose();
    }
}
