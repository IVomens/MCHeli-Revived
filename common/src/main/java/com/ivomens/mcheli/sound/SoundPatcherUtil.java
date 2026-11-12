package com.ivomens.mcheli.sound;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.util.ModLogger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Old addon packs ship a {@code sounds.json} whose entries reference sound files without a
 * namespace. Inside an addon resource pack those would resolve to {@code minecraft:}, so the files
 * are rewritten once (and marked) to use the {@code mcheli} namespace. Every event declared there
 * is also recorded in {@link SoundRegistry}.
 */
public final class SoundPatcherUtil {

    private static final String MARKER = "mchce_patched";

    private SoundPatcherUtil() {
    }

    public static void patchSounds() {
        try {
            List<Path> jsonSounds = discoverSoundJsons();
            if (jsonSounds.isEmpty()) return;
            List<SoundJson> soundJsonList = serializeSoundJsons(jsonSounds);
            ModLogger.log("Patching sound jsons...");
            patchSoundJsons(soundJsonList);
            saveJsonObject(soundJsonList);
        } catch (IOException e) {
            ModLogger.log("Failed to patch addon sounds.json: %s", e);
        }
    }

    private static void patchSoundJsons(List<SoundJson> soundJsons) {
        Iterator<SoundJson> iterator = soundJsons.iterator();
        while (iterator.hasNext()) {
            SoundJson soundJson = iterator.next();

            // Already patched: nothing to save, only record the events.
            if (soundJson.object.has(MARKER)) {
                registerSound(soundJson.object);
                iterator.remove();
                continue;
            }

            for (Map.Entry<String, JsonElement> entry : soundJson.object.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject soundData = entry.getValue().getAsJsonObject();
                if (!soundData.has("sounds") || !soundData.get("sounds").isJsonArray()) continue;

                JsonArray soundsArray = soundData.getAsJsonArray("sounds");
                for (int i = 0; i < soundsArray.size(); i++) {
                    JsonElement element = soundsArray.get(i);
                    if (element.isJsonPrimitive()) {
                        String currentSound = element.getAsString();
                        if (!currentSound.contains(":")) {
                            soundsArray.set(i, new JsonPrimitive(Ids.MOD_ID + ":" + currentSound));
                        }
                    } else if (element.isJsonObject() && element.getAsJsonObject().has("name")) {
                        JsonObject sound = element.getAsJsonObject();
                        String currentSound = sound.get("name").getAsString();
                        // References to other events keep their name.
                        boolean isEvent = sound.has("type") && "event".equals(sound.get("type").getAsString());
                        if (!currentSound.contains(":") && !isEvent) {
                            sound.addProperty("name", Ids.MOD_ID + ":" + currentSound);
                        }
                    }
                }
            }

            JsonObject markerEvent = new JsonObject();
            markerEvent.add("sounds", new JsonArray());
            markerEvent.addProperty("_note", "Patched by MCHeli " + Services.PLATFORM.getModVersion());
            soundJson.object.add(MARKER, markerEvent);
            registerSound(soundJson.object);
        }
    }

    public static void registerSound(JsonObject object) {
        for (String event : object.keySet()) {
            if (MARKER.equals(event)) continue;
            SoundRegistry.INSTANCE.soundSet.add(Ids.of(event));
        }
    }

    public static void saveJsonObject(List<SoundJson> soundJsons) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        for (SoundJson soundJson : soundJsons) {
            try (BufferedWriter writer = Files.newBufferedWriter(soundJson.path)) {
                gson.toJson(soundJson.object, writer);
            }
        }
    }

    private static List<SoundJson> serializeSoundJsons(List<Path> paths) throws IOException {
        List<SoundJson> soundJsons = new ArrayList<>(paths.size());
        for (Path path : paths) {
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root.isJsonObject()) soundJsons.add(new SoundJson(path, root.getAsJsonObject()));
            } catch (RuntimeException e) {
                ModLogger.log("Skipping malformed sounds.json %s: %s", path, e.getMessage());
            }
        }
        return soundJsons;
    }

    private static List<Path> discoverSoundJsons() throws IOException {
        Path addonPath = Services.PLATFORM.getGameDir().resolve("mcheli_addons");
        List<Path> jsonPaths = new ArrayList<>();
        if (!Files.exists(addonPath)) return jsonPaths;

        try (Stream<Path> stream = Files.walk(addonPath, 4)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("sounds.json"))
                    .forEach(jsonPaths::add);
        }
        return jsonPaths;
    }

    record SoundJson(Path path, JsonObject object) {
    }
}
