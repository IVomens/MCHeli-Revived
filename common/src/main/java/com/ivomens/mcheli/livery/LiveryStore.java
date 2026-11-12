package com.ivomens.mcheli.livery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.ivomens.mcheli.config.LiveryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Liveries stored with a world: {@code <world>/mcheli_liveries/index.json} plus one
 * {@code <id>.png} per image. Image files are named by content hash, so two players uploading the
 * same picture share one file; the index records who uploaded what.
 * <p>
 * Used only on the server thread.
 */
public final class LiveryStore {

    private static final Logger LOG = LoggerFactory.getLogger("mcheli/liveries");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String INDEX_FILE = "index.json";

    private final Path directory;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** One index line. Kept separate from {@link Livery} so the JSON layout stays stable. */
    private static final class Entry {
        String id;
        String vehicleType;
        String name;
        String owner;
        String ownerName;
        int width;
        int height;
        int bytes;
        long created;

        Livery toLivery() {
            return new Livery(id, vehicleType, name, UUID.fromString(owner), ownerName, width, height, bytes, created);
        }

        static Entry of(Livery l) {
            Entry e = new Entry();
            e.id = l.id();
            e.vehicleType = l.vehicleType();
            e.name = l.name();
            e.owner = l.owner().toString();
            e.ownerName = l.ownerName();
            e.width = l.width();
            e.height = l.height();
            e.bytes = l.bytes();
            e.created = l.created();
            return e;
        }
    }

    public LiveryStore(Path directory) {
        this.directory = directory;
        load();
    }

    // Queries

    public Optional<Livery> get(String id) {
        Entry e = entries.get(id);
        return e == null ? Optional.empty() : Optional.of(e.toLivery());
    }

    public List<Livery> all() {
        List<Livery> list = new ArrayList<>(entries.size());
        for (Entry e : entries.values()) list.add(e.toLivery());
        list.sort(Comparator.comparingLong(Livery::created).reversed());
        return list;
    }

    public List<Livery> forVehicle(String vehicleType) {
        return all().stream().filter(l -> l.vehicleType().equalsIgnoreCase(vehicleType)).toList();
    }

    public int countOwnedBy(UUID owner) {
        String key = owner.toString();
        int n = 0;
        for (Entry e : entries.values()) if (e.owner.equals(key)) n++;
        return n;
    }

    public long totalBytes() {
        long sum = 0;
        for (Entry e : entries.values()) sum += e.bytes;
        return sum;
    }

    /** The stored PNG, or empty if the id is unknown or the file went missing. */
    public Optional<byte[]> readImage(String id) {
        if (!entries.containsKey(id)) return Optional.empty();
        try {
            return Optional.of(Files.readAllBytes(imagePath(id)));
        } catch (IOException e) {
            LOG.warn("Livery image {} is missing", id);
            return Optional.empty();
        }
    }

    // Changes

    /**
     * Stores a sanitised upload.
     *
     * @throws LiveryImages.RejectedException when a limit in {@code policy} would be exceeded
     */
    public Livery add(LiveryImages.Sanitized image, String vehicleType, String name, UUID owner, String ownerName,
                      LiveryPolicy policy) throws LiveryImages.RejectedException {
        if (entries.containsKey(image.id())) {
            return entries.get(image.id()).toLivery();
        }
        if (countOwnedBy(owner) >= policy.perPlayer()) {
            throw new LiveryImages.RejectedException("You already have " + policy.perPlayer() + " liveries. Delete one first.");
        }
        if (totalBytes() + image.png().length > policy.totalBytes()) {
            throw new LiveryImages.RejectedException("The server's livery storage is full.");
        }

        Livery livery = new Livery(image.id(), vehicleType, sanitizeName(name), owner, ownerName,
                image.width(), image.height(), image.png().length, System.currentTimeMillis());
        try {
            Files.createDirectories(directory);
            writeAtomically(imagePath(livery.id()), image.png());
        } catch (IOException e) {
            LOG.error("Could not store livery {}", livery.id(), e);
            throw new LiveryImages.RejectedException("The server could not save the image.");
        }
        entries.put(livery.id(), Entry.of(livery));
        save();
        return livery;
    }

    /** Removes a livery. Only its owner or an operator may do this. */
    public boolean remove(String id, UUID requester, boolean isOperator) {
        Entry e = entries.get(id);
        if (e == null || (!isOperator && !e.owner.equals(requester.toString()))) {
            return false;
        }
        entries.remove(id);
        try {
            Files.deleteIfExists(imagePath(id));
        } catch (IOException ex) {
            LOG.warn("Could not delete livery file {}", id, ex);
        }
        save();
        return true;
    }

    // Persistence

    private Path imagePath(String id) {
        if (!Livery.isValidId(id)) throw new IllegalArgumentException("bad livery id");
        return directory.resolve(id + ".png");
    }

    private void load() {
        Path index = directory.resolve(INDEX_FILE);
        if (!Files.isRegularFile(index)) return;
        try (Reader reader = Files.newBufferedReader(index, StandardCharsets.UTF_8)) {
            List<Entry> list = GSON.fromJson(reader, new TypeToken<List<Entry>>() { }.getType());
            if (list == null) return;
            for (Entry e : list) {
                if (e != null && Livery.isValidId(e.id) && Files.isRegularFile(imagePath(e.id))) {
                    entries.put(e.id, e);
                }
            }
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            LOG.error("Could not read {}, starting with an empty livery list", index, e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(directory);
            Path tmp = directory.resolve(INDEX_FILE + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(entries.values()), writer);
            }
            Files.move(tmp, directory.resolve(INDEX_FILE), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.error("Could not save the livery index", e);
        }
    }

    private static void writeAtomically(Path target, byte[] data) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, data);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    static String sanitizeName(String name) {
        String trimmed = name == null ? "" : name.strip();
        StringBuilder sb = new StringBuilder(Math.min(trimmed.length(), 32));
        for (int i = 0; i < trimmed.length() && sb.length() < 32; i++) {
            char c = trimmed.charAt(i);
            if (c >= 0x20 && c != 0x7F && c != '§') sb.append(c);
        }
        return sb.isEmpty() ? "Untitled" : sb.toString();
    }
}
