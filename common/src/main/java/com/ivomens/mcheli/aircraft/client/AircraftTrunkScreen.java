package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.aircraft.AircraftPanelMenu;
import com.ivomens.mcheli.ui.client.McContainerScreen;
import com.ivomens.mcheli.ui.client.SlotFrames;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import static com.ivomens.mcheli.ui.client.SlotFrames.SLOT_SIZE;

/** The trunk on its own: the scrolling storage grid of a vehicle or container above the player inventory. */
public final class AircraftTrunkScreen extends McContainerScreen<AircraftPanelMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int SCROLL_TRACK = 0xFF373737;
    private static final int SCROLL_THUMB = 0xFFFFFFFF;
    private static final int MIN_THUMB = 6;

    public AircraftTrunkScreen(AircraftPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = menu.imageWidth;
        this.imageHeight = menu.imageHeight;
    }

    private void menuButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.menu.clickMenuButton(this.minecraft.player, id);
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.menu.getMaxScrollRow() > 0 && mouseY - this.topPos < this.menu.inventoryY) {
            if (scrollY > 0 && this.menu.getScrollRow() > 0) {
                this.menuButton(AircraftPanelMenu.BTN_SCROLL_UP);
            } else if (scrollY < 0 && this.menu.getScrollRow() < this.menu.getMaxScrollRow()) {
                this.menuButton(AircraftPanelMenu.BTN_SCROLL_DOWN);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        String name = this.menu.aircraft.getInventoryName();
        int tabWidth = this.font.width(name) + 8;
        g.fill(x + (this.imageWidth - tabWidth) / 2, y - 14, x + (this.imageWidth + tabWidth) / 2, y, PANEL);
        g.drawCenteredString(this.font, name, x + this.imageWidth / 2, y - 11, this.theme.text());
        g.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        g.drawString(this.font, Component.translatable("mcheli.aircraft_panel.storage", this.menu.trunk.getSlots()),
                x + 8, y + 6, this.theme.label(), false);

        for (int row = 0; row < this.menu.trunkVisibleRows; row++) {
            for (int column = 0; column < this.menu.trunkColumns; column++) {
                int index = (row + this.menu.getScrollRow()) * this.menu.trunkColumns + column;
                if (index < this.menu.trunk.getSlots()) {
                    SlotFrames.slot(g, x + this.menu.trunkX + column * SLOT_SIZE, y + this.menu.trunkY + row * SLOT_SIZE);
                }
            }
        }
        if (this.menu.getMaxScrollRow() > 0) {
            int barX = x + this.menu.trunkX + this.menu.trunkColumns * SLOT_SIZE + 2;
            int track = this.menu.trunkVisibleRows * SLOT_SIZE;
            g.fill(barX, y + this.menu.trunkY, barX + 4, y + this.menu.trunkY + track, SCROLL_TRACK);
            int thumb = Math.max(MIN_THUMB, track * this.menu.trunkVisibleRows / this.menu.trunkRows);
            int thumbY = y + this.menu.trunkY + (track - thumb) * this.menu.getScrollRow() / this.menu.getMaxScrollRow();
            g.fill(barX, thumbY, barX + 4, thumbY + thumb, SCROLL_THUMB);
        }
        SlotFrames.playerInventory(g, x + (this.imageWidth - 9 * SLOT_SIZE) / 2, y + this.menu.inventoryY);
    }
}
