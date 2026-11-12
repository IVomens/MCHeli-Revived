package com.ivomens.mcheli.wingman.client;

import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.McScreen;
import com.ivomens.mcheli.ui.client.ScrollPane;
import com.ivomens.mcheli.ui.client.TabBar;
import com.ivomens.mcheli.ui.client.Widgets;
import com.ivomens.mcheli.core.wingman.brain.AttackMode;
import com.ivomens.mcheli.wingman.net.FormationSettings;
import com.ivomens.mcheli.wingman.net.FormationSettingsMessage;
import com.ivomens.mcheli.wingman.net.NearbyAircraft;
import com.ivomens.mcheli.wingman.net.OpenWingmanPanelMessage;
import com.ivomens.mcheli.wingman.net.WingmanAttackModeMessage;
import com.ivomens.mcheli.wingman.net.WingmanFollowMessage;
import com.ivomens.mcheli.wingman.net.WingmanPanelMessage;
import com.ivomens.mcheli.wingman.net.WingmanStopMessage;
import com.ivomens.mcheli.wingman.net.WingmanView;
import com.ivomens.mcheli.wingman.net.WingmanWeaponMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Wingman control panel, opened with the panel key (numpad 0 by default) while flying an aircraft.
 * The Wingmen tab assigns nearby aircraft as followers and sets their attack mode and weapon; the
 * Formation tab edits the formation distances and attack altitude limits.
 *
 * <p>Values are changed with -/+ buttons instead of text fields, so flight keys never end up typed
 * into the screen.
 */
public final class WingmanPanelScreen extends McScreen {

    private enum Tab { WINGMEN, FORMATION }

    private static final int MAX_NEARBY = 8;
    private static final int MAX_WINGMEN = 8;
    /** Height of one wingman: name line, gap, button row, spacing. */
    private static final int WINGMAN_ROW = 32;
    /** Offset of the first wingman below the section title. */
    private static final int WINGMEN_TOP = 18;
    private static final int NEARBY_ROW = 18;
    /** Content height of the Wingmen tab with the maximum number of wingmen, for scrolling. */
    private static final int WINGMEN_CONTENT_HEIGHT = WINGMEN_TOP + MAX_WINGMEN * WINGMAN_ROW + 8;
    private static final int SCROLL_STEP = 14;
    private static final int FOOTER = 28;
    private static final int FORMATION_ROW = 22;
    private static final int SMALL_BUTTON = 14;
    private static final double STEP_DISTANCE = 5.0;
    private static final double STEP_ALTITUDE = 10.0;
    private static final int MAX_FORMATION_SIZE = 64;
    /** Weapon filters to cycle through; "" means any weapon. */
    private static final String[] WEAPON_TYPES = {"", "gun", "aamissile", "asmissile", "cas", "rocket", "bomb"};

    private final List<NearbyAircraft> nearby;
    private final List<WingmanView> wingmen;
    /** Index into {@link #WEAPON_TYPES} per wingman. */
    private final List<Integer> weaponIndex = new ArrayList<>();
    private Tab tab = Tab.WINGMEN;
    private int wingmenScroll;

    /** Formation values being edited; sent with "Apply". */
    private double sideDistance;
    private double altitudeOffset;
    private double rearDistance;
    private int maxWingmen;
    private double minAltitude;
    private double maxAltitude;

    public WingmanPanelScreen(WingmanPanelMessage panel) {
        super(Component.translatable("mcheli.wingman.panel.title"));
        this.nearby = new ArrayList<>(panel.nearby());
        this.wingmen = new ArrayList<>(panel.wingmen());
        FormationSettings formation = panel.formation();
        this.sideDistance = formation.sideDist();
        this.altitudeOffset = formation.altOffset();
        this.rearDistance = formation.rearDist();
        this.maxWingmen = formation.maxWingmen();
        this.minAltitude = formation.minAttackAltitude();
        this.maxAltitude = formation.maxAttackAltitude();
        for (WingmanView wingman : this.wingmen) {
            this.weaponIndex.add(weaponIndexOf(wingman.weaponType()));
        }
    }

