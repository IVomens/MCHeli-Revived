package com.ivomens.mcheli.gametest.bench;

import com.ivomens.mcheli.networking.Message;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntPredicate;

/**
 * Counts what one observing player is sent about MCHeli: every MCHeli message, and the vanilla entity
 * packets (spawn, entity data, moves, teleports, motion, passengers, removal...) of entities of MCHeli's
 * types. Each packet is encoded the way the game's network pipeline encodes it (the play protocol's
 * codec for vanilla packets, the message's own codec for ours) and counted with its frame length
 * prefix, uncompressed. Everything else the observer is sent (chunks, players, other entities) is not
 * counted.
 * <p>
 * The packets pile up unencoded in the mock connection during the tick; they are counted after the
 * tick has been timed, so counting costs the measured tick nothing.
 */
final class PacketMeter {

    /** Packet types whose body starts with the entity id as a VarInt. */
    private static final Set<Class<?>> LEADING_ENTITY_ID = Set.of(ClientboundAddEntityPacket.class,
            ClientboundSetEntityDataPacket.class, ClientboundMoveEntityPacket.Pos.class,
            ClientboundMoveEntityPacket.PosRot.class, ClientboundMoveEntityPacket.Rot.class,
            ClientboundTeleportEntityPacket.class, ClientboundSetEntityMotionPacket.class,
            ClientboundRotateHeadPacket.class, ClientboundSetPassengersPacket.class, ClientboundAnimatePacket.class,
            ClientboundUpdateAttributesPacket.class, ClientboundHurtAnimationPacket.class,
            ClientboundDamageEventPacket.class, ClientboundProjectilePowerPacket.class,
            ClientboundSetEquipmentPacket.class);
    /** The two delimiter packets that frame a bundle on the wire: id and length byte each. */
    private static final int BUNDLE_DELIMITERS = 4;

    private final ServerLevel level;
    private final IntPredicate ours;
    private final StreamCodec<ByteBuf, Packet<? super ClientGamePacketListener>> codec;
    private final ByteBuf scratch = Unpooled.buffer(1 << 16);
    private final Map<String, long[]> byKind = new TreeMap<>();
    private final Map<String, long[]> entityData = new TreeMap<>();
    private final Map<String, long[]> byEntityType = new TreeMap<>();
    private final EntityDataNames dataNames = new EntityDataNames();
    private boolean counting;
    private long modBytes;
    private long vanillaBytes;

