package com.ivomens.mcheli.world.uavstation.client;

import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.ListView;
import com.ivomens.mcheli.ui.client.McContainerScreen;
import com.ivomens.mcheli.ui.client.SlotFrames;
import com.ivomens.mcheli.ui.client.Widgets;
import com.ivomens.mcheli.world.uavstation.UavStationEntity;
import com.ivomens.mcheli.world.uavstation.UavStationGuiData.UavEntry;
import com.ivomens.mcheli.world.uavstation.UavStationMenu;
import com.ivomens.mcheli.world.uavstation.net.UavConnectMessage;
import com.ivomens.mcheli.world.uavstation.net.UavDropMessage;
import com.ivomens.mcheli.world.uavstation.net.UavSpawnOffsetMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

import static com.ivomens.mcheli.world.uavstation.UavStationMenu.LEGACY_SLOT_Y;
import static com.ivomens.mcheli.world.uavstation.UavStationMenu.LIST_ROWS;
import static com.ivomens.mcheli.world.uavstation.UavStationMenu.LIST_WIDTH;
import static com.ivomens.mcheli.world.uavstation.UavStationMenu.LIST_X;
import static com.ivomens.mcheli.world.uavstation.UavStationMenu.LIST_Y;
import static com.ivomens.mcheli.world.uavstation.UavStationMenu.ROW_HEIGHT;

/**
 * The UAV station: the paired UAVs with the selected one's live camera, Connect and Drop, the deploy
 * offset pad and the legacy "deploy a UAV item" slot above the player inventory.
 */
