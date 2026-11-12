package com.ivomens.mcheli.vehicle.definition;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A drawn, usually animated part. Only what every part shares is here; the animation settings
 * stay on the parsed definition until the renderer reads snapshots.
 *
 * @param kind      what animates the part
 * @param modelName the part's name: the {@code $name} group of the body model, or the
 *                  {@code <vehicle>_<name>} model file
 * @param pos       pivot
 * @param rot       rotation axis
 * @param children  parts that move with this one (weapon barrels, pylons, propeller blades)
 */
public record PartDef(PartKind kind, String modelName, Vec3 pos, Vec3 rot, List<PartDef> children) {

    public PartDef {
        children = List.copyOf(children);
    }

    public enum PartKind {
        HATCH,
        LIGHT_HATCH,
        CANOPY,
        CAMERA,
        THROTTLE,
        ROTATING_PART,
        WEAPON,
        WEAPON_CHILD,
        WEAPON_BAY,
        LANDING_GEAR,
        CRAWLER_TRACK,
        TRACK_ROLLER,
        WHEEL,
        STEERING_WHEEL,
        ROTOR,
        BLADE,
        WING,
        PYLON,
        NOZZLE,
        TURRET_PART;

        /** Light hatches have only ever been drawn from groups of the body model, never from a file of their own. */
        public boolean mayHaveOwnModel() {
            return this != LIGHT_HATCH;
        }
    }
}
