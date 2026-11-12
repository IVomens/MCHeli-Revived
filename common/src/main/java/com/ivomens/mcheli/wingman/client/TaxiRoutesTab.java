package com.ivomens.mcheli.wingman.client;

import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.ui.client.ScrollPane;
import com.ivomens.mcheli.ui.client.Widgets;
import com.ivomens.mcheli.wingman.net.BaseMarkers;
import com.ivomens.mcheli.wingman.net.DeleteTaxiRouteMessage;
import com.ivomens.mcheli.wingman.net.MarkerView;
import com.ivomens.mcheli.wingman.net.SaveTaxiRouteMessage;
import com.ivomens.mcheli.wingman.net.TaxiRouteData;
import com.ivomens.mcheli.wingman.net.TaxiRunways;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Taxi Routes tab of the base planner: the base's routes on the left, and the form of the route
 * being edited on the right. Everything but the route id is picked with buttons: the parking spot,
 * the runway ends or helipad, the arrival entry point and the departure and arrival waypoint lists.
 */
final class TaxiRoutesTab {

    private static final int MAX_ROUTES = 8;
    private static final int MAX_RUNWAYS = 4;
    private static final int MAX_ENTRY_POINTS = 8;
    private static final int MAX_PICKER = 99;
    private static final int PICKER_WIDTH = 58;
    private static final int PICKER_GAP = 4;
    private static final int PICKERS_PER_ROW = 4;
    /** Height of the HELIPAD_B info, the route preview lines and the waypoint mode toggle. */
    private static final int HELIPAD_B_SECTION = 72;
    /** Extra offset of the arrival waypoint list for the entry point row above it. */
    private static final int ENTRY_ROW = 18;
    private static final int CONTENT_HEIGHT = 480;
    private static final int WAYPOINT_COLUMN = 100;
    private static final int ROUTE_SELECTED = 0x44FFAA00;
    private static final int ERROR = 0xFFFF4444;
    private static final int FAINT = 0xFF666666;

    private final BasePlannerScreen screen;
    private final List<String> departureWaypoints = new ArrayList<>();
    /** Arrival waypoints; empty means the departure waypoints reversed. */
    private final List<String> arrivalWaypoints = new ArrayList<>();
    private int selectedRoute = -1;
    private String routeId = "";
    private String parkingId = "";
    private String runwayId = "";
    private String runwayBId = "";
    /** Arrival entry point; empty means the route's runway end. */
    private String arrivalRunwayId = "";
    /** Whether the waypoint editor shows the arrival list instead of the departure list. */
    private boolean editingArrival;

    private final List<Button> routeButtons = new ArrayList<>();
    private final List<Button> parkingButtons = new ArrayList<>();
    private final List<Button> runwayAButtons = new ArrayList<>();
    private final List<Button> runwayBButtons = new ArrayList<>();
    private final List<Button> helipadButtons = new ArrayList<>();
    private final List<Button> entryButtons = new ArrayList<>();
    private Button modeToggle;

    TaxiRoutesTab(BasePlannerScreen screen) {
        this.screen = screen;
    }

    private BaseMarkers markers() {
        return this.screen.base().markers();
    }

    /** Offsets of the form's sections from the top of the tab. */
    private final class Sections {
        final int parking = 28;
        final int runwayA = 56 + (markers().runwayA().isEmpty() ? 0 : (markers().parking().size() > 4 ? 2 : 1)) * 20;
        final int runwayB = this.runwayA + 24;
        final int helipad = this.runwayB + 24;
        final int helipadRows = markers().helipads().isEmpty() ? 0 : (markers().helipads().size() > 4 ? 2 : 1);
        final int helipadB = this.helipad + this.helipadRows * 20 + 6;
        final int waypoints = this.helipadB + HELIPAD_B_SECTION;
        final int waypointList = this.waypoints + (TaxiRoutesTab.this.editingArrival ? ENTRY_ROW : 0);
    }

    private int listX() {
        return this.screen.width / 2 - 195;
    }

    private int formX() {
        return this.screen.width / 2 - 65;
    }