public final class UavStationScreen extends McContainerScreen<UavStationMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int ROW = 0xC0101010;
    private static final int ROW_SELECTED = 0xC0305030;
    private static final int POSITION = 0xFFCCCCCC;
    private static final int DESTROYED = 0xFFAA0000;
    private static final int FEED_WIDTH = 110;
    private static final int FEED_HEIGHT = 90;
    private static final int BUTTON_SIZE = 18;
    private static final int STATUS_DOT_WIDTH = 12;
    private static final int OFFSET_LIMIT = 127;
    private static final String[] AXES = {"X", "Y", "Z"};

    private final WidgetUavCameraFeed feed;
    /** Item icon per UAV item name, so the registry is not searched for every row every frame. */
    private final Map<String, ItemStack> icons = new HashMap<>();
    private UavEntry selected;
    private ListView<UavEntry> list;

    public UavStationScreen(UavStationMenu menu, Inventory inventory, Component title, UavCameraFeed feed) {
        super(menu, inventory, title);
        this.imageWidth = UavStationMenu.WIDTH;
        this.imageHeight = UavStationMenu.HEIGHT;
        this.selected = menu.entries.isEmpty() ? null : menu.entries.getFirst();
        this.feed = new WidgetUavCameraFeed(() -> this.selected, feed);
    }

    @Override
    protected void build() {
        this.list = this.add(Widgets.<UavEntry>list(this.theme, LIST_WIDTH, LIST_ROWS, ROW_HEIGHT, this::paintRow,
                entry -> this.selected = entry).framed(false).rowBackgrounds(false).setItems(this.menu.entries));
        this.list.setPosition(this.leftPos + LIST_X, this.topPos + LIST_Y);
        this.list.select(this.menu.entries.indexOf(this.selected));

        int x = this.feedX();
        int y = this.topPos + LIST_Y + FEED_HEIGHT + 2;
        Layout actions = this.row(x, y).gap(2);
        actions.add(Widgets.button(Component.translatable("mcheli.uav_station.connect"), 54, BUTTON_SIZE, this::connect));
        actions.add(Widgets.button(Component.translatable("mcheli.uav_station.drop"), 54, BUTTON_SIZE, this::drop));
        for (int axis = 0; axis < AXES.length; axis++) {
            int dx = axis == 0 ? 1 : 0;
            int dy = axis == 1 ? 1 : 0;
            int dz = axis == 2 ? 1 : 0;
            Layout pad = this.row(x + 14, y + 32 + axis * 20).gap(2);
            pad.add(Widgets.button(Component.literal("−"), BUTTON_SIZE, BUTTON_SIZE, () -> this.moveOffset(-dx, -dy, -dz)));
            pad.add(Widgets.button(Component.literal("+"), BUTTON_SIZE, BUTTON_SIZE, () -> this.moveOffset(dx, dy, dz)));
        }
    }

    private int feedX() {
        return this.leftPos + LIST_X + LIST_WIDTH + 8;
    }

    private void connect() {
        if (this.selected != null && !this.selected.destroyed()) {
            Network.sendToServer(new UavConnectMessage(this.menu.station.getId(), this.selected.id()));
        }
    }

    private void drop() {
        if (this.selected != null) {
            Network.sendToServer(new UavDropMessage(this.menu.station.getId(), this.selected.id()));
        }
    }

    private void moveOffset(int dx, int dy, int dz) {
        UavStationEntity station = this.menu.station;
        Network.sendToServer(new UavSpawnOffsetMessage(
                (byte) Math.clamp(station.uavOffset().getX() + dx, -OFFSET_LIMIT, OFFSET_LIMIT),
                (byte) Math.clamp(station.uavOffset().getY() + dy, -OFFSET_LIMIT, OFFSET_LIMIT),
                (byte) Math.clamp(station.uavOffset().getZ() + dz, -OFFSET_LIMIT, OFFSET_LIMIT)));
    }

    @Override
    public void removed() {
        super.removed();
        this.feed.close();
    }

    private void paintRow(GuiGraphics g, UavEntry entry, int x, int y, int width, boolean selected, boolean hovered) {
        g.fill(x, y, x + width, y + ROW_HEIGHT - 2, selected ? ROW_SELECTED : ROW);
        g.drawString(this.font, selected ? "▶" : "▷", x + 2, y + 6, this.theme.text(), false);
        ItemStack icon = entry.itemName() == null ? ItemStack.EMPTY
                : this.icons.computeIfAbsent(entry.itemName(), UavStationScreen::stackFor);
        if (!icon.isEmpty()) {
            g.renderItem(icon, x + 12, y + 2);
        }
        g.drawString(this.font, this.font.plainSubstrByWidth(entry.displayName(), 70), x + 30, y + 6, this.theme.text(),
                false);
        g.drawString(this.font, String.format("(%d, %d, %d)", entry.x(), entry.y(), entry.z()), x + 102, y + 2, POSITION,
                false);
        g.drawString(this.font, entry.loaded() ? entry.hp() + "/" + entry.maxHp() : "?", x + 102, y + 11,
                entry.destroyed() ? DESTROYED : this.theme.good(), false);
        if (entry.destroyed()) {
            g.drawString(this.font, Component.translatable("mcheli.uav_station.destroyed"), x + 160, y + 11, DESTROYED,
                    false);
        } else {
            g.drawString(this.font, "●", x + width - 10, y + 6, entry.reachable() ? this.theme.good() : this.theme.bad(),
                    false);
        }
    }

    private static ItemStack stackFor(String itemName) {
        ResourceLocation id = itemName.isEmpty() ? null : ResourceLocation.tryParse(itemName);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        g.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        g.drawString(this.font, Component.translatable("mcheli.uav_station.title"), x + 8, y + 6, this.theme.label(),
                false);
        if (this.menu.entries.isEmpty()) {
            g.drawString(this.font, Component.translatable("mcheli.uav_station.empty"), x + LIST_X + 4, y + LIST_Y + 4,
                    this.theme.label(), false);
        }

        int feedX = this.feedX();
        int feedY = y + LIST_Y;
        this.feed.render(g, feedX, feedY, FEED_WIDTH, FEED_HEIGHT, partialTick);
        int padY = feedY + FEED_HEIGHT + 2;
        g.drawString(this.font, Component.translatable("mcheli.uav_station.offset"), feedX, padY + 22, this.theme.label(),
                false);
        UavStationEntity station = this.menu.station;
        int[] offset = {station.uavOffset().getX(), station.uavOffset().getY(), station.uavOffset().getZ()};
        for (int axis = 0; axis < AXES.length; axis++) {
            g.drawString(this.font, AXES[axis], feedX + 2, padY + 37 + axis * 20, this.theme.label(), false);
            g.drawString(this.font, String.format("%+d", offset[axis]), feedX + 58, padY + 37 + axis * 20,
                    this.theme.label(), false);
        }

        SlotFrames.slot(g, x + LIST_X, y + LEGACY_SLOT_Y);
        g.drawString(this.font, Component.translatable("mcheli.uav_station.deploy_slot"), x + LIST_X + 24,
                y + LEGACY_SLOT_Y + 5, this.theme.label(), false);
        SlotFrames.playerInventory(g, x + (this.imageWidth - 9 * SlotFrames.SLOT_SIZE) / 2,
                y + UavStationMenu.INVENTORY_Y);
    }

    /** The reachability dot at the end of each row explains itself. */
    @Override
    protected void renderForeground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        UavEntry entry = this.list.itemAt(mouseX, mouseY);
        if (entry == null || mouseX < this.list.getRight() - STATUS_DOT_WIDTH) {
            return;
        }
        Component tip = entry.destroyed()
                ? Component.translatable("mcheli.uav_station.cannot_connect").withStyle(ChatFormatting.DARK_RED)
                : Component.translatable(entry.reachable() ? "mcheli.uav_station.in_range" : "mcheli.uav_station.out_of_range");
        g.renderTooltip(this.font, tip, mouseX, mouseY);
    }
}
