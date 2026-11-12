package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.content.def.vehicle.Parts;
import com.ivomens.mcheli.entity.McheliEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * The parts of a vehicle as run-time code reads them, built once per vehicle from its {@code VehicleFile}: the
 * drawn parts with the values the renderer derives from the file (opening factors of 90 degrees, crawler
 * track links, a search light's cone), and the seats' camera positions, mounted weapons and countermeasure
 * launchers. Nothing here changes after it is built.
 */
public final class VehicleParts {

    private static final float RIGHT_ANGLE = 90.0F;

    private VehicleParts() {
    }

    /** A camera model that turns with the gunner's view. */
    public static class Camera extends DrawnPart {

        public final boolean yawSync;
        public final boolean pitchSync;

        public Camera(DrawnPart base, boolean yawSync, boolean pitchSync) {
            super(base);
            this.yawSync = yawSync;
            this.pitchSync = pitchSync;
        }
    }

    /**
     * A camera position. The position is given from the model's origin; the entity sits
     * {@link McheliEntity#GLOBAL_Y_OFFSET} lower, so the constructor raises it by that much.
     */
    public record CameraPosition(Vec3 pos, boolean fixRot, float yaw, float pitch) {

        public CameraPosition(Vec3 pos, boolean fixRot, float yaw, float pitch) {
            this.pos = pos.add(0, McheliEntity.GLOBAL_Y_OFFSET, 0);
            this.fixRot = fixRot;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    public static class Canopy extends DrawnPart {

        public final float maxRotFactor;
        public final boolean isSlide;

        public Canopy(DrawnPart base, float maxRotationFactor, boolean isSlide) {
            super(base);
            this.maxRotFactor = maxRotationFactor;
            this.isSlide = isSlide;
        }
    }

    /** A crawler track with its links laid along its outline; see {@link TrackLinks}. */
    public static class CrawlerTrack extends DrawnPart {

        public final float len;
        public final double[] cx;
        public final double[] cy;
        public final List<CrawlerTrackPrm> lp;
        public final float z;
        /** 1 for a track on the right, 0 on the left. */
        public final int side;

        public CrawlerTrack(DrawnPart base, float len, double[] cx, double[] cy, List<CrawlerTrackPrm> links,
                            float z) {
            super(base);
            this.len = len;
            this.cx = cx;
            this.cy = cy;
            this.lp = List.copyOf(links);
            this.z = z;
            this.side = z >= 0.0F ? 1 : 0;
        }
    }

    /** A link of a crawler track: where it sits and the angle it lies at. */
    public static class CrawlerTrackPrm {

        public final float x;
        public final float y;
        public final float nx;
        public final float ny;
        public final float r;

        public CrawlerTrackPrm(float x, float y, float r) {
            this.x = x;
            this.y = y;
            this.nx = 0.0F;
            this.ny = 0.0F;
            this.r = r;
        }
    }

    /** A part drawn from the body model: its place, its axis or angles, and its group name. */
    public static class DrawnPart {

        public final Vec3 pos;
        public final Vec3 rot;
        /** The {@code $name} group of the body model, or the {@code <vehicle>_<name>} model file. */
        public final String modelName;

        public DrawnPart(Vec3 pos, Vec3 rot, String modelName) {
            this.pos = pos;
            this.rot = rot;
            this.modelName = modelName;
        }

        public DrawnPart(DrawnPart other) {
            this.pos = other.pos;
            this.rot = other.rot;
            this.modelName = other.modelName;
        }
    }

    /** Where flares leave the vehicle and the flare patterns, by their legacy numbers. */
    public static class Flare {

        public final Vec3 pos;
        public final int[] types;

        public Flare(Vec3 pos, int[] types) {
            this.pos = pos;
            this.types = types;
        }

        @Override
        public String toString() {
            return "Flare{pos=" + this.pos + ", types=" + Arrays.toString(this.types) + '}';
        }
    }

    public static class Hatch extends DrawnPart {

        public final float maxRotFactor;
        public final float maxRot;
        public final boolean isSlide;

        public Hatch(DrawnPart base, float maxRot, boolean isSlide) {
            super(base);
            this.maxRot = maxRot;
            this.maxRotFactor = maxRot / RIGHT_ANGLE;
            this.isSlide = isSlide;
        }
    }

    public static class LandingGear extends DrawnPart {

        public final float maxRotFactor;
        public final boolean reverse;
        public final boolean hatch;
        @Nullable
        public final Vec3 slide;
        public final boolean enableRot2;
        @Nullable
        public final Vec3 rot2;
        public final float maxRotFactor2;

        public LandingGear(DrawnPart base, float maxRotFactor, boolean reverse, boolean hatch, @Nullable Vec3 slide,
                           boolean enableRot2, @Nullable Vec3 rot2, float maxRotFactor2) {
            super(base);
            this.maxRotFactor = maxRotFactor;
            this.reverse = reverse;
            this.hatch = hatch;
            this.slide = slide;
            this.enableRot2 = enableRot2;
            this.rot2 = rot2;
            this.maxRotFactor2 = maxRotFactor2;
        }
    }

    public static class PartWeapon extends DrawnPart {

        public final String[] name;
        public final boolean rotBarrel;
        public final boolean isMissile;
        public final boolean hideGM;
        public final boolean yaw;
        public final boolean pitch;
        public final float recoilBuf;
        public final boolean turret;
        public final List<PartWeaponChild> child;

        public PartWeapon(DrawnPart base, String[] name, boolean rotBarrel, boolean isMissile, boolean hideGM,
                          boolean yaw, boolean pitch, float recoilBuf, boolean turret, List<PartWeaponChild> child) {
            super(base);
            this.name = name;
            this.rotBarrel = rotBarrel;
            this.isMissile = isMissile;
            this.hideGM = hideGM;
            this.yaw = yaw;
            this.pitch = pitch;
            this.recoilBuf = recoilBuf;
            this.turret = turret;
            this.child = List.copyOf(child);
        }
    }

    public static class PartWeaponChild extends DrawnPart {

        public final String[] name;
        public final boolean yaw;
        public final boolean pitch;
        public final float recoilBuf;

        public PartWeaponChild(Vec3 pos, Vec3 rot, String modelName, String[] name, boolean yaw, boolean pitch,
                               float recoilBuf) {
            super(pos, rot, modelName);
            this.name = name;
            this.yaw = yaw;
            this.pitch = pitch;
            this.recoilBuf = recoilBuf;
        }
    }

    public static class PartWheel extends DrawnPart {

        public final float rotDir;
        public final Vec3 pos2;

        public PartWheel(DrawnPart base, float rotDir, Vec3 pos2) {
            super(base);
            this.rotDir = rotDir;
            this.pos2 = pos2;
        }
    }

    public record ParticleSplash(int num, float acceleration, float size, Vec3 pos, int age, float motionY,
                                 float gravity) {
    }

    public record RepellingHook(Vec3 pos, int interval) {
    }

    public record RideRack(String name, int rackID) {
    }

    public static class RotPart extends DrawnPart {

        public final float rotSpeed;
        public final boolean rotAlways;

        public RotPart(DrawnPart base, float rotSpeed, boolean rotAlways) {
            super(base);
            this.rotSpeed = rotSpeed;
            this.rotAlways = rotAlways;
        }
    }

    public static class SearchLight {

        public final Vec3 pos;
        public final int colorStart;
        public final int colorEnd;
        public final float height;
        public final float width;
        public final float angle;
        public final boolean fixDir;
        public final float yaw;
        public final float pitch;
        public final boolean steering;
        public final float stRot;

        public SearchLight(Vec3 pos, int colorStart, int colorEnd, float height, float width, boolean fixDir,
                           float yaw, float pitch, boolean steering, float stRot) {
            this.colorStart = colorStart;
            this.colorEnd = colorEnd;
            this.pos = pos;
            this.height = height;
            this.width = width;
            this.angle = (float) (Math.atan2(width / 2.0F, height) * 180.0 / Math.PI);
            this.fixDir = fixDir;
            this.steering = steering;
            this.yaw = yaw;
            this.pitch = pitch;
            this.stRot = stRot;
        }
    }

    public static class Throttle extends DrawnPart {

        public final Vec3 slide;
        public final float rot2;

        public Throttle(DrawnPart base, Vec3 slide, float rot2) {
            super(base);
            this.slide = slide;
            this.rot2 = rot2;
        }
    }

    public static class TrackRoller extends DrawnPart {

        /** 1 for a roller on the right, 0 on the left. */
        public final int side;

        public TrackRoller(DrawnPart base) {
            super(base);
            this.side = base.pos.x >= 0 ? 1 : 0;
        }
    }

    /** A mounted weapon, as run-time code reads it; its position is raised by the entity offset. */
    public static class Weapon {

        public final Vec3 pos;
        public final float yaw;
        public final float pitch;
        public final boolean canUsePilot;
        public final int seatID;
        public final float defaultYaw;
        public final float minYaw;
        public final float maxYaw;
        public final float minPitch;
        public final float maxPitch;
        public final boolean turret;

        public Weapon(float x, float y, float z, float yaw, float pitch, boolean canUsePilot, int seatId,
                      float defaultYaw, float minYaw, float maxYaw, float minPitch, float maxPitch, boolean turret) {
            this.pos = new Vec3(x, y + McheliEntity.GLOBAL_Y_OFFSET, z);
            this.yaw = yaw;
            this.pitch = pitch;
            this.canUsePilot = canUsePilot;
            this.seatID = seatId;
            this.defaultYaw = defaultYaw;
            this.minYaw = minYaw;
            this.maxYaw = maxYaw;
            this.minPitch = minPitch;
            this.maxPitch = maxPitch;
            this.turret = turret;
        }
    }

    public static class WeaponBay extends DrawnPart {

        public final float maxRotFactor;
        public final boolean isSlide;
        public final String weaponName;
        /** Ids of the weapon sets named in {@link #weaponName}. */
        public final Integer[] weaponIds;

        public WeaponBay(DrawnPart base, float maxRotFactor, boolean isSlide, String weaponName,
                         Integer[] weaponIds) {
            super(base);
            this.maxRotFactor = maxRotFactor;
            this.isSlide = isSlide;
            this.weaponName = weaponName;
            this.weaponIds = weaponIds;
        }
    }

    /** Mounts of one weapon type that the crew selects together. */
    public static class WeaponSet {

        public final String type;
        public final List<Weapon> weapons;

        public WeaponSet(String type, List<Weapon> weapons) {
            this.type = type;
            this.weapons = List.copyOf(weapons);
        }
    }

    public record Wheel(Vec3 pos, float size) {
    }

    /** Chaff: the radar counterpart of flares. */
    public static class Chaff {

        public final Vec3 pos = Vec3.ZERO;
    }

    /** A rotor with its blades. */
    public static class HeliRotor extends DrawnPart {

        public final int bladeNum;
        public final int bladeRot;
        public final boolean haveFoldFunc;
        public final boolean oldRenderMethod;

        public HeliRotor(Parts.HeliRotor rotor) {
            super(rotor.position(), rotor.rotation(), rotor.name());
            this.bladeNum = rotor.blades();
            this.bladeRot = rotor.bladeAngle();
            this.haveFoldFunc = rotor.foldable();
            this.oldRenderMethod = rotor.oldRenderer();
        }
    }

    /** {@code numBlade} blades of a propeller, {@code rotBlade} degrees apart. */
    public static class Blade extends DrawnPart {

        public final int numBlade;
        public final int rotBlade;

        public Blade(Parts.Blade blade) {
            super(blade.position(), blade.rotation(), blade.name());
            this.numBlade = blade.count();
            this.rotBlade = blade.angle();
        }
    }

    /** A pylon that turns with its wing. */
    public static class Pylon extends DrawnPart {

        public final float maxRotFactor;
        public final float maxRot;

        public Pylon(Parts.Pylon pylon) {
            super(pylon.position(), pylon.rotation(), pylon.name());
            this.maxRot = pylon.maxRotation();
            this.maxRotFactor = this.maxRot / 90.0F;
        }
    }

    /** A propeller or ship screw; {@code maxRotFactor} is how far it tilts with VTOL, as a factor of 90 degrees. */
    public static class Propeller extends DrawnPart {

        public final float maxRotFactor;
        public final List<Blade> blades;

        public Propeller(Parts.Propeller propeller) {
            super(propeller.position(), propeller.rotation(), propeller.name());
            this.maxRotFactor = propeller.tilt() / 90.0F;
            this.blades = propeller.blades().stream().map(Blade::new).toList();
        }
    }

    /** A wing that folds or tilts; {@code pylonList} is null for a wing without pylons. */
    public static class Wing extends DrawnPart {

        public final float maxRotFactor;
        public final float maxRot;
        public final List<Pylon> pylonList;

        public Wing(Parts.Wing wing) {
            super(wing.position(), wing.rotation(), wing.name());
            this.maxRot = wing.maxRotation();
            this.maxRotFactor = this.maxRot / 90.0F;
            this.pylonList = wing.pylons().isEmpty() ? null : wing.pylons().stream().map(Pylon::new).toList();
        }
    }

    /** An afterburner flame; its texture frames are numbered after {@code texturePrefix}. */
    public static class ExhaustFlame extends DrawnPart {

        public final String texturePrefix;
        public final float degreeYaw;
        public final int delay;

        public ExhaustFlame(Parts.ExhaustFlame flame) {
            super(flame.position(), flame.rotation(), flame.name());
            this.texturePrefix = flame.texture();
            this.degreeYaw = flame.yaw();
            this.delay = flame.delay();
        }
    }

    /** A part of the turret, with the parts that move with it. */
    public static class VPart extends DrawnPart {

        public final boolean rotPitch;
        public final boolean rotYaw;
        public final int type;
        public final boolean drawFP;
        public final float recoilBuf;
        /** Null for a legacy TXT part without children, which YAML keeps as an empty list. */
        @Nullable
        public final List<VPart> child;

        public VPart(Parts.TurretPart part, boolean legacyTxt) {
            super(part.position(), part.rotation(), part.name());
            this.rotPitch = part.pitch();
            this.rotYaw = part.yaw();
            this.type = part.type();
            this.drawFP = part.drawFirstPerson();
            this.recoilBuf = part.recoilBuffer();
            this.child = legacyTxt && part.children().isEmpty() ? null
                    : part.children().stream().map(child -> new VPart(child, legacyTxt)).toList();
        }
    }
}