    void build(ScrollPane pane) {
        Sections sections = new Sections();
        int listX = this.listX();
        int formX = this.formX();
        List<TaxiRouteData> routes = this.screen.routes();
        this.routeButtons.clear();
        for (int i = 0; i < routes.size() && i < MAX_ROUTES; i++) {
            int index = i;
            this.routeButtons.add(pane.add(Widgets.button(Component.literal(routes.get(i).routeId()), 110, 18,
                    () -> this.loadRoute(index)), listX, i * 22));
        }
        int actionsY = MAX_ROUTES * 22 + 4;
        pane.add(this.button("new", 52, 16, this::newRoute), listX, actionsY);
        pane.add(this.button("save", 52, 16, this::saveRoute), listX + 58, actionsY);
        pane.add(this.button("delete", 52, 16, this::deleteRoute), listX + 58, actionsY + 20);

        EditBox idField = pane.add(Widgets.text(200, 16, 64, Component.translatable("mcheli.wingman.base.route_id")),
                formX, 0);
        idField.setValue(this.routeId);
        idField.setResponder(value -> this.routeId = value);
        pane.focus(idField);

        this.addPickers(pane, this.parkingButtons, this.markers().parking(), MAX_PICKER, sections.parking, true,
                id -> this.parkingId = id);
        this.addPickers(pane, this.runwayAButtons, this.markers().runwayA(), MAX_RUNWAYS, sections.runwayA, false,
                id -> this.runwayId = id);
        this.addPickers(pane, this.runwayBButtons, this.markers().runwayB(), MAX_RUNWAYS, sections.runwayB, false,
                this::pickRunwayB);
        this.addPickers(pane, this.helipadButtons, this.markers().helipads(), MAX_PICKER, sections.helipad, true,
                this::pickHelipad);
        this.buildWaypointEditor(pane, sections);
        pane.contentHeight(CONTENT_HEIGHT).painter((g, left, top, mouseX, mouseY) -> this.paint(g, top, sections));
    }

    private Button button(String name, int width, int height, Runnable onPress) {
        return Widgets.button(Component.translatable("mcheli.wingman.base." + name), width, height, onPress);
    }

    /** A picker button per marker, four to a row when {@code wrap}, else in one row. */
    private void addPickers(ScrollPane pane, List<Button> buttons, List<MarkerView> markers, int max, int y, boolean wrap,
                            Consumer<String> onPick) {
        buttons.clear();
        for (int i = 0; i < markers.size() && i < max; i++) {
            String id = markers.get(i).id();
            int column = wrap ? i % PICKERS_PER_ROW : i;
            int row = wrap ? i / PICKERS_PER_ROW : 0;
            buttons.add(pane.add(Widgets.button(Component.literal(id), PICKER_WIDTH, 16, () -> onPick.accept(id)),
                    this.formX() + column * (PICKER_WIDTH + PICKER_GAP), y + row * 20));
        }
    }

    /** Without a runway A, B becomes the route's runway end: an arrival-only route that starts at B after a landing. */
    private void pickRunwayB(String id) {
        if (this.runwayId.isEmpty()) {
            this.runwayId = id;
        } else {
            this.runwayBId = id;
        }
    }

    /** The helipad is the route's runway end; with no parking chosen it is the parking spot too. */
    private void pickHelipad(String id) {
        this.runwayId = id;
        if (this.parkingId.isEmpty()) {
            this.parkingId = id;
        }
    }

    /** Departure or arrival waypoint list with up/down/remove buttons, next to the markers that can be added. */
    private void buildWaypointEditor(ScrollPane pane, Sections sections) {
        int formX = this.formX();
        this.modeToggle = pane.add(this.button(this.editingArrival ? "arrival_mode" : "departure_mode", 90, 13, () -> {
            this.editingArrival = !this.editingArrival;
            this.screen.refresh();
        }), formX, sections.waypoints - 16);

        this.entryButtons.clear();
        if (this.editingArrival) {
            List<MarkerView> entries = this.entryPoints();
            for (int i = 0; i < entries.size() && i < MAX_ENTRY_POINTS; i++) {
                String id = entries.get(i).id();
                this.entryButtons.add(pane.add(Widgets.button(Component.literal(id), 52, 14, () ->
                                // Clicking the selected entry point again clears it.
                                this.arrivalRunwayId = this.arrivalRunwayId.equals(id) ? "" : id),
                        formX + WAYPOINT_COLUMN + 8 + i * 54, sections.waypoints));
            }
        }

        List<String> active = this.activeWaypoints();
        int buttonsX = formX + WAYPOINT_COLUMN - 52;
        for (int i = 0; i < active.size() && i < MAX_PICKER; i++) {
            int index = i;
            int y = sections.waypointList + i * 18;
            if (i > 0) {
                pane.add(Widgets.button(Component.literal("^"), 14, 14, () -> this.editWaypoints(() ->
                        Collections.swap(active, index, index - 1))), buttonsX, y);
            }
            if (i < active.size() - 1) {
                pane.add(Widgets.button(Component.literal("v"), 14, 14, () -> this.editWaypoints(() ->
                        Collections.swap(active, index, index + 1))), buttonsX + 16, y);
            }
            pane.add(Widgets.button(Component.literal("x"), 14, 14, () -> this.editWaypoints(() ->
                    active.remove(index))), buttonsX + 34, y);
        }
        List<String> available = this.availableWaypoints(active);
        for (int i = 0; i < available.size() && i < MAX_PICKER; i++) {
            String id = available.get(i);
            pane.add(Widgets.button(Component.literal(id), WAYPOINT_COLUMN - 10, 14,
                    () -> this.editWaypoints(() -> active.add(id))), formX + WAYPOINT_COLUMN + 8, sections.waypointList + i * 18);
        }
    }

