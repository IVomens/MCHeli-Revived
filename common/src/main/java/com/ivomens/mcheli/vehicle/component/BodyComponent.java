package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.PartBoundingBox;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.util.math.Rotations;
import com.ivomens.mcheli.vehicle.definition.VehicleDefinition;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * The hull's size and frame: the collision box from the definition, the radius around the origin that
 * encloses the whole model (which frustum culling uses), which way the hull faces, and where a point given in
 * the vehicle's own coordinates is in the world.
 */
public final class BodyComponent implements VehicleComponent {

    private static final float MIN_CULLING_RADIUS = 4.0F;

    private final AircraftEntity vehicle;
    private final PartBoxesComponent partBoxes;
    private final RollComponent roll;
    /** Body size from the definition; 0 until set, which leaves the entity type's size. */
    private float width;
    private float height;
    private float cullingRadius = 8.0F;

    public BodyComponent(AircraftEntity vehicle, PartBoxesComponent partBoxes, RollComponent roll) {
        this.vehicle = vehicle;
        this.partBoxes = partBoxes;
        this.roll = roll;
    }

    /** Which way the hull faces: the entity's yaw and pitch with the vehicle's roll. */
    public Attitude attitude() {
        return new Attitude(this.vehicle.getYRot(), this.vehicle.getXRot(), this.roll.get());
    }

    /** Turns the hull to the yaw and pitch, each kept within one turn. */
    public void turnTo(float yaw, float pitch) {
        this.vehicle.setYRot(yaw % 360.0F);
        this.vehicle.setXRot(pitch % 360.0F);
    }

    /** World position of a point given in vehicle-local coordinates. */
    public Vec3 toWorld(Vec3 local) {
        return toWorld(local.x, local.y, local.z);
    }

    /** World position of a point given in vehicle-local coordinates. */
    public Vec3 toWorld(double x, double y, double z) {
        AircraftEntity v = this.vehicle;
        return toWorld(x, y, z, v.getX(), v.getY(), v.getZ());
    }

    /** World position of a vehicle-local point, for the vehicle placed at {@code origin}. */
    public Vec3 toWorld(Vec3 local, Vec3 origin) {
        return toWorld(local.x, local.y, local.z, origin.x, origin.y, origin.z);
    }

    /** World position of a vehicle-local point, for the vehicle placed at (px, py, pz). */
    public Vec3 toWorld(double x, double y, double z, double px, double py, double pz) {
        AircraftEntity v = this.vehicle;
        return Rotations.rotate(x, y, z, -v.getYRot(), -v.getXRot(), -this.roll.get()).add(px, py, pz);
    }

    /** Takes the size from the definition; ignored until both values are positive. */
    public void setSize(float width, float height) {
        if (width > 0.0F && height > 0.0F && (width != this.width || height != this.height)) {
            this.width = width;
            this.height = height;
            this.vehicle.refreshDimensions();
        }
    }

    /** The definitions were read again: the hull takes the new size. */
    public void onDefinitionReloaded() {
        VehicleDefinition definition = this.vehicle.definition();
        if (definition != null) {
            this.setSize(definition.body().width(), definition.body().height());
            this.updateCullingRadius();
        }
    }

    /** The definition's size, or null to keep the entity type's. */
    @Nullable
    public EntityDimensions dimensions() {
        return this.width > 0.0F && this.height > 0.0F ? EntityDimensions.scalable(this.width, this.height) : null;
    }

    /** The hull's collision box as a plain box, without the part boxes the entity's own box tests too. */
    public AABB hullBox() {
        AABB bb = this.vehicle.getBoundingBox();
        return new AABB(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
    }

    /** The collision box grown to the culling radius around the origin. */
    public AABB cullingBox() {
        AircraftEntity v = this.vehicle;
        double r = this.cullingRadius;
        AABB box = v.getBoundingBox();
        return new AABB(Math.min(box.minX, v.getX() - r), Math.min(box.minY, v.getY() - r),
                Math.min(box.minZ, v.getZ() - r), Math.max(box.maxX, v.getX() + r),
                Math.max(box.maxY, v.getY() + r), Math.max(box.maxZ, v.getZ() + r));
    }

    /** Recomputes the culling radius from the definition's body size, part boxes and seats. */
    public void updateCullingRadius() {
        VehicleSpec info = this.vehicle.spec();
        float r = MIN_CULLING_RADIUS;
        if (info != null) {
            r = Math.max(r, Math.max(info.file().physics().bodySize().width(),
                    info.file().physics().bodySize().height()));
            for (PartBoundingBox bb : this.partBoxes.boxes()) {
                double d = Math.sqrt(bb.offsetX() * bb.offsetX() + bb.offsetY() * bb.offsetY() + bb.offsetZ() * bb.offsetZ())
                        + Math.sqrt(bb.halfWidth() * bb.halfWidth() + bb.halfHeight() * bb.halfHeight()
                        + bb.halfDepth() * bb.halfDepth());
                r = (float) Math.max(r, d);
            }
            for (SeatInfo seat : info.crew().seats()) {
                if (seat != null && seat.pos != null) {
                    r = (float) Math.max(r, seat.pos.length() + 2.0);
                }
            }
            // rotors, wings and weapons stick out past the hit boxes
            r = r * 1.5F + 2.0F;
        }
        this.cullingRadius = r;
    }
}
