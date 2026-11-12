package com.ivomens.mcheli.content.def.vehicle;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The drawn parts of {@code Components}, one record per part type. Most parts are a group of the body model
 * placed at {@code position}, turning about {@code rotation} (an axis, or angles for a fixed part), and named
 * {@code name}: the {@code $name} group of the model, or the {@code <vehicle>_<name>} model file. See
 * {@link PartSchemas} for the keys and their defaults.
 */
public final class Parts {

    private Parts() {
    }

    /** What every drawn part has: where it sits, its axis or angles, and its model group. */
    public interface Placed {

        Vec3 position();

        Vec3 rotation();

        String name();
    }

    /** A part that only has a place, such as a plane's nozzle or a tank's track roller. */
    public record Plain(Vec3 position, Vec3 rotation, String name) implements Placed {
    }

    /** A camera model that follows the gunner's view. */
    public record Camera(Vec3 position, Vec3 rotation, String name, boolean yawSync,
                         boolean pitchSync) implements Placed {
    }

    /**
     * A canopy that opens with the canopy key.
     *
     * @param maxRotation how far it opens, as a factor of 90 degrees
     */
    public record Canopy(Vec3 position, Vec3 rotation, String name, float maxRotation,
                         boolean sliding) implements Placed {
    }

    /** A hatch (or a light's hatch) that opens with the hatch key, {@code maxRotation} degrees. */
    public record Hatch(Vec3 position, Vec3 rotation, String name, float maxRotation,
                        boolean sliding) implements Placed {
    }

    /**
     * A bay door that opens while one of its weapons is selected.
     *
     * @param weapons     the weapon types it opens for, separated by {@code /}
     * @param maxRotation how far it opens, as a factor of 90 degrees
     */
    public record WeaponBay(Vec3 position, Vec3 rotation, String name, String weapons, float maxRotation,
                            boolean sliding) implements Placed {
    }

    /** A part that spins {@code speed} degrees per tick, with the engine or always. */
    public record Rotating(Vec3 position, Vec3 rotation, String name, float speed, boolean always) implements Placed {
    }

    /**
     * A wheel, or the steering wheel, that turns with speed.
     *
     * @param direction degrees the wheel turns while steering
     * @param pivot     the point the wheel steers around
     */
    public record Wheel(Vec3 position, Vec3 rotation, String name, float direction, Vec3 pivot) implements Placed {
    }

    /**
     * Landing gear that folds with the gear key.
     *
     * @param maxRotation            how far it folds, in degrees
     * @param articulatedRotation    a second axis it folds about, if it has one
     * @param maxArticulatedRotation how far it folds about the second axis, in degrees
     * @param slide                  how far it slides while folding, if it slides
     */
    public record LandingGear(Vec3 position, Vec3 rotation, String name, float maxRotation, boolean reverse,
                              boolean hatch, @Nullable Vec3 articulatedRotation, float maxArticulatedRotation,
                              @Nullable Vec3 slide) implements Placed {
    }

    /**
     * A gun or launcher model that aims with its weapons.
     *
     * @param weapons        the weapon types that move it
     * @param barrelRotation the barrels spin while firing
     * @param hideInGunner   it is hidden in gunner view
     * @param recoilBuffer   how far it recoils
     * @param children       barrels that move with it
     */
    public record Weapon(Vec3 position, Vec3 rotation, String name, List<String> weapons, boolean barrelRotation,
                         boolean missile, boolean hideInGunner, boolean yaw, boolean pitch, float recoilBuffer,
                         boolean turret, List<WeaponChild> children) implements Placed {

        public Weapon {
            weapons = List.copyOf(weapons);
            children = List.copyOf(children);
        }

        Weapon withChild(WeaponChild child) {
            List<WeaponChild> more = new ArrayList<>(this.children);
            more.add(child);
            return new Weapon(this.position, this.rotation, this.name, this.weapons, this.barrelRotation,
                    this.missile, this.hideInGunner, this.yaw, this.pitch, this.recoilBuffer, this.turret, more);
        }
    }

    /** A barrel of a {@link Weapon}; it is named after the weapon part and its place. */
    public record WeaponChild(Vec3 position, Vec3 rotation, boolean yaw, boolean pitch, float recoilBuffer) {
    }