    PacketMeter(ServerLevel level, IntPredicate ours) {
        this.level = level;
        this.ours = ours;
        ProtocolInfo<ClientGamePacketListener> protocol =
                GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(level.registryAccess()));
        this.codec = protocol.codec();
    }

    void setCounting(boolean counting) {
        this.counting = counting;
    }

    long modBytes() {
        return this.modBytes;
    }

    long vanillaBytes() {
        return this.vanillaBytes;
    }

    /** Bytes and packet count per kind of packet, largest first is up to the reader. */
    Map<String, long[]> byKind() {
        return this.byKind;
    }

    /**
     * Bytes and count per synced value in the entity data packets about our entities ({@code Class#FIELD}),
     * each value counted with its id, serializer and payload.
     */
    Map<String, long[]> entityData() {
        return this.entityData;
    }

    /** Bytes and count of the vanilla packets per entity type and kind ({@code seat Pos}). */
    Map<String, long[]> byEntityType() {
        return this.byEntityType;
    }

    /** The group a packet kind of {@link #byKind} belongs to, for the summary tables. */
    static String group(String kind) {
        return switch (kind) {
            case "mod combat/radar_contacts" -> "radar contacts";
            case "mod combat/explosions" -> "explosion effects";
            case "Pos", "PosRot", "Rot", "ClientboundRotateHeadPacket" -> "move/rotation";
            case "ClientboundSetEntityDataPacket" -> "entity data";
            case "ClientboundTeleportEntityPacket" -> "teleport";
            case "ClientboundSetEntityMotionPacket" -> "motion";
            case "ClientboundAddEntityPacket", "ClientboundRemoveEntitiesPacket" -> "spawn/remove";
            case "ClientboundSetPassengersPacket" -> "passengers";
            default -> kind.startsWith("mod ") ? "other MCHeli messages" : "other vanilla";
        };
    }

    /** One object the observer's connection was asked to send. */
    void accept(Object sent) {
        try {
            if (this.counting && sent instanceof Packet<?> packet) {
                count(packet);
            }
        } finally {
            ReferenceCountUtil.release(sent);
        }
    }

    private void count(Packet<?> packet) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            boolean any = false;
            for (Packet<?> sub : bundle.subPackets()) {
                any |= countSingle(sub);
            }
            if (any) {
                add("bundle delimiters", BUNDLE_DELIMITERS, false);
            }
        } else {
            countSingle(packet);
        }
    }

    /** @return whether the packet was one of ours */
    private boolean countSingle(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof Message message) {
            add("mod " + message.messageType().id().getPath(), encodeMessage(message), true);
            return true;
        }
        if (!aboutOurEntity(packet)) {
            return false;
        }
        int size = encodeVanilla(packet);
        add(packet.getClass().getSimpleName(), size, false);
        if (packet instanceof ClientboundSetEntityDataPacket data) {
            countEntityData(data);
        }
        if (LEADING_ENTITY_ID.contains(packet.getClass())) {
            FriendlyByteBuf buf = new FriendlyByteBuf(this.scratch.duplicate());
            buf.readVarInt(); // packet id
            Entity entity = this.level.getEntity(buf.readVarInt());
            String type = entity == null ? "(removed)" : entity.getType().builtInRegistryHolder().key().location().getPath();
            long[] entry = this.byEntityType.computeIfAbsent(type + " " + packet.getClass().getSimpleName(),
                    k -> new long[2]);
            entry[0] += size;
            entry[1]++;
        }
        return true;
    }

    private boolean aboutOurEntity(Packet<?> packet) {
        if (LEADING_ENTITY_ID.contains(packet.getClass())) {
            encodeVanilla(packet);
            FriendlyByteBuf buf = new FriendlyByteBuf(this.scratch.duplicate());
            buf.readVarInt(); // packet id
            return this.ours.test(buf.readVarInt());
        }
        if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
            return remove.getEntityIds().intStream().anyMatch(this.ours);
        }
        if (packet instanceof ClientboundEntityEventPacket event) {
            var entity = event.getEntity(this.level);
            return entity != null && this.ours.test(entity.getId());
        }
        if (packet instanceof ClientboundSoundEntityPacket sound) {
            return this.ours.test(sound.getId());
        }
        return false;
    }

    private void countEntityData(ClientboundSetEntityDataPacket packet) {
        Entity entity = this.level.getEntity(packet.id());
        for (SynchedEntityData.DataValue<?> value : packet.packedItems()) {
            this.scratch.clear();
            value.write(new RegistryFriendlyByteBuf(this.scratch, this.level.registryAccess()));
            long[] entry = this.entityData.computeIfAbsent(this.dataNames.name(entity, value.id()), k -> new long[2]);
            entry[0] += this.scratch.writerIndex();
            entry[1]++;
        }
    }

    @SuppressWarnings("unchecked")
    private int encodeVanilla(Packet<?> packet) {
        this.scratch.clear();
        this.codec.encode(this.scratch, (Packet<? super ClientGamePacketListener>) packet);
        return framed(this.scratch.writerIndex());
    }

    @SuppressWarnings("unchecked")
    private int encodeMessage(Message message) {
        this.scratch.clear();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(this.scratch, this.level.registryAccess());
        ((StreamCodec<RegistryFriendlyByteBuf, Message>) (StreamCodec<?, ?>) message.messageType().codec())
                .encode(buf, message);
        byte[] id = message.messageType().id().toString().getBytes(StandardCharsets.UTF_8);
        // custom payload packet id, then the payload id as a string, then the payload
        int body = 1 + VarInt.getByteSize(id.length) + id.length + this.scratch.writerIndex();
        return framed(body);
    }

    private static int framed(int length) {
        return VarInt.getByteSize(length) + length;
    }

    private void add(String kind, int bytes, boolean mod) {
        long[] entry = this.byKind.computeIfAbsent(kind, k -> new long[2]);
        entry[0] += bytes;
        entry[1]++;
        if (mod) {
            this.modBytes += bytes;
        } else {
            this.vanillaBytes += bytes;
        }
    }
}
