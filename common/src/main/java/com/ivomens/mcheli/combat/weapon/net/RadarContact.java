package com.ivomens.mcheli.combat.weapon.net;

/**
 * What a radar contact is: sent the first time a receiver sees the contact, and again when any of it
 * changes. Its positions come as {@link RadarContactFix}es.
 *
 * @param entityId  the entity's id in its level
 * @param level     the dimension id of the entity's level
 * @param name      the vehicle's type name, or the entity's display name
 * @param kind      what the entity is, which the instruments tell vehicles, decoys and missiles by
 */
public record RadarContact(int entityId, String level, String name, RadarContactKind kind) {

    static final int MAX_NAME_LENGTH = 256;

    /** Cuts a name to the length the codec accepts; null becomes empty. */
    public static String shortName(String name) {
        if (name == null) {
            return "";
        }
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }
}
