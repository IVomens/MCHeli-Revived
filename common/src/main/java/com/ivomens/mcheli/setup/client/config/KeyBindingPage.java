package com.ivomens.mcheli.setup.client.config;

import com.ivomens.mcheli.config.client.KeyNames;
import com.ivomens.mcheli.setup.client.config.ConfigDraft.KeyBinding;
import com.ivomens.mcheli.ui.client.ScrollBar;
import com.ivomens.mcheli.ui.client.ScrollPane;
import com.ivomens.mcheli.ui.client.Theme;
import com.ivomens.mcheli.ui.client.Widgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The key binding page of the options screen: a scrolling list with a label, the bound key and a reset
 * button per binding. Pressing a key button waits for the next key or mouse button, which becomes the
 * binding; Escape cancels. The page outlives the screen's rebuilds, so the scroll position and a
 * pending wait survive a resize.
 */
final class KeyBindingPage {

    private static final int LIST_WIDTH = 310;
    private static final int LIST_HEIGHT = 150;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 7;
    private static final int TOP_PADDING = 5;
    private static final int BAR_WIDTH = 20;
    private static final int WHEEL_ROWS = 2;
    private static final int LABEL_X = 10;
    private static final int LABEL_Y = 6;
    private static final int KEY_X = 160;
    private static final int KEY_WIDTH = 70;
    private static final int RESET_X = 240;
    private static final int RESET_WIDTH = 40;
    private static final int BACKGROUND = 0x80404040;
    private static final int PROMPT_MARGIN = 30;
    private static final int PROMPT_BACKGROUND = 0xE0303030;
    /** Escape in the config's key codes; it cancels instead of becoming a binding. */
    private static final int ESCAPE_CODE = 1;
    /** The click on a key button must not become its new binding, so clicks wait a few ticks. */
    private static final int ACCEPT_DELAY_TICKS = 5;

    private record Row(KeyBinding binding, Button key, Button reset) {
    }

    private final ConfigDraft draft;
    private final Theme theme;
    private final List<Row> rows = new ArrayList<>();
    private ScrollPane pane;
    private int scrollRows;
    private KeyBinding waiting;
    private int acceptDelay;

    KeyBindingPage(ConfigDraft draft, Theme theme) {
        this.draft = draft;
        this.theme = theme;
    }

    /** Adds the list at {@code (x, y)} through {@code add}. */
    void build(Consumer<AbstractWidget> add, int x, int y) {
        this.rows.clear();
        this.pane = Widgets.scrollPane(this.theme, LIST_WIDTH - BAR_WIDTH, TOP_PADDING + VISIBLE_ROWS * ROW_HEIGHT)
                .step(WHEEL_ROWS * ROW_HEIGHT)
                .indicator(false)
                .contentHeight(TOP_PADDING + ConfigDraft.KEYS.size() * ROW_HEIGHT);
        this.pane.setPosition(x, y);
        ScrollBar bar = Widgets.scrollBar(BAR_WIDTH, LIST_HEIGHT, position -> this.pane.setScroll(position * ROW_HEIGHT));
        bar.setPosition(x + LIST_WIDTH - BAR_WIDTH, y);
        bar.setMax(ConfigDraft.KEYS.size() - VISIBLE_ROWS);
        this.pane.onScroll(scroll -> {
            this.scrollRows = scroll / ROW_HEIGHT;
            bar.setPosition(this.scrollRows);
        });
        for (int i = 0; i < ConfigDraft.KEYS.size(); i++) {
            KeyBinding binding = ConfigDraft.KEYS.get(i);
            int rowY = TOP_PADDING + i * ROW_HEIGHT;
            Button key = this.pane.add(Widgets.button(Component.empty(), KEY_WIDTH, ROW_HEIGHT, () -> this.await(binding)),
                    KEY_X, rowY);
            Button reset = this.pane.add(Widgets.button(Component.translatable("mcheli.config.key.reset"), RESET_WIDTH,
                    ROW_HEIGHT, () -> this.bind(binding, binding.option().defaultValue())), RESET_X, rowY);
            this.rows.add(new Row(binding, key, reset));
        }
        this.pane.painter(this::paintLabels);
        this.pane.setScroll(this.scrollRows * ROW_HEIGHT);
        bar.setPosition(this.scrollRows);
        this.refresh();
        add.accept(this.pane);
        add.accept(bar);
    }

    private void paintLabels(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < ConfigDraft.KEYS.size(); i++) {
            graphics.drawString(font, label(ConfigDraft.KEYS.get(i)), left + LABEL_X,
                    top + TOP_PADDING + i * ROW_HEIGHT + LABEL_Y, this.theme.text());
        }
    }

    private static Component label(KeyBinding binding) {
        return Component.translatable("mcheli.config.key." + binding.name());
    }

    private void refresh() {
        for (Row row : this.rows) {
            int code = this.draft.key(row.binding().option());
            row.key().setMessage(Component.literal(KeyNames.getDescOrName(code)));
            row.reset().active = code != row.binding().option().defaultValue();
        }
    }

    /** Sets a binding, unless the code names no key. */
    private void bind(KeyBinding binding, int code) {
        if (code != 0 && !KeyNames.getDescOrName(code).isEmpty()) {
            this.draft.setKey(binding.option(), code);
            this.refresh();
        }
    }

    void resetAll() {
        for (KeyBinding binding : ConfigDraft.KEYS) {
            this.bind(binding, binding.option().defaultValue());
        }
    }

    private void await(KeyBinding binding) {
        this.waiting = binding;
        this.acceptDelay = ACCEPT_DELAY_TICKS;
    }

    boolean isWaiting() {
        return this.waiting != null;
    }

    void cancelWait() {
        this.waiting = null;
    }

    /** Takes a key as the awaited binding; Escape cancels. */
    void acceptKey(int glfwKey) {
        if (glfwKey != GLFW.GLFW_KEY_ESCAPE) {
            this.accept(KeyNames.fromGlfw(glfwKey));
        }
        this.waiting = null;
    }

    /** Takes a mouse button as the awaited binding, once the click that started the wait is over. */
    void acceptMouseButton(int button) {
        if (this.acceptDelay == 0) {
            this.accept(KeyNames.fromMouseButton(button));
            this.waiting = null;
        }
    }

    private void accept(int code) {
        if (code != ESCAPE_CODE && this.waiting != null) {
            this.bind(this.waiting, code);
        }
    }

    void tick() {
        if (this.acceptDelay > 0) {
            this.acceptDelay--;
        }
    }

    /** The wheel anywhere on the page scrolls the list two rows. */
    void scroll(double amount) {
        this.pane.setScroll(this.pane.scroll() - (int) Math.signum(amount) * WHEEL_ROWS * ROW_HEIGHT);
    }

    /** The list's backdrop, under its rows; {@code (x, y)} is where {@link #build} put the list. */
    static void renderBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + LIST_WIDTH, y + LIST_HEIGHT, BACKGROUND);
    }

    /** The "press a key" prompt over the panel at {@code (left, top)}, while a binding is awaited. */
    void renderPrompt(GuiGraphics graphics, Font font, int left, int top, int width, int height) {
        if (this.waiting == null) {
            return;
        }
        graphics.fill(left + PROMPT_MARGIN, top + PROMPT_MARGIN, left + width - PROMPT_MARGIN,
                top + height - PROMPT_MARGIN, PROMPT_BACKGROUND);
        Component message = Component.translatable("mcheli.config.key.prompt");
        graphics.drawString(font, message, left + (width - font.width(message)) / 2, top + height / 2 - 4,
                this.theme.text());
    }
}
