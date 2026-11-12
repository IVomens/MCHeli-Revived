package com.ivomens.mcheli.world.uavstation;

import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The paired UAV snapshot of the UAV station screen. It is computed on the server and sent with
 * the menu opening (see {@link UavStationGuiFactory}), so the client can list UAVs it has not loaded.
 */
public final class UavStationGuiData {

    private static final int MAX_NAME_LENGTH = 256;

    private UavStationGuiData() {
    }

    /** One paired-UAV row in the station list. */
    public record UavEntry(UUID id, boolean loaded, String itemName, String displayName,
                           int x, int y, int z, int hp, int maxHp, boolean reachable, boolean destroyed) {

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeBoolean(loaded);
            buf.writeUtf(itemName, MAX_NAME_LENGTH);
            buf.writeUtf(displayName, MAX_NAME_LENGTH);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeInt(hp);
            buf.writeInt(maxHp);
            buf.writeBoolean(reachable);
            buf.writeBoolean(destroyed);
        }

        public static UavEntry read(RegistryFriendlyByteBuf buf) {
            return new UavEntry(buf.readUUID(), buf.readBoolean(), buf.readUtf(MAX_NAME_LENGTH), buf.readUtf(MAX_NAME_LENGTH), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean());
        }
    }

    public static void writeEntries(RegistryFriendlyByteBuf buf, List<UavEntry> entries) {
        buf.writeVarInt(entries.size());
        for (UavEntry entry : entries) {
            entry.write(buf);
        }
    }

    public static List<UavEntry> readEntries(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<UavEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(UavEntry.read(buf));
        }
        return list;
    }
}
