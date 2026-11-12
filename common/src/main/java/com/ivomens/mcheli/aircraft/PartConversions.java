package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.content.def.vehicle.CrewEntries.HitBox;
import com.ivomens.mcheli.content.def.vehicle.Parts;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.content.def.vehicle.VehicleRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Turns the parts of a {@link VehicleFile} into the {@link VehicleParts} run-time code reads. Positions and
 * names are taken as the definition holds them; the values the game derives (opening factors, the parts that
 * cancel each other) are worked out here.
 */
final class PartConversions {

    /** Openings are given in degrees; the renderer works in factors of 90 degrees. */
    private static final float RIGHT_ANGLE = 90.0F;

    private PartConversions() {
    }

    static <P, I> List<I> map(List<P> parts, Function<P, I> convert) {
        List<I> converted = new ArrayList<>(parts.size());
        for (P part : parts) {
            converted.add(convert.apply(part));
        }
        return List.copyOf(converted);
    }

    static VehicleParts.DrawnPart placed(Vec3 position, Vec3 rotation, String name) {
        return new VehicleParts.DrawnPart(position, rotation, name);
    }

    static VehicleParts.Camera camera(Parts.Camera part) {
        return new VehicleParts.Camera(placed(part.position(), part.rotation(), part.name()), part.yawSync(),
                part.pitchSync());
    }

    static VehicleParts.Canopy canopy(Parts.Canopy part) {
        return new VehicleParts.Canopy(placed(part.position(), part.rotation(), part.name()), part.maxRotation(),
                part.sliding());
    }

    static VehicleParts.Hatch hatch(Parts.Hatch part) {
        return new VehicleParts.Hatch(placed(part.position(), part.rotation(), part.name()), part.maxRotation(),
                part.sliding());
    }

    static VehicleParts.RotPart rotating(Parts.Rotating part) {
        return new VehicleParts.RotPart(placed(part.position(), part.rotation(), part.name()), part.speed(),
                part.always());
    }

    static VehicleParts.PartWheel wheel(Parts.Wheel part) {
        return new VehicleParts.PartWheel(placed(part.position(), part.rotation(), part.name()), part.direction(),
                part.pivot());
    }

    /**
     * A gear folds by its opening over 90 degrees. Legacy TXT gear without a second axis kept a zero second
     * axis where YAML gear keeps none; both draw the same.
     */
    static VehicleParts.LandingGear landingGear(Parts.LandingGear part, boolean legacyTxt) {
        Vec3 secondAxis = part.articulatedRotation();
        boolean articulated = secondAxis != null;
        return new VehicleParts.LandingGear(placed(part.position(), part.rotation(), part.name()),
                part.maxRotation() / RIGHT_ANGLE, part.reverse(), part.hatch(), part.slide(), articulated,
                articulated ? secondAxis : legacyTxt ? Vec3.ZERO : null,
                articulated ? part.maxArticulatedRotation() / RIGHT_ANGLE : 0.0F);
    }

    static VehicleParts.PartWeapon weapon(Parts.Weapon part) {
        String[] weapons = part.weapons().toArray(new String[0]);
        List<VehicleParts.PartWeaponChild> children = new ArrayList<>();
        for (Parts.WeaponChild child : part.children()) {
            children.add(new VehicleParts.PartWeaponChild(child.position(), child.rotation(),
                    part.name() + "_" + children.size(), weapons, child.yaw(), child.pitch(), child.recoilBuffer()));
        }
        return new VehicleParts.PartWeapon(placed(part.position(), part.rotation(), part.name()), weapons,
                part.barrelRotation(), part.missile(), part.hideInGunner(), part.yaw(), part.pitch(),
                part.recoilBuffer(), part.turret(), children);
    }

    static VehicleParts.SearchLight searchLight(Parts.SearchLight part) {
        return new VehicleParts.SearchLight(part.position(), part.colorStart(), part.colorEnd(), part.height(),
                part.width(), part.fixedDirection(), part.yaw(), part.pitch(), part.steering(),
                part.steeringRotation());
    }

    static VehicleParts.Throttle throttle(Parts.Throttle part) {
        return new VehicleParts.Throttle(placed(part.position(), part.rotation(), part.name()), part.slide(),
                part.maxAngle());
    }

    static VehicleParts.TrackRoller trackRoller(Parts.Plain part) {
        return new VehicleParts.TrackRoller(placed(part.position(), part.rotation(), part.name()));
    }

