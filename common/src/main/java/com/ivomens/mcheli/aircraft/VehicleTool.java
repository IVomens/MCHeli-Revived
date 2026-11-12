package com.ivomens.mcheli.aircraft;

/**
 * An item that works on a vehicle instead of boarding it, such as the wrench or the gunner spawner. Using
 * it on the vehicle or one of its seats never boards the vehicle or opens its inventory, and the "enter
 * seat" prompt is hidden while it is held. Items of other features implement it; the vehicle only asks.
 */
public interface VehicleTool {

    /** Whether using the tool while sneaking switches the vehicle to its next texture, as the wrench does. */
    default boolean cyclesTexture() {
        return false;
    }
}
