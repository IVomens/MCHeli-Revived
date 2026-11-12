package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.vehicle.client.VehicleModels;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Everything needed to draw one vehicle's model in one frame, read from the interpolated entity and
 * its components by {@link VehicleSnapshots}. The model renderers ({@link VehicleModelRenderer}) read
 * only this, never the entity, so both backends draw exactly the same state and a snapshot can be
 * posed off the render thread.
 *
 * @param info     the vehicle definition
 * @param models   the definition's models; empty while they load
 * @param hull     where the airframe is and how it is turned
 * @param texture  the texture to draw with: the livery, the definition's texture or the thermal one
 * @param tint     the colour the model is multiplied with
 * @param light    packed block and sky light
 * @param detail   how far away the vehicle is, which decides the level of detail
 * @param parts    the animation state of every moving part
 */
public record VehicleRenderSnapshot(VehicleSpec info, VehicleModels models, HullPose hull, ResourceLocation texture,
                                    Tint tint, int light, Detail detail, PartPoses parts) {

    /** The same frame at another level of detail. */
    public VehicleRenderSnapshot withDetail(Detail other) {
        return new VehicleRenderSnapshot(this.info, this.models, this.hull, this.texture, this.tint, this.light, other,
                this.parts);
    }

    /**
     * The airframe pose: interpolated position and orientation in degrees. Ground vehicles do not
     * roll ({@code rolls} is false), whatever their roll value says.
     */
    public record HullPose(double x, double y, double z, float yaw, float pitch, float roll, boolean rolls) {
    }

    /** Colour channels from 0 to 1. */
    public record Tint(float red, float green, float blue, float alpha) {

        /** ARGB with each channel truncated to a byte, as vertex buffers store it. */
        public int argb() {
            return ((int) (this.alpha * 255.0F) & 0xFF) << 24
                    | ((int) (this.red * 255.0F) & 0xFF) << 16
                    | ((int) (this.green * 255.0F) & 0xFF) << 8
                    | ((int) (this.blue * 255.0F) & 0xFF);
        }
    }

    /**
     * Distance to the camera and the screen size it gives: {@code pixelsPerBlock} is how many pixels
     * one block covers at the vehicle, infinite for full detail (GUI previews, the camera inside).
     */
    public record Detail(double cameraDistanceSq, float pixelsPerBlock) {

        public static final Detail FULL = new Detail(0.0, Float.POSITIVE_INFINITY);

        /** Model units one screen pixel spans, for picking a reduced mesh; 0 for full detail. */
        public float unitsPerPixel() {
            return this.pixelsPerBlock == Float.POSITIVE_INFINITY ? 0.0F : 1.0F / this.pixelsPerBlock;
        }
    }

    /**
     * The animation state of the parts, interpolated for the frame. Lists follow the order of the
     * matching parts in the definition.
     *
     * @param intactEra     per reactive armour box: whether its tile is still there
     * @param weapons       per {@code partWeapon}
     * @param spinningParts per spinning part, its angle
     * @param hatch         the hatches, or null when there are none
     * @param canopy        the canopies, or null when there are none
     * @param trackRollers  roller angle by side (0 left, 1 right)
     * @param trackOffsets  how far the links have moved along the track, by side; parked tracks use the static mesh
     * @param staticTracks  whether the crawler tracks are drawn as one static mesh (parked or far away)
     * @param steering      wheel steering angle
     * @param wheelRoll     road wheel rolling angle
     * @param lightHatch    how far the light hatches are open, 0 to 1
     * @param throttle      throttle lever position, 0 to 1
     * @param cameraYaw     camera turret yaw relative to the airframe
     * @param cameraPitch   camera turret pitch relative to the airframe
     * @param landingGear   the landing gear, or null when there is none
     * @param weaponBays    per weapon bay
     * @param rotors        helicopter rotors: per rotor, the angle of each blade
     * @param plane         plane and ship parts; {@link PlaneParts#NONE} for other vehicles
     * @param ground        ground vehicle part tree; null for other vehicles
     */
    public record PartPoses(List<Boolean> intactEra, List<WeaponPose> weapons, Angles spinningParts,
                            @Nullable Swing hatch, @Nullable Swing canopy, Angles trackRollers,
                            TrackOffsets trackOffsets, boolean staticTracks, float steering, float wheelRoll,
                            float lightHatch, float throttle, float cameraYaw, float cameraPitch,
                            @Nullable LandingGear landingGear, List<Swing> weaponBays, List<Angles> rotors,
                            PlaneParts plane, @Nullable GroundParts ground) {
    }

    /** A part that either swings by {@code angle} degrees or slides by {@code slide} (0 to 1). */
    public record Swing(float angle, float slide) {
    }

    /** How far the links of the left and right crawler tracks have moved along their outline. */
    public record TrackOffsets(double left, double right) {

        public static final TrackOffsets AT_REST = new TrackOffsets(0.0, 0.0);

        public double side(int side) {
            return side == 0 ? this.left : this.right;
        }
    }

    /**
     * The landing gear: the gear angle, the angle of parts that move the other way, the gear doors
     * and the slide fraction.
     */
    public record LandingGear(float gear, float reversed, float door, float slide) {
    }

    /**
     * A weapon part on the airframe.
     *
     * @param hidden    not drawn: a first-person weapon the client player is using
     * @param loaded    drawn: always, except a missile that has been fired and is reloading
     * @param turretYaw the turret's yaw relative to the airframe (turret parts only)
     * @param yaw       the weapon's own yaw
     * @param pitch     the weapon's pitch, before a sideways mount flips it
     * @param mount     the weapon set the part belongs to, or null when the name matches none
     */
    public record WeaponPose(boolean hidden, boolean loaded, float turretYaw, float yaw, float pitch,
                             @Nullable MountPose mount) {
    }

    /**
     * The weapon set state a weapon part follows.
     *
     * @param defaultYaw          the set's resting yaw
     * @param turretRelativeYaw   turret yaw minus the set's turret yaw
     * @param recoil              recoil of the part (the strongest of its linked weapons)
     * @param barrel              barrel spin angle
     * @param childRecoil         recoil of each child part
     */
    public record MountPose(float defaultYaw, float turretRelativeYaw, float recoil, float barrel,
                              Angles childRecoil) {
    }

    /**
     * Nozzles, folding wings and tilt rotors.
     *
     * @param nozzles       whether nozzles are drawn
     * @param wings         whether folding wings are drawn
     * @param rotors        whether tilt rotors are drawn
     * @param nozzleAngle   nozzle (and rotor tilt) angle
     * @param wingSweep     wing fold angle
     * @param propellerSpin propeller angle
     */
    public record PlaneParts(boolean nozzles, boolean wings, boolean rotors, float nozzleAngle, float wingSweep,
                             float propellerSpin) {

        public static final PlaneParts NONE = new PlaneParts(false, false, false, 0.0F, 0.0F, 0.0F);
    }

    /**
     * The aimed part tree of a ground vehicle.
     *
     * @param aimYaw          where the gunner looks, in world yaw
     * @param aimPitch        where the gunner looks, clamped to the vehicle's pitch range
     * @param barrel          barrel spin angle
     * @param hideFirstPerson whether parts not drawn in first person are hidden (the client player aims)
     * @param recoil          per part, in tree order: its recoil
     * @param loaded          per part, in tree order: whether its ammunition is drawn
     */
    public record GroundParts(float aimYaw, float aimPitch, float barrel, boolean hideFirstPerson, Angles recoil,
                              List<Boolean> loaded) {
    }
}
