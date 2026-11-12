package com.ivomens.mcheli.livery.client;

import com.ivomens.mcheli.livery.Livery;
import com.ivomens.mcheli.livery.LiveryImages;
import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.livery.net.LiveryImageChunkMessage;
import com.ivomens.mcheli.livery.net.LiveryImageRequestMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.util.ModLogger;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Livery textures on the client, registered as {@code mcheli:livery/<id>}.
 * <p>
 * A texture is looked for in the disk cache first ({@code <gameDir>/mcheli_liveries/cache}) and
 * fetched from the server when it is not there. Files are read, checked and decoded on the IO pool;
 * only the GPU upload happens on the render thread. Textures that have not been drawn for a few
 * minutes are released again.
 * <p>
 * All state is touched on the render thread only.
 */
public final class LiveryTextures {

    private static final long RELEASE_AFTER_MS = 5 * 60_000L;
    /** A livery the server did not have is asked for again after this long (it may be re-uploaded). */
    private static final long RETRY_MISSING_MS = 60_000L;
    /** A request the server never answered is forgotten after this long. */
    private static final long REQUEST_TIMEOUT_MS = 2 * 60_000L;

    private enum State {LOADING, DOWNLOADING, READY, MISSING}

    private static final class Entry {
        final String id;
        final ResourceLocation location;
        State state = State.LOADING;
        long lastUsed;
        long stateSince;
        byte[] download = new byte[0];
        int received;

        Entry(String id, long now) {
            this.id = id;
            this.location = Ids.of("livery/" + id);
            this.lastUsed = now;
            this.stateSince = now;
        }

        void setState(State state) {
            this.state = state;
            this.stateSince = Util.getMillis();
        }
    }

    private static final Map<String, Entry> ENTRIES = new HashMap<>();

    private LiveryTextures() {
    }

    /**
     * The texture of a livery if it is ready to draw, otherwise null (and loading is started).
     * Called per aircraft and frame, so it does nothing but a map lookup once a texture is known.
     */
    @Nullable
    public static ResourceLocation textureFor(String liveryId) {
        if (liveryId.isEmpty()) {
            return null;
        }
        long now = Util.getMillis();
        Entry entry = ENTRIES.get(liveryId);
        if (entry == null) {
            if (!Livery.isValidId(liveryId)) {
                return null;
            }
            entry = new Entry(liveryId, now);
            ENTRIES.put(liveryId, entry);
            loadFromCache(entry);
        }
        entry.lastUsed = now;
        if (entry.state == State.READY) {
            return entry.location;
        }
        if (entry.state == State.MISSING && now - entry.stateSince > RETRY_MISSING_MS) {
            loadFromCache(entry);
        }
        return null;
    }

    /** Whether the texture of a livery is loaded, without starting a load. */
    public static boolean isReady(String liveryId) {
        Entry entry = ENTRIES.get(liveryId);
        return entry != null && entry.state == State.READY;
    }

    static Path cacheDirectory() {
        return LiveryFiles.root().resolve("cache");
    }

    private static void loadFromCache(Entry entry) {
        entry.setState(State.LOADING);
        String id = entry.id;
        CompletableFuture.supplyAsync(() -> readVerifiedCache(id), Util.ioPool())
                .exceptionally(error -> logFailure(id, error))
                .thenAcceptAsync(image -> {
                    if (image != null) {
                        install(id, image);
                    } else {
                        requestFromServer(id);
                    }
                }, Minecraft.getInstance());
    }