    /**
     * A search light.
     *
     * @param colorStart        ARGB at the lamp
     * @param colorEnd          ARGB at the end of the beam
     * @param steeringRotation  how much a steering light turns with the steering
     */
    public record SearchLight(Vec3 position, int colorStart, int colorEnd, float height, float width, float yaw,
                              float pitch, float steeringRotation, boolean fixedDirection, boolean steering) {
    }

    /** A throttle lever that tilts {@code maxAngle} degrees and slides by {@code slide}. */
    public record Throttle(Vec3 position, Vec3 rotation, String name, Vec3 slide, float maxAngle) implements Placed {
    }

    /**
     * A crawler track, its links laid along the closed outline {@code points} (y, z in the vehicle's side
     * view).
     *
     * @param segmentLength the distance between links
     * @param offset        the track's x offset; positive is the right side
     */
    public record CrawlerTrack(Vec3 position, Vec3 rotation, String name, boolean reverse, float segmentLength,
                               float offset, List<TrackPoint> points) implements Placed {

        public CrawlerTrack {
            points = List.copyOf(points);
        }
    }

    /** A corner of a crawler track's outline. */
    public record TrackPoint(float x, float y) {
    }

    /** A helicopter rotor with {@code blades} blades, {@code bladeAngle} degrees apart. */
    public record HeliRotor(Vec3 position, Vec3 rotation, String name, int blades, int bladeAngle,
                            boolean foldable, boolean oldRenderer) implements Placed {
    }

    /** A rope a crew member slides down every {@code interval} ticks. */
    public record RepellingHook(Vec3 position, int interval) {
    }

    /**
     * A propeller or ship screw.
     *
     * @param tilt how far it tilts with VTOL, in degrees
     */
    public record Propeller(Vec3 position, Vec3 rotation, String name, float tilt,
                            List<Blade> blades) implements Placed {

        public Propeller {
            blades = List.copyOf(blades);
        }

        Propeller withBlade(Blade blade) {
            List<Blade> more = new ArrayList<>(this.blades);
            more.add(blade);
            return new Propeller(this.position, this.rotation, this.name, this.tilt, more);
        }
    }

    /**
     * {@code count} blades of a {@link Propeller}, {@code angle} degrees apart.
     *
     * @param angle null when the file leaves it out, which leaves the blade out of its propeller
     */
    public record Blade(Vec3 position, Vec3 rotation, String name, int count,
                        @Nullable Integer angle) implements Placed {
    }

    /** A wing that folds or tilts {@code maxRotation} degrees, with its pylons. */
    public record Wing(Vec3 position, Vec3 rotation, String name, float maxRotation,
                       List<Pylon> pylons) implements Placed {

        public Wing {
            pylons = List.copyOf(pylons);
        }

        Wing withPylon(Pylon pylon) {
            List<Pylon> more = new ArrayList<>(this.pylons);
            more.add(pylon);
            return new Wing(this.position, this.rotation, this.name, this.maxRotation, more);
        }
    }

    /** A pylon under a {@link Wing} that turns {@code maxRotation} degrees with it. */
    public record Pylon(Vec3 position, Vec3 rotation, String name, float maxRotation) implements Placed {
    }

    /**
     * An afterburner flame drawn behind a plane.
     *
     * @param texture the flame texture's name prefix; its frames are numbered after it
     * @param yaw     degrees the flame turns towards the side
     * @param delay   ticks between frames
     */
    public record ExhaustFlame(Vec3 position, Vec3 rotation, String name, String texture, float yaw,
                               int delay) implements Placed {
    }

    /**
     * A part of a ground vehicle's turret.
     *
     * @param type     what moves it, by the number of a {@link TurretPartType}; legacy TXT files may give
     *                 numbers no type has
     * @param children parts that move with it
     */
    public record TurretPart(Vec3 position, Vec3 rotation, String name, boolean drawFirstPerson, boolean yaw,
                             boolean pitch, int type, float recoilBuffer, List<TurretPart> children) implements Placed {

        public TurretPart {
            children = List.copyOf(children);
        }

        TurretPart withChild(TurretPart child) {
            List<TurretPart> more = new ArrayList<>(this.children);
            more.add(child);
            return new TurretPart(this.position, this.rotation, this.name, this.drawFirstPerson, this.yaw,
                    this.pitch, this.type, this.recoilBuffer, more);
        }
    }

    /** What moves a {@link TurretPart}; the order is the number legacy TXT files use. */
    public enum TurretPartType {
        NORMAL,
        ROTATES_WEAPON,
        RECOILS_WEAPON,
        TYPE_3
    }
}
