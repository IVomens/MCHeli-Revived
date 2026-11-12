package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * What a client learns about a vehicle when it starts tracking it, before the synced data: its body
 * size and camera distance, its type, and its attitude, so the first frame already looks right.
 */
final class VehicleSpawnData {

    private static final float DEFAULT_THIRD_PERSON_DISTANCE = 4.0F;

    private VehicleSpawnData() {
    }

    static void write(AircraftEntity v, RegistryFriendlyByteBuf buffer) {
        VehicleSpec info = v.spec();
        if (info != null) {
            buffer.writeFloat(info.file().physics().bodySize().height());
            buffer.writeFloat(info.file().physics().bodySize().width());
            buffer.writeFloat(info.file().camera().thirdPersonDistance());
            byte[] name = v.identity().typeName().getBytes(StandardCharsets.UTF_8);
            buffer.writeShort(name.length);
            buffer.writeBytes(name);
        } else {
            buffer.writeFloat(v.getBbHeight());
            buffer.writeFloat(v.getBbWidth());
            buffer.writeFloat(DEFAULT_THIRD_PERSON_DISTANCE);
            buffer.writeShort(0);
        }
        buffer.writeFloat(v.getYRot());
        buffer.writeFloat(v.getXRot());
        buffer.writeFloat(v.roll());
    }

    static void read(AircraftEntity v, RegistryFriendlyByteBuf data) {
        try {
            float height = data.readFloat();
            float width = data.readFloat();
            v.body().setSize(width, height);
            v.cameras().setThirdPersonDistance(data.readFloat());
            int length = data.readShort();
            if (length > 0) {
                byte[] name = new byte[length];
                data.readBytes(name);
                String typeName = new String(name, StandardCharsets.UTF_8);
                // a client tick before this data arrived may already have set the type from the synced name
                if (v.spec() == null || !typeName.equals(v.spec().name())) {
                    v.changeType(typeName);
                }
            }
            float yaw = data.readFloat();
            float pitch = data.readFloat();
            float roll = data.readFloat();
            v.setYRot(yaw);
            v.setXRot(pitch);
            v.rollAngle().init(roll);
            v.yRotO = yaw;
            v.xRotO = pitch;
        } catch (Exception e) {
            ModLogger.error("Failed to read aircraft spawn data", e);
        }
    }
}
