package com.ivomens.mcheli.vehicle.component;

/**
 * The id a vehicle names its livery by: the lower-case hex SHA-256 of the livery's PNG. The vehicle only
 * stores the id; the livery feature resolves it to a texture.
 */
public final class LiveryId {

    /** Length of an id: 32 bytes of SHA-256 written as hex. */
    public static final int LENGTH = 64;

    private LiveryId() {
    }

    /** Whether the text is a well-formed id; anything else is rejected outright. */
    public static boolean isValid(String id) {
        if (id == null || id.length() != LENGTH) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) return false;
        }
        return true;
    }
}