    private void editWaypoints(Runnable edit) {
        edit.run();
        this.screen.refresh();
    }

    private List<String> activeWaypoints() {
        return this.editingArrival ? this.arrivalWaypoints : this.departureWaypoints;
    }

    /** Possible arrival entry points: runway A ends, runway B ends, helipads. */
    private List<MarkerView> entryPoints() {
        List<MarkerView> entries = new ArrayList<>(this.markers().runwayA());
        entries.addAll(this.markers().runwayB());
        entries.addAll(this.markers().helipads());
        return entries;
    }

    /** Markers of any kind that can still be added to the waypoint list (any marker can be a taxi point). */
    private List<String> availableWaypoints(List<String> active) {
        List<String> result = new ArrayList<>();
        for (List<MarkerView> markers : List.of(this.markers().waypoints(), this.markers().runwayA(),
                this.markers().runwayB(), this.markers().helipads(), this.markers().parking())) {
            for (MarkerView marker : markers) {
                if (!active.contains(marker.id())) result.add(marker.id());
            }
        }
        return result;
    }

    // Routes

    private void loadRoute(int index) {
        TaxiRouteData route = this.screen.routes().get(index);
        this.selectedRoute = index;
        this.routeId = route.routeId();
        this.parkingId = route.parkingId();
        this.runwayId = route.runways().runwayId();
        this.runwayBId = route.runways().runwayBId();
        this.arrivalRunwayId = route.runways().arrivalRunwayId();
        this.departureWaypoints.clear();
        this.departureWaypoints.addAll(route.waypointIds());
        this.arrivalWaypoints.clear();
        this.arrivalWaypoints.addAll(route.arrivalWaypointIds());
        this.screen.refresh();
    }

    private void newRoute() {
        this.selectedRoute = -1;
        this.clearForm();
        this.screen.refresh();
    }

    private void clearForm() {
        this.routeId = "";
        this.parkingId = "";
        this.runwayId = "";
        this.runwayBId = "";
        this.arrivalRunwayId = "";
        this.departureWaypoints.clear();
        this.arrivalWaypoints.clear();
        this.editingArrival = false;
    }

    private void saveRoute() {
        String id = this.routeId.trim();
        if (id.isEmpty()) {
            this.tell("route_id_empty");
            return;
        }
        if (this.parkingId.isEmpty()) {
            this.tell("select_parking");
            return;
        }
        if (this.runwayId.isEmpty()) {
            if (!this.screen.isHelipad(this.parkingId)) {
                this.tell("select_endpoint");
                return;
            }
            // A helipad used as parking is also the route's runway end.
            this.runwayId = this.parkingId;
        }
        // The parking heading is set on the parking marker itself.
        TaxiRouteData route = new TaxiRouteData(id, this.parkingId,
                new TaxiRunways(this.runwayId, this.runwayBId, this.arrivalRunwayId),
                this.departureWaypoints, this.arrivalWaypoints, -1);
        Network.sendToServer(new SaveTaxiRouteMessage(this.screen.base().baseId(), route));
        List<TaxiRouteData> routes = this.screen.routes();
        int existing = this.screen.indexOfRoute(id);
        if (existing >= 0) {
            routes.set(existing, route);
        } else {
            routes.add(route);
            this.selectedRoute = routes.size() - 1;
        }
        this.screen.refresh();
    }

    private void tell(String problem) {
        Minecraft.getInstance().player.sendSystemMessage(
                Component.translatable("mcheli.wingman.base.error." + problem).withStyle(ChatFormatting.RED));
    }

