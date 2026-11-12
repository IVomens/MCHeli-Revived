package com.ivomens.mcheli.content.registry;

import com.ivomens.mcheli.platform.Services;

import javax.annotation.Nullable;

/**
 * The kinds of content an addon can define. Each kind lives in its own folder,
 * {@code assets/mcheli/<dirName>/}, and every file in that folder becomes one entry of the matching
 * {@link ContentRegistry}.
 */
public enum ContentType {

    HELICOPTER("helicopters"),
    PLANE("planes"),
    SHIP("ships"),
    TANK("tanks"),
    VEHICLE("vehicles"),
    WEAPON("weapons"),
    THROWABLE("throwable"),
    /** HUD layouts are only needed by the client renderer, so dedicated servers skip them. */
    HUD("hud");

    public final String dirName;

    ContentType(String dirName) {
        this.dirName = dirName;
    }

    /** Whether {@code dirName} is the folder of any content type. */
    public static boolean validateDirName(String dirName) {
        for (ContentType type : values()) {
            if (type.dirName.equals(dirName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The content type stored in the given folder, or null when the folder holds no content this side loads.
     */
    @Nullable
    public static ContentType fromDirName(@Nullable String dirName) {
        if (dirName == null) {
            return null;
        }
        for (ContentType type : values()) {
            if (type.dirName.equals(dirName)) {
                return type == HUD && !Services.PLATFORM.isClient() ? null : type;
            }
        }
        return null;
    }
}
