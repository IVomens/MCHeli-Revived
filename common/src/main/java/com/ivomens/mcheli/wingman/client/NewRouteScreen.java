package com.ivomens.mcheli.wingman.client;

import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.McScreen;
import com.ivomens.mcheli.ui.client.Widgets;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import com.ivomens.mcheli.wingman.net.MarkerView;
import com.ivomens.mcheli.wingman.net.MissionRouteView;
import com.ivomens.mcheli.wingman.net.SaveMissionRouteMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds a node-list route. The node type is picked with buttons; coordinates can be filled from the
 * player position and marker ids from clickable hints of the markers placed in the world.
 */
public final class NewRouteScreen extends McScreen {

    /** The kinds of route node, with the marker type offered as hints for each, if any. */
    private enum NodeType {
        TAKEOFF(MarkerType.RUNWAY_A), FLY_TO(MarkerType.WAYPOINT), ATTACK(null), LOITER(null),
        LAND(MarkerType.RUNWAY_B), PARK(MarkerType.PARKING);

        @Nullable
        final MarkerType hints;

        NodeType(@Nullable MarkerType hints) {
            this.hints = hints;
        }

        String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        boolean takesMarkerId() {
            return this == TAKEOFF || this == LAND || this == PARK;
        }

        boolean takesNumber() {
            return this == ATTACK || this == LOITER;
        }
    }

    private static final int DEFAULT_ATTACK_RADIUS = 200;
    private static final int DEFAULT_LOITER_TICKS = 200;
    /** Most recent nodes shown in the list. */
    private static final int SHOWN_NODES = 5;
    private static final int NODE_ROW = 12;
    private static final int TYPE_WIDTH = 72;
    private static final int TYPE_GAP = 3;
    private static final String HINT_SEPARATOR = " | ";
    private static final int SELECTED_FRAME = 0xFFFFAA00;
    private static final int HINT = 0xFF555555;

    private final Screen parent;
    private final List<MissionRouteView> existingRoutes;
    private final List<MarkerView> markers;
    private final Vec3 playerPosition;
    private final List<String> nodes = new ArrayList<>();
    private final List<Button> typeButtons = new ArrayList<>();
    @Nullable
    private NodeType type;
    private Component error = Component.empty();
    private EditBox nameField;
    /** Marker id for TAKEOFF, LAND and PARK. */
    private EditBox idField;
    private EditBox xField;
    private EditBox yField;
    private EditBox zField;
    /** Radius for ATTACK, duration for LOITER. */
    private EditBox numberField;
    private Button myPosition;
    private Button addNode;

    public NewRouteScreen(Screen parent, List<MissionRouteView> existingRoutes, List<MarkerView> markers,
                          Vec3 playerPosition) {
        super(Component.translatable("mcheli.wingman.route.title"));
        this.parent = parent;
        this.existingRoutes = existingRoutes;
        this.markers = markers;
        this.playerPosition = playerPosition;
    }

    private int cx() {
        return this.width / 2;
    }

    private int formY() {
        return this.height / 2 - 30;
    }

    @Override
    protected void build() {
        int cx = this.cx();
        int formY = this.formY();
        this.nameField = this.field(this.nameField, cx - 120, formY - 90, 240, 64);
        this.setInitialFocus(this.nameField);

        NodeType[] types = NodeType.values();
        this.typeButtons.clear();
        Layout typeRow = this.row(cx - (TYPE_WIDTH * types.length + TYPE_GAP * (types.length - 1)) / 2, formY - 60)
                .gap(TYPE_GAP);
        for (NodeType nodeType : types) {
            this.typeButtons.add(typeRow.add(Widgets.button(Component.translatable("mcheli.wingman.route." + nodeType.key()),
                    TYPE_WIDTH, 18, () -> this.selectType(nodeType))));
        }
        this.idField = this.field(this.idField, cx - 120, formY - 30, 200, 64);
        this.xField = this.field(this.xField, cx - 120, formY - 30, 55, 8);
        this.yField = this.field(this.yField, cx - 58, formY - 30, 55, 8);
        this.zField = this.field(this.zField, cx + 4, formY - 30, 55, 8);
        this.numberField = this.field(this.numberField, cx - 60, formY - 30, 120, 10);

        this.myPosition = this.add(this.button("my_position", 55, 20, this::fillPlayerPosition));
        this.myPosition.setPosition(cx + 68, formY - 30);
        this.addNode = this.add(this.button("add", 60, 18, this::addNode));
        this.addNode.setPosition(cx - 30, formY - 4);
        Layout actions = this.row(cx - 85, this.height / 2 + 90).gap(20);
        actions.add(this.button("save", 75, 20, this::save));
        actions.add(Widgets.button(Component.translatable("gui.cancel"), 75, 20, this::back));
        this.refreshVisibility();
    }

    /** A text field, keeping what was typed in the field it replaces. */
    private EditBox field(@Nullable EditBox previous, int x, int y, int width, int maxLength) {
        EditBox field = this.add(Widgets.text(width, 20, maxLength, Component.empty()));
        field.setPosition(x, y);
        field.setValue(previous == null ? "" : previous.getValue());
        return field;
    }

