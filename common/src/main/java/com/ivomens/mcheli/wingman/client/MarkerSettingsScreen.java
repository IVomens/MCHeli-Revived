package com.ivomens.mcheli.wingman.client;

import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.McScreen;
import com.ivomens.mcheli.ui.client.TabBar;
import com.ivomens.mcheli.ui.client.Widgets;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import com.ivomens.mcheli.wingman.net.MarkerSettings;
import com.ivomens.mcheli.wingman.net.UpdateMarkerMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Settings screen of a marker block: type, id, parent base and, for parking spots, the heading to park
 * in. Opened by right-clicking any marker except a BASE.
 */
public final class MarkerSettingsScreen extends McScreen {

    private static final MarkerType[] TYPE_ORDER = {
            MarkerType.BASE, MarkerType.RUNWAY_A, MarkerType.RUNWAY_B, MarkerType.PARKING,
            MarkerType.WAYPOINT, MarkerType.HELIPAD, MarkerType.HELIPAD_B
    };
    private static final int TYPES_PER_FIRST_ROW = 4;
    private static final int TYPE_WIDTH = 78;
    private static final int GAP = 4;
    private static final int FIELD_MAX_LENGTH = 64;
    /** "Any", then north, east, south and west (headings -1 to 3). */
    private static final String[] HEADINGS = {"any", "north", "east", "south", "west"};
    private static final int HEADING_WIDTH = 44;
    private static final int SELECTED_FRAME = 0xAAFFAA00;

    private final BlockPos pos;
    private final List<Button> headingButtons = new ArrayList<>();
    private MarkerType type;
    /** -1 any, 0 north, 1 east, 2 south, 3 west. */
    private int parkingHeading;
    private String id;
    private String baseId;
    private EditBox idField;
    private EditBox baseIdField;

    public MarkerSettingsScreen(BlockPos pos, MarkerSettings settings) {
        super(Component.translatable("mcheli.wingman.marker.title"));
        this.pos = pos;
        this.type = settings.type();
        this.id = settings.id();
        this.baseId = settings.baseId();
        this.parkingHeading = settings.parkingHeading();
    }

    @Override
    protected void build() {
        int cx = this.width / 2;
        int top = this.height / 4;
        TabBar<MarkerType> types = Widgets.tabs(this.theme, this.type, this::selectType);
        this.addTypeRow(types, 0, TYPES_PER_FIRST_ROW, cx, top);
        this.addTypeRow(types, TYPES_PER_FIRST_ROW, TYPE_ORDER.length, cx, top + 24);
        this.addRenderableOnly(types);

        this.idField = this.field(cx - 100, top + 60, this.id, value -> this.id = value);
        this.setInitialFocus(this.idField);
        if (this.type != MarkerType.BASE) {
            this.baseIdField = this.field(cx - 100, top + 96, this.baseId, value -> this.baseId = value);
        } else {
            this.baseIdField = null;
        }

        this.headingButtons.clear();
        if (this.type == MarkerType.PARKING) {
            int width = HEADINGS.length * HEADING_WIDTH + (HEADINGS.length - 1) * GAP;
            Layout headings = this.row(cx - width / 2, top + 130).gap(GAP);
            for (int i = 0; i < HEADINGS.length; i++) {
                int heading = i - 1;
                this.headingButtons.add(headings.add(Widgets.button(
                        Component.translatable("mcheli.wingman.marker.heading." + HEADINGS[i]), HEADING_WIDTH, 18,
                        () -> this.parkingHeading = heading)));
            }
        }
        Layout actions = this.row(cx - 54, this.type == MarkerType.PARKING ? top + 156 : top + 132).gap(8);
        actions.add(Widgets.button(Component.translatable("mcheli.wingman.save"), 50, 20, () -> {
            this.save();
            this.onClose();
        }));
        actions.add(Widgets.button(Component.translatable("gui.cancel"), 50, 20, this::onClose));
    }

    private void addTypeRow(TabBar<MarkerType> types, int from, int to, int cx, int y) {
        int count = to - from;
        Layout row = this.row(cx - (TYPE_WIDTH * count + GAP * (count - 1)) / 2, y).gap(GAP);
        for (int i = from; i < to; i++) {
            row.add(types.tab(TYPE_ORDER[i], Component.literal(TYPE_ORDER[i].shortName()), TYPE_WIDTH, 20));
        }
    }

    private EditBox field(int x, int y, String value, Consumer<String> onChange) {
        EditBox field = this.add(Widgets.text(200, 20, FIELD_MAX_LENGTH, Component.empty()));
        field.setPosition(x, y);
        field.setValue(value);
        field.setResponder(onChange);
        return field;
    }

    /** A BASE has no parent base, and only parking spots have a heading. */
    private void selectType(MarkerType selected) {
        this.type = selected;
        if (selected == MarkerType.BASE) {
            this.baseId = "";
        }
        if (selected != MarkerType.PARKING) {
            this.parkingHeading = -1;
        }
        this.rebuild();
    }

    private void save() {
        String baseName = this.type == MarkerType.BASE ? "" : this.baseId.trim();
        int heading = this.type == MarkerType.PARKING ? this.parkingHeading : -1;
        Network.sendToServer(new UpdateMarkerMessage(this.pos,
                new MarkerSettings(this.type, this.id.trim(), baseName, heading)));
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = this.width / 2;
        int top = this.height / 4;
        int x = cx - 100;
        g.drawCenteredString(this.font, this.title, cx, top - 22, this.theme.text());
        if (this.type == MarkerType.BASE) {
            g.drawString(this.font, Component.translatable("mcheli.wingman.marker.base_name"), x, top + 52, this.theme.muted());
        } else {
            g.drawString(this.font, Component.translatable("mcheli.wingman.marker.marker_id"), x, top + 52, this.theme.muted());
            g.drawString(this.font, Component.translatable("mcheli.wingman.marker.parent"), x, top + 88, this.theme.muted());
        }
        if (this.type == MarkerType.PARKING) {
            g.drawString(this.font, Component.translatable("mcheli.wingman.marker.heading"), x, top + 122,
                    this.theme.muted());
            int index = this.parkingHeading + 1;
            if (index < 0 || index >= this.headingButtons.size()) {
                return;
            }
            Button selected = this.headingButtons.get(index);
            g.fill(selected.getX() - 1, selected.getY() - 1, selected.getRight() + 1, selected.getBottom() + 1,
                    SELECTED_FRAME);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            this.setFocused(this.idField.isFocused() && this.baseIdField != null ? this.baseIdField : this.idField);
            return true;
        }
        if (this.getFocused() instanceof EditBox field) {
            field.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
