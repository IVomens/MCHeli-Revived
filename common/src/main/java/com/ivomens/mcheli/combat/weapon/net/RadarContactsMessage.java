package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.networking.ClientboundMessage;
import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.networking.MessageType;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * A radar snapshot for one player, as a change to the one before: the contacts the player had not
 * seen (or that changed what they are), the contacts that moved, and the ids of the contacts that are
 * still there and did not move. A contact missing from all three is no longer on the radar; the
 * client lets it expire as before. Newer snapshots replace older ones.
 * <p>
 * Positions are fixed point relative to {@code origin}, a block near the receiver, so they stay a few
 * bytes each. Every list is sorted by entity id and each id is written as the difference to the one
 * before it.
 */
public record RadarContactsMessage(long snapshotSeq, int originX, int originY, int originZ,
                                   List<RadarContact> introduced, List<RadarContactFix> fixes,
                                   int[] unchanged) implements ClientboundMessage {

    public static final int MAX_CONTACTS = 65536;

    private static final StreamCodec<ByteBuf, String> NAME = Codecs.utf(RadarContact.MAX_NAME_LENGTH);
    private static final StreamCodec<ByteBuf, RadarContactsMessage> CODEC = StreamCodec.of(
            RadarContactsMessage::write, RadarContactsMessage::read);

    public static final MessageType<RadarContactsMessage> TYPE =
            MessageType.clientbound("combat/radar_contacts", CODEC);

    public RadarContactsMessage {
        introduced = List.copyOf(introduced);
        fixes = List.copyOf(fixes);
        unchanged = unchanged.clone();
    }

    @Override
    public int[] unchanged() {
        return this.unchanged.clone();
    }

    @Override
    public MessageType<RadarContactsMessage> messageType() {
        return TYPE;
    }

    private static void write(ByteBuf buf, RadarContactsMessage message) {
        VarLong.write(buf, message.snapshotSeq);
        writeSigned(buf, message.originX);
        writeSigned(buf, message.originY);
        writeSigned(buf, message.originZ);
        writeCount(buf, message.introduced.size());
        int last = 0;
        for (RadarContact contact : message.introduced) {
            writeSigned(buf, contact.entityId() - last);
            last = contact.entityId();
            NAME.encode(buf, contact.level());
            NAME.encode(buf, contact.name());
            VarInt.write(buf, contact.kind().id());
        }
        writeCount(buf, message.fixes.size());
        last = 0;
        for (RadarContactFix fix : message.fixes) {
            // the id difference and whether the motion follows share one number
            VarLong.write(buf, (zigzag(fix.entityId() - last) & 0xFFFFFFFFL) << 1 | (fix.moving() ? 1 : 0));
            last = fix.entityId();
            writeSigned(buf, fix.x());
            writeSigned(buf, fix.y());
            writeSigned(buf, fix.z());
            if (fix.moving()) {
                writeSigned(buf, fix.dx());
                writeSigned(buf, fix.dy());
                writeSigned(buf, fix.dz());
            }
        }
        writeCount(buf, message.unchanged.length);
        last = 0;
        for (int id : message.unchanged) {
            writeSigned(buf, id - last);
            last = id;
        }
    }

    private static RadarContactsMessage read(ByteBuf buf) {
        long seq = VarLong.read(buf);
        int originX = readSigned(buf);
        int originY = readSigned(buf);
        int originZ = readSigned(buf);
        int count = readCount(buf);
        List<RadarContact> introduced = new ArrayList<>(count);
        int last = 0;
        for (int i = 0; i < count; i++) {
            last += readSigned(buf);
            introduced.add(new RadarContact(last, NAME.decode(buf), NAME.decode(buf), readKind(buf)));
        }
        count = readCount(buf);
        List<RadarContactFix> fixes = new ArrayList<>(count);
        last = 0;
        for (int i = 0; i < count; i++) {
            long head = VarLong.read(buf);
            last += unzigzag((int) (head >>> 1));
            int x = readSigned(buf);
            int y = readSigned(buf);
            int z = readSigned(buf);
            boolean moving = (head & 1) != 0;
            fixes.add(new RadarContactFix(last, x, y, z, moving ? readSigned(buf) : 0, moving ? readSigned(buf) : 0,
                    moving ? readSigned(buf) : 0));
        }
        count = readCount(buf);
        int[] unchanged = new int[count];
        last = 0;
        for (int i = 0; i < count; i++) {
            last += readSigned(buf);
            unchanged[i] = last;
        }
        return new RadarContactsMessage(seq, originX, originY, originZ, introduced, fixes, unchanged);
    }

    private static RadarContactKind readKind(ByteBuf buf) {
        int id = VarInt.read(buf);
        RadarContactKind kind = RadarContactKind.byId(id);
        if (kind == null) {
            throw new DecoderException("Radar contact of unknown kind " + id);
        }
        return kind;
    }

    private static void writeCount(ByteBuf buf, int count) {
        if (count > MAX_CONTACTS) {
            throw new EncoderException("Radar snapshot with " + count + " contacts, at most " + MAX_CONTACTS);
        }
        VarInt.write(buf, count);
    }

    private static int readCount(ByteBuf buf) {
        int count = VarInt.read(buf);
        if (count < 0 || count > MAX_CONTACTS) {
            throw new DecoderException("Radar snapshot with " + count + " contacts, at most " + MAX_CONTACTS);
        }
        return count;
    }

    private static void writeSigned(ByteBuf buf, int value) {
        VarInt.write(buf, zigzag(value));
    }

    private static int readSigned(ByteBuf buf) {
        return unzigzag(VarInt.read(buf));
    }

    private static int zigzag(int value) {
        return value << 1 ^ value >> 31;
    }

    private static int unzigzag(int value) {
        return value >>> 1 ^ -(value & 1);
    }
}
