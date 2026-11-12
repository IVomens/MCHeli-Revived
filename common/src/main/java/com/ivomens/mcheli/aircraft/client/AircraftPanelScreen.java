package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftPanelMenu.WeaponEntry;
import com.ivomens.mcheli.aircraft.AircraftPanelMenu;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.net.ResupplyMessage;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.content.registry.ContentBindings;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.ship.ShipEntity;
import com.ivomens.mcheli.sound.client.UiSounds;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.ui.client.McContainerScreen;
import com.ivomens.mcheli.ui.client.SlotFrames;
import com.ivomens.mcheli.ui.client.Widgets;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The aircraft panel: fuel gauge with fuel in and out slots and the tank dump, the model viewport with
 * speed and health, parachute slots, the scrolling trunk, the weapon list with ammunition and resupply
 * buttons, and the player inventory.
 */
public final class AircraftPanelScreen extends McContainerScreen<AircraftPanelMenu> {

    private static final ResourceLocation WEAPON_ICONS = tex("textures/gui/wepon_icons.png");
    private static final ResourceLocation PARACHUTE_SLOT = tex("textures/gui/parachute_slot.png");
    private static final ResourceLocation FUEL_SLOT = tex("textures/gui/fuel_slot.png");
    private static final ResourceLocation FUEL = tex("textures/gui/fuel_.png");
    private static final ResourceLocation RELOAD_LIGHT = tex("textures/gui/reload_light.png");
    private static final ResourceLocation RELOAD_DARK = tex("textures/gui/reload_dark.png");
    private static final WeaponIcon APFSDS_SHELL = new WeaponIcon(0, 0);
    private static final WeaponIcon HE_SHELL = new WeaponIcon(18, 0);
    private static final WeaponIcon HEAT_SHELL = new WeaponIcon(36, 0);
    private static final WeaponIcon CANISTER_SHELL = new WeaponIcon(54, 0);
    private static final WeaponIcon LARGE_CALIBER_MG = new WeaponIcon(0, 18);
    private static final WeaponIcon MORTAR_SHOT = new WeaponIcon(18, 18);
    private static final WeaponIcon BOMB = new WeaponIcon(0, 36);
    private static final WeaponIcon CLUSTER_BOMB = new WeaponIcon(18, 36);
    private static final WeaponIcon GATLING_BULLET = new WeaponIcon(36, 36);
    private static final WeaponIcon AAM_ROCKET = new WeaponIcon(54, 36);
    private static final WeaponIcon TV_ROCKET = new WeaponIcon(72, 36);
    private static final WeaponIcon SAM_ROCKET = new WeaponIcon(90, 36);
    private static final WeaponIcon NUC_WARHEAD = new WeaponIcon(108, 36);
    private static final WeaponIcon MG_7_62MM = new WeaponIcon(126, 36);
    private static final WeaponIcon MG_12_7MM = new WeaponIcon(144, 36);
    private static final WeaponIcon NO_ICON = new WeaponIcon(-1, -1);
    /** Icon per weapon display name; guessing from the name is too slow to repeat every frame. */
    private static final Map<String, WeaponIcon> ICON_BY_NAME = new HashMap<>();

    private static ResourceLocation tex(String path) {
        return Ids.of(path);
    }

    /** A 16x16 icon in {@code wepon_icons.png} (160x53). */
    private record WeaponIcon(int u, int v) {
        void draw(GuiGraphics g, int x, int y) {
            g.blit(WEAPON_ICONS, x, y, 16, 16, u, v, 16, 16, 160, 53);
        }
    }

