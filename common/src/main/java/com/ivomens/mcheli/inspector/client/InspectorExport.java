package com.ivomens.mcheli.inspector.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ivomens.mcheli.inspector.InspectorEntry;
import com.ivomens.mcheli.inspector.InspectorSection;
import com.ivomens.mcheli.inspector.InspectorSnapshot;
import com.ivomens.mcheli.inspector.InspectorTab;
import com.ivomens.mcheli.inspector.SnapshotDiff;
import com.ivomens.mcheli.inspector.VehicleSnapshots;
import com.ivomens.mcheli.inspector.net.InspectorReplyMessage;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Writes the inspector's snapshots (the client's, the definition and, for operators, the server's) as JSON to
 * {@code <game dir>/mcheli/inspector/<type>-<time>.json}, and makes the short text summary a bug report
 * starts with. The format is described in docs/INSPECTOR.md.
 */
final class InspectorExport {

    /** Bumped when the JSON layout changes. */
    static final int FORMAT = 1;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private InspectorExport() {
    }

    /** Writes the export file and says where it went, or why it could not be written. */
    static Component export(Minecraft mc, InspectorClient inspector) {
        InspectorSnapshot client = inspector.clientSnapshot();
        if (client == null) {
            return Component.translatable("mcheli.inspector.export.nothing");
        }
        String name = client.typeId().replace('/', '-') + "-" + LocalDateTime.now().format(FILE_TIME) + ".json";
        Path file = mc.gameDirectory.toPath().resolve("mcheli").resolve("inspector").resolve(name);
        try {
            Files.createDirectories(file.getParent());
            String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                    .toJson(json(mc, inspector, client));
            Files.writeString(file, json, StandardCharsets.UTF_8);
            ModLogger.get().info("Vehicle inspector export written to {}", file);
            return Component.translatable("mcheli.inspector.export.done", name);
        } catch (IOException e) {
            ModLogger.get().warn("Could not write the vehicle inspector export {}", file, e);
            return Component.translatable("mcheli.inspector.export.failed", e.getMessage());
        }
    }

    static JsonObject json(Minecraft mc, InspectorClient inspector, InspectorSnapshot client) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("exported", LocalDateTime.now().toString());
        root.addProperty("mod", Services.PLATFORM.getModVersion());
        root.addProperty("loader", Services.PLATFORM.getLoaderName());
        root.addProperty("minecraft", SharedConstants.getCurrentVersion().getName());
        root.addProperty("type", client.typeId());
        root.addProperty("entityId", client.entityId());
        root.addProperty("singlePlayer", mc.hasSingleplayerServer());
        root.add("client", snapshot(client));
        InspectorSnapshot definition = inspector.definition();
        root.add("definition", definition == null ? null : snapshot(definition));
        InspectorReplyMessage reply = inspector.server();
        JsonObject server = new JsonObject();
        server.addProperty("status", reply == null ? "NOT_ASKED" : reply.status().name());
        if (reply != null && reply.status() == InspectorReplyMessage.Status.OK) {
            server.addProperty("ageTicks", inspector.serverAge());
            server.add("snapshot", snapshot(reply.snapshot()));
            root.add("mismatches", mismatches(client, definition, reply.snapshot()));
        }
        root.add("server", server);
        return root;
    }

    private static JsonObject snapshot(InspectorSnapshot snapshot) {
        JsonObject out = new JsonObject();
        out.addProperty("gameTime", snapshot.gameTime());
        out.addProperty("truncated", snapshot.truncated());
        JsonArray sections = new JsonArray();
        for (InspectorSection section : snapshot.sections()) {
            JsonObject json = new JsonObject();
            json.addProperty("tab", section.tab().name().toLowerCase(Locale.ROOT));
            json.addProperty("title", section.title());
            json.addProperty("detail", section.detail());
            JsonObject entries = new JsonObject();
            for (InspectorEntry entry : section.entries()) {
                entries.addProperty(entry.key(), entry.value());
            }
            json.add("values", entries);
            JsonObject details = new JsonObject();
            section.entries().stream().filter(entry -> !entry.detail().isEmpty() || entry.marked())
                    .forEach(entry -> details.addProperty(entry.key(), (entry.marked() ? "changed; " : "")
                            + entry.detail()));
            if (!details.isEmpty()) {
                json.add("details", details);
            }
            sections.add(json);
        }
        out.add("sections", sections);
        return out;
    }

    private static JsonArray mismatches(InspectorSnapshot client, @Nullable InspectorSnapshot definition,
                                        InspectorSnapshot server) {
        JsonArray out = new JsonArray();
        for (InspectorSnapshot side : definition == null ? List.of(client) : List.of(client, definition)) {
            for (InspectorSection section : side.sections()) {
                for (InspectorEntry entry : section.entries()) {
                    String other = SnapshotDiff.serverValue(server, section, entry).orElse(null);
                    if (SnapshotDiff.compare(entry.key(), entry.value(), other) == SnapshotDiff.Verdict.MISMATCH) {
                        JsonObject json = new JsonObject();
                        json.addProperty("where", section.tab().title() + " / " + section.title() + " / " + entry.key());
                        json.addProperty("client", entry.value());
                        json.addProperty("server", other);
                        out.add(json);
                    }
                }
            }
        }
        return out;
    }

    /** A few lines for a bug report: versions, the vehicle, its key numbers and the server comparison. */
    static String summary(Minecraft mc, InspectorClient inspector) {
        InspectorSnapshot client = inspector.clientSnapshot();
        if (client == null) {
            return "MCHeli vehicle inspector: no vehicle";
        }
        StringBuilder text = new StringBuilder();
        text.append("MCHeli ").append(Services.PLATFORM.getModVersion()).append(" (")
                .append(Services.PLATFORM.getLoaderName()).append(", Minecraft ")
                .append(SharedConstants.getCurrentVersion().getName()).append(")\n");
        text.append("Vehicle: ").append(client.typeId()).append(" #").append(client.entityId())
                .append(", pack ").append(value(client, VehicleSnapshots.IDENTITY, "pack"))
                .append(", file ").append(value(client, VehicleSnapshots.IDENTITY, "file")).append('\n');
        text.append("Health ").append(value(client, VehicleSnapshots.STATE, "health"))
                .append(", fuel ").append(value(client, VehicleSnapshots.STATE, "fuel"))
                .append(", throttle ").append(value(client, VehicleSnapshots.STATE, "throttle"))
                .append(", rest ").append(value(client, VehicleSnapshots.STATE, "rest")).append('\n');
        text.append("Position ").append(value(client, VehicleSnapshots.MOTION, "position"))
                .append(", speed ").append(value(client, VehicleSnapshots.MOTION, "speed (km/h)")).append(" km/h\n");
        InspectorReplyMessage reply = inspector.server();
        if (reply == null) {
            text.append("Server view: not available");
        } else if (reply.status() != InspectorReplyMessage.Status.OK) {
            text.append("Server view: ").append(reply.status().name().toLowerCase(Locale.ROOT));
        } else {
            int count = SnapshotDiff.mismatches(client, reply.snapshot());
            text.append("Server view: ").append(count).append(count == 1 ? " mismatch" : " mismatches");
        }
        return text.toString();
    }

    private static String value(InspectorSnapshot snapshot, String section, String key) {
        return snapshot.value(InspectorTab.OVERVIEW, section, key).orElse("-");
    }
}