    private void deleteRoute() {
        List<TaxiRouteData> routes = this.screen.routes();
        if (this.selectedRoute < 0 || this.selectedRoute >= routes.size()) return;
        Network.sendToServer(new DeleteTaxiRouteMessage(routes.get(this.selectedRoute).routeId()));
        routes.remove(this.selectedRoute);
        this.selectedRoute = -1;
        this.clearForm();
        this.screen.refresh();
    }

    // Drawing

    private void paint(GuiGraphics g, int top, Sections sections) {
        Font font = Minecraft.getInstance().font;
        int formX = this.formX();
        int muted = this.screen.theme().muted();
        g.drawString(font, Component.translatable("mcheli.wingman.base.routes"), this.listX(), top + 2, muted);
        if (this.selectedRoute >= 0 && this.selectedRoute < this.routeButtons.size()) {
            BasePlannerScreen.frame(g, this.routeButtons.get(this.selectedRoute), ROUTE_SELECTED);
        }
        g.drawString(font, Component.translatable("mcheli.wingman.base.route_id"), formX, top + 2, muted);

        g.drawString(font, Component.translatable("mcheli.wingman.base.parking"), formX, top + sections.parking - 10, muted);
        this.frameSelected(g, this.parkingButtons, this.parkingId);
        g.drawString(font, Component.translatable("mcheli.wingman.base.runway_a"), formX, top + sections.runwayA - 10, muted);
        if (this.markers().runwayA().isEmpty()) {
            g.drawString(font, Component.translatable("mcheli.wingman.base.no_runway_a"), formX, top + sections.runwayA + 2, ERROR);
        }
        this.frameSelected(g, this.runwayAButtons, this.runwayId);
        g.drawString(font, Component.translatable("mcheli.wingman.base.runway_b"), formX, top + sections.runwayB - 10, muted);
        if (this.markers().runwayB().isEmpty()) {
            g.drawString(font, Component.translatable("mcheli.wingman.base.no_runway_b"), formX, top + sections.runwayB + 2, muted);
        }
        this.frameSelected(g, this.runwayBButtons, this.runwayBId);
        // A helipad can be the runway end, or parking and runway end at once for pure VTOL routes.
        g.drawString(font, Component.translatable("mcheli.wingman.base.helipad").withStyle(ChatFormatting.BLUE)
                .append(Component.translatable("mcheli.wingman.base.helipad.use").withStyle(ChatFormatting.GRAY)),
                formX, top + sections.helipad - 10, muted);
        if (this.markers().helipads().isEmpty()) {
            g.drawString(font, Component.translatable("mcheli.wingman.base.no_helipad"), formX, top + sections.helipad + 2, muted);
        }
        for (Button button : this.helipadButtons) {
            String id = button.getMessage().getString();
            if (id.equals(this.parkingId) || id.equals(this.runwayId)) {
                BasePlannerScreen.frame(g, button, BasePlannerScreen.SELECTED);
            }
        }
        this.paintHelipadB(g, font, formX, top + sections.helipadB);
        this.paintPreview(g, font, formX, top + sections.waypoints);
        this.paintWaypointEditor(g, font, formX, top, sections);
    }

    private void frameSelected(GuiGraphics g, List<Button> buttons, String selectedId) {
        for (Button button : buttons) {
            if (button.getMessage().getString().equals(selectedId)) {
                BasePlannerScreen.frame(g, button, BasePlannerScreen.SELECTED);
                return;
            }
        }
    }

    /** HELIPAD_B markers only give the VTOL heading; they are listed for information. */
    private void paintHelipadB(GuiGraphics g, Font font, int x, int y) {
        g.drawString(font, Component.translatable("mcheli.wingman.base.helipad_b").withStyle(ChatFormatting.AQUA)
                .append(Component.translatable("mcheli.wingman.base.helipad_b.use").withStyle(ChatFormatting.GRAY)), x, y,
                this.screen.theme().muted());
        List<MarkerView> helipads = this.markers().helipadB();
        if (helipads.isEmpty()) {
            g.drawString(font, Component.translatable("mcheli.wingman.base.no_helipad_b"), x, y + 12, ERROR);
            return;
        }
        // Room for three on one line.
        for (int i = 0; i < helipads.size() && i < 3; i++) {
            MarkerView marker = helipads.get(i);
            g.drawString(font, Component.literal(marker.id() + " ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("(" + marker.pos().getX() + "," + marker.pos().getZ() + ")")
                            .withStyle(ChatFormatting.GRAY)), x + i * 100, y + 12, this.screen.theme().text());
        }
    }

