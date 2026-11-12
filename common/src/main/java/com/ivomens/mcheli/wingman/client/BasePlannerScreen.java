package com.ivomens.mcheli.wingman.client;

import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.McScreen;
import com.ivomens.mcheli.ui.client.ScrollPane;
import com.ivomens.mcheli.ui.client.TabBar;
import com.ivomens.mcheli.ui.client.Theme;
import com.ivomens.mcheli.ui.client.Widgets;
import com.ivomens.mcheli.wingman.net.MarkerView;
import com.ivomens.mcheli.wingman.net.OpenBaseScreenMessage;
import com.ivomens.mcheli.wingman.net.TaxiRouteData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Base planner, opened by right-clicking a BASE marker.
 *
 * <p>The Taxi Routes tab ({@link TaxiRoutesTab}) edits the base's taxi routes: parking spot, runway ends
 * or helipad and the departure and arrival waypoint lists are all picked from buttons, only the route id
 * is typed. The Mission tab ({@link BaseMissionTab}) builds a mission order and dispatches it to a
 * nearby aircraft. Each tab is a scroll pane between the tab bar and the Close button.
 */
public final class BasePlannerScreen extends McScreen {

    private enum Tab { ROUTES, MISSION }

    /** The frame drawn around a selected picker button; the button covers all but its border. */
    static final int SELECTED = 0xAAFFAA00;
    static final int CHOSEN = 0xFFFFAA00;
    private static final int FOOTER = 30;
    private static final int SCROLL_STEP = 14;

    private final OpenBaseScreenMessage base;
    private final List<TaxiRouteData> routes;
    private final TaxiRoutesTab routesTab;
    private final BaseMissionTab missionTab;
    private Tab tab = Tab.ROUTES;
    private ScrollPane pane;

    public BasePlannerScreen(OpenBaseScreenMessage base) {
        super(Component.translatable("mcheli.wingman.base.title"));
        this.base = base;
        this.routes = new ArrayList<>(base.routes());
        this.routesTab = new TaxiRoutesTab(this);
        this.missionTab = new BaseMissionTab(this);
    }

    OpenBaseScreenMessage base() {
        return this.base;
    }

    Theme theme() {
        return this.theme;
    }

    List<TaxiRouteData> routes() {
        return this.routes;
    }

    /** Rebuilds the screen, keeping the tab's scroll position; safe from a widget's handler. */
    void refresh() {
        this.rebuild();
    }

    @Override
    protected void build() {
        int cx = this.width / 2;
        int top = this.height / 4 + 12;
        TabBar<Tab> tabs = Widgets.tabs(this.theme, this.tab, this::showTab);
        Layout tabRow = this.row(cx - 82, top - 24).gap(4);
        tabRow.add(tabs.tab(Tab.ROUTES, Component.translatable("mcheli.wingman.base.tab.routes"), 80, 18));
        tabRow.add(tabs.tab(Tab.MISSION, Component.translatable("mcheli.wingman.base.tab.mission"), 80, 18));
        this.addRenderableOnly(tabs);

        int scroll = this.pane == null ? 0 : this.pane.scroll();
        this.pane = Widgets.scrollPane(this.theme, this.width, this.height - FOOTER - top).step(SCROLL_STEP);
        this.pane.setPosition(0, top);
        if (this.tab == Tab.ROUTES) {
            this.routesTab.build(this.pane);
        } else {
            this.missionTab.build(this.pane);
        }
        this.pane.setScroll(scroll);
        this.add(this.pane);
        this.add(Widgets.button(Component.translatable("mcheli.wingman.close"), 50, 16, this::onClose))
                .setPosition(cx - 25, this.height - 26);
    }

    private void showTab(Tab target) {
        this.tab = target;
        this.pane = null;
        this.rebuild();
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Component title = Component.translatable("mcheli.wingman.base.heading").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(this.base.baseId()).withStyle(ChatFormatting.WHITE));
        g.drawCenteredString(this.font, title, this.width / 2, this.height / 4 - 26, this.theme.text());
        if (this.tab == Tab.MISSION) {
            this.missionTab.renderFooter(g, this.height - FOOTER);
        }
    }

    /** Escape closes; typing goes to the tab's focused field, and Tab moves between the mission fields. */
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            if (this.tab == Tab.MISSION) {
                this.missionTab.focusNextField(this.pane);
            }
            return true;
        }
        if (this.pane.focusedField() != null) {
            this.pane.keyPressed(key, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return this.pane.charTyped(character, modifiers);
    }

    /** A click outside the fields leaves the keyboard where it was. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        return true;
    }

    // Helpers shared by the tabs

    static void frame(GuiGraphics g, AbstractWidget widget, int color) {
        g.fill(widget.getX() - 1, widget.getY() - 1, widget.getRight() + 1, widget.getBottom() + 1, color);
    }

    boolean isHelipad(String id) {
        for (MarkerView marker : this.base.markers().helipads()) {
            if (marker.id().equals(id)) return true;
        }
        return false;
    }

    int indexOfRoute(String routeId) {
        for (int i = 0; i < this.routes.size(); i++) {
            if (this.routes.get(i).routeId().equals(routeId)) return i;
        }
        return -1;
    }

    /** True if the arrival route ends at a helipad, which selects a vertical landing. */
    boolean isVtolArrivalRoute(String routeId) {
        int index = routeId.isEmpty() ? -1 : this.indexOfRoute(routeId);
        return index >= 0 && this.isHelipad(this.routes.get(index).runways().runwayId());
    }
}
