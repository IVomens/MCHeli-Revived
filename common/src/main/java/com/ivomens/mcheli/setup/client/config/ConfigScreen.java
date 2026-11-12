package com.ivomens.mcheli.setup.client.config;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.config.BoolOption;
import com.ivomens.mcheli.config.ControlSettings;
import com.ivomens.mcheli.config.DebugSettings;
import com.ivomens.mcheli.config.HudSettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.config.McheliSettings;
import com.ivomens.mcheli.config.RenderSettings;
import com.ivomens.mcheli.content.def.vehicle.VehicleSchema;
import com.ivomens.mcheli.setup.client.McheliClient;
import com.ivomens.mcheli.client.overlay.HudCanvas.LineMode;
import com.ivomens.mcheli.client.overlay.ScreenCanvas;
import com.ivomens.mcheli.multiplay.client.TargetMarkerOverlay;
import com.ivomens.mcheli.setup.client.config.ConfigDraft.HitMarkChannel;
import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.McScreen;
import com.ivomens.mcheli.ui.client.Slider;
import com.ivomens.mcheli.ui.client.SliderModel;
import com.ivomens.mcheli.ui.client.Widgets;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.util.ModLogger;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * The MCHeli options: controls, rendering, key bindings and, in single player, content reloading for
 * pack authors. Nothing is saved until "Save & Close"; "Cancel" and Escape drop the changes.
 */
public final class ConfigScreen extends McScreen {

    private enum Page { CONTROLS, RENDER, KEYS, HANDLING, DEVELOPMENT }

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(Ids.MOD_ID,
            "textures/gui/config.png");
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 200;
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int MARGIN = 10;
    private static final int COLUMN_WIDTH = 150;
    private static final int HALF_GAP = 4;
    private static final int HALF_WIDTH = (COLUMN_WIDTH - HALF_GAP) / 2;
    private static final int RIGHT_COLUMN = MARGIN + COLUMN_WIDTH + 10;
    private static final int ROW = 25;
    private static final int FIRST_ROW = 25;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BACK_WIDTH = 90;
    private static final int MARKER_PREVIEW_COLOR = 0xFFFF0000;
    private static final int WARNING_COLOR = 0xFFFF0000;
    private static final int HIT_MARK_OUTER = 10;
    private static final int HIT_MARK_INNER = 5;
    /** The rhombus preview of the block marker shows all four tips. */
    private static final int ALL_TIPS = 15;

    private final ConfigDraft draft;
    private final KeyBindingPage keys;
    private final HandlingPage handling;
    private final boolean developmentTools;
    private Page page = Page.CONTROLS;
    private int left;
    private int top;
    private Slider<Float> entityMarkerSize;
    private Slider<Float> blockMarkerSize;
    private Button reloadAircraft;

    public ConfigScreen() {
        super(Component.translatable("mcheli.config.title"));
        this.developmentTools = Minecraft.getInstance().hasSingleplayerServer();
        this.draft = new ConfigDraft(McheliConfig.get(), this.developmentTools);
        this.keys = new KeyBindingPage(this.draft, this.theme);
        this.handling = new HandlingPage(this.draft);
    }

