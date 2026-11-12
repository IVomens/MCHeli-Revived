package com.ivomens.mcheli.gametest.bench;

import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.setup.McheliMessages;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A player with no client behind it, as {@code GameTestHelper.makeMockServerPlayerInLevel} makes one,
 * but keeping hold of its connection: whatever the server sends the player piles up in the embedded
 * channel, where the benchmark counts it ({@link PacketMeter}) or throws it away. Its client asks for
 * {@code viewDistance} chunks.
 * <p>
 * The game sends some messages from other threads (the radar contacts go out from a virtual thread).
 * A real connection hands those to its network thread; an embedded channel would write from both
 * threads at once and break, so sending and draining take a lock here.
 */
public final class MockPlayer {

    private final ServerPlayer player;
    private final Connection connection;
    private final EmbeddedChannel channel;
    private final Object lock;

    private MockPlayer(ServerPlayer player, Connection connection, EmbeddedChannel channel, Object lock) {
        this.player = player;
        this.connection = connection;
        this.channel = channel;
        this.lock = lock;
    }

    public static MockPlayer join(ServerLevel level, String name, int viewDistance) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name);
        ClientInformation client = new ClientInformation("en_us", viewDistance, ChatVisiblity.FULL, true, 0x7F,
                HumanoidArm.RIGHT, false, false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, client) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Object lock = new Object();
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
                synchronized (lock) {
                    super.send(packet, listener, flush);
                }
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player,
                new CommonListenerCookie(profile, 0, client, false));
        player.updateOptions(client);
        return new MockPlayer(player, connection, channel, lock);
    }

    public ServerPlayer player() {
        return this.player;
    }

    public Connection connection() {
        return this.connection;
    }

    /** Opens MCHeli's channels, as a client with the mod would, so the player is sent the mod's messages. */
    public void openModChannels() {
        List<ResourceLocation> channels = McheliMessages.registry().types().stream()
                .filter(type -> type.direction() == MessageType.Direction.CLIENTBOUND).map(MessageType::id).toList();
        ModChannels.get().open(this.player, this.connection, channels);
    }

    /** Everything sent to the player since the last call, in order; the queue is emptied. */
    public void drain(java.util.function.Consumer<Object> sink) {
        List<Object> sent = new ArrayList<>();
        synchronized (this.lock) {
            Object message;
            while ((message = this.channel.readOutbound()) != null) {
                sent.add(message);
            }
        }
        sent.forEach(sink);
    }

    /** Throws away everything sent to the player so far. */
    void discardSent() {
        synchronized (this.lock) {
            this.channel.releaseOutbound();
        }
    }

    /** Tells the server the client took its last chunk batch, as a real client does, so chunks keep coming. */
    public void acknowledgeChunks() {
        this.player.connection.chunkSender.onChunkBatchReceivedByClient(64.0F);
    }

    public void leave() {
        this.player.stopRiding();
        this.player.server.getPlayerList().remove(this.player);
        discardSent();
    }
}