    private static int weaponIndexOf(String weaponType) {
        for (int i = 0; i < WEAPON_TYPES.length; i++) {
            if (WEAPON_TYPES[i].equals(weaponType)) return i;
        }
        return 0;
    }

    private int top() {
        return this.height / 4;
    }

    @Override
    protected void build() {
        int cx = this.width / 2;
        TabBar<Tab> tabs = Widgets.tabs(this.theme, this.tab, this::showTab);
        Layout tabRow = this.row(cx - 82, this.top() - 14).gap(4);
        tabRow.add(tabs.tab(Tab.WINGMEN, Component.translatable("mcheli.wingman.panel.tab.wingmen"), 80, 16));
        tabRow.add(tabs.tab(Tab.FORMATION, Component.translatable("mcheli.wingman.panel.tab.formation"), 80, 16));
        this.addRenderableOnly(tabs);
        this.add(Widgets.button(Component.translatable("mcheli.wingman.refresh"), 55, SMALL_BUTTON,
                () -> Network.sendToServer(new OpenWingmanPanelMessage()))).setPosition(cx - 82, this.height - FOOTER);
        this.add(Widgets.button(Component.translatable("mcheli.wingman.close"), 52, SMALL_BUTTON, this::onClose))
                .setPosition(cx + 30, this.height - FOOTER);
        if (this.tab == Tab.WINGMEN) {
            this.buildWingmen(cx);
        } else {
            this.buildFormation(cx);
        }
    }

    private void showTab(Tab target) {
        this.tab = target;
        this.wingmenScroll = 0;
        this.rebuild();
    }

    private void buildWingmen(int cx) {
        int top = this.top();
        ScrollPane pane = Widgets.scrollPane(this.theme, this.width, this.height - FOOTER - top)
                .contentHeight(WINGMEN_CONTENT_HEIGHT).step(SCROLL_STEP)
                .painter((g, left, contentTop, mouseX, mouseY) -> this.paintWingmen(g, cx, contentTop))
                .onScroll(scroll -> this.wingmenScroll = scroll);
        pane.setPosition(0, top);
        for (int i = 0; i < Math.min(this.nearby.size(), MAX_NEARBY); i++) {
            UUID id = this.nearby.get(i).id();
            pane.add(Widgets.button(Component.translatable("mcheli.wingman.panel.follow"), 52, SMALL_BUTTON,
                    () -> this.follow(id)), cx - 100, 8 + i * NEARBY_ROW);
        }
        for (int i = 0; i < Math.min(this.wingmen.size(), MAX_WINGMEN); i++) {
            this.addWingmanButtons(pane, i, cx - 30, WINGMEN_TOP + i * WINGMAN_ROW + 11);
        }
        pane.setScroll(this.wingmenScroll);
        this.add(pane);
    }

    private void addWingmanButtons(ScrollPane pane, int index, int x, int y) {
        WingmanView wingman = this.wingmen.get(index);
        Component auto = Component.translatable("mcheli.wingman.panel.auto");
        if (wingman.attackMode() == AttackMode.AUTO.code()) {
            auto = auto.copy().withStyle(ChatFormatting.DARK_GREEN);
        }
        pane.add(Widgets.button(auto, 34, SMALL_BUTTON, () -> this.setAttack(index, true)), x, y);
        pane.add(Widgets.button(Component.translatable("mcheli.wingman.panel.hold"), 32, SMALL_BUTTON,
                () -> this.setAttack(index, false)), x + 36, y);
        pane.add(Widgets.button(Component.translatable("mcheli.wingman.panel.stop").withStyle(ChatFormatting.RED), 30,
                SMALL_BUTTON, () -> this.stop(index)), x + 70, y);
        pane.add(Widgets.button(Component.literal("<"), 16, SMALL_BUTTON, () -> this.cycleWeapon(index, -1)), x + 104, y);
        pane.add(Widgets.button(Component.literal(">"), 16, SMALL_BUTTON, () -> this.cycleWeapon(index, 1)), x + 156, y);
    }