    /** Opens the options, closing an open container first so the server does not keep it open. */
    public static void open(Minecraft mc) {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.closeContainer();
        }
        mc.setScreen(new ConfigScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    protected void build() {
        this.left = (this.width - PANEL_WIDTH) / 2;
        this.top = (this.height - PANEL_HEIGHT) / 2;
        this.reloadAircraft = null;
        switch (this.page) {
            case CONTROLS -> this.buildControls();
            case RENDER -> this.buildRender();
            case KEYS -> this.buildKeys();
            case HANDLING -> this.handling.build(this.leftColumn(), this.rightColumn(), COLUMN_WIDTH, BUTTON_HEIGHT,
                    this.backButton());
            case DEVELOPMENT -> this.buildDevelopment();
        }
        Layout actions = this.row(this.left + RIGHT_COLUMN, this.top + FIRST_ROW + 6 * ROW).gap(10);
        actions.add(Widgets.button(Component.translatable("mcheli.config.save"), 80, BUTTON_HEIGHT, this::saveAndClose));
        actions.add(Widgets.button(Component.translatable("gui.cancel"), 60, BUTTON_HEIGHT, this::onClose));
    }

    private Layout leftColumn() {
        return this.column(this.left + MARGIN, this.top + FIRST_ROW).gap(ROW - BUTTON_HEIGHT);
    }

    private Layout rightColumn() {
        return this.column(this.left + RIGHT_COLUMN, this.top + FIRST_ROW).gap(ROW - BUTTON_HEIGHT);
    }

    private void buildControls() {
        Layout left = this.leftColumn();
        left.add(this.toggle("invert_mouse", ControlSettings.INVERT_MOUSE));
        left.add(this.slider(COLUMN_WIDTH, ConfigDraft.SENSITIVITY, this.draft.sensitivity(), "sensitivity", "%.1f",
                this.draft::setSensitivity));
        left.add(this.toggle("flight_sim_mode", ControlSettings.FLIGHT_SIM_MODE));
        left.add(this.toggle("switch_weapon_wheel", ControlSettings.SWITCH_WEAPON_WITH_MOUSE_WHEEL));
        this.halves(left, this.pageButton("render.short", Page.RENDER, HALF_WIDTH),
                this.pageButton("keys.short", Page.KEYS, HALF_WIDTH));
        this.halves(left, this.pageButton("handling", Page.HANDLING, HALF_WIDTH),
                Widgets.button(Component.translatable("mcheli.config.page.joystick"), HALF_WIDTH, BUTTON_HEIGHT,
                        this::openJoystick));
        left.add(this.toggle("test_mode", DebugSettings.TEST_MODE));

        Layout right = this.rightColumn();
        right.add(this.toggle("stick_mode_heli", ControlSettings.STICK_MODE_HELI));
        right.add(this.toggle("stick_mode_plane", ControlSettings.STICK_MODE_PLANE));
        right.add(this.toggle("throttle_down_heli", ControlSettings.AUTO_THROTTLE_DOWN_HELI));
        right.add(this.toggle("throttle_down_plane", ControlSettings.AUTO_THROTTLE_DOWN_PLANE));
        right.add(this.toggle("throttle_down_tank", ControlSettings.AUTO_THROTTLE_DOWN_TANK));
        right.add(this.pageButton("development", Page.DEVELOPMENT, COLUMN_WIDTH));
    }

    /** Two half-width buttons side by side in one row of a column. */
    private void halves(Layout column, Button first, Button second) {
        int y = column.nextY();
        column.add(first);
        this.add(second).setPosition(first.getX() + HALF_WIDTH + HALF_GAP, y);
    }

    private void openJoystick() {
        this.keys.cancelWait();
        this.minecraft.setScreen(new JoystickScreen(this, McheliClient.joystickSource()));
    }

    private void buildRender() {
        Layout left = this.leftColumn();
        left.add(this.toggle("hud_third_person", HudSettings.DISPLAY_IN_THIRD_PERSON));
        left.add(this.toggle("hide_key_binding", RenderSettings.HIDE_KEYBIND));
        left.space(3 * ROW);
        left.add(this.toggle("camera_position", RenderSettings.REPLACE_RENDER_VIEW_ENTITY));
        left.add(this.backButton());

        int half = COLUMN_WIDTH / 2;
        Layout colors = this.column(this.left + MARGIN + half, this.top + FIRST_ROW + 2 * ROW).gap(ROW - BUTTON_HEIGHT);
        colors.add(this.hitMarkSlider(HitMarkChannel.RED, "red"));
        colors.add(this.hitMarkSlider(HitMarkChannel.GREEN, "green"));
        colors.add(this.hitMarkSlider(HitMarkChannel.BLUE, "blue"));
        this.add(this.hitMarkSlider(HitMarkChannel.ALPHA, "alpha"))
                .setPosition(this.left + MARGIN, this.top + FIRST_ROW + 4 * ROW);

        Layout right = this.rightColumn();
        right.add(this.toggle("smooth_shading", RenderSettings.SMOOTH_SHADING));
        right.add(this.toggle("entity_marker", RenderSettings.DISPLAY_ENTITY_MARKER));
        this.entityMarkerSize = this.add(this.slider(120, ConfigDraft.ENTITY_MARKER_SIZE, this.draft.entityMarkerSize(),
                "entity_marker_size", "%.0f", this.draft::setEntityMarkerSize));
        this.entityMarkerSize.setPosition(this.left + RIGHT_COLUMN + 30, this.top + FIRST_ROW + 2 * ROW);
        this.blockMarkerSize = this.add(this.slider(90, ConfigDraft.BLOCK_MARKER_SIZE, this.draft.blockMarkerSize(),
                "block_marker_size", "%.0f", this.draft::setBlockMarkerSize));
        this.blockMarkerSize.setPosition(this.left + RIGHT_COLUMN + 60, this.top + FIRST_ROW + 3 * ROW);
        right.space(3 * ROW);
        right.add(this.toggle("default_explosion", RenderSettings.DEFAULT_EXPLOSION_PARTICLE));
    }

    private void buildKeys() {
        this.keys.build(this::add, this.left + MARGIN, this.top + FIRST_ROW - 2);
        Layout back = this.row(this.left + MARGIN, this.top + FIRST_ROW + 6 * ROW);
        back.add(this.backButton());
        back.add(Widgets.button(Component.translatable("mcheli.config.key.reset_all"), 60, BUTTON_HEIGHT,
                this.keys::resetAll));
    }

    private void buildDevelopment() {
        Layout left = this.leftColumn();
        left.space(ROW);
        if (this.developmentTools) {
            this.reloadAircraft = left.add(this.devButton("reload_aircraft", DevelopmentReloads::reloadRiddenAircraft));
            left.add(this.devButton("reload_weapons", DevelopmentReloads::reloadAllWeapons));
            left.add(this.devButton("reload_huds", DevelopmentReloads::reloadHuds));
            left.add(this.slider(COLUMN_WIDTH, ConfigDraft.TEXTURE_ALPHA, this.draft.textureAlpha(), "texture_alpha",
                    "%.0f", this.draft::setTextureAlpha));
        } else {
            left.space(4 * ROW);
        }
        left.space(ROW);
        left.add(this.backButton());
    }

    private Button toggle(String name, BoolOption option) {
        return Widgets.toggle(Component.translatable("mcheli.config." + name), COLUMN_WIDTH, BUTTON_HEIGHT,
                () -> this.draft.get(option), value -> this.draft.set(option, value));
    }

    private Slider<Float> slider(int width, SliderModel<Float> model, float initial, String name, String format,
                                 Consumer<Float> onChange) {
        return Widgets.slider(width, BUTTON_HEIGHT, model, initial,
                value -> Component.translatable("mcheli.config." + name, String.format(format, value)), onChange);
    }

    private Slider<Float> hitMarkSlider(HitMarkChannel channel, String name) {
        return this.slider(COLUMN_WIDTH / 2, ConfigDraft.COLOR_CHANNEL, this.draft.hitMark(channel), "hit_mark." + name,
                "%.0f", value -> this.draft.setHitMark(channel, value));
    }

    private Button pageButton(String name, Page target, int width) {
        return Widgets.button(Component.translatable("mcheli.config.page." + name), width, BUTTON_HEIGHT,
                () -> this.show(target));
    }

    private Button backButton() {
        return this.pageButton("back", Page.CONTROLS, BACK_WIDTH);
    }

    private Button devButton(String name, Consumer<Minecraft> reload) {
        return Widgets.button(Component.translatable("mcheli.config." + name), COLUMN_WIDTH, BUTTON_HEIGHT, () -> {
            try {
                reload.accept(this.minecraft);
            } catch (RuntimeException e) {
                ModLogger.error("Config screen action failed", e);
            }
            this.onClose();
        });
    }

    private void show(Page target) {
        this.keys.cancelWait();
        this.page = target;
        this.rebuild();
    }

    private void saveAndClose() {
        // The server keeps its own copy of the pilot's throttle settings.
        ControlSettings old = McheliConfig.controls();
        McheliSettings config = McheliConfig.update(this.draft::applyTo);
        if (old.autoThrottleDownHeli() != config.controls().autoThrottleDownHeli()
                || old.autoThrottleDownPlane() != config.controls().autoThrottleDownPlane()) {
            this.sendPilotSettings();
        }
        McheliClient.rebindKeys(config.keys());
        McheliConfig.save();
        this.onClose();
    }

    private void sendPilotSettings() {
        AircraftEntity aircraft = RiddenVehicle.of(this.minecraft.player);
        if (aircraft != null && aircraft.seats().seatIdOf(this.minecraft.player) == 0) {
            aircraft.pilotPreferences().send(0);
        }
    }

    @Override
    public void tick() {
        this.keys.tick();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.keys.isWaiting()) {
            this.keys.acceptKey(keyCode);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.keys.isWaiting()) {
            this.keys.acceptMouseButton(button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.page == Page.KEYS && !this.keys.isWaiting() && scrollY != 0) {
            this.keys.scroll(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(BACKGROUND, this.left, this.top, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        graphics.drawString(this.font, this.title, this.left + MARGIN, this.top + MARGIN, this.theme.text());
        graphics.drawString(this.font, Component.translatable("mcheli.config.page_title",
                        Component.translatable("mcheli.config.page_title." + this.page.name().toLowerCase())),
                this.left + RIGHT_COLUMN, this.top + MARGIN, this.theme.text());
        if (this.page == Page.KEYS) {
            KeyBindingPage.renderBackground(graphics, this.left + MARGIN, this.top + FIRST_ROW - 2);
        }
    }

    /** The previews and notes go over the widgets, as the marker preview overlaps its toggle. */
    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        switch (this.page) {
            case RENDER -> this.renderPreviews(graphics);
            case KEYS -> this.keys.renderPrompt(graphics, this.font, this.left, this.top, PANEL_WIDTH, PANEL_HEIGHT);
            case DEVELOPMENT -> this.renderDevelopmentNotes(graphics);
            case CONTROLS, HANDLING -> {
            }
        }
    }

    /** Live previews of the hit mark colour and the marker sizes. */
    private void renderPreviews(GuiGraphics graphics) {
        ScreenCanvas canvas = new ScreenCanvas(graphics);
        graphics.drawString(this.font, Component.translatable("mcheli.config.hit_mark"), this.left + MARGIN,
                this.top + FIRST_ROW + 2 * ROW, this.theme.text());
        int x = this.left + 40;
        int y = this.top + 105;
        double[] cross = {
                x - HIT_MARK_OUTER, y - HIT_MARK_OUTER, x - HIT_MARK_INNER, y - HIT_MARK_INNER,
                x - HIT_MARK_OUTER, y + HIT_MARK_OUTER, x - HIT_MARK_INNER, y + HIT_MARK_INNER,
                x + HIT_MARK_OUTER, y - HIT_MARK_OUTER, x + HIT_MARK_INNER, y - HIT_MARK_INNER,
                x + HIT_MARK_OUTER, y + HIT_MARK_OUTER, x + HIT_MARK_INNER, y + HIT_MARK_INNER};
        canvas.lines(cross, cross.length, LineMode.LINES, this.draft.hitMarkArgb());

        double size = this.entityMarkerSize.value();
        double markerX = this.left + RIGHT_COLUMN + (30.0 - size) / 2.0;
        double markerY = this.entityMarkerSize.getY() - this.entityMarkerSize.getHeight();
        double[] triangle = {markerX + size, markerY, markerX, markerY, markerX + size / 2.0, markerY + size};
        canvas.polygon(triangle, triangle.length, MARKER_PREVIEW_COLOR, VertexFormat.Mode.TRIANGLES);
        TargetMarkerOverlay.drawRhombus(canvas, ALL_TIPS, this.left + 185.0, this.blockMarkerSize.getY(),
                this.blockMarkerSize.value(), MARKER_PREVIEW_COLOR);
        canvas.flush();
    }

    private void renderDevelopmentNotes(GuiGraphics graphics) {
        int noteY = this.top + 30;
        graphics.drawString(this.font, Component.translatable("mcheli.config.single_player_only"), this.left + MARGIN,
                noteY, WARNING_COLOR);
        if (this.reloadAircraft == null || !this.reloadAircraft.isHovered()) {
            return;
        }
        int x = this.left + RIGHT_COLUMN;
        graphics.drawString(this.font, Component.translatable("mcheli.config.not_reloaded"), x, noteY, this.theme.text());
        int y = noteY + 10;
        for (String item : VehicleSchema.FIXED_ON_RELOAD) {
            graphics.drawString(this.font, "  " + item, x, y, this.theme.text());
            y += 10;
        }
    }
}
