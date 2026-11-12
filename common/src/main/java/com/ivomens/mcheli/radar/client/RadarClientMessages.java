package com.ivomens.mcheli.radar.client;

import com.ivomens.mcheli.combat.weapon.net.RadarContact;
import com.ivomens.mcheli.combat.weapon.net.RadarContactFix;
import com.ivomens.mcheli.combat.weapon.net.RadarContactKind;
import com.ivomens.mcheli.combat.weapon.net.RadarContactsMessage;
import com.ivomens.mcheli.networking.MessageRegistry;
import com.ivomens.mcheli.radar.EntityInfo;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client handler of the radar contacts the server streams for the radar displays. */
public final class RadarClientMessages {

    private RadarClientMessages() {
    }

    /** @param contacts where the client keeps the contacts the server sends */
    public static void register(MessageRegistry registry, EntityInfoClientTracker contacts) {
        registry.onClient(RadarContactsMessage.TYPE, message -> updateRadar(message, contacts));
    }

    /**
     * Applies a snapshot: new and moved contacts get their new positions, unchanged ones are kept as
     * they were. Together they are the contacts on the radar now, which the tracker refreshes.
     */
    public static void updateRadar(RadarContactsMessage message, EntityInfoClientTracker contacts) {
        Map<Integer, RadarContact> introduced = new HashMap<>();
        message.introduced().forEach(contact -> introduced.put(contact.entityId(), contact));
        int[] unchanged = message.unchanged();
        List<EntityInfo> infos = new ArrayList<>(message.fixes().size() + unchanged.length);
        for (RadarContactFix fix : message.fixes()) {
            RadarContact contact = introduced.get(fix.entityId());
            EntityInfo known = contacts.getEntityInfo(fix.entityId());
            if (contact != null) {
                infos.add(entityInfo(contact.entityId(), contact.level(), contact.name(), contact.kind(), fix,
                        message));
            } else if (known != null) {
                infos.add(entityInfo(known.entityId(), known.worldName(), known.entityName(),
                        known.kind(), fix, message));
            }
        }
        for (int id : unchanged) {
            EntityInfo known = contacts.getEntityInfo(id);
            if (known != null) {
                infos.add(known);
            }
        }
        contacts.updateEntities(infos, message.snapshotSeq());
    }

    private static EntityInfo entityInfo(int id, String level, String name, RadarContactKind kind,
                                         RadarContactFix fix, RadarContactsMessage message) {
        Vec3 pos = fix.position(message.originX(), message.originY(), message.originZ());
        Vec3 previous = fix.previousPosition(message.originX(), message.originY(), message.originZ());
        return EntityInfo.seenNow(id, level, name, kind, pos.x, pos.y, pos.z, previous.x, previous.y,
                previous.z);
    }
}