    private void buildFormation(int cx) {
        int y = this.top() + 10;
        this.addSpinner(cx, y, () -> this.sideDistance = Math.max(0, this.sideDistance - STEP_DISTANCE),
                () -> this.sideDistance += STEP_DISTANCE);
        this.addSpinner(cx, y + FORMATION_ROW, () -> this.altitudeOffset -= STEP_DISTANCE,
                () -> this.altitudeOffset += STEP_DISTANCE);
        this.addSpinner(cx, y + FORMATION_ROW * 2, () -> this.rearDistance = Math.max(0, this.rearDistance - STEP_DISTANCE),
                () -> this.rearDistance += STEP_DISTANCE);
        this.addSpinner(cx, y + FORMATION_ROW * 3, () -> this.maxWingmen = Math.max(1, this.maxWingmen - 1),
                () -> this.maxWingmen = Math.min(MAX_FORMATION_SIZE, this.maxWingmen + 1));
        this.addSpinner(cx, y + FORMATION_ROW * 4 + 8, () -> this.minAltitude = Math.max(0, this.minAltitude - STEP_ALTITUDE),
                () -> this.minAltitude += STEP_ALTITUDE);
        this.addSpinner(cx, y + FORMATION_ROW * 5 + 8, () -> this.maxAltitude = Math.max(0, this.maxAltitude - STEP_ALTITUDE),
                () -> this.maxAltitude += STEP_ALTITUDE);
        this.add(Widgets.button(Component.translatable("mcheli.wingman.panel.apply"), 60, 16, this::sendFormation))
                .setPosition(cx - 30, y + FORMATION_ROW * 7 + 10);
    }

    /** The value is drawn centred between the two buttons. */
    private void addSpinner(int cx, int y, Runnable decrease, Runnable increase) {
        this.add(Widgets.button(Component.literal("-"), 18, SMALL_BUTTON, decrease)).setPosition(cx - 50, y);
        this.add(Widgets.button(Component.literal("+"), 18, SMALL_BUTTON, increase)).setPosition(cx + 32, y);
    }

    private void follow(UUID id) {
        Network.sendToServer(new WingmanFollowMessage(id));
        this.nearby.removeIf(aircraft -> aircraft.id().equals(id));
        this.rebuild();
    }

    private void setAttack(int index, boolean auto) {
        WingmanView wingman = this.wingmen.get(index);
        Network.sendToServer(new WingmanAttackModeMessage(wingman.id(), auto));
        this.wingmen.set(index, wingman.withAttackMode((auto ? AttackMode.AUTO : AttackMode.NONE).code()));
        this.rebuild();
    }

    private void stop(int index) {
        Network.sendToServer(new WingmanStopMessage(this.wingmen.get(index).id()));
        this.wingmen.remove(index);
        this.weaponIndex.remove(index);
        this.rebuild();
    }

    private void cycleWeapon(int index, int direction) {
        int weapon = Math.floorMod(this.weaponIndex.get(index) + direction, WEAPON_TYPES.length);
        this.weaponIndex.set(index, weapon);
        Network.sendToServer(new WingmanWeaponMessage(this.wingmen.get(index).id(), WEAPON_TYPES[weapon]));
    }

