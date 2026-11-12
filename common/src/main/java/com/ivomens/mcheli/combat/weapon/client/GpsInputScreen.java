package com.ivomens.mcheli.combat.weapon.client;

import com.ivomens.mcheli.combat.weapon.GPSPosition;
import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.McScreen;
import com.ivomens.mcheli.ui.client.Widgets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

/**
 * Manual entry of the GPS target coordinates. Confirming sets them through {@link GPSPosition#set},
 * which keeps the client copy and syncs it to the server for GPS-guided weapons. Opened with the
 * "Open GPS Panel" key (default K) while operating an aircraft.
 */
public final class GpsInputScreen extends McScreen {

    private static final int FIELD_WIDTH = 200;
    private static final int FIELD_HEIGHT = 20;
    private static final int FIELD_SPACING = 26;
    private static final int MAX_LENGTH = 24;
    private static final int AXIS_LABEL_OFFSET = 12;

    private final Player player;
    private EditBox xField;
    private EditBox yField;
    private EditBox zField;
    private Component message = Component.empty();

    public GpsInputScreen(Player player) {
        super(Component.translatable("mcheli.gps.title"));
        this.player = player;
    }

    @Override
    protected void build() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        this.add(Widgets.button(Component.translatable("mcheli.gps.fill"), 204, 20, this::fillFromPlayerPosition))
                .setPosition(centerX - 102, centerY + 44);
        Layout actions = this.row(centerX - 102, centerY + 68).gap(4);
        actions.add(Widgets.button(Component.translatable("gui.done"), 100, 20, this::apply));
        actions.add(Widgets.button(Component.translatable("gui.cancel"), 100, 20, this::onClose));

        Layout fields = this.column(centerX - 100, centerY - 34).gap(FIELD_SPACING - FIELD_HEIGHT);
        this.xField = fields.add(this.field("X"));
        this.yField = fields.add(this.field("Y"));
        this.zField = fields.add(this.field("Z"));
        this.setInitialFocus(this.xField);

        GPSPosition gps = GPSPosition.currentClientGPSPosition;
        if (gps != null && gps.isActive()) {
            this.show(gps.x(), gps.y(), gps.z());
        }
    }

    private EditBox field(String axis) {
        return Widgets.text(FIELD_WIDTH, FIELD_HEIGHT, MAX_LENGTH, Component.literal(axis));
    }

    private void show(double x, double y, double z) {
        this.xField.setValue(String.valueOf(x));
        this.yField.setValue(String.valueOf(y));
        this.zField.setValue(String.valueOf(z));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.apply();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        graphics.drawCenteredString(this.font, this.title, centerX, centerY - 62, this.theme.text());
        for (EditBox field : new EditBox[]{this.xField, this.yField, this.zField}) {
            graphics.drawString(this.font, field.getMessage(), field.getX() - AXIS_LABEL_OFFSET, field.getY() + 6,
                    this.theme.text());
        }
        graphics.drawCenteredString(this.font, this.message, centerX, centerY + 94, this.theme.bad());
    }

    private void apply() {
        try {
            double x = Double.parseDouble(this.xField.getValue().trim());
            double y = Double.parseDouble(this.yField.getValue().trim());
            double z = Double.parseDouble(this.zField.getValue().trim());
            GPSPosition.set(x, y, z, true, this.player);
            this.onClose();
        } catch (NumberFormatException e) {
            this.message = Component.translatable("mcheli.gps.invalid");
        }
    }

    private void fillFromPlayerPosition() {
        this.show(this.player.getX(), this.player.getY(), this.player.getZ());
        this.message = Component.empty();
    }
}
