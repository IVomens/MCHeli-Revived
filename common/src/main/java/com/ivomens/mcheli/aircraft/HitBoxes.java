package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.aircraft.VehicleParts.Wheel;

import java.util.List;

/**
 * Where a vehicle can be hit and where it touches the ground, and the size of its radar marker.
 *
 * @param boxes        the extra hit boxes; each entity works on copies
 * @param wheels       where the vehicle touches the ground
 * @param markerWidth  half the widest extent of the hit boxes, for the radar marker
 * @param markerHeight the top of the hit boxes, for the radar marker
 * @param zMin         the hit boxes' near end along the vehicle
 * @param zMax         the hit boxes' far end along the vehicle
 */
public record HitBoxes(List<PartBoundingBox> boxes, List<Wheel> wheels, float markerWidth, float markerHeight,
                       float zMin, float zMax) {

    public HitBoxes {
        boxes = List.copyOf(boxes);
        wheels = List.copyOf(wheels);
    }

    /** The middle of the hit boxes along the vehicle. */
    public float z() {
        return (this.zMax + this.zMin) / 2.0F;
    }
}