    private void sendFormation() {
        Network.sendToServer(new FormationSettingsMessage(new FormationSettings(this.sideDistance, this.altitudeOffset,
                this.rearDistance, this.maxWingmen, this.minAltitude, this.maxAltitude)));
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, this.top() - 26, this.theme.title());
        if (this.tab == Tab.FORMATION) {
            this.paintFormation(g, cx);
        }
    }

    private void paintWingmen(GuiGraphics g, int cx, int top) {
        int left = cx - 190;
        g.drawString(this.font, Component.translatable("mcheli.wingman.panel.nearby"), left, top + 2, this.theme.muted());
        for (int i = 0; i < Math.min(this.nearby.size(), MAX_NEARBY); i++) {
            NearbyAircraft aircraft = this.nearby.get(i);
            Component label = Component.literal(aircraft.name() + " ")
                    .append(Component.literal(shortId(aircraft.id())).withStyle(ChatFormatting.GRAY));
            g.drawString(this.font, label, left, top + 8 + i * NEARBY_ROW + 3, this.theme.text());
        }
        if (this.nearby.isEmpty()) {
            g.drawString(this.font, Component.translatable("mcheli.wingman.panel.none_nearby"), left, top + 11,
                    this.theme.muted());
        }
        int right = cx - 30;
        g.drawString(this.font, Component.translatable("mcheli.wingman.panel.yours"), right, top + 2, this.theme.muted());
        for (int i = 0; i < Math.min(this.wingmen.size(), MAX_WINGMEN); i++) {
            WingmanView wingman = this.wingmen.get(i);
            int rowY = top + WINGMEN_TOP + i * WINGMAN_ROW;
            Component header = Component.literal(wingman.name() + " ")
                    .append(Component.literal("#" + wingman.slot() + " ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("[" + wingman.state() + "]").withStyle(ChatFormatting.DARK_GRAY));
            g.drawString(this.font, header, right, rowY, this.theme.text());
            g.drawCenteredString(this.font, weaponLabel(this.weaponIndex.get(i)), right + 138, rowY + 14,
                    this.theme.title());
        }
        if (this.wingmen.isEmpty()) {
            g.drawString(this.font, Component.translatable("mcheli.wingman.panel.none_assigned"), right, top + 11,
                    this.theme.muted());
        }
    }

    private static Component weaponLabel(int index) {
        String type = WEAPON_TYPES[index];
        return type.isEmpty() ? Component.translatable("mcheli.wingman.panel.any_weapon") : Component.literal(type);
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8) + "...";
    }

    private void paintFormation(GuiGraphics g, int cx) {
        int labelX = cx - 140;
        int valueX = cx - 1;
        int y = this.top() + 10;
        g.drawString(this.font, Component.translatable("mcheli.wingman.panel.formation"), labelX, y - 8, this.theme.muted());
        this.paintValue(g, labelX, valueX, y, "side_distance", format(this.sideDistance));
        this.paintValue(g, labelX, valueX, y + FORMATION_ROW, "altitude_offset", format(this.altitudeOffset));
        this.paintValue(g, labelX, valueX, y + FORMATION_ROW * 2, "rear_distance", format(this.rearDistance));
        this.paintValue(g, labelX, valueX, y + FORMATION_ROW * 3, "max_wingmen", Component.literal(Integer.toString(this.maxWingmen)));
        g.drawString(this.font, Component.translatable("mcheli.wingman.panel.attack_altitude"), labelX,
                y + FORMATION_ROW * 4 + 2, this.theme.muted());
        this.paintValue(g, labelX, valueX, y + FORMATION_ROW * 4 + 8, "min_altitude", limit(this.minAltitude));
        this.paintValue(g, labelX, valueX, y + FORMATION_ROW * 5 + 8, "max_altitude", limit(this.maxAltitude));
        g.drawString(this.font, Component.translatable("mcheli.wingman.panel.no_limit_hint"), labelX,
                y + FORMATION_ROW * 6 + 4, this.theme.muted());
    }

    private void paintValue(GuiGraphics g, int labelX, int valueX, int y, String name, Component value) {
        g.drawString(this.font, Component.translatable("mcheli.wingman.panel." + name), labelX, y + 3, this.theme.muted());
        g.drawCenteredString(this.font, value, valueX, y + 3, this.theme.text());
    }

    private static Component format(double value) {
        return Component.literal(String.format("%.1f", value));
    }

    private static Component limit(double value) {
        return value == 0 ? Component.translatable("mcheli.wingman.panel.off") : format(value);
    }

    /** No text fields here, so every key is swallowed; Escape closes the panel. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
        }
        return true;
    }
}