    /** Guesses the ammunition icon from the weapon name. */
    private static WeaponIcon determineIcon(@NotNull String name) {
        String lowerCase = name.toLowerCase(Locale.ROOT);
        if (lowerCase.contains("apfsds")) return APFSDS_SHELL;
        if (lowerCase.contains("heat")) return HEAT_SHELL;
        if ((name.contains("HE") && lowerCase.contains("cannon")) || lowerCase.contains("high explosive") || lowerCase.contains("hesh"))
            return HE_SHELL;
        if (lowerCase.contains("canister")) return CANISTER_SHELL;
        if (lowerCase.contains("machine gun") || lowerCase.contains("browning") || lowerCase.contains("mg")) {
            if (lowerCase.contains("12.7mm")) return MG_12_7MM;
            if (lowerCase.contains("7.62mm")) return MG_7_62MM;
            if (lowerCase.contains("gatling")) return GATLING_BULLET;
            return LARGE_CALIBER_MG;
        }
        if (lowerCase.contains("aam")) return AAM_ROCKET;
        if (lowerCase.contains("sam")) return SAM_ROCKET;
        if (name.contains("TV")) return TV_ROCKET;
        if (lowerCase.contains("nuc") || lowerCase.contains("nuclear")) return NUC_WARHEAD;
        if (lowerCase.contains("cluster")) return CLUSTER_BOMB;
        if (lowerCase.contains("bomb")) return BOMB;
        if (lowerCase.contains("mortar")) return MORTAR_SHOT;
        return NO_ICON;
    }

    public static ResourceLocation getTexturePath(AircraftEntity ac) {
        String path = switch (ac) {
            case HelicopterEntity heli -> "helicopters/" + heli.appearance().texture();
            case PlaneEntity plane -> "planes/" + plane.appearance().texture();
            case GroundVehicleEntity vehicle -> "vehicles/" + vehicle.appearance().texture();
            case ShipEntity ship -> "ships/" + ship.appearance().texture();
            case TankEntity tank -> "tanks/" + tank.appearance().texture();
            default -> throw new IllegalStateException("Unexpected value: " + ac);
        };
        return Ids.of("textures/" + path + ".png");
    }


    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_DARK = 0xFF373737;
    private static final int ROW_BG = 0xC0101010;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 118;
    private static final int LIST_W = 300;
    private static final int ROW_H = 22;

    private final AircraftEntity aircraft;
    private final Player player;
    private final Runnable openOptions;
    private final WidgetGauge gauge = WidgetGauge.make(0.4f);
    private final WidgetAircraftViewport viewport;
    private int listScroll;

    /** @param openOptions opens the mod's options screen, from the gear button */
    public AircraftPanelScreen(AircraftPanelMenu menu, Inventory inventory, Component title, Runnable openOptions) {
        super(menu, inventory, title);
        this.openOptions = openOptions;
        this.aircraft = menu.aircraft;
        this.player = inventory.player;
        this.imageWidth = menu.imageWidth;
        this.imageHeight = menu.imageHeight;
        this.viewport = new WidgetAircraftViewport(getTexturePath(this.aircraft), this.aircraft.spec());
    }

    @Override
    protected void build() {
        this.add(Widgets.button(Component.empty(), 18, 18, () -> this.menuButton(AircraftPanelMenu.BTN_DUMP_FUEL)))
                .setPosition(this.leftPos + 40, this.topPos + 86);
        this.add(Widgets.button(Component.literal("⚙"), 18, 18, this.openOptions))
                .setPosition(this.leftPos + this.imageWidth + 2, this.topPos + this.menu.inventoryY);
    }

