package com.ivomens.mcheli.hud.client.widget;

import com.ivomens.mcheli.api.client.HudWidget;
import com.ivomens.mcheli.api.client.HudWidgets;
import com.ivomens.mcheli.api.event.Subscription;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * The HUD widgets addons registered through the API. One instance lives as long as the game, owned by the client
 * API's entry point ({@code ClientApiService}); the HUD reads it through {@link HudWidgetLayer}.
 * <p>
 * The widgets are kept as an array sorted by order and id, replaced on every change, so the render thread reads
 * it without locking or copying, and finds it empty at the cost of one field read.
 */
public final class HudWidgetRegistry implements HudWidgets {

    private static final Entry[] NONE = new Entry[0];
    private static final Comparator<Entry> PLACEMENT = Comparator.comparingInt(Entry::order)
            .thenComparing(Entry::id);

    private volatile Entry[] entries = NONE;

    @Override
    public synchronized Subscription register(ResourceLocation id, HudWidget widget) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(widget, "widget");
        for (Entry entry : this.entries) {
            if (entry.id().equals(id)) {
                throw new IllegalArgumentException("A HUD widget " + id + " is registered already");
            }
        }
        Entry added = new Entry(id, widget, widget.order());
        Entry[] next = Arrays.copyOf(this.entries, this.entries.length + 1);
        next[next.length - 1] = added;
        Arrays.sort(next, PLACEMENT);
        this.entries = next;
        return () -> remove(added);
    }

    private synchronized void remove(Entry entry) {
        Entry[] current = this.entries;
        int index = Arrays.asList(current).indexOf(entry);
        if (index < 0) {
            return;
        }
        Entry[] next = new Entry[current.length - 1];
        System.arraycopy(current, 0, next, 0, index);
        System.arraycopy(current, index + 1, next, index, next.length - index);
        this.entries = next;
    }

    /** Whether no widget is registered, so the HUD has nothing to lay out. */
    public boolean isEmpty() {
        return this.entries.length == 0;
    }

    /** The registered widgets in placement order. Shared: do not change it. */
    Entry[] entries() {
        return this.entries;
    }

    /** A registered widget. Identity counts: registering the same widget again after removing it is a new entry. */
    static final class Entry {

        private final ResourceLocation id;
        private final HudWidget widget;
        private final int order;
        /** Set on the render thread when the widget threw; it is not drawn again. */
        private boolean broken;

        Entry(ResourceLocation id, HudWidget widget, int order) {
            this.id = id;
            this.widget = widget;
            this.order = order;
        }

        ResourceLocation id() {
            return this.id;
        }

        HudWidget widget() {
            return this.widget;
        }

        int order() {
            return this.order;
        }

        boolean isBroken() {
            return this.broken;
        }

        void markBroken() {
            this.broken = true;
        }
    }
}
