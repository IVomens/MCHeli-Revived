package com.ivomens.mcheli.core.wingman.airfield;

/**
 * One wingman marker block as the airfield knows it. The game side builds it from the marker's block
 * entity (common's {@code Markers}).
 *
 * @param blockX         block position of the marker
 * @param blockY         block position of the marker
 * @param blockZ         block position of the marker
 * @param id             user-chosen id the routes and missions refer to; never null
 * @param baseId         owning base id; empty for BASE markers
 * @param parkingHeading PARKING only: -1 any, 0 north, 1 east, 2 south, 3 west
 */
public record Marker(int blockX, int blockY, int blockZ, MarkerType type, String id, String baseId,
                     int parkingHeading) {

    public Marker {
        id = id != null ? id : "";
        baseId = baseId != null ? baseId : "";
    }

    public Marker withId(String newId) {
        return new Marker(this.blockX, this.blockY, this.blockZ, this.type, newId, this.baseId, this.parkingHeading);
    }

    /** Whether the marker stands on this block. */
    public boolean isAt(int x, int y, int z) {
        return this.blockX == x && this.blockY == y && this.blockZ == z;
    }

    /** Block centre, where aircraft steer to. */
    public double centerX() {
        return this.blockX + 0.5;
    }

    public double centerZ() {
        return this.blockZ + 0.5;
    }
}