    /** The weapon bays the game keeps, each with the ids of the weapon sets it opens for. */
    static List<VehicleParts.WeaponBay> weaponBays(VehicleFile file) {
        return map(VehicleRules.weaponBays(file), link -> {
            Parts.WeaponBay bay = link.bay();
            return new VehicleParts.WeaponBay(placed(bay.position(), bay.rotation(), bay.name()), bay.maxRotation(),
                    bay.sliding(), bay.weapons(), link.weaponSets().toArray(new Integer[0]));
        });
    }

    /** Consecutive mounts of one weapon type form one weapon set. */
    static List<VehicleParts.WeaponSet> weaponSets(VehicleFile file) {
        List<String> types = new ArrayList<>();
        List<List<VehicleParts.Weapon>> mounts = new ArrayList<>();
        for (var mount : file.weapons()) {
            String type = mount.weapon().name();
            if (types.isEmpty() || !types.getLast().equals(type)) {
                types.add(type);
                mounts.add(new ArrayList<>());
            }
            Vec3 position = mount.position();
            mounts.getLast().add(new VehicleParts.Weapon((float) position.x, (float) position.y,
                    (float) position.z, mount.yaw(), mount.pitch(), mount.pilotCanUse(), mount.seat(),
                    mount.defaultYaw(), mount.minYaw(), mount.maxYaw(), mount.minPitch(), mount.maxPitch(),
                    mount.turret()));
        }
        List<VehicleParts.WeaponSet> sets = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            sets.add(new VehicleParts.WeaponSet(types.get(i), mounts.get(i)));
        }
        return List.copyOf(sets);
    }

    static PartBoundingBox hitBox(HitBox box) {
        Vec3 position = box.position();
        Vec3 size = box.size();
        PartBoundingBox converted = new PartBoundingBox(position.x, position.y, position.z, (float) size.x,
                (float) size.y, (float) size.z, box.damageFactor());
        converted.setBoundingBoxType(box.type());
        converted.setName(box.name());
        converted.setReactiveArmor(box.reactiveArmor(), box.eraExplosion(), box.eraMinDamage());
        return converted;
    }

    /**
     * Radar marker size and the hit boxes' z extent: {@code width}, {@code height}, {@code zMin}, {@code zMax}.
     * YAML measures the boxes; legacy TXT grew the defaults box by box (and kept the smaller far end).
     */
    static float[] markerBounds(List<PartBoundingBox> boxes, boolean legacyTxt) {
        float[] bounds = {2.0F, 1.0F, -1.0F, 1.0F};
        if (legacyTxt) {
            boxes.forEach(box -> growLegacy(bounds, box.boundingBox()));
            return bounds;
        }
        if (boxes.isEmpty()) {
            return bounds;
        }
        float maxY = Float.NEGATIVE_INFINITY;
        float maxAbsXZ = 0.0F;
        float zMin = Float.POSITIVE_INFINITY;
        float zMax = Float.NEGATIVE_INFINITY;
        for (PartBoundingBox box : boxes) {
            AABB aabb = box.boundingBox();
            maxY = Math.max(maxY, (float) aabb.maxY);
            maxAbsXZ = Math.max(maxAbsXZ, Math.max(Math.max(Math.abs((float) aabb.maxX), Math.abs((float) aabb.minX)),
                    Math.max(Math.abs((float) aabb.maxZ), Math.abs((float) aabb.minZ))));
            zMin = Math.min(zMin, (float) aabb.minZ);
            zMax = Math.max(zMax, (float) aabb.maxZ);
        }
        return new float[]{maxAbsXZ / 2.0F, maxY, zMin, zMax};
    }

    private static void growLegacy(float[] bounds, AABB box) {
        if (box.maxY > bounds[1]) {
            bounds[1] = (float) box.maxY;
        }
        for (double side : Arrays.asList(box.maxX, box.minX, box.maxZ, box.minZ)) {
            bounds[0] = (float) Math.max(bounds[0], Math.abs(side) / 2.0);
        }
        bounds[2] = (float) Math.min(bounds[2], box.minZ);
        bounds[3] = (float) Math.min(bounds[3], box.maxZ);
    }

    static List<VehicleParts.Hatch> hatches(VehicleFile file) {
        return VehicleRules.hatchesAndWingsClash(file) ? List.of()
                : map(file.components().hatches(), PartConversions::hatch);
    }
}