    private void menuButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.menu.clickMenuButton(this.player, id);
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private List<WeaponEntry> visibleWeapons() {
        List<WeaponEntry> all = this.menu.weaponEntries;
        int from = Math.min(this.listScroll, Math.max(0, all.size() - 4));
        return all.subList(from, Math.min(all.size(), from + 4));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int lx = (int) mouseX - this.leftPos;
        int ly = (int) mouseY - this.topPos;
        if (this.menu.trunkRows > 0 && lx >= this.menu.trunkX && lx < this.menu.trunkX + this.menu.trunkColumns * AircraftPanelMenu.SLOT_SIZE + 4
                && ly >= this.menu.trunkY && ly < this.menu.trunkY + this.menu.trunkVisibleRows * AircraftPanelMenu.SLOT_SIZE + 2) {
            if (scrollY > 0 && this.menu.getScrollRow() > 0) this.menuButton(AircraftPanelMenu.BTN_SCROLL_UP);
            else if (scrollY < 0 && this.menu.getScrollRow() < this.menu.getMaxScrollRow()) this.menuButton(AircraftPanelMenu.BTN_SCROLL_DOWN);
            return true;
        }
        if (lx >= LIST_X && lx < LIST_X + LIST_W && ly >= LIST_Y && ly < LIST_Y + 4 * ROW_H) {
            int max = Math.max(0, this.menu.weaponEntries.size() - 4);
            this.listScroll = Math.max(0, Math.min(max, this.listScroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int lx = (int) mouseX - this.leftPos;
            int ly = (int) mouseY - this.topPos;
            List<WeaponEntry> rows = this.visibleWeapons();
            for (int i = 0; i < rows.size(); i++) {
                int bx = LIST_X + LIST_W - 22;
                int by = LIST_Y + i * ROW_H + 2;
                if (lx >= bx && lx < bx + 18 && ly >= by && ly < by + 18) {
                    WeaponEntry entry = rows.get(i);
                    if (AircraftPanelMenu.canResupply(entry.set(), this.aircraft, this.player)) {
                        Network.sendToServer(new ResupplyMessage(this.aircraft.getId(), entry.id()));
                        this.aircraft.ammoSupply().supply(entry.set());
                        UiSounds.click();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        int x = this.leftPos, y = this.topPos;
        // Title tab
        String name = Component.translatable(this.aircraft.identity().translationKey()).getString();
        int tw = this.font.width(name) + 8;
        g.fill(x, y - 14, x + tw, y, PANEL);
        g.drawString(this.font, name, x + 4, y - 11, 0x404040, false);

        int panelBottom = this.menu.inventoryY - 6;
        g.fill(x, y, x + this.imageWidth, y + panelBottom, PANEL);
        int invX = x + (this.imageWidth - 9 * AircraftPanelMenu.SLOT_SIZE) / 2 - 6;
        g.fill(invX, y + this.menu.inventoryY - 5, invX + 9 * AircraftPanelMenu.SLOT_SIZE + 12, y + this.imageHeight, PANEL);

        this.renderFuel(g, x, y);
        this.renderViewport(g, x, y, partialTicks);
        if (this.menu.trunk.getSlots() > 0) {
            this.renderTrunk(g, x, y);
        }
        List<WeaponEntry> rows = this.visibleWeapons();
        for (int i = 0; i < rows.size(); i++) {
            drawWeaponRow(g, rows.get(i), x + LIST_X, y + LIST_Y + i * ROW_H, mouseX, mouseY);
        }
        SlotFrames.playerInventory(g, x + (this.imageWidth - 9 * AircraftPanelMenu.SLOT_SIZE) / 2, y + this.menu.inventoryY);
    }

    /** The gauge, the amount and the fuel in, dump and out slots. */
    private void renderFuel(GuiGraphics g, int x, int y) {
        this.gauge.render(g, x + 6, y + 6, this.aircraft.fuel().percentage());
        int fuelColor = this.aircraft.fuel().percentage() >= 1.0F ? 0xFFB2FF59 : (this.aircraft.fuel().percentage() <= 0.0F ? 0xFFFF5252 : 0xFFFFFFFF);
        g.fill(x + 6, y + 56, x + 90, y + 68, 0xFF000000);
        g.drawCenteredString(this.font, String.format("%d mB", this.aircraft.fuel().amount()), x + 48, y + 58, fuelColor);
        g.blit(FUEL_SLOT, x + 9, y + 85, 0, 0, 18, 18, 18, 18);
        g.blit(FUEL, x + 41, y + 87, 16, 16, 0, 0, 32, 32, 32, 32);
        SlotFrames.slot(g, x + 65, y + 85);
    }

    /** The turning model with its speed and health, and the parachute slots under it. */
    private void renderViewport(GuiGraphics g, int x, int y, float partialTicks) {
        int vx = x + 96, vy = y + 6;
        int vh = this.menu.showParachuteSlot ? 66 : 86;
        g.fill(vx, vy, vx + 110, vy + vh, PANEL_DARK);
        this.viewport.render(g, vx, vy, 110, vh, partialTicks);
        g.fill(vx, vy + vh, vx + 110, vy + vh + 12, 0xFF000000);
        g.drawString(this.font, String.format("%.0f m/s", this.aircraft.getCurrentSpeed()), vx + 2, vy + vh + 2, 0xFFFFFF, false);
        g.drawString(this.font, Component.translatable("mcheli.aircraft_panel.hp", this.aircraft.damage().hp(),
                this.aircraft.damage().maxHp()), vx + 48, vy + vh + 2,
                0x55FF55, false);
        if (this.menu.showParachuteSlot) {
            for (int i = 0; i < 2; i++) g.blit(PARACHUTE_SLOT, x + 97 + i * AircraftPanelMenu.SLOT_SIZE, y + 93, 0, 0, 18, 18, 18, 18);
        }
    }

    /** The visible trunk rows and, when it scrolls, its scroll bar. */
    private void renderTrunk(GuiGraphics g, int x, int y) {
        g.drawString(this.font, Component.translatable("mcheli.aircraft_panel.storage", this.menu.trunk.getSlots()),
                x + this.menu.trunkX, y + this.menu.trunkY - 10, this.theme.label(), false);
        for (int r = 0; r < this.menu.trunkVisibleRows; r++) {
            for (int c = 0; c < this.menu.trunkColumns; c++) {
                int idx = (r + this.menu.getScrollRow()) * this.menu.trunkColumns + c;
                if (idx < this.menu.trunk.getSlots()) {
                    SlotFrames.slot(g, x + this.menu.trunkX + c * AircraftPanelMenu.SLOT_SIZE, y + this.menu.trunkY + r * AircraftPanelMenu.SLOT_SIZE);
                }
            }
        }
        if (this.menu.getMaxScrollRow() > 0) {
            int sx = x + this.menu.trunkX + this.menu.trunkColumns * AircraftPanelMenu.SLOT_SIZE + 1;
            int sh = this.menu.trunkVisibleRows * AircraftPanelMenu.SLOT_SIZE;
            g.fill(sx, y + this.menu.trunkY, sx + 4, y + this.menu.trunkY + sh, PANEL_DARK);
            int kh = Math.max(6, sh * this.menu.trunkVisibleRows / this.menu.trunkRows);
            int ky = y + this.menu.trunkY + (sh - kh) * this.menu.getScrollRow() / this.menu.getMaxScrollRow();
            g.fill(sx, ky, sx + 4, ky + kh, 0xFFFFFFFF);
        }
    }

    private void drawWeaponRow(GuiGraphics g, WeaponEntry entry, int x, int y, int mouseX, int mouseY) {
        WeaponGroup ws = entry.set();
        g.fill(x, y, x + LIST_W, y + ROW_H - 2, ROW_BG);
        int cx = x + 2;
        WeaponIcon icon = ICON_BY_NAME.computeIfAbsent(ws.displayName(), AircraftPanelScreen::determineIcon);
        if (icon != NO_ICON) {
            icon.draw(g, cx, y + 2);
        }
        cx += 18;
        String name = ws.displayName();
        String shown = this.font.plainSubstrByWidth(name, 90);
        g.drawString(this.font, shown, cx, y + 6, 0xFFFFFF, false);
        cx += 92;
        g.drawString(this.font, String.format("%d/%d", ws.ammo(), ws.magazineSize()), cx, y + 6, 0xFFFFFF, false);
        cx += 46;
        g.drawString(this.font, String.format("(%d)", ws.reserveAmmo()), cx, y + 6, 0xAAAAAA, false);
        cx += 34;
        WeaponSpec info = ws.spec();
        if (info != null) {
            for (ContentBindings.RoundStack round : ContentBindings.rounds(info.id())) {
                if (cx > x + LIST_W - 50) break;
                String n = round.count() + "x";
                g.drawString(this.font, n, cx, y + 6, 0xFFFFFF, false);
                cx += this.font.width(n);
                g.pose().pushPose();
                g.pose().translate(cx, y + 3, 0);
                g.pose().scale(0.75F, 0.75F, 1.0F);
                g.renderItem(round.stack(), 0, 0);
                g.pose().popPose();
                cx += 16;
            }
        }
        boolean can = AircraftPanelMenu.canResupply(ws, this.aircraft, this.player);
        int bx = x + LIST_W - 22, by = y + 2;
        g.fill(bx, by, bx + 18, by + 18, can ? 0xFF6F6F6F : 0xFF2F2F2F);
        g.blit(can ? RELOAD_LIGHT : RELOAD_DARK, bx + 1, by + 1, 16, 16, 0, 0, 18, 18, 18, 18);
    }

    /** Slot frames of the main inventory and, 4 pixels below, the hotbar; (x, y) is the first slot. */
    @Override
    protected void renderForeground(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        int lx = mouseX - this.leftPos, ly = mouseY - this.topPos;
        if ((lx >= 6 && lx < 90 && ly >= 6 && ly < 68)) {
            g.renderComponentTooltip(this.font, fuelTooltip(), mouseX, mouseY);
        } else if (lx >= 40 && lx < 58 && ly >= 86 && ly < 104) {
            g.renderComponentTooltip(this.font, List.of(Component.translatable("mcheli.aircraft_panel.dump"),
                    Component.translatable("mcheli.aircraft_panel.dump.warning").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                            .append(Component.translatable("mcheli.aircraft_panel.dump.lost").withStyle(ChatFormatting.RED))),
                    mouseX, mouseY);
        } else {
            List<WeaponEntry> rows = this.visibleWeapons();
            for (int i = 0; i < rows.size(); i++) {
                int bx = LIST_X + LIST_W - 22, by = LIST_Y + i * ROW_H + 2;
                if (lx >= bx && lx < bx + 18 && ly >= by && ly < by + 18) {
                    List<Component> tip = new ArrayList<>();
                    tip.add(Component.translatable("mcheli.aircraft_panel.reload"));
                    List<ItemStack> missing = this.aircraft.ammoSupply().missingAmmo(this.player, rows.get(i).set());
                    if (!missing.isEmpty()) {
                        tip.add(Component.translatable("mcheli.aircraft_panel.missing_ammo").withStyle(ChatFormatting.RED));
                        for (ItemStack s : missing) {
                            tip.add(Component.translatable("mcheli.aircraft_panel.missing_item", s.getCount(), s.getHoverName()).withStyle(ChatFormatting.GRAY));
                        }
                    }
                    g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
                } else if (lx >= LIST_X + 20 && lx < LIST_X + 110 && ly >= LIST_Y + i * ROW_H && ly < LIST_Y + (i + 1) * ROW_H) {
                    g.renderTooltip(this.font, Component.literal(rows.get(i).set().displayName()), mouseX, mouseY);
                }
            }
        }
    }

    private List<Component> fuelTooltip() {
        List<Component> lines = new ArrayList<>();
        VehicleSpec info = this.aircraft.spec();
        String type = this.aircraft.fuel().fuelType();
        Fluid fluid = this.aircraft.fuel().amount() > 0 && type != null ? AircraftPanelMenu.fluidByName(type) : null;
        lines.add(Component.translatable("mcheli.aircraft_panel.fuel").withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable("mcheli.aircraft_panel.current_fuel", AircraftPanelMenu.fluidName(fluid), String.format("%.2f",
                info != null && type != null ? info.getFuelConsumption(type) : 1.0F)));
        lines.add(Component.translatable("mcheli.aircraft_panel.fuel_amount", this.aircraft.fuel().amount(),
                this.aircraft.fuel().capacity()));
        lines.add(Component.translatable("mcheli.aircraft_panel.accepted_fuels"));
        if (info != null && info.file().fuel().types() != null) {
            info.file().fuel().types().forEach((fuel, eff) -> {
                ChatFormatting color;
                if (eff == 1f) color = ChatFormatting.WHITE;
                else if (eff < 0.5f) color = ChatFormatting.BLUE;
                else if (eff < 1.0f) color = ChatFormatting.GREEN;
                else if (eff <= 1.5f) color = ChatFormatting.YELLOW;
                else color = ChatFormatting.RED;
                Fluid f = AircraftPanelMenu.fluidByName(fuel);
                if (f == null) return;
                lines.add(Component.literal(String.format("- %s : %.2f", AircraftPanelMenu.fluidName(f), eff)).withStyle(color));
            });
        }
        return lines;
    }
}
