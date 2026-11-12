package com.ivomens.mcheli.wingman.client;

import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.ui.client.ScrollPane;
import com.ivomens.mcheli.ui.client.Widgets;
import com.ivomens.mcheli.core.wingman.mission.MissionType;
import com.ivomens.mcheli.wingman.net.DispatchOrderMessage;
import com.ivomens.mcheli.wingman.net.FlightPlan;
import com.ivomens.mcheli.wingman.net.NearbyAircraft;
import com.ivomens.mcheli.wingman.net.StrikePlan;
import com.ivomens.mcheli.wingman.net.TaxiRouteData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Mission tab of the base planner: mission types and weapons, the order's numbers, the aircraft to
 * send and its departure and arrival routes, and the Dispatch button.
 */
final class BaseMissionTab {

    private static final List<String> MISSION_TYPES = List.of("CAP", "CAS", "STRIKE", "ESCORT", "RECON", "FERRY");
    /** STRIKE and FERRY replace the selection; the others combine. */
    private static final Set<String> EXCLUSIVE_MISSIONS = Set.of("STRIKE", "FERRY");
    private static final List<String> WEAPON_TYPES = List.of("gun", "aamissile", "asmissile", "cas", "rocket", "bomb");
    /** The order's numbers, in field order, with their defaults. */
    private static final String[] FIELDS = {"target_x", "target_z", "orbit_radius", "cruise_altitude", "passes", "time_limit"};
    private static final int TARGET_X = 0;
    private static final int TARGET_Z = 1;
    private static final int ORBIT_RADIUS = 2;
    private static final int CRUISE_ALTITUDE = 3;
    private static final int STRIKE_PASSES = 4;
    private static final int TIME_LIMIT = 5;
    private static final int MAX_AIRCRAFT = 8;
    private static final int MAX_ROUTES = 8;
    private static final int MAX_BUTTON_LABEL = 14;
    private static final int GRID_BUTTON = 58;
    private static final int GAP = 4;
    private static final int GRID_WIDTH = 3 * GRID_BUTTON + 2 * GAP;
    private static final int CONTENT_HEIGHT = 460;

    private final BasePlannerScreen screen;
    private final Set<String> missionTypes = new LinkedHashSet<>();
    private final Set<String> weapons = new LinkedHashSet<>();
    /** The numbers as typed, kept across rebuilds. */
    private final String[] values = {"0", "0", "300", "80", "2", "600"};
    private final List<EditBox> fields = new ArrayList<>();
    private final Map<String, Button> missionButtons = new LinkedHashMap<>();
    private final Map<String, Button> weaponButtons = new LinkedHashMap<>();
    private final List<Button> aircraftButtons = new ArrayList<>();
    private final List<Button> departureButtons = new ArrayList<>();
    private final List<Button> arrivalButtons = new ArrayList<>();
    private Button orbitAttackButton;
    private int focusedField = TARGET_X;
    private UUID aircraftId;
    /** Departure route of the order; empty for an airborne start. */
    private String departureRoute = "";
    /** Dedicated arrival route of the order; empty to use the fallback search. */
    private String arrivalRoute = "";
    private boolean orbitAttack;

    BaseMissionTab(BasePlannerScreen screen) {
        this.screen = screen;
        this.weapons.add("gun");
    }

    /** Offsets of the tab's sections from its top. */
    private final class Sections {
        final int fields = 112;
        final int fieldWidth = (GRID_WIDTH - 8) / 2;
        final int aircraft = 204;
        final int departure = this.aircraft + Math.min((aircraft().size() + 1) / 2, MAX_AIRCRAFT / 2) * 20 + 14;
        final int arrival = this.departure + this.routeRows() * 20 + 14;
        final int dispatch = this.arrival + this.routeRows() * 20 + 6;

        private int routeRows() {
            List<TaxiRouteData> routes = BaseMissionTab.this.screen.routes();
            return routes.isEmpty() ? 0 : Math.min((routes.size() + 1) / 2, MAX_ROUTES / 2);
        }
    }

    private List<NearbyAircraft> aircraft() {
        return this.screen.base().nearbyAircraft();
    }

    private int gridX() {
        return this.screen.width / 2 - GRID_WIDTH / 2;
    }