    private Button button(String name, int width, int height, Runnable onPress) {
        return Widgets.button(Component.translatable("mcheli.wingman.route." + name), width, height, onPress);
    }

    private void refreshVisibility() {
        boolean markerId = this.type != null && this.type.takesMarkerId();
        boolean coordinates = this.type == NodeType.FLY_TO;
        this.idField.visible = markerId;
        this.xField.visible = coordinates;
        this.yField.visible = coordinates;
        this.zField.visible = coordinates;
        this.numberField.visible = this.type != null && this.type.takesNumber();
        this.myPosition.visible = coordinates;
        this.addNode.active = this.type != null;
    }

    private void selectType(NodeType selected) {
        if (selected != this.type) {
            this.type = selected;
            for (EditBox field : List.of(this.idField, this.numberField, this.xField, this.yField, this.zField)) {
                field.setValue("");
            }
            this.error = Component.empty();
            // A single candidate marker is filled in right away.
            List<MarkerView> candidates = this.hints();
            if (candidates.size() == 1 && selected == NodeType.FLY_TO) {
                this.applyMarker(candidates.getFirst());
            } else if (candidates.size() == 1) {
                this.idField.setValue(candidates.getFirst().id());
            }
        }
        this.refreshVisibility();
    }

    private List<MarkerView> hints() {
        List<MarkerView> result = new ArrayList<>();
        if (this.type != null && this.type.hints != null) {
            for (MarkerView marker : this.markers) {
                if (marker.type() == this.type.hints) result.add(marker);
            }
        }
        return result;
    }

    /** Fills the form from a marker: its coordinates for FLY_TO, its id otherwise. */
    private void applyMarker(MarkerView marker) {
        if (this.type == NodeType.FLY_TO) {
            this.xField.setValue(String.valueOf(marker.pos().getX()));
            this.yField.setValue(String.valueOf(marker.pos().getY()));
            this.zField.setValue(String.valueOf(marker.pos().getZ()));
        } else {
            this.idField.setValue(hintLabel(marker));
        }
    }

    private void fillPlayerPosition() {
        this.xField.setValue(String.valueOf((int) this.playerPosition.x));
        this.yField.setValue(String.valueOf((int) this.playerPosition.y));
        this.zField.setValue(String.valueOf((int) this.playerPosition.z));
    }

    private void addNode() {
        String node = this.buildNode();
        if (node != null) {
            this.nodes.add(node);
            this.error = Component.empty();
        }
    }

    /** The node text for the form, or null (with {@link #error} set) if the input is invalid. */
    @Nullable
    private String buildNode() {
        try {
            return switch (this.type) {
                case TAKEOFF -> this.idNode("takeoff:", "runway_a");
                case FLY_TO -> "flyto:" + Integer.parseInt(this.xField.getValue().trim())
                        + "," + Integer.parseInt(this.yField.getValue().trim())
                        + "," + Integer.parseInt(this.zField.getValue().trim());
                case ATTACK -> "attack:" + this.numberOr(DEFAULT_ATTACK_RADIUS);
                case LOITER -> "loiter:" + this.numberOr(DEFAULT_LOITER_TICKS);
                case LAND -> this.idNode("land:", "runway_b");
                case PARK -> this.idNode("park:", "parking");
                case null -> null;
            };
        } catch (NumberFormatException e) {
            this.error = Component.translatable("mcheli.wingman.route.error.number");
            return null;
        }
    }

    @Nullable
    private String idNode(String prefix, String marker) {
        String id = this.idField.getValue().trim();
        if (id.isEmpty()) {
            this.error = Component.translatable("mcheli.wingman.route.error.id_" + marker);
            return null;
        }
        return prefix + id;
    }

    private int numberOr(int fallback) {
        String text = this.numberField.getValue().trim();
        return text.isEmpty() ? fallback : Integer.parseInt(text);
    }

    private void save() {
        String name = this.nameField.getValue().trim();
        if (name.isEmpty()) {
            this.error = Component.translatable("mcheli.wingman.route.error.name");
        } else if (this.nodes.isEmpty()) {
            this.error = Component.translatable("mcheli.wingman.route.error.empty");
        } else if (this.existingRoutes.stream().anyMatch(route -> route.name().equals(name))) {
            this.error = Component.translatable("mcheli.wingman.route.error.exists", name);
        } else {
            Network.sendToServer(new SaveMissionRouteMessage(name, this.nodes));
            this.onClose();
        }
    }

    private void back() {
        this.minecraft.setScreen(this.parent);
    }

