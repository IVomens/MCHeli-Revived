package com.ivomens.mcheli.inspector.client;

import com.ivomens.mcheli.inspector.InspectorSection;
import com.ivomens.mcheli.inspector.InspectorSnapshot;
import com.ivomens.mcheli.inspector.InspectorTab;
import com.ivomens.mcheli.inspector.client.InspectorRows.Row;
import com.ivomens.mcheli.inspector.net.InspectorReplyMessage;
import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.ListView;
import com.ivomens.mcheli.ui.client.McScreen;
import com.ivomens.mcheli.ui.client.ScrollPane;
import com.ivomens.mcheli.ui.client.TabBar;
import com.ivomens.mcheli.ui.client.Widgets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * The vehicle inspector (docs/INSPECTOR.md): tabs over the vehicle's live state, refreshed every tick, with
 * the server's view beside the client's for operators, and buttons to export the snapshot, copy a summary
 * for a bug report, or switch to the compact overlay.
 */
final class InspectorScreen extends McScreen {

    private static final int MARGIN = 12;
    private static final int TAB_WIDTH = 76;
    private static final int TITLE_Y = 6;
    private static final int TOP = 17;
    private static final int TABLE_TOP = TOP + 24;
    private static final int TITLES_HEIGHT = 13;
    /** From the bottom of the screen: the buttons, and the status line above them. */
    private static final int BOTTOM = 26;
    private static final int STATUS_LINE = BOTTOM + 13;
    private static final int PANE_GAP = BOTTOM + 17;
    private static final int LIST_ROW = 12;
    private static final int LIST_MAX_WIDTH = 200;
    private static final String COMPONENT = "Component";
    private static final int TICKS_PER_SECOND = 20;

    private final InspectorClient inspector;
    private InspectorTab tab = InspectorTab.OVERVIEW;
    private int selectedComponent;
    private Component message = Component.empty();
    private InspectorTable table;
    private ScrollPane pane;
    @Nullable
    private ListView<InspectorSection> components;

    InspectorScreen(InspectorClient inspector) {
        super(Component.translatable("mcheli.inspector.title"));
        this.inspector = inspector;
    }

    @Override
    protected void build() {
        TabBar<InspectorTab> tabs = Widgets.tabs(this.theme, this.tab, this::selectTab);
        Layout tabRow = row(MARGIN, TOP).gap(2);
        for (InspectorTab each : InspectorTab.values()) {
            tabRow.add(tabs.tab(each, tabLabel(each), TAB_WIDTH, this.theme.buttonHeight()));
        }
        this.addRenderableOnly(tabs);
        int left = MARGIN;
        int paneTop = TABLE_TOP + TITLES_HEIGHT;
        int paneHeight = this.height - PANE_GAP - paneTop;
        this.components = null;
        if (this.tab == InspectorTab.COMPONENTS) {
            int listWidth = Math.min(LIST_MAX_WIDTH, (this.width - 2 * MARGIN) * 36 / 100);
            this.components = Widgets.<InspectorSection>list(this.theme, listWidth, paneHeight / LIST_ROW, LIST_ROW,
                    this::paintComponent, section -> this.selectedComponent = this.components.selectedIndex());
            this.components.setPosition(left, paneTop);
            this.components.setItems(componentSections()).select(this.selectedComponent);
            add(this.components);
            left += listWidth + 10;
        }
        int tableWidth = this.width - MARGIN - left;
        this.table = new InspectorTable(this.theme, this.font, tableWidth);
        this.pane = Widgets.scrollPane(this.theme, tableWidth, paneHeight).painter(this.table)
                .step(InspectorTable.ROW_HEIGHT * 3);
        this.pane.setPosition(left, paneTop);
        add(this.pane);
        buttons();
    }

