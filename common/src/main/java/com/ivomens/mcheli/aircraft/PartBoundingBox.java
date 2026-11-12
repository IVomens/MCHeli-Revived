package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.content.def.vehicle.HitBoxType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Oriented part box of an aircraft (wing, turret, armour tile...). Each aircraft keeps its own
 * copies of the info's boxes and moves them with its position and attitude every tick; the
 * enclosing AABB is used for the cheap broad-phase test, the oriented box for the exact one.
 */
public class PartBoundingBox {

    /** Enclosing axis-aligned box, for broad-phase tests. */
    private AABB boundingBox;
    /** The enclosing box of the previous update (player platform carrying uses it). */
    private AABB backupBoundingBox;

    /** Box center relative to the aircraft origin, in aircraft-local coordinates. */
    private double offsetX;
    private double offsetY;
    private double offsetZ;

    /** Size along the local X (width), Y (height) and Z (widthZ) axes. */
    private float width;
    private float widthZ;
    private float height;
    private float halfWidth;
    private float halfHeight;
    private float halfDepth;

    /** Offset rotated into world orientation. */
    private Vec3 rotatedOffset;
    /** Current and previous world center. */
    private Vec3 nowPos;
    private Vec3 prevPos;

    /** Damage multiplier for hits on this box. */
    private float damageFactor;
    /** Turret boxes also turn with the turret's local rotation. */
    private HitBoxType boundingBoxType = HitBoxType.DEFAULT;
    private String name = "";

    /** Explosive reactive armor: the tile pops on a hit above {@link #eraMinDamage}, see {@link ReactiveArmor}. */
    private boolean isERA;
    /** Explosion size when the tile pops (0 = none). */
    private float eraExplosion;
    private float eraMinDamage;
    /** False once the tile has popped; popped tiles are ignored for collision and hits. */
    private boolean eraActive = true;

    /** Attitude of the last update, in degrees. */
    private float rotationYaw;
    private float rotationPitch;
    private float rotationRoll;

    /** Local axes in world space, now and at the previous update. */
    private Vec3 axisX = new Vec3(1.0D, 0.0D, 0.0D);
    private Vec3 axisY = new Vec3(0.0D, 1.0D, 0.0D);
    private Vec3 axisZ = new Vec3(0.0D, 0.0D, 1.0D);
    private Vec3 prevAxisX = new Vec3(1.0D, 0.0D, 0.0D);
    private Vec3 prevAxisY = new Vec3(0.0D, 1.0D, 0.0D);
    private Vec3 prevAxisZ = new Vec3(0.0D, 0.0D, 1.0D);

    /** World center (same as {@link #nowPos}). */
    private Vec3 center;

    /** Extra rotation of turret boxes. */
    private float localRotYaw;
    private float localRotPitch;
    private float localRotRoll;

    /** Told whenever {@link #updatePosition} moves the box; copies start without a listener. */
    private transient Runnable movedListener = () -> {
    };

    // scratch objects for updatePosition (each aircraft owns its own box copies, so no sharing)
    private final Quaternionf scratchOrientation = new Quaternionf();
    private final Vector3f scratchVector = new Vector3f();

    public PartBoundingBox(double x, double y, double z, float w, float h, float df) {
        this(x, y, z, w, h, w, df);
    }

    public PartBoundingBox(double posX, double posY, double posZ,
                           float widthX, float height, float widthZ, float df) {
        this.offsetX = posX;
        this.offsetY = posY;
        this.offsetZ = posZ;
        this.width = widthX;
        this.widthZ = widthZ;
        this.height = height;

        this.halfWidth = widthX / 2.0F;
        this.halfHeight = height / 2.0F;
        this.halfDepth = widthZ / 2.0F;

        this.damageFactor = df;

        this.center = new Vec3(posX, posY, posZ);
        this.nowPos = new Vec3(posX, posY, posZ);
        this.prevPos = new Vec3(posX, posY, posZ);

        this.boundingBox = new AABB(
                posX - halfWidth, posY - halfHeight, posZ - halfDepth,
                posX + halfWidth, posY + halfHeight, posZ + halfDepth);
        this.backupBoundingBox = this.boundingBox;
    }