    void build(ScrollPane pane) {
        Sections sections = new Sections();
        this.missionButtons.clear();
        this.weaponButtons.clear();
        for (int i = 0; i < MISSION_TYPES.size(); i++) {
            String type = MISSION_TYPES.get(i);
            this.missionButtons.put(type, this.gridButton(pane, Component.translatable("mcheli.wingman.mission." + type.toLowerCase()),
                    i, 10, () -> this.toggleMission(type)));
        }
        for (int i = 0; i < WEAPON_TYPES.size(); i++) {
            String weapon = WEAPON_TYPES.get(i);
            this.weaponButtons.put(weapon, this.gridButton(pane, Component.translatable("mcheli.wingman.weapon." + weapon),
                    i, 58, () -> this.toggleWeapon(weapon)));
        }
        this.buildFields(pane, sections);
        this.buildPickers(pane, sections);
        int cx = this.screen.width / 2;
        pane.add(Widgets.button(Component.translatable("mcheli.wingman.mission.dispatch"), 80, 16, this::dispatch),
                cx - 40, sections.dispatch);
        this.orbitAttackButton = pane.add(Widgets.button(Component.translatable("mcheli.wingman.mission.orbit_attack"),
                70, 16, () -> this.orbitAttack = !this.orbitAttack), cx + 44, sections.dispatch);
        pane.contentHeight(CONTENT_HEIGHT).painter((g, left, top, mouseX, mouseY) -> this.paint(g, top, sections));
    }

    private Button gridButton(ScrollPane pane, Component label, int index, int y, Runnable onPress) {
        return pane.add(Widgets.button(label, GRID_BUTTON, 16, onPress),
                this.gridX() + index % 3 * (GRID_BUTTON + GAP), y + index / 3 * 20);
    }

    private void buildFields(ScrollPane pane, Sections sections) {
        this.fields.clear();
        for (int i = 0; i < FIELDS.length; i++) {
            int index = i;
            EditBox field = pane.add(Widgets.text(sections.fieldWidth, 16, 128,
                            Component.translatable("mcheli.wingman.mission.field." + FIELDS[i])),
                    this.gridX() + i % 2 * (sections.fieldWidth + 8), sections.fields + i / 2 * 30);
            field.setValue(this.values[i]);
            field.setResponder(value -> this.values[index] = value);
            this.fields.add(field);
        }
        this.focusedField = TARGET_X;
        pane.focus(this.fields.get(TARGET_X));
    }

    private void buildPickers(ScrollPane pane, Sections sections) {
        int half = (GRID_WIDTH - GAP) / 2;
        this.aircraftButtons.clear();
        List<NearbyAircraft> aircraft = this.aircraft();
        for (int i = 0; i < aircraft.size() && i < MAX_AIRCRAFT; i++) {
            UUID id = aircraft.get(i).id();
            this.aircraftButtons.add(pane.add(Widgets.button(Component.literal(truncate(aircraft.get(i).name())), half, 16,
                    () -> this.aircraftId = id), this.gridX() + i % 2 * (half + GAP), sections.aircraft + i / 2 * 20));
        }
        // Route buttons toggle: clicking the selected route again clears it.
        List<TaxiRouteData> routes = this.screen.routes();
        this.departureButtons.clear();
        this.arrivalButtons.clear();
        for (int i = 0; i < routes.size() && i < MAX_ROUTES; i++) {
            String route = routes.get(i).routeId();
            int x = this.gridX() + i % 2 * (half + GAP);
            this.departureButtons.add(pane.add(Widgets.button(Component.literal(truncate(route)), half, 16,
                    () -> this.departureRoute = this.departureRoute.equals(route) ? "" : route), x, sections.departure + i / 2 * 20));
            this.arrivalButtons.add(pane.add(Widgets.button(Component.literal(truncate(route)), half, 16,
                    () -> this.arrivalRoute = this.arrivalRoute.equals(route) ? "" : route), x, sections.arrival + i / 2 * 20));
        }
    }

    private static String truncate(String label) {
        return label.length() > MAX_BUTTON_LABEL ? label.substring(0, MAX_BUTTON_LABEL - 1) + "…" : label;
    }

    private void toggleMission(String type) {
        if (EXCLUSIVE_MISSIONS.contains(type)) {
            if (!this.missionTypes.remove(type)) {
                this.missionTypes.clear();
                this.missionTypes.add(type);
            }
            return;
        }
        if (this.missionTypes.stream().anyMatch(EXCLUSIVE_MISSIONS::contains)) {
            this.missionTypes.clear();
        }
        if (!this.missionTypes.remove(type)) {
            this.missionTypes.add(type);
        }
    }

    private void toggleWeapon(String weapon) {
        if (!this.weapons.remove(weapon)) {
            this.weapons.add(weapon);
        }
    }

    /** Tab moves the keyboard on to the next number. */
    void focusNextField(ScrollPane pane) {
        this.focusedField = (this.focusedField + 1) % this.fields.size();
        pane.focus(this.fields.get(this.focusedField));
    }

