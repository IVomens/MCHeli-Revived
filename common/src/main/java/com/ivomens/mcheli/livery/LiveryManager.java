package com.ivomens.mcheli.livery;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.config.LiveryPolicy;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.livery.net.LiveryImageChunkMessage;
import com.ivomens.mcheli.networking.Network;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server side of custom liveries for the running world: the {@link LiveryStore}, uploads in
 * progress, image downloads to clients and the rules for putting a livery on an aircraft.
 * <p>
 * Each server has one from start to stop ({@link #of}); the server holds it. Everything runs on the
 * server thread. All input comes from untrusted clients, so every method checks its arguments.
 */
public final class LiveryManager {

    /** Largest piece of an image sent in one packet, in either direction. */
    public static final int MAX_CHUNK = 30 * 1024;
    /** Longest livery name or vehicle type name accepted over the network. */
    public static final int MAX_TEXT_LENGTH = 64;
    /** Largest image a client is willing to download; the config cannot allow more than this. */
    public static final int MAX_IMAGE_BYTES = 16 * 1024 * 1024;

    private static final String DIRECTORY = "mcheli_liveries";
    private static final int OPERATOR_LEVEL = 2;
    private static final double OPERATOR_REACH = 16.0;
    private static final long UPLOAD_TIMEOUT_MS = 60_000L;
    private static final int UPLOAD_SWEEP_TICKS = 20;
    /** Per player and tick, so a client joining a busy airfield fetches about 1.2 MB/s. */
    private static final int DOWNLOAD_BYTES_PER_TICK = 2 * MAX_CHUNK;
    private static final int MAX_QUEUED_DOWNLOADS = 64;

    private final LiveryStore store;
    private final Supplier<LiveryPolicy> policy;
    private final Map<UUID, Upload> uploads = new HashMap<>();
    private final Map<UUID, DownloadQueue> downloads = new HashMap<>();
    private int ticks;

    LiveryManager(Path directory, Supplier<LiveryPolicy> policy) {
        this.store = new LiveryStore(directory);
        this.policy = policy;
    }

    /** The manager of the running server, or null while no server runs (or on a remote client). */
    /** Implemented on {@link MinecraftServer} by a mixin, so every server owns its liveries. */
    public interface Holder {
        @Nullable
        LiveryManager mcheli$liveries();

        void mcheli$setLiveries(@Nullable LiveryManager liveries);
    }

    /** The server's liveries, or null outside its start and stop. */
    @Nullable
    public static LiveryManager of(MinecraftServer server) {
        return ((Holder) server).mcheli$liveries();
    }

    public static void onServerStarting(MinecraftServer server) {
        ((Holder) server).mcheli$setLiveries(new LiveryManager(server.getWorldPath(LevelResource.ROOT).resolve(DIRECTORY),
                McheliConfig::liveryPolicy));
    }

    public static void onServerStopped(MinecraftServer server) {
        ((Holder) server).mcheli$setLiveries(null);
    }

    public static void onServerTick(MinecraftServer server) {
        LiveryManager liveries = of(server);
        if (liveries != null) {
            liveries.tick(server);
        }
    }

    public static void onPlayerLogout(Player player) {
        LiveryManager liveries = player.getServer() != null ? of(player.getServer()) : null;
        if (liveries != null) {
            liveries.forget(player.getUUID());
        }
    }

    public LiveryStore store() {
        return this.store;
    }

    /** The configured limits; uploads are capped at what clients accept to download. */
    public LiveryPolicy policy() {
        LiveryPolicy configured = this.policy.get();
        if (configured.maxBytes() <= MAX_IMAGE_BYTES) {
            return configured;
        }
        return new LiveryPolicy(configured.enabled(), MAX_IMAGE_BYTES, configured.maxDimension(),
                configured.perPlayer(), configured.totalBytes(), configured.operatorsOnly());
    }

    public static boolean isOperator(ServerPlayer player) {
        return player.hasPermissions(OPERATOR_LEVEL);
    }

    public boolean canUpload(ServerPlayer player) {
        LiveryPolicy current = this.policy();
        return current.enabled() && (!current.operatorsOnly() || isOperator(player));
    }

    // Listing

    /** The liveries made for {@code vehicleType} plus every livery the player uploaded, newest first. */
    public List<Livery> listFor(ServerPlayer player, String vehicleType) {
        List<Livery> list = new ArrayList<>();
        for (Livery livery : this.store.all()) {
            if (livery.vehicleType().equalsIgnoreCase(vehicleType) || livery.owner().equals(player.getUUID())) {
                list.add(livery);
            }
        }
        return list;
    }

    // Uploads

    /** Where an upload stands after a call to {@link #beginUpload} or {@link #receiveChunk}. */
    public record UploadStatus(State state, int received, int total, String message, @Nullable Livery livery) {

        public enum State {RECEIVING, DONE, FAILED}

        static UploadStatus receiving(int received, int total) {
            return new UploadStatus(State.RECEIVING, received, total, "", null);
        }

        static UploadStatus failed(String message) {
            return new UploadStatus(State.FAILED, 0, 0, message, null);
        }

        static UploadStatus done(Livery livery) {
            return new UploadStatus(State.DONE, livery.bytes(), livery.bytes(), "Uploaded \"" + livery.name() + "\".", livery);
        }
    }

    private static final class Upload {
        final String vehicleType;
        final String name;
        final byte[] data;
        int received;
        long lastActivity;

        Upload(String vehicleType, String name, int size) {
            this.vehicleType = vehicleType;
            this.name = name;
            this.data = new byte[size];
            this.lastActivity = Util.getMillis();
        }
    }

    /**
     * Starts an upload of {@code totalBytes} bytes. A player has at most one upload at a time;
     * starting a new one drops the previous one.
     */
    public UploadStatus beginUpload(ServerPlayer player, String vehicleType, String name, int totalBytes) {
        this.uploads.remove(player.getUUID());
        LiveryPolicy current = this.policy();
        if (!current.enabled()) {
            return UploadStatus.failed("Liveries are disabled on this server.");
        }
        if (!this.canUpload(player)) {
            return UploadStatus.failed("Only operators may upload liveries on this server.");
        }
        if (!isKnownVehicleType(vehicleType)) {
            return UploadStatus.failed("Unknown vehicle type.");
        }
        if (totalBytes <= 0) {
            return UploadStatus.failed("The image is empty.");
        }
        if (totalBytes > current.maxBytes()) {
            return UploadStatus.failed("Image is larger than " + current.maxBytes() / 1024 + " KB.");
        }
        if (this.store.countOwnedBy(player.getUUID()) >= current.perPlayer()) {
            return UploadStatus.failed("You already have " + current.perPlayer() + " liveries. Delete one first.");
        }
        this.uploads.put(player.getUUID(), new Upload(vehicleType, name, totalBytes));
        return UploadStatus.receiving(0, totalBytes);
    }

    /** Appends the next piece of the player's upload; stores the image once it is complete. */
    public UploadStatus receiveChunk(ServerPlayer player, int offset, byte[] chunk) {
        Upload upload = this.uploads.get(player.getUUID());
        if (upload == null) {
            return UploadStatus.failed("The upload timed out. Please try again.");
        }
        int total = upload.data.length;
        if (offset != upload.received || chunk.length == 0 || chunk.length > MAX_CHUNK
                || chunk.length > total - upload.received) {
            this.uploads.remove(player.getUUID());
            return UploadStatus.failed("The upload got out of step. Please try again.");
        }
        System.arraycopy(chunk, 0, upload.data, offset, chunk.length);
        upload.received += chunk.length;
        upload.lastActivity = Util.getMillis();
        if (upload.received < total) {
            return UploadStatus.receiving(upload.received, total);
        }
        this.uploads.remove(player.getUUID());
        return this.store(player, upload);
    }

    private UploadStatus store(ServerPlayer player, Upload upload) {
        LiveryPolicy current = this.policy();
        try {
            LiveryImages.Sanitized image = LiveryImages.sanitize(upload.data, current);
            Livery livery = this.store.add(image, upload.vehicleType, upload.name, player.getUUID(),
                    player.getGameProfile().getName(), current);
            if (!livery.vehicleType().equalsIgnoreCase(upload.vehicleType)) {
                return UploadStatus.failed("This image is already on the server as a livery for " + livery.vehicleType() + ".");
            }
            return UploadStatus.done(livery);
        } catch (LiveryImages.RejectedException e) {
            return UploadStatus.failed(e.getMessage());
        }
    }

    private void dropIdleUploads() {
        long now = Util.getMillis();
        this.uploads.values().removeIf(upload -> now - upload.lastActivity > UPLOAD_TIMEOUT_MS);
    }

    // Applying and deleting

    /** Outcome of an apply or delete request, with a line to show the player. */
    public record Result(boolean success, String message) {

        static Result ok(String message) {
            return new Result(true, message);
        }

        static Result error(String message) {
            return new Result(false, message);
        }
    }

    /** The pilot may repaint their aircraft; operators may repaint any aircraft close to them. */
    public static boolean mayChangeLivery(ServerPlayer player, AircraftEntity aircraft) {
        if (aircraft.isRemoved() || aircraft.level() != player.level()) {
            return false;
        }
        if (aircraft.seats().seatIdOf(player) == 0) {
            return true;
        }
        return isOperator(player) && player.distanceToSqr(aircraft) <= OPERATOR_REACH * OPERATOR_REACH;
    }

    /** Puts a livery on the aircraft; an empty id restores the default texture. */
    public Result apply(ServerPlayer player, AircraftEntity aircraft, String liveryId) {
        if (!mayChangeLivery(player, aircraft)) {
            return Result.error("Only the pilot can change this vehicle's livery.");
        }
        if (liveryId.isEmpty()) {
            aircraft.appearance().setLiveryId("");
            return Result.ok("Default texture restored.");
        }
        if (!this.policy().enabled()) {
            return Result.error("Liveries are disabled on this server.");
        }
        Optional<Livery> livery = Livery.isValidId(liveryId) ? this.store.get(liveryId) : Optional.empty();
        if (livery.isEmpty()) {
            return Result.error("That livery no longer exists.");
        }
        if (!livery.get().vehicleType().equalsIgnoreCase(aircraft.identity().typeName())) {
            return Result.error("That livery was made for " + livery.get().vehicleType() + ".");
        }
        aircraft.appearance().setLiveryId(liveryId);
        return Result.ok("Applied \"" + livery.get().name() + "\".");
    }

    /** Deletes a livery the player uploaded; operators may delete any. */
    public Result delete(ServerPlayer player, String liveryId) {
        Optional<Livery> livery = Livery.isValidId(liveryId) ? this.store.get(liveryId) : Optional.empty();
        if (livery.isEmpty()) {
            return Result.error("That livery no longer exists.");
        }
        if (!this.store.remove(liveryId, player.getUUID(), isOperator(player))) {
            return Result.error("You can only delete your own liveries.");
        }
        return Result.ok("Deleted \"" + livery.get().name() + "\".");
    }

    // Downloads

    /** Images queued for one player; the one being sent is held in memory until it is done. */
    private static final class DownloadQueue {
        final ArrayDeque<String> pending = new ArrayDeque<>();
        @Nullable
        String currentId;
        byte[] current = new byte[0];
        int sent;

        boolean contains(String id) {
            return id.equals(this.currentId) || this.pending.contains(id);
        }
    }

    /**
     * Queues an image for the player. Unknown ids, and every id while liveries are disabled, are
     * answered with an empty "not found" reply so the client shows the default texture.
     */
    public void requestImage(ServerPlayer player, String liveryId) {
        if (!Livery.isValidId(liveryId)) {
            return;
        }
        if (!this.policy().enabled()) {
            Network.sendToPlayer(player, LiveryImageChunkMessage.notFound(liveryId));
            return;
        }
        DownloadQueue queue = this.downloads.computeIfAbsent(player.getUUID(), id -> new DownloadQueue());
        if (queue.contains(liveryId) || queue.pending.size() >= MAX_QUEUED_DOWNLOADS) {
            return;
        }
        queue.pending.add(liveryId);
    }

    private void tick(MinecraftServer server) {
        if (++this.ticks % UPLOAD_SWEEP_TICKS == 0) {
            this.dropIdleUploads();
        }
        Iterator<Map.Entry<UUID, DownloadQueue>> it = this.downloads.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, DownloadQueue> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !this.sendSome(player, entry.getValue())) {
                it.remove();
            }
        }
    }

    /** Sends up to the per-tick budget; returns false once the queue is empty. */
    private boolean sendSome(ServerPlayer player, DownloadQueue queue) {
        int budget = DOWNLOAD_BYTES_PER_TICK;
        while (budget > 0) {
            if (queue.currentId == null && !this.startNext(player, queue)) {
                return false;
            }
            int length = Math.min(MAX_CHUNK, queue.current.length - queue.sent);
            byte[] chunk = Arrays.copyOfRange(queue.current, queue.sent, queue.sent + length);
            Network.sendToPlayer(player,
                    new LiveryImageChunkMessage(queue.currentId, queue.sent, queue.current.length, chunk));
            queue.sent += length;
            budget -= length;
            if (queue.sent >= queue.current.length) {
                queue.currentId = null;
                queue.current = new byte[0];
            }
        }
        return true;
    }

    /** Loads the next queued image that exists; missing ones are answered right away. */
    private boolean startNext(ServerPlayer player, DownloadQueue queue) {
        while (!queue.pending.isEmpty()) {
            String id = queue.pending.poll();
            Optional<byte[]> image = this.store.readImage(id);
            if (image.isPresent() && image.get().length > 0) {
                queue.currentId = id;
                queue.current = image.get();
                queue.sent = 0;
                return true;
            }
            Network.sendToPlayer(player, LiveryImageChunkMessage.notFound(id));
        }
        return false;
    }

    private void forget(UUID player) {
        this.uploads.remove(player);
        this.downloads.remove(player);
    }

    private static boolean isKnownVehicleType(String type) {
        return VehicleTypes.registry(VehicleKind.HELICOPTER).contains(type)
                || VehicleTypes.registry(VehicleKind.PLANE).contains(type)
                || VehicleTypes.registry(VehicleKind.TANK).contains(type)
                        || VehicleTypes.registry(VehicleKind.VEHICLE).contains(type)
                || VehicleTypes.registry(VehicleKind.SHIP).contains(type);
    }
}