    private void buttons() {
        Layout bottom = row(MARGIN, this.height - BOTTOM).gap(4);
        int height = this.theme.buttonHeight();
        bottom.add(Widgets.button(Component.translatable("mcheli.inspector.export"),
                Component.translatable("mcheli.inspector.export.tooltip"), 84, height, this::export));
        bottom.add(Widgets.button(Component.translatable("mcheli.inspector.copy"),
                Component.translatable("mcheli.inspector.copy.tooltip"), 84, height, this::copySummary));
        bottom.add(Widgets.button(Component.translatable("mcheli.inspector.overlay"),
                Component.translatable("mcheli.inspector.overlay.tooltip"), 70, height, this::showOverlay));
        bottom.add(Widgets.button(Component.translatable("gui.done"), 60, height, this::onClose));
    }

    private void selectTab(InspectorTab selected) {
        this.tab = selected;
        rebuild();
    }

    private static Component tabLabel(InspectorTab tab) {
        return Component.translatable("mcheli.inspector.tab." + tab.name().toLowerCase(Locale.ROOT));
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        InspectorSnapshot client = this.inspector.clientSnapshot();
        InspectorSnapshot server = serverSnapshot();
        List<Row> rows = rows(client, server);
        this.table.show(rows, server != null, this.tab == InspectorTab.DEFINITION);
        this.pane.contentHeight(this.table.contentHeight());
        if (this.components != null) {
            this.components.setItems(componentSections());
        }
        header(graphics, client);
        graphics.fill(this.pane.getX(), this.pane.getY(), this.pane.getRight(), this.pane.getBottom(),
                this.theme.panel());
        this.table.paintColumnTitles(graphics, this.pane.getX(), TABLE_TOP, Component.translatable(
                "mcheli.inspector.column.server"));
        if (rows.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("mcheli.inspector.empty"), this.pane.getX() + 6,
                    this.pane.getY() + 6, this.theme.faint(), false);
        }
        statusLine(graphics);
    }

    /** The colour legend on the left of the line above the buttons, the last action's result on the right. */
    private void statusLine(GuiGraphics graphics) {
        int y = this.height - STATUS_LINE;
        int messageWidth = this.font.width(this.message);
        int messageX = this.width - MARGIN - messageWidth;
        graphics.drawString(this.font, this.message, messageX, y, this.theme.good(), false);
        int x = MARGIN;
        x = legend(graphics, x, y, this.theme.accent(), "mcheli.inspector.legend.changed", messageX);
        x = legend(graphics, x, y, this.theme.bad(), "mcheli.inspector.legend.mismatch", messageX);
        legend(graphics, x, y, this.theme.faint(), "mcheli.inspector.legend.drift", messageX);
    }

    private int legend(GuiGraphics graphics, int x, int y, int colour, String key, int limit) {
        Component text = Component.translatable(key);
        int end = x + 8 + this.font.width(text);
        if (end > limit - 8) {
            return limit;
        }
        graphics.fill(x, y + 1, x + 5, y + 6, colour);
        graphics.drawString(this.font, text, x + 8, y, this.theme.muted(), false);
        return end + 12;
    }

    private List<Row> rows(@Nullable InspectorSnapshot client, @Nullable InspectorSnapshot server) {
        if (this.tab == InspectorTab.DEFINITION) {
            InspectorSnapshot definition = this.inspector.definition();
            return definition == null ? List.of() : InspectorRows.of(definition.sections(this.tab), server, true);
        }
        if (client == null) {
            return List.of();
        }
        if (this.tab == InspectorTab.COMPONENTS) {
            List<InspectorSection> sections = client.sections(this.tab);
            int index = Math.min(this.selectedComponent, sections.size() - 1);
            return index < 0 ? List.of() : InspectorRows.of(List.of(sections.get(index)), server, true);
        }
        return InspectorRows.of(client.sections(this.tab), server, true);
    }

    private void header(GuiGraphics graphics, @Nullable InspectorSnapshot client) {
        Component status = serverStatus(client);
        int statusX = this.width - MARGIN - this.font.width(status);
        graphics.drawString(this.font, status, statusX, TITLE_Y, statusColour(client), false);
        graphics.drawString(this.font, this.title, MARGIN, TITLE_Y, this.theme.title(), false);
        int x = MARGIN + this.font.width(this.title) + 8;
        String type = client == null ? "-" : client.typeId();
        if (this.inspector.vehicle(this.minecraft) == null) {
            type += "  " + Component.translatable("mcheli.inspector.gone").getString();
        }
        graphics.drawString(this.font, this.table.fit(type, statusX - 10 - x), x, TITLE_Y, this.theme.text(), false);
    }

    private Component serverStatus(@Nullable InspectorSnapshot client) {
        InspectorReplyMessage reply = this.inspector.server();
        if (!this.inspector.mayAskServer(this.minecraft)) {
            return Component.translatable("mcheli.inspector.server.operators");
        }
        if (reply == null) {
            return Component.translatable("mcheli.inspector.server.waiting");
        }
        return switch (reply.status()) {
            case DENIED -> Component.translatable("mcheli.inspector.server.denied");
            case NOT_FOUND -> Component.translatable("mcheli.inspector.server.not_found");
            case OK -> Component.translatable("mcheli.inspector.server.live",
                    String.format(Locale.ROOT, "%.1f", this.inspector.serverAge() / (double) TICKS_PER_SECOND),
                    mismatches(client, reply.snapshot()));
        };
    }

    private int statusColour(@Nullable InspectorSnapshot client) {
        InspectorSnapshot server = serverSnapshot();
        if (server == null) {
            return this.theme.faint();
        }
        return mismatches(client, server) > 0 ? this.theme.bad() : this.theme.good();
    }

    private int mismatches(@Nullable InspectorSnapshot client, InspectorSnapshot server) {
        int count = client == null ? 0 : InspectorRows.mismatches(InspectorRows.of(client.sections(), server, false));
        InspectorSnapshot definition = this.inspector.definition();
        return definition == null ? count
                : count + InspectorRows.mismatches(InspectorRows.of(definition.sections(), server, false));
    }

    @Nullable
    private InspectorSnapshot serverSnapshot() {
        InspectorReplyMessage reply = this.inspector.server();
        return reply != null && reply.status() == InspectorReplyMessage.Status.OK ? reply.snapshot() : null;
    }

    private List<InspectorSection> componentSections() {
        InspectorSnapshot client = this.inspector.clientSnapshot();
        return client == null ? List.of() : client.sections(InspectorTab.COMPONENTS);
    }

    private void paintComponent(GuiGraphics graphics, InspectorSection section, int x, int y, int width,
                                boolean selected, boolean hovered) {
        String detail = section.detail();
        String phase = detail.contains(" ") && !detail.startsWith("no tick") ? detail.substring(0, detail.indexOf(' '))
                : "-";
        int phaseWidth = this.font.width(phase);
        String name = section.title().endsWith(COMPONENT) && section.title().length() > COMPONENT.length()
                ? section.title().substring(0, section.title().length() - COMPONENT.length()) : section.title();
        graphics.drawString(this.font, this.table.fit(name, width - phaseWidth - 12), x + 4, y + 2,
                selected ? this.theme.title() : this.theme.text(), false);
        graphics.drawString(this.font, phase, x + width - phaseWidth - 4, y + 2, this.theme.faint(), false);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String hovered = this.table.hoveredText();
        if (hovered != null) {
            graphics.renderTooltip(this.font, Component.literal(hovered), mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (InspectorKeys.OPEN.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void export() {
        this.message = InspectorExport.export(this.minecraft, this.inspector);
    }

    private void copySummary() {
        this.minecraft.keyboardHandler.setClipboard(InspectorExport.summary(this.minecraft, this.inspector));
        this.message = Component.translatable("mcheli.inspector.copied");
    }

    private void showOverlay() {
        this.inspector.setOverlay(true);
        onClose();
    }
}