    /** One line each for the departure and arrival sequence of the route being edited. */
    private void paintPreview(GuiGraphics g, Font font, int x, int waypointsY) {
        Component parking = this.parkingId.isEmpty() ? unknown() : Component.literal(this.parkingId).withStyle(ChatFormatting.GREEN);
        Component runway = this.runwayId.isEmpty() ? unknown() : Component.literal(this.runwayId).withStyle(ChatFormatting.AQUA);
        MutableComponent departure = sequence("departure", parking, this.departureWaypoints, runway);
        int maxWidth = this.screen.width - x - 12;
        if (font.width(departure) > maxWidth && this.departureWaypoints.size() > 2) {
            List<String> shortened = List.of(this.departureWaypoints.getFirst(),
                    "…(" + (this.departureWaypoints.size() - 2) + ")…", this.departureWaypoints.getLast());
            departure = sequence("departure", parking, shortened, runway);
        }
        g.drawString(font, departure, x, waypointsY - 52, this.screen.theme().text());

        String entry = this.arrivalRunwayId.isEmpty() ? this.runwayId : this.arrivalRunwayId;
        Component entryLabel = entry.isEmpty() ? Component.literal("?") : Component.literal(entry).withStyle(ChatFormatting.AQUA);
        Component parkingLabel = this.parkingId.isEmpty() ? Component.literal("?")
                : Component.literal(this.parkingId).withStyle(ChatFormatting.GREEN);
        Component arrival = this.arrivalWaypoints.isEmpty()
                ? Component.translatable("mcheli.wingman.base.arrival_reversed", entryLabel, parkingLabel)
                .withStyle(ChatFormatting.GRAY)
                : sequence("arrival", entryLabel, this.arrivalWaypoints, parkingLabel);
        g.drawString(font, arrival, x, waypointsY - 38, FAINT);
    }

    private static Component unknown() {
        return Component.literal("?").withStyle(ChatFormatting.RED);
    }

    /** "Departure: start→wp→wp→end", with the arrows grey and the waypoints white. */
    private static MutableComponent sequence(String kind, Component start, List<String> waypoints, Component end) {
        MutableComponent line = Component.translatable("mcheli.wingman.base." + kind).withStyle(ChatFormatting.GRAY)
                .append(start);
        for (String waypoint : waypoints) {
            line.append(Component.literal("→").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(waypoint).withStyle(ChatFormatting.WHITE));
        }
        return line.append(Component.literal("→").withStyle(ChatFormatting.GRAY)).append(end);
    }

    private void paintWaypointEditor(GuiGraphics g, Font font, int formX, int top, Sections sections) {
        int waypointsY = top + sections.waypoints;
        int listY = top + sections.waypointList;
        int column = 118;
        if (this.editingArrival) {
            BasePlannerScreen.frame(g, this.modeToggle, BasePlannerScreen.SELECTED);
            g.drawString(font, Component.translatable("mcheli.wingman.base.entry_point"), formX + column + 8,
                    waypointsY - 6, 0xFF55FFFF);
            this.frameSelected(g, this.entryButtons, this.arrivalRunwayId);
        }
        g.drawString(font, Component.translatable(this.editingArrival ? "mcheli.wingman.base.arrival_list"
                        : "mcheli.wingman.base.departure_list")
                .withStyle(this.editingArrival ? ChatFormatting.AQUA : ChatFormatting.WHITE), formX + 94, waypointsY - 6,
                this.screen.theme().text());
        g.drawString(font, Component.translatable("mcheli.wingman.base.available"), formX + column + 8, listY - 6,
                this.screen.theme().muted());
        List<String> active = this.activeWaypoints();
        for (int i = 0; i < active.size() && i < MAX_PICKER; i++) {
            g.drawString(font, (i + 1) + ". " + active.get(i), formX, listY + i * 18 + 3, this.screen.theme().text());
        }
        if (active.isEmpty() && this.editingArrival) {
            g.drawString(font, Component.translatable("mcheli.wingman.base.arrival_empty"), formX, listY + 3,
                    this.screen.theme().muted());
        } else if (active.isEmpty()) {
            g.drawString(font, Component.translatable("mcheli.wingman.base.none"), formX, waypointsY + 3,
                    this.screen.theme().muted());
        }
    }
}
