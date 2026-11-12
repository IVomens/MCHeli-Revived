package com.ivomens.mcheli.wingman.client;

import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.ListView;
import com.ivomens.mcheli.ui.client.McScreen;
import com.ivomens.mcheli.ui.client.Widgets;
import com.ivomens.mcheli.wingman.net.AbortMissionMessage;
import com.ivomens.mcheli.wingman.net.AssignMissionMessage;
import com.ivomens.mcheli.wingman.net.DeleteMissionRouteMessage;
import com.ivomens.mcheli.wingman.net.MarkerView;
import com.ivomens.mcheli.wingman.net.MissionRouteView;
import com.ivomens.mcheli.wingman.net.UavView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Assigns saved node-list routes to UAVs, and creates or deletes routes. */
public final class MissionPlannerScreen extends McScreen {

    private static final int VISIBLE_ROWS = 8;
    private static final int PANEL_WIDTH = 230;
    private static final int PANEL_GAP = 20;
    private static final int BUTTON_WIDTH = 110;
    /** Route nodes previewed under the route list. */
    private static final int PREVIEW_NODES = 3;

    private final List<UavView> uavs;
    private final List<MissionRouteView> routes;
    private final List<MarkerView> markers;
    private final Vec3 playerPosition;
    private int selectedUav = -1;
    private int selectedRoute = -1;
    private ListView<UavView> uavList;
    private ListView<MissionRouteView> routeList;
    private Button assign;
    private Button abort;
    private Button delete;

    public MissionPlannerScreen(List<UavView> uavs, List<MissionRouteView> routes, List<MarkerView> markers,
                                Vec3 playerPosition) {
        super(Component.translatable("mcheli.wingman.planner.title"));
        this.uavs = uavs;
        this.routes = routes;
        this.markers = markers;
        this.playerPosition = playerPosition;
    }

    private int panelTop() {
        return this.height / 2 - (VISIBLE_ROWS * this.theme.rowHeight() + 50) / 2;
    }

    private int uavX() {
        return this.width / 2 - (PANEL_WIDTH * 2 + PANEL_GAP) / 2;
    }

    private int routeX() {
        return this.uavX() + PANEL_WIDTH + PANEL_GAP;
    }

    @Override
    protected void build() {
        int top = this.panelTop();
        int rowHeight = this.theme.rowHeight();
        this.uavList = this.add(Widgets.<UavView>list(this.theme, PANEL_WIDTH, VISIBLE_ROWS, rowHeight, this::paintUav,
                uav -> this.select()).setItems(this.uavs).select(this.selectedUav));
        this.uavList.setPosition(this.uavX(), top);
        this.routeList = this.add(Widgets.<MissionRouteView>list(this.theme, PANEL_WIDTH, VISIBLE_ROWS, rowHeight,
                this::paintRoute, route -> this.select()).setItems(this.routes).select(this.selectedRoute));
        this.routeList.setPosition(this.routeX(), top);

        int buttonY = top + VISIBLE_ROWS * rowHeight + 20;
        Layout uavButtons = this.row(this.uavX(), buttonY).gap(5);
        this.assign = uavButtons.add(this.button("assign", this::assign));
        this.abort = uavButtons.add(this.button("abort", this::abort));
        Layout routeButtons = this.row(this.routeX(), buttonY).gap(5);
        routeButtons.add(this.button("new_route", () -> this.minecraft.setScreen(
                new NewRouteScreen(this, this.routes, this.markers, this.playerPosition))));
        this.delete = routeButtons.add(this.button("delete_route", this::deleteRoute));
        this.add(Widgets.button(Component.translatable("mcheli.wingman.close"), 80, 20, this::onClose))
                .setPosition(this.width / 2 - 40, buttonY + 25);
        this.updateButtons();
    }

    private Button button(String name, Runnable onPress) {
        return Widgets.button(Component.translatable("mcheli.wingman.planner." + name), BUTTON_WIDTH, 20, onPress);
    }

    private void select() {
        this.selectedUav = this.uavList.selectedIndex();
        this.selectedRoute = this.routeList.selectedIndex();
        this.updateButtons();
    }

    private void updateButtons() {
        this.assign.active = this.selectedUav >= 0 && this.selectedRoute >= 0;
        this.abort.active = this.selectedUav >= 0;
        this.delete.active = this.selectedRoute >= 0;
    }

    private void assign() {
        Network.sendToServer(new AssignMissionMessage(this.uavs.get(this.selectedUav).id(),
                this.routes.get(this.selectedRoute).name()));
        this.onClose();
    }

    private void abort() {
        Network.sendToServer(new AbortMissionMessage(this.uavs.get(this.selectedUav).id()));
        this.onClose();
    }

    private void deleteRoute() {
        Network.sendToServer(new DeleteMissionRouteMessage(this.routes.get(this.selectedRoute).name()));
        this.onClose();
    }

    private void paintUav(GuiGraphics g, UavView uav, int x, int y, int width, boolean selected, boolean hovered) {
        Component label = Component.literal(uav.name()).withStyle(selected ? ChatFormatting.YELLOW : ChatFormatting.WHITE)
                .append(Component.literal(" [" + uav.state() + "]").withStyle(ChatFormatting.GRAY));
        if (uav.nodeCount() > 0) {
            label = label.copy().append(Component.literal(" " + uav.nodeIndex() + "/" + uav.nodeCount())
                    .withStyle(ChatFormatting.GREEN));
        }
        g.drawString(this.font, label, x + 4, y + 3, this.theme.text());
        g.drawString(this.font, uav.id().toString().substring(0, 8) + "...", x + 4, y + 12, 0xFF555555);
    }

    private void paintRoute(GuiGraphics g, MissionRouteView route, int x, int y, int width, boolean selected,
                            boolean hovered) {
        g.drawString(this.font, route.name(), x + 4, y + 3, selected ? 0xFFFFFF55 : this.theme.text());
        g.drawString(this.font, Component.translatable("mcheli.wingman.planner.nodes", route.nodes().size()), x + 4,
                y + 12, 0xFF555555);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int top = this.panelTop();
        g.drawCenteredString(this.font, this.title, this.width / 2, top - 20, this.theme.title());
        g.drawString(this.font, Component.translatable("mcheli.wingman.planner.uavs", this.uavs.size())
                .withStyle(ChatFormatting.AQUA), this.uavX(), top - 10, this.theme.text());
        g.drawString(this.font, Component.translatable("mcheli.wingman.planner.routes", this.routes.size())
                .withStyle(ChatFormatting.GREEN), this.routeX(), top - 10, this.theme.text());
        if (this.selectedRoute < 0 || this.selectedRoute >= this.routes.size()) {
            return;
        }
        List<String> nodes = this.routes.get(this.selectedRoute).nodes();
        int y = top + VISIBLE_ROWS * this.theme.rowHeight() + 5;
        for (int i = 0; i < Math.min(PREVIEW_NODES, nodes.size()); i++) {
            g.drawString(this.font, "[" + i + "] " + nodes.get(i), this.routeX(), y + i * 10, 0xFF555555);
        }
        if (nodes.size() > PREVIEW_NODES) {
            g.drawString(this.font, Component.translatable("mcheli.wingman.planner.more_nodes", nodes.size()),
                    this.routeX(), y + PREVIEW_NODES * 10, 0xFF555555);
        }
    }
}