    // Drawing

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = this.cx();
        int formY = this.formY();
        int left = cx - 120;
        g.drawCenteredString(this.font, this.title, cx, this.height / 2 - 130, this.theme.title());
        g.drawString(this.font, Component.translatable("mcheli.wingman.route.name"), left, formY - 103, this.theme.muted());
        g.drawString(this.font, Component.translatable("mcheli.wingman.route.add_node"), left, formY - 73, this.theme.muted());
        if (this.type != null) {
            Button selected = this.typeButtons.get(this.type.ordinal());
            g.fill(selected.getX() - 1, selected.getY() - 1, selected.getRight() + 1, selected.getBottom() + 1,
                    SELECTED_FRAME);
            this.renderFormLabels(g, cx, formY - 43);
            if (this.type.hints != null) {
                this.renderHints(g, mouseX, mouseY);
            }
        }
        this.renderNodes(g, mouseX, mouseY);
        g.drawCenteredString(this.font, this.error, cx, this.height / 2 + 72, this.theme.bad());
    }

    private void renderFormLabels(GuiGraphics g, int cx, int y) {
        if (this.type == NodeType.FLY_TO) {
            g.drawString(this.font, "X:", cx - 122, y, this.theme.muted());
            g.drawString(this.font, "Y:", cx - 60, y, this.theme.muted());
            g.drawString(this.font, "Z:", cx + 2, y, this.theme.muted());
        } else {
            g.drawString(this.font, Component.translatable("mcheli.wingman.route.field." + this.type.key()), cx - 120, y,
                    this.theme.muted());
        }
    }

    private int hintY() {
        return this.formY() - 8;
    }

    private int hintStartX() {
        return this.cx() - 120 + this.font.width(Component.translatable("mcheli.wingman.route.quick"));
    }

    private static String hintLabel(MarkerView marker) {
        return marker.id().isEmpty() ? "(no-id)" : marker.id();
    }

    /** Lists the ids of placed markers of the type; clicking one fills the form. */
    private void renderHints(GuiGraphics g, int mouseX, int mouseY) {
        int y = this.hintY();
        List<MarkerView> hints = this.hints();
        if (hints.isEmpty()) {
            g.drawString(this.font, Component.translatable("mcheli.wingman.route.no_markers", this.type.hints.name()),
                    this.cx() - 120, y, HINT);
            return;
        }
        g.drawString(this.font, Component.translatable("mcheli.wingman.route.quick"), this.cx() - 120, y, HINT);
        int x = this.hintStartX();
        for (int i = 0; i < hints.size(); i++) {
            String label = hintLabel(hints.get(i));
            int width = this.font.width(label);
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 9;
            g.drawString(this.font, label, x, y, hovered ? this.theme.title() : 0xFF55FFFF);
            if (i < hints.size() - 1) {
                g.drawString(this.font, HINT_SEPARATOR, x + width, y, HINT);
            }
            x += width + this.font.width(HINT_SEPARATOR);
        }
    }

    /** The marker hint under the mouse, laid out as {@link #renderHints} draws them. */
    @Nullable
    private MarkerView hintAt(double mouseX, double mouseY) {
        int y = this.hintY();
        if (mouseY < y || mouseY >= y + 9) return null;
        int x = this.hintStartX();
        for (MarkerView marker : this.hints()) {
            int width = this.font.width(hintLabel(marker));
            if (mouseX >= x && mouseX < x + width) return marker;
            x += width + this.font.width(HINT_SEPARATOR);
        }
        return null;
    }

    private int nodeListY() {
        return this.height / 2 + 10;
    }

    private int firstShownNode() {
        return Math.max(0, this.nodes.size() - SHOWN_NODES);
    }

    private void renderNodes(GuiGraphics g, int mouseX, int mouseY) {
        int listY = this.nodeListY();
        int left = this.cx() - 120;
        g.drawString(this.font, Component.translatable("mcheli.wingman.route.nodes", this.nodes.size()), left, listY - 12,
                this.theme.muted());
        if (this.nodes.isEmpty()) {
            g.drawString(this.font, Component.translatable("mcheli.wingman.route.no_nodes"), left, listY, HINT);
            return;
        }
        int first = this.firstShownNode();
        for (int i = first; i < this.nodes.size(); i++) {
            int rowY = listY + (i - first) * NODE_ROW;
            g.drawString(this.font, Component.literal("[" + i + "] ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(this.nodes.get(i)).withStyle(ChatFormatting.WHITE)), left, rowY, this.theme.text());
            g.drawString(this.font, "[×]", this.cx() + 110, rowY, this.isOverDelete(mouseX, mouseY, rowY) ? this.theme.bad() : HINT);
        }
    }

    private boolean isOverDelete(double mouseX, double mouseY, int rowY) {
        return mouseX >= this.cx() + 110 && mouseX < this.cx() + 125 && mouseY >= rowY && mouseY < rowY + 9;
    }

    // Input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        MarkerView hint = this.type == null || this.type.hints == null ? null : this.hintAt(mouseX, mouseY);
        if (hint != null) {
            this.applyMarker(hint);
            if (this.type != NodeType.FLY_TO) {
                this.setFocused(this.idField);
            }
        }
        int first = this.firstShownNode();
        for (int i = first; i < this.nodes.size(); i++) {
            if (this.isOverDelete(mouseX, mouseY, this.nodeListY() + (i - first) * NODE_ROW)) {
                this.nodes.remove(i);
                break;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.back();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            if (this.type == NodeType.FLY_TO) {
                this.setFocused(this.xField.isFocused() ? this.yField : this.yField.isFocused() ? this.zField : this.xField);
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
}