    private void dispatch() {
        if (this.aircraftId == null || this.missionTypes.isEmpty()) {
            return;
        }
        List<MissionType> types = this.missionTypes.stream().map(MissionType::valueOf).toList();
        StrikePlan strike = new StrikePlan(this.number(TARGET_X, 0), this.number(TARGET_Z, 0),
                this.number(ORBIT_RADIUS, 300), this.orbitAttack, this.whole(STRIKE_PASSES, 2));
        // An arrival route ending at a helipad means a vertical landing.
        FlightPlan flight = new FlightPlan(this.screen.base().baseId(), "", this.arrivalRoute,
                this.number(CRUISE_ALTITUDE, 80), this.whole(TIME_LIMIT, 600),
                this.screen.isVtolArrivalRoute(this.arrivalRoute));
        Network.sendToServer(new DispatchOrderMessage(this.aircraftId, this.departureRoute, types,
                new ArrayList<>(this.weapons), strike, flight));
    }

    private double number(int field, double fallback) {
        try {
            return Double.parseDouble(this.values[field].trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int whole(int field, int fallback) {
        try {
            return Integer.parseInt(this.values[field].trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // Drawing

    private void paint(GuiGraphics g, int top, Sections sections) {
        Font font = Minecraft.getInstance().font;
        int x = this.gridX();
        int muted = this.screen.theme().muted();
        g.drawString(font, Component.translatable("mcheli.wingman.mission.types"), x, top, muted);
        g.drawString(font, Component.translatable("mcheli.wingman.mission.weapons"), x, top + 48, muted);
        for (int i = 0; i < FIELDS.length; i++) {
            EditBox field = this.fields.get(i);
            g.drawString(font, field.getMessage(), field.getX(), field.getY() - 12, muted);
        }
        this.missionTypes.forEach(type -> BasePlannerScreen.frame(g, this.missionButtons.get(type), BasePlannerScreen.CHOSEN));
        this.weapons.forEach(weapon -> BasePlannerScreen.frame(g, this.weaponButtons.get(weapon), BasePlannerScreen.CHOSEN));
        if (this.orbitAttack) {
            BasePlannerScreen.frame(g, this.orbitAttackButton, BasePlannerScreen.CHOSEN);
        }
        this.paintPickers(g, font, x, top, sections);
    }

    private void paintPickers(GuiGraphics g, Font font, int x, int top, Sections sections) {
        int muted = this.screen.theme().muted();
        g.drawString(font, Component.translatable("mcheli.wingman.mission.aircraft"), x, top + sections.aircraft - 12, muted);
        if (this.aircraft().isEmpty()) {
            g.drawString(font, Component.translatable("mcheli.wingman.mission.no_aircraft"), x, top + sections.aircraft + 3, muted);
        }
        for (int i = 0; i < this.aircraftButtons.size(); i++) {
            if (this.aircraft().get(i).id().equals(this.aircraftId)) {
                BasePlannerScreen.frame(g, this.aircraftButtons.get(i), BasePlannerScreen.SELECTED);
            }
        }
        boolean noRoutes = this.screen.routes().isEmpty();
        g.drawString(font, this.optional("departure"), x, top + sections.departure - 12, muted);
        if (noRoutes) {
            g.drawString(font, Component.translatable("mcheli.wingman.mission.no_routes"), x, top + sections.departure + 3, muted);
        }
        this.frameRoute(g, this.departureButtons, this.departureRoute);
        g.drawString(font, this.optional("arrival"), x, top + sections.arrival - 12, muted);
        if (noRoutes) {
            g.drawString(font, Component.translatable("mcheli.wingman.base.none"), x, top + sections.arrival + 3, muted);
        }
        this.frameRoute(g, this.arrivalButtons, this.arrivalRoute);
        if (this.arrivalRoute.isEmpty()) {
            g.drawString(font, Component.translatable("mcheli.wingman.mission.fallback"), x + GRID_WIDTH + 4,
                    top + sections.arrival + 3, this.screen.theme().faint());
        }
    }

    private Component optional(String route) {
        return Component.translatable("mcheli.wingman.mission." + route).withStyle(ChatFormatting.WHITE)
                .append(Component.translatable("mcheli.wingman.mission.optional").withStyle(ChatFormatting.GRAY));
    }

    private void frameRoute(GuiGraphics g, List<Button> buttons, String route) {
        List<TaxiRouteData> routes = this.screen.routes();
        for (int i = 0; i < buttons.size(); i++) {
            if (routes.get(i).routeId().equals(route)) {
                BasePlannerScreen.frame(g, buttons.get(i), BasePlannerScreen.SELECTED);
            }
        }
    }

    /** Below the tab: the landing an arrival route at a helipad brings. */
    void renderFooter(GuiGraphics g, int y) {
        if (this.screen.isVtolArrivalRoute(this.arrivalRoute)) {
            g.drawString(Minecraft.getInstance().font, Component.translatable("mcheli.wingman.mission.vtol")
                    .withStyle(ChatFormatting.AQUA), this.gridX(), y, this.screen.theme().text());
        }
    }
}
