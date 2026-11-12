package com.ivomens.mcheli.livery;

import com.ivomens.mcheli.vehicle.component.LiveryId;
import java.util.UUID;

/**
 * A player-made texture for one vehicle type.
 *
 * @param id          content hash of the PNG; also its file name, so identical uploads share storage
 * @param vehicleType the aircraft type name the texture was made for (e.g. {@code "ah-64"})
 * @param name        display name chosen by the uploader
 * @param owner       uploading player
 * @param ownerName   uploader's name at upload time, for display
 * @param width       image width in pixels
 * @param height      image height in pixels
 * @param bytes       PNG size on disk
 * @param created     upload time, epoch milliseconds
 */
public record Livery(String id, String vehicleType, String name, UUID owner, String ownerName,
                     int width, int height, int bytes, long created) {

    /** Length of an id: 32 bytes of SHA-256 written as hex. */
    public static final int ID_LENGTH = LiveryId.LENGTH;

    /** Liveries are addressed by a lower-case hex SHA-256; anything else is rejected outright. */
    public static boolean isValidId(String id) {
        return LiveryId.isValid(id);
    }
}