    /** What kind of box this is; turret boxes also turn with the turret. */
    public void setBoundingBoxType(HitBoxType type) {
        this.boundingBoxType = type;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Makes this box an explosive reactive armour tile that pops on hits of at least {@code minDamage}. */
    public void setReactiveArmor(boolean reactive, float explosion, float minDamage) {
        this.isERA = reactive;
        this.eraExplosion = explosion;
        this.eraMinDamage = minDamage;
    }

    /** Pops (false) or restores (true) a reactive armour tile. */
    public void setEraActive(boolean active) {
        this.eraActive = active;
    }

    public AABB boundingBox() {
        return this.boundingBox;
    }

    public Vec3 axisX() {
        return this.axisX;
    }

    public Vec3 axisY() {
        return this.axisY;
    }

    public Vec3 axisZ() {
        return this.axisZ;
    }

    public AABB backupBoundingBox() {
        return this.backupBoundingBox;
    }

    public HitBoxType boundingBoxType() {
        return this.boundingBoxType;
    }

    public Vec3 center() {
        return this.center;
    }

    public float damageFactor() {
        return this.damageFactor;
    }

    public boolean eraActive() {
        return this.eraActive;
    }

    public float eraExplosion() {
        return this.eraExplosion;
    }

    public float eraMinDamage() {
        return this.eraMinDamage;
    }

    public float halfDepth() {
        return this.halfDepth;
    }

    public float halfHeight() {
        return this.halfHeight;
    }

    public float halfWidth() {
        return this.halfWidth;
    }

    public float height() {
        return this.height;
    }

    public boolean isERA() {
        return this.isERA;
    }

    public float localRotPitch() {
        return this.localRotPitch;
    }

    public float localRotRoll() {
        return this.localRotRoll;
    }

    public float localRotYaw() {
        return this.localRotYaw;
    }

    public Vec3 nowPos() {
        return this.nowPos;
    }

    public double offsetX() {
        return this.offsetX;
    }

    public double offsetY() {
        return this.offsetY;
    }

    public double offsetZ() {
        return this.offsetZ;
    }

    public Vec3 prevAxisX() {
        return this.prevAxisX;
    }

    public Vec3 prevAxisY() {
        return this.prevAxisY;
    }

    public Vec3 prevAxisZ() {
        return this.prevAxisZ;
    }

    public Vec3 prevPos() {
        return this.prevPos;
    }

    public Vec3 rotatedOffset() {
        return this.rotatedOffset;
    }

    public float rotationPitch() {
        return this.rotationPitch;
    }

    public float rotationRoll() {
        return this.rotationRoll;
    }

    public float rotationYaw() {
        return this.rotationYaw;
    }

    public float width() {
        return this.width;
    }

    public float widthZ() {
        return this.widthZ;
    }

    /** Per-entity copies of a definition's part boxes: each aircraft moves and damages its own. */
    public static PartBoundingBox[] copyAll(@Nullable List<PartBoundingBox> boxes) {
        if (boxes == null) {
            return new PartBoundingBox[0];
        }
        PartBoundingBox[] copies = new PartBoundingBox[boxes.size()];
        for (int i = 0; i < copies.length; i++) {
            copies[i] = boxes.get(i).copy();
        }
        return copies;
    }

    /** Deep copy, so every aircraft can move and damage its own boxes (vectors and AABBs are immutable). */
    public PartBoundingBox copy() {
        PartBoundingBox bb = new PartBoundingBox(this.offsetX, this.offsetY, this.offsetZ,
                this.width, this.height, this.widthZ, this.damageFactor);
        bb.rotationYaw = this.rotationYaw;
        bb.rotationPitch = this.rotationPitch;
        bb.rotationRoll = this.rotationRoll;
        bb.axisX = this.axisX;
        bb.axisY = this.axisY;
        bb.axisZ = this.axisZ;
        bb.prevAxisX = this.prevAxisX;
        bb.prevAxisY = this.prevAxisY;
        bb.prevAxisZ = this.prevAxisZ;
        bb.center = this.center;
        bb.halfWidth = this.halfWidth;
        bb.halfHeight = this.halfHeight;
        bb.halfDepth = this.halfDepth;
        bb.rotatedOffset = this.rotatedOffset;
        bb.nowPos = this.nowPos;
        bb.prevPos = this.prevPos;
        bb.boundingBox = this.boundingBox;
        bb.backupBoundingBox = this.backupBoundingBox;
        bb.boundingBoxType = this.boundingBoxType;
        bb.name = this.name;
        bb.isERA = this.isERA;
        bb.eraExplosion = this.eraExplosion;
        bb.eraMinDamage = this.eraMinDamage;
        bb.eraActive = this.eraActive;
        return bb;
    }

    /**
     * Moves the box with the aircraft. Angles are in degrees, applied in the same order as
     * Rotations.rotate.
     */
    public void updatePosition(double posX, double posY, double posZ, float yaw, float pitch, float roll) {
        this.prevAxisX = this.axisX;
        this.prevAxisY = this.axisY;
        this.prevAxisZ = this.axisZ;
        this.rotationYaw = yaw;
        this.rotationPitch = pitch;
        this.rotationRoll = roll;

        float boxYaw = yaw;
        float boxPitch = pitch;
        float boxRoll = roll;
        if (this.boundingBoxType == HitBoxType.TURRET) {
            boxYaw += this.localRotYaw;
            boxPitch += this.localRotPitch;
            boxRoll += this.localRotRoll;
        }

        // same rotation as Rotations.orientation(-yaw, -pitch, -roll), without allocating
        Quaternionf orientation = this.scratchOrientation.identity()
                .rotateY((float) Math.toRadians(-boxYaw))
                .rotateX((float) Math.toRadians(boxPitch))
                .rotateZ((float) Math.toRadians(boxRoll));

        this.rotatedOffset = rotate(orientation, this.offsetX, this.offsetY, this.offsetZ);
        this.prevPos = this.nowPos;
        this.nowPos = new Vec3(posX + this.rotatedOffset.x, posY + this.rotatedOffset.y, posZ + this.rotatedOffset.z);
        this.center = this.nowPos;
        this.axisX = rotate(orientation, 1.0D, 0.0D, 0.0D);
        this.axisY = rotate(orientation, 0.0D, 1.0D, 0.0D);
        this.axisZ = rotate(orientation, 0.0D, 0.0D, 1.0D);

        this.backupBoundingBox = this.boundingBox;
        this.boundingBox = this.enclosingBox(orientation, posX, posY, posZ);
        this.movedListener.run();
    }

    /** Calls {@code listener} after every {@link #updatePosition}, which is the only thing that moves the box. */
    public void setMovedListener(Runnable listener) {
        this.movedListener = listener;
    }

    /** The world-axis box around this box's eight corners turned by {@code orientation} about the given origin. */
    private AABB enclosingBox(Quaternionf orientation, double posX, double posY, double posZ) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        Vector3f corner = this.scratchVector;
        for (int xi = -1; xi <= 1; xi += 2) {
            for (int yi = -1; yi <= 1; yi += 2) {
                for (int zi = -1; zi <= 1; zi += 2) {
                    orientation.transform(corner.set((float) (this.offsetX + xi * this.halfWidth),
                            (float) (this.offsetY + yi * this.halfHeight), (float) (this.offsetZ + zi * this.halfDepth)));
                    double px = posX + corner.x;
                    double py = posY + corner.y;
                    double pz = posZ + corner.z;
                    minX = Math.min(minX, px);
                    minY = Math.min(minY, py);
                    minZ = Math.min(minZ, pz);
                    maxX = Math.max(maxX, px);
                    maxY = Math.max(maxY, py);
                    maxZ = Math.max(maxZ, pz);
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private Vec3 rotate(Quaternionf orientation, double x, double y, double z) {
        Vector3f rotated = orientation.transform(this.scratchVector.set((float) x, (float) y, (float) z));
        return new Vec3(rotated.x, rotated.y, rotated.z);
    }

    // scratch buffers for the separating axis tests
    private final double[][] satR = new double[3][3];
    private final double[][] satAbsR = new double[3][3];
    private final double[] satA = new double[3];
    private final double[] satB = new double[3];
    private final double[] satT = new double[3];

    /** Exact oriented box against oriented box test (separating axis theorem). */
    public boolean intersectsOBB(PartBoundingBox other) {
        Vec3 a0 = this.axisX, a1 = this.axisY, a2 = this.axisZ;
        Vec3 b0 = other.axisX, b1 = other.axisY, b2 = other.axisZ;
        double[][] R = this.satR;
        Vec3[] A = {a0, a1, a2};
        Vec3[] B = {b0, b1, b2};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                R[i][j] = A[i].dot(B[j]);
            }
        }
        double dx = other.center.x - this.center.x;
        double dy = other.center.y - this.center.y;
        double dz = other.center.z - this.center.z;
        this.satT[0] = dx * a0.x + dy * a0.y + dz * a0.z;
        this.satT[1] = dx * a1.x + dy * a1.y + dz * a1.z;
        this.satT[2] = dx * a2.x + dy * a2.y + dz * a2.z;
        this.satA[0] = this.halfWidth;
        this.satA[1] = this.halfHeight;
        this.satA[2] = this.halfDepth;
        this.satB[0] = other.halfWidth;
        this.satB[1] = other.halfHeight;
        this.satB[2] = other.halfDepth;
        return SeparatingAxes.overlap(this.satR, this.satAbsR, this.satA, this.satB, this.satT);
    }

    /** Exact test against an axis-aligned box, treated as an oriented box with the world axes. */
    public boolean intersectsAABB(AABB aabb) {
        double cx = (aabb.minX + aabb.maxX) * 0.5;
        double cy = (aabb.minY + aabb.maxY) * 0.5;
        double cz = (aabb.minZ + aabb.maxZ) * 0.5;
        double[][] R = this.satR;
        Vec3 a0 = this.axisX, a1 = this.axisY, a2 = this.axisZ;
        // R[i][j] = A_i . e_j
        R[0][0] = a0.x; R[0][1] = a0.y; R[0][2] = a0.z;
        R[1][0] = a1.x; R[1][1] = a1.y; R[1][2] = a1.z;
        R[2][0] = a2.x; R[2][1] = a2.y; R[2][2] = a2.z;
        double dx = cx - this.center.x;
        double dy = cy - this.center.y;
        double dz = cz - this.center.z;
        this.satT[0] = dx * a0.x + dy * a0.y + dz * a0.z;
        this.satT[1] = dx * a1.x + dy * a1.y + dz * a1.z;
        this.satT[2] = dx * a2.x + dy * a2.y + dz * a2.z;
        this.satA[0] = this.halfWidth;
        this.satA[1] = this.halfHeight;
        this.satA[2] = this.halfDepth;
        this.satB[0] = (aabb.maxX - aabb.minX) * 0.5;
        this.satB[1] = (aabb.maxY - aabb.minY) * 0.5;
        this.satB[2] = (aabb.maxZ - aabb.minZ) * 0.5;
        return SeparatingAxes.overlap(this.satR, this.satAbsR, this.satA, this.satB, this.satT);
    }

    @Override
    public String toString() {
        return "PartBoundingBox{" +
                "boundingBox=" + boundingBox +
                ", backupBoundingBox=" + backupBoundingBox +
                ", offsetX=" + offsetX +
                ", offsetY=" + offsetY +
                ", offsetZ=" + offsetZ +
                ", width=" + width +
                ", widthZ=" + widthZ +
                ", height=" + height +
                ", halfWidth=" + halfWidth +
                ", halfHeight=" + halfHeight +
                ", halfDepth=" + halfDepth +
                ", rotatedOffset=" + rotatedOffset +
                ", nowPos=" + nowPos +
                ", prevPos=" + prevPos +
                ", damageFactor=" + damageFactor +
                ", boundingBoxType=" + boundingBoxType +
                ", name='" + name + '\'' +
                ", rotationYaw=" + rotationYaw +
                ", rotationPitch=" + rotationPitch +
                ", rotationRoll=" + rotationRoll +
                ", axisX=" + axisX +
                ", axisY=" + axisY +
                ", axisZ=" + axisZ +
                ", center=" + center +
                ", localRotYaw=" + localRotYaw +
                ", localRotPitch=" + localRotPitch +
                ", localRotRoll=" + localRotRoll +
                '}';
    }

}
