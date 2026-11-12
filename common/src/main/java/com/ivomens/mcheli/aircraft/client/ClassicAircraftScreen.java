package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftMenu;
import com.ivomens.mcheli.aircraft.net.AircraftCommandMessage;
import com.ivomens.mcheli.aircraft.net.GuiOpener;
import com.ivomens.mcheli.aircraft.net.OpenScreenMessage;
import com.ivomens.mcheli.aircraft.net.ResupplyMessage;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponKind;
import com.ivomens.mcheli.content.registry.ContentBindings;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.McContainerScreen;
import com.ivomens.mcheli.ui.client.Widgets;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

/**
 * The classic aircraft screen of the 1.7 mod: fuel, parachutes, weapon resupply and the aircraft's
 * command line, over {@link AircraftMenu}.
 */
public final class ClassicAircraftScreen extends McContainerScreen<AircraftMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Ids.MOD_ID,
            "textures/gui/gui.png");
    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF0000;
    private static final int FULL_COLOR = 0xFF28D448;
    private static final int LOW_FUEL_PERCENT = 20;
    /** Ticks between re-checking whether the current weapon can be resupplied. */
    private static final int RELOAD_CHECK_INTERVAL = 20;
    private static final int FIRST_RELOAD_CHECK = 10;
    private static final int RELOAD_DELAY = 3;
    private static final int COMMAND_MAX_LENGTH = 512;

    private final Player player;
    private final AircraftEntity aircraft;
    private final Runnable openOptions;
    private Button reload;
    private EditBox command;
    private int weapon;
    private int reloadWait;

    /** @param openOptions opens the mod's options screen, from the options button */
    public ClassicAircraftScreen(AircraftMenu menu, Inventory inventory, Component title, Runnable openOptions) {
        super(menu, inventory, title);
        this.openOptions = openOptions;
        this.aircraft = menu.aircraft;
        this.player = inventory.player;
        this.imageWidth = 210;
        this.imageHeight = 236;
    }

    @Override
    protected void build() {
        int x = this.leftPos;
        int y = this.topPos;
        this.add(this.button("options", 80, this.openOptions)).setPosition(x + 120, y + 110);
        this.add(this.button("close", 40, this::closeScreen)).setPosition(x + 160, y + 10);
        this.reload = this.add(this.button("reload", 50, this::resupply));
        this.reload.setPosition(x + 85, y + 40);
        this.reload.active = this.canReload();
        boolean several = this.aircraft.weapons().count() >= 2;
        Layout cycle = this.row(x + 140, y + 40);
        cycle.add(Widgets.button(Component.literal("<<"), 20, 20, () -> this.cycleWeapon(-1))).active = several;
        cycle.add(Widgets.button(Component.literal(">>"), 20, 20, () -> this.cycleWeapon(1))).active = several;
        if (this.aircraft.getSizeInventory() > 0) {
            this.add(this.button("inventory", 80,
                    () -> Network.sendToServer(new OpenScreenMessage(GuiOpener.GUIID_INVENTORY)))).setPosition(x + 120, y + 90);
        }
        this.command = this.add(Widgets.text(160, 15, COMMAND_MAX_LENGTH, Component.translatable("mcheli.aircraft.command")));
        this.command.setPosition(x + 25, y + 215);
        this.command.setValue(this.aircraft.wreck().command());
        this.weapon = 0;
        this.reloadWait = FIRST_RELOAD_CHECK;
    }

    private Button button(String name, int width, Runnable onPress) {
        return Widgets.button(Component.translatable("mcheli.aircraft." + name), width, 20, onPress);
    }

    private boolean canReload() {
        return this.aircraft.ammoSupply().canPlayerSupply(this.player, this.aircraft.weapons().get(this.weapon));
    }

    /** Sends the command line, cut to what the server accepts, and closes. */
    private void closeScreen() {
        String text = this.command.getValue();
        if (text.length() > AircraftCommandMessage.MAX_LENGTH) {
            text = text.substring(0, AircraftCommandMessage.MAX_LENGTH);
        }
        Network.sendToServer(new AircraftCommandMessage(text));
        this.onClose();
    }

    private void resupply() {
        this.reload.active = this.canReload();
        if (this.reload.active) {
            Network.sendToServer(new ResupplyMessage(this.aircraft.getId(), this.weapon));
            this.aircraft.ammoSupply().supply(this.aircraft.weapons().get(this.weapon));
            this.reloadWait = RELOAD_DELAY;
            this.reload.active = false;
        }
    }

    private void cycleWeapon(int step) {
        this.weapon = Math.floorMod(this.weapon + step, this.aircraft.weapons().count());
        this.reload.active = this.canReload();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.reloadWait > 0 && --this.reloadWait == 0) {
            this.reload.active = this.canReload();
            this.reloadWait = RELOAD_CHECK_INTERVAL;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.closeScreen();
            return true;
        }
        if (!this.command.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String text = this.command.getValue().trim();
            if (text.startsWith("/")) {
                text = text.substring(1);
            }
            if (!text.isEmpty()) {
                this.minecraft.player.connection.sendCommand(text);
            }
        } else {
            this.command.keyPressed(keyCode, scanCode, modifiers);
        }
        // Typed keys, the inventory key among them, stay away from the screen.
        return true;
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        g.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
        for (int i = 0; i < this.aircraft.ejectionSeats().count(); i++) {
            g.blit(TEXTURE, x + 10 + 18 * i - 1, y + 105 - 1, 215, 55, 18, 18);
        }
        int gauge = (int) (this.aircraft.fuel().percentage() * 50.0F);
        gauge = gauge >= 99 ? 100 : gauge;
        g.blit(TEXTURE, x + 57, y + 30 + 50 - gauge, 215, 0, 12, gauge);
        int percent = (int) (this.aircraft.fuel().percentage() * 100.0F + 0.5);
        g.drawString(this.font, String.format("%3d", percent) + "%", x + 30, y + 65,
                percent > LOW_FUEL_PERCENT ? FULL_COLOR : RED);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.aircraft.getGuiInventory().getInventoryName(), 10, 10, WHITE);
        if (this.aircraft.ejectionSeats().count() > 0) {
            g.drawString(this.font, Component.translatable("mcheli.aircraft.parachute"), 9, 95, WHITE);
        }
        if (this.aircraft.weapons().count() <= 0) {
            g.drawString(this.font, Component.translatable("mcheli.aircraft.no_weapon"), 79, 45, WHITE);
            return;
        }
        WeaponGroup group = this.aircraft.weapons().get(this.weapon);
        if (group == null || group.kind() == WeaponKind.DUMMY) {
            return;
        }
        g.drawString(this.font, group.displayName(), 79, 30, WHITE);
        int total = group.reserveAmmo() + group.ammo();
        int color = total == group.maxAmmo() ? FULL_COLOR : total == 0 ? RED : WHITE;
        g.drawString(this.font, String.format("%4d/%4d", total, group.maxAmmo()), 145, 70, color);
        // Resupply cost: the items and how many of each.
        int x = 85;
        for (ContentBindings.RoundStack round : ContentBindings.rounds(group.spec().id())) {
            if (!round.stack().isEmpty()) {
                g.renderItem(round.stack(), x, 62);
                g.renderItemDecorations(this.font, round.stack(), x, 62);
            }
            g.drawString(this.font, Integer.toString(round.count()), x + 5, 80, WHITE);
            x += 20;
        }
    }
}