    /** The cached image if the file is there and still hashes to its id; a bad file is deleted. */
    @Nullable
    private static NativeImage readVerifiedCache(String id) {
        Path file = cacheDirectory().resolve(id + ".png");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            byte[] png = Files.readAllBytes(file);
            if (LiveryImages.sha256(png).equals(id)) {
                return decode(png);
            }
            Files.deleteIfExists(file);
        } catch (IOException e) {
            ModLogger.warn("Could not read cached livery {}: {}", id, e.getMessage());
        }
        return null;
    }

    private static void requestFromServer(String id) {
        Entry entry = ENTRIES.get(id);
        if (entry == null || entry.state != State.LOADING) {
            return;
        }
        if (Minecraft.getInstance().getConnection() == null) {
            entry.setState(State.MISSING);
            return;
        }
        entry.setState(State.DOWNLOADING);
        entry.received = 0;
        entry.download = new byte[0];
        Network.sendToServer(new LiveryImageRequestMessage(id));
    }

    /** A piece of an image we asked for. Anything unexpected ends the download. */
    static void onChunk(LiveryImageChunkMessage chunk) {
        Entry entry = ENTRIES.get(chunk.liveryId());
        if (entry == null || entry.state != State.DOWNLOADING) {
            return;
        }
        if (chunk.total() <= 0 || chunk.total() > LiveryManager.MAX_IMAGE_BYTES) {
            entry.setState(State.MISSING);
            return;
        }
        if (chunk.offset() == 0) {
            entry.download = new byte[chunk.total()];
            entry.received = 0;
        }
        if (chunk.offset() != entry.received || entry.download.length != chunk.total()
                || chunk.data().length > chunk.total() - entry.received) {
            entry.setState(State.MISSING);
            return;
        }
        System.arraycopy(chunk.data(), 0, entry.download, chunk.offset(), chunk.data().length);
        entry.received += chunk.data().length;
        if (entry.received == chunk.total()) {
            byte[] png = entry.download;
            entry.download = new byte[0];
            entry.setState(State.LOADING);
            String id = entry.id;
            CompletableFuture.supplyAsync(() -> storeAndDecode(id, png), Util.ioPool())
                    .exceptionally(error -> logFailure(id, error))
                    .thenAcceptAsync(image -> {
                        if (image != null) {
                            install(id, image);
                        } else {
                            markMissing(id);
                        }
                    }, Minecraft.getInstance());
        }
    }

    @Nullable
    private static NativeImage storeAndDecode(String id, byte[] png) {
        if (!LiveryImages.sha256(png).equals(id)) {
            ModLogger.warn("Livery {} from the server does not match its id", id);
            return null;
        }
        try {
            Path directory = cacheDirectory();
            Files.createDirectories(directory);
            Path tmp = directory.resolve(id + ".png.tmp");
            Files.write(tmp, png);
            Files.move(tmp, directory.resolve(id + ".png"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            ModLogger.warn("Could not cache livery {}: {}", id, e.getMessage());
        }
        try {
            return decode(png);
        } catch (IOException e) {
            ModLogger.warn("Could not decode livery {}: {}", id, e.getMessage());
            return null;
        }
    }

    /** Decodes through a heap buffer; {@code NativeImage.read(byte[])} copies onto the small thread stack. */
    private static NativeImage decode(byte[] png) throws IOException {
        return NativeImage.read(new ByteArrayInputStream(png));
    }

    private static <T> T logFailure(String id, Throwable error) {
        ModLogger.warn("Could not load livery {}: {}", id, error.toString());
        return null;
    }

    private static void install(String id, NativeImage image) {
        Entry entry = ENTRIES.get(id);
        if (entry == null || entry.state == State.READY) {
            image.close();
            return;
        }
        Minecraft.getInstance().getTextureManager().register(entry.location, new DynamicTexture(image));
        entry.setState(State.READY);
    }

    private static void markMissing(String id) {
        Entry entry = ENTRIES.get(id);
        if (entry != null && entry.state != State.READY) {
            entry.setState(State.MISSING);
        }
    }

    /** Releases textures nobody drew for a while and forgets requests that were never answered. */
    static void sweep() {
        long now = Util.getMillis();
        Iterator<Entry> it = ENTRIES.values().iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            boolean unused = now - entry.lastUsed > RELEASE_AFTER_MS;
            boolean stalled = entry.state == State.DOWNLOADING && now - entry.stateSince > REQUEST_TIMEOUT_MS;
            if (unused || stalled) {
                release(entry);
                it.remove();
            }
        }
    }

    /** Drops everything, e.g. when leaving a server. */
    static void clear() {
        ENTRIES.values().forEach(LiveryTextures::release);
        ENTRIES.clear();
    }

    private static void release(Entry entry) {
        if (entry.state == State.READY) {
            Minecraft.getInstance().getTextureManager().release(entry.location);
        }
    }
}
