package com.ivomens.mcheli.ui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The tabs of a screen: one button per key, placed by the screen like any other button, and the theme's
 * accent drawn under the selected one. Pressing another tab selects it and tells the owner, which
 * usually rebuilds the screen for it. The bar itself is only the underline, added to the screen as a
 * renderable.
 *
 * @param <K> the tab key, typically an enum
 */
public final class TabBar<K> implements Renderable {

    private static final int UNDERLINE = 2;

    private final Theme theme;
    private final Consumer<? super K> onSelect;
    private final Map<K, Button> buttons = new LinkedHashMap<>();
    private K selected;

    TabBar(Theme theme, K selected, Consumer<? super K> onSelect) {
        this.theme = theme;
        this.selected = selected;
        this.onSelect = onSelect;
    }

    /** The button of a new tab, for the screen to place and add. */
    public Button tab(K key, Component label, int width, int height) {
        Button button = Button.builder(label, pressed -> this.choose(key)).size(width, height).build();
        this.buttons.put(key, button);
        return button;
    }

    public K selected() {
        return this.selected;
    }

    private void choose(K key) {
        if (!key.equals(this.selected)) {
            this.selected = key;
            this.onSelect.accept(key);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Button button = this.buttons.get(this.selected);
        if (button != null && button.visible) {
            graphics.fill(button.getX(), button.getBottom(), button.getRight(), button.getBottom() + UNDERLINE,
                    this.theme.accent());
        }
    }
}
