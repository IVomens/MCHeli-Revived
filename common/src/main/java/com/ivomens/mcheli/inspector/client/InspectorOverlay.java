package com.ivomens.mcheli.inspector.client;

import com.ivomens.mcheli.inspector.InspectorSnapshot;
import com.ivomens.mcheli.inspector.InspectorTab;
import com.ivomens.mcheli.inspector.SnapshotDiff;
import com.ivomens.mcheli.inspector.VehicleSnapshots;
import com.ivomens.mcheli.inspector.net.InspectorReplyMessage;
import com.ivomens.mcheli.ui.client.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * The inspector's compact mode: a small box in the top right corner with the few numbers worth watching while
 * flying, in two label and value columns, and the state of the server comparison.
 */
final class InspectorOverlay {

    private static final int MARGIN = 4;
    private static final int PADDING = 5;
    private static final int GAP = 6;
    private static final int COLUMN_GAP = 12;
    private static final int BACKGROUND = 0xB0000000;
    private static final int TICKS_PER_SECOND = 20;

    private record Item(String label, String value) {
    }

    private InspectorOverlay() {
    }

    static void draw(GuiGraphics graphics, InspectorClient inspector) {
        InspectorSnapshot client = inspector.clientSnapshot();
        if (client == null) {
            return;
        }
        Theme theme = Theme.DEFAULT;
        Font font = Minecraft.getInstance().font;
        List<Item> items = List.of(
                new Item(label("hp"), value(client, VehicleSnapshots.STATE, "health")),
                new Item(label("fuel"), value(client, VehicleSnapshots.STATE, "fuel")),
                new Item(label("throttle"), value(client, VehicleSnapshots.STATE, "throttle")),
                new Item(label("speed"), value(client, VehicleSnapshots.MOTION, "speed (km/h)") + " km/h"),
                new Item(label("altitude"), altitude(client)),
                new Item(label("rest"), value(client, VehicleSnapshots.STATE, "rest")));
        int labelWidth = 0;
        int valueWidth = 0;
        for (Item item : items) {
            labelWidth = Math.max(labelWidth, font.width(item.label()));
            valueWidth = Math.max(valueWidth, font.width(item.value()));
        }
        int column = labelWidth + GAP + valueWidth;
        String server = server(inspector, client);
        int width = Math.max(column * 2 + COLUMN_GAP, Math.max(font.width(client.typeId()), font.width(server)))
                + PADDING * 2;
        int lineHeight = font.lineHeight + 2;
        int rows = (items.size() + 1) / 2;
        int height = PADDING * 2 + lineHeight * (rows + 2) + 2;
        int left = graphics.guiWidth() - MARGIN - width;
        int top = MARGIN;
        graphics.fill(left, top, left + width, top + height, BACKGROUND);
        graphics.fill(left, top, left + 2, top + height, theme.accent());
        int x = left + PADDING;
        int y = top + PADDING;
        graphics.drawString(font, client.typeId(), x, y, theme.title(), false);
        y += lineHeight + 2;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int cellX = x + (i % 2) * (column + COLUMN_GAP);
            int cellY = y + (i / 2) * lineHeight;
            graphics.drawString(font, item.label(), cellX, cellY, theme.muted(), false);
            graphics.drawString(font, item.value(), cellX + labelWidth + GAP, cellY, theme.text(), false);
        }
        y += rows * lineHeight;
        graphics.drawString(font, server, x, y, serverColour(inspector, client, theme), false);
    }

    private static String server(InspectorClient inspector, InspectorSnapshot client) {
        InspectorReplyMessage reply = inspector.server();
        if (reply == null) {
            return Component.translatable("mcheli.inspector.overlay.server_none").getString();
        }
        if (reply.status() != InspectorReplyMessage.Status.OK) {
            return Component.translatable("mcheli.inspector.overlay.server_" + reply.status().name()
                    .toLowerCase(Locale.ROOT)).getString();
        }
        return Component.translatable("mcheli.inspector.overlay.server", SnapshotDiff.mismatches(client,
                reply.snapshot()), String.format(Locale.ROOT, "%.1f", inspector.serverAge() / (double) TICKS_PER_SECOND))
                .getString();
    }

    private static int serverColour(InspectorClient inspector, InspectorSnapshot client, Theme theme) {
        InspectorReplyMessage reply = inspector.server();
        if (reply == null || reply.status() != InspectorReplyMessage.Status.OK) {
            return theme.faint();
        }
        return SnapshotDiff.mismatches(client, reply.snapshot()) > 0 ? theme.bad() : theme.good();
    }

    private static String label(String key) {
        return Component.translatable("mcheli.inspector.overlay." + key).getString();
    }

    private static String altitude(InspectorSnapshot client) {
        String position = value(client, VehicleSnapshots.MOTION, "position");
        String[] parts = position.split(", ");
        try {
            return parts.length == 3 ? String.format(Locale.ROOT, "%.1f", Double.parseDouble(parts[1])) : "-";
        } catch (NumberFormatException e) {
            return parts[1];
        }
    }

    private static String value(InspectorSnapshot snapshot, String section, String key) {
        return snapshot.value(InspectorTab.OVERVIEW, section, key).orElse("-");
    }
}
