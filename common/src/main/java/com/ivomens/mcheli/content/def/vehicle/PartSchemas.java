package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.Parts.Camera;
import com.ivomens.mcheli.content.def.vehicle.Parts.Canopy;
import com.ivomens.mcheli.content.def.vehicle.Parts.CrawlerTrack;
import com.ivomens.mcheli.content.def.vehicle.Parts.Hatch;
import com.ivomens.mcheli.content.def.vehicle.Parts.LandingGear;
import com.ivomens.mcheli.content.def.vehicle.Parts.Plain;
import com.ivomens.mcheli.content.def.vehicle.Parts.RepellingHook;
import com.ivomens.mcheli.content.def.vehicle.Parts.Rotating;
import com.ivomens.mcheli.content.def.vehicle.Parts.SearchLight;
import com.ivomens.mcheli.content.def.vehicle.Parts.Throttle;
import com.ivomens.mcheli.content.def.vehicle.Parts.TrackPoint;
import com.ivomens.mcheli.content.def.vehicle.Parts.WeaponBay;
import com.ivomens.mcheli.content.def.vehicle.Parts.Wheel;
import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ContentValueException;
import com.ivomens.mcheli.core.content.schema.EntrySchema;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.InlineList;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import com.ivomens.mcheli.content.schema.Vectors;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.FLOAT;
import static com.ivomens.mcheli.core.content.schema.Codecs.INT;
import static com.ivomens.mcheli.core.content.schema.Codecs.TEXT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;

/**
 * The entries of the {@code Components} lists every vehicle kind can have. Each starts with the keys of
 * {@link Placement}; the part type's own keys follow:
 * <pre>
 * Components:
 *   Hatch:
 *   - Position: [0.0, 0.858, -11.549]
 *     Rotation: [-1.0, 0.0, 0.0]
 *     PartName: hatch0
 *     MaxRotation: 34.0
 * </pre>
 * The kinds' own parts (rotors, wings, turret parts) are in {@link KindPartSchemas}; the weapon parts in
 * {@link WeaponPartSchemas}.
 */
final class PartSchemas {

    /** Degrees a hatch, canopy or gear opens by default. */
    static final float DEFAULT_OPENING = 90.0F;
    private static final ValueCodec<Float> OPENING = floats(-180, 180);

    static final Placement<Camera> CAMERA_PLACE = Placement.of("camera", Camera::position, Camera::rotation,
            Camera::name);
    static final Field<Camera, Boolean> CAMERA_YAW_SYNC = Field.of("YawSync", BOOLEAN, true, Camera::yawSync);
    static final Field<Camera, Boolean> CAMERA_PITCH_SYNC = Field.of("PitchSync", BOOLEAN, false,
            Camera::pitchSync);
    static final EntrySchema<Camera> CAMERA = CAMERA_PLACE.entry("camera",
            v -> new Camera(CAMERA_PLACE.position(v), CAMERA_PLACE.rotation(v), CAMERA_PLACE.name(v),
                    v.get(CAMERA_YAW_SYNC), v.get(CAMERA_PITCH_SYNC)),
            CAMERA_YAW_SYNC, CAMERA_PITCH_SYNC);

    static final Placement<Canopy> CANOPY_PLACE = Placement.of("canopy", Canopy::position, Canopy::rotation,
            Canopy::name);
    /** How far the canopy opens, as a factor of 90 degrees. */
    static final Field<Canopy, Float> CANOPY_OPENING = Field.of("MaxRotation", OPENING, DEFAULT_OPENING,
            Canopy::maxRotation)
            .alwaysWritten();
    static final Field<Canopy, Boolean> CANOPY_SLIDING = Field.of("IsSliding", BOOLEAN, false, Canopy::sliding);
    static final EntrySchema<Canopy> CANOPY = CANOPY_PLACE.entry("canopy",
            v -> new Canopy(CANOPY_PLACE.position(v), CANOPY_PLACE.rotation(v), CANOPY_PLACE.name(v),
                    v.get(CANOPY_OPENING), v.get(CANOPY_SLIDING)),
            CANOPY_OPENING, CANOPY_SLIDING);

    static final Placement<Hatch> HATCH_PLACE = Placement.of("hatch", Hatch::position, Hatch::rotation,
            Hatch::name);
    static final Field<Hatch, Float> HATCH_OPENING = Field.of("MaxRotation", OPENING, DEFAULT_OPENING,
            Hatch::maxRotation)
            .alwaysWritten();
    static final Field<Hatch, Boolean> HATCH_SLIDING = Field.of("IsSliding", BOOLEAN, false, Hatch::sliding);
    static final EntrySchema<Hatch> HATCH = HATCH_PLACE.entry("hatch",
            v -> new Hatch(HATCH_PLACE.position(v), HATCH_PLACE.rotation(v), HATCH_PLACE.name(v),
                    v.get(HATCH_OPENING), v.get(HATCH_SLIDING)),
            HATCH_OPENING, HATCH_SLIDING);

    static final Placement<Hatch> LIGHT_HATCH_PLACE = Placement.of("light_hatch", Hatch::position,
            Hatch::rotation, Hatch::name);
    /** Light hatches turn up to ten full turns either way. */
    static final Field<Hatch, Float> LIGHT_HATCH_OPENING = Field.of("MaxRotation", floats(-1800, 1800),
            DEFAULT_OPENING, Hatch::maxRotation)
            .alwaysWritten();
    static final Field<Hatch, Boolean> LIGHT_HATCH_SLIDING = Field.of("IsSliding", BOOLEAN, false, Hatch::sliding);
    static final EntrySchema<Hatch> LIGHT_HATCH = LIGHT_HATCH_PLACE.entry("light hatch",
            v -> new Hatch(LIGHT_HATCH_PLACE.position(v), LIGHT_HATCH_PLACE.rotation(v), LIGHT_HATCH_PLACE.name(v),
                    v.get(LIGHT_HATCH_OPENING), v.get(LIGHT_HATCH_SLIDING)),
            LIGHT_HATCH_OPENING, LIGHT_HATCH_SLIDING);

    static final Placement<WeaponBay> BAY_PLACE = Placement.of("wb", WeaponBay::position, WeaponBay::rotation,
            WeaponBay::name);
    static final Field<WeaponBay, String> BAY_WEAPONS = Field.of("WeaponName", TEXT, null, WeaponBay::weapons)
            .alwaysWritten();
    /** How far the bay door opens, as a factor of 90 degrees. */
    static final Field<WeaponBay, Float> BAY_OPENING = Field.of("MaxRotation", OPENING, DEFAULT_OPENING,
            WeaponBay::maxRotation)
            .alwaysWritten();
    static final Field<WeaponBay, Boolean> BAY_SLIDING = Field.of("IsSliding", BOOLEAN, false, WeaponBay::sliding);
    static final EntrySchema<WeaponBay> WEAPON_BAY = BAY_PLACE.entry("weapon bay",
            v -> new WeaponBay(BAY_PLACE.position(v), BAY_PLACE.rotation(v), BAY_PLACE.name(v), v.get(BAY_WEAPONS),
                    v.get(BAY_OPENING), v.get(BAY_SLIDING)),
            BAY_WEAPONS, BAY_OPENING, BAY_SLIDING);

    static final Placement<Rotating> ROTATING_PLACE = Placement.of("rotpart", Rotating::position,
            Rotating::rotation, Rotating::name);
    static final Field<Rotating, Float> ROTATING_SPEED = Field.of("Speed", FLOAT, 0.0F, Rotating::speed);
    static final Field<Rotating, Boolean> ROTATING_ALWAYS = Field.of("AlwaysRotate", BOOLEAN, false,
            Rotating::always);
    static final EntrySchema<Rotating> ROTATING = ROTATING_PLACE.entry("rotating part",
            v -> new Rotating(ROTATING_PLACE.position(v), ROTATING_PLACE.rotation(v), ROTATING_PLACE.name(v),
                    v.get(ROTATING_SPEED), v.get(ROTATING_ALWAYS)),
            ROTATING_SPEED, ROTATING_ALWAYS);

    static final Placement<Wheel> STEERING_PLACE = Placement.of("steering_wheel", Wheel::position,
            Wheel::rotation, Wheel::name);
    static final Field<Wheel, Float> STEERING_DIRECTION = Field.of("Direction", FLOAT, 0.0F, Wheel::direction)
            .alwaysWritten();
    static final Field<Wheel, Vec3> STEERING_PIVOT = Field.of("Pivot", Vectors.VEC3, Vec3.ZERO, Wheel::pivot)
            .alwaysWritten();
    static final EntrySchema<Wheel> STEERING_WHEEL = STEERING_PLACE.entry("steering wheel",
            v -> new Wheel(STEERING_PLACE.position(v), STEERING_PLACE.rotation(v), STEERING_PLACE.name(v),
                    v.get(STEERING_DIRECTION), v.get(STEERING_PIVOT)),
            STEERING_DIRECTION, STEERING_PIVOT);

    static final Placement<Wheel> WHEEL_PLACE = Placement.ofWheel("wheel", Wheel::position, Wheel::rotation,
            Wheel::name);
    static final Field<Wheel, Float> WHEEL_DIRECTION = Field.of("Direction", floats(-1800, 1800), 0.0F,
            Wheel::direction)
            .alwaysWritten();
    static final Field<Wheel, Vec3> WHEEL_PIVOT = Field.of("Pivot", Vectors.VEC3, Vec3.ZERO, Wheel::pivot)
            .txt(Vectors.TXT_FLOATS)
            .alwaysWritten();
    static final EntrySchema<Wheel> WHEEL = WHEEL_PLACE.entry("wheel",
            v -> new Wheel(WHEEL_PLACE.position(v), WHEEL_PLACE.rotation(v), WHEEL_PLACE.name(v),
                    v.get(WHEEL_DIRECTION), v.get(WHEEL_PIVOT)),
            WHEEL_DIRECTION, WHEEL_PIVOT);

    static final Placement<LandingGear> GEAR_PLACE = Placement.of("lg", LandingGear::position,
            LandingGear::rotation, LandingGear::name);
    static final Field<LandingGear, Float> GEAR_OPENING = Field.of("MaxRotation", OPENING, DEFAULT_OPENING,
            LandingGear::maxRotation)
            .alwaysWritten();
    static final Field<LandingGear, Boolean> GEAR_REVERSE = Field.of("IsReverse", BOOLEAN, false,
            LandingGear::reverse);
    static final Field<LandingGear, Boolean> GEAR_HATCH = Field.of("IsHatch", BOOLEAN, false, LandingGear::hatch);
    static final Field<LandingGear, Vec3> GEAR_SECOND_AXIS = Field.of("ArticulatedRotation", Vectors.VEC3, null,
            LandingGear::articulatedRotation).txt(Vectors.TXT_FLOATS);
    static final Field<LandingGear, Float> GEAR_SECOND_OPENING = Field.of("MaxArticulatedRotation", OPENING,
            DEFAULT_OPENING, LandingGear::maxArticulatedRotation);
    static final Field<LandingGear, Vec3> GEAR_SLIDE = Field.of("SlideVec", Vectors.VEC3, null, LandingGear::slide)
            .txt(Vectors.TXT_FLOATS);
    static final EntrySchema<LandingGear> LANDING_GEAR = GEAR_PLACE.entry("landing gear",
            v -> new LandingGear(GEAR_PLACE.position(v), GEAR_PLACE.rotation(v), GEAR_PLACE.name(v),
                    v.get(GEAR_OPENING), v.get(GEAR_REVERSE), v.get(GEAR_HATCH), v.get(GEAR_SECOND_AXIS),
                    v.get(GEAR_SECOND_OPENING), v.get(GEAR_SLIDE)),
            GEAR_OPENING, GEAR_REVERSE, GEAR_HATCH, GEAR_SECOND_AXIS, GEAR_SECOND_OPENING, GEAR_SLIDE);

    static final Field<SearchLight, Vec3> LIGHT_POSITION = Field.of("Position", Vectors.VEC3, null,
            SearchLight::position).alias("Pos");
    static final Field<SearchLight, Integer> LIGHT_COLOR_START = Field.of("ColorStart", VehicleCodecs.HEX_COLOR,
            0xFFFFFF, SearchLight::colorStart)
            .alwaysWritten();
    static final Field<SearchLight, Integer> LIGHT_COLOR_END = Field.of("ColorEnd", VehicleCodecs.HEX_COLOR,
            0xFFFFFF, SearchLight::colorEnd)
            .alwaysWritten();
    static final Field<SearchLight, Float> LIGHT_HEIGHT = Field.of("Height", FLOAT, 1.0F, SearchLight::height)
            .alwaysWritten();
    static final Field<SearchLight, Float> LIGHT_WIDTH = Field.of("Width", FLOAT, 1.0F, SearchLight::width)
            .alwaysWritten();
    static final Field<SearchLight, Float> LIGHT_YAW = Field.of("Yaw", FLOAT, 0.0F, SearchLight::yaw);
    static final Field<SearchLight, Float> LIGHT_PITCH = Field.of("Pitch", FLOAT, 0.0F, SearchLight::pitch);
    static final Field<SearchLight, Float> LIGHT_STEERING_ROTATION = Field.of("StRot", FLOAT, 0.0F,
            SearchLight::steeringRotation);
    static final Field<SearchLight, Boolean> LIGHT_FIXED = Field.of("FixedDirection", BOOLEAN, false,
            SearchLight::fixedDirection);
    static final Field<SearchLight, Boolean> LIGHT_STEERING = Field.of("Steering", BOOLEAN, false,
            SearchLight::steering);
    static final EntrySchema<SearchLight> SEARCH_LIGHT = new EntrySchema<>("search light",
            Section.root(LIGHT_POSITION, LIGHT_COLOR_START, LIGHT_COLOR_END, LIGHT_HEIGHT, LIGHT_WIDTH, LIGHT_YAW,
                    LIGHT_PITCH, LIGHT_STEERING_ROTATION, LIGHT_FIXED, LIGHT_STEERING)
                    .requires(LIGHT_POSITION).unsupported("Type"),
            v -> new SearchLight(v.get(LIGHT_POSITION), v.get(LIGHT_COLOR_START), v.get(LIGHT_COLOR_END),
                    v.get(LIGHT_HEIGHT), v.get(LIGHT_WIDTH), v.get(LIGHT_YAW), v.get(LIGHT_PITCH),
                    v.get(LIGHT_STEERING_ROTATION), v.get(LIGHT_FIXED), v.get(LIGHT_STEERING)));

    static final Placement<Plain> TRACK_ROLLER_PLACE = Placement.of("track_roller", Plain::position,
            Plain::rotation, Plain::name);
    static final EntrySchema<Plain> TRACK_ROLLER = TRACK_ROLLER_PLACE.entry("track roller",
            v -> new Plain(TRACK_ROLLER_PLACE.position(v), TRACK_ROLLER_PLACE.rotation(v),
                    TRACK_ROLLER_PLACE.name(v)));

    static final Placement<Throttle> THROTTLE_PLACE = Placement.of("throttle", Throttle::position,
            Throttle::rotation, Throttle::name);
    static final Field<Throttle, Vec3> THROTTLE_SLIDE = Field.of("SlidePos", Vectors.VEC3, Vec3.ZERO,
            Throttle::slide)
            .alwaysWritten();
    static final Field<Throttle, Float> THROTTLE_ANGLE = Field.of("MaxAngle", FLOAT, 0.0F, Throttle::maxAngle);
    static final EntrySchema<Throttle> THROTTLE = THROTTLE_PLACE.entry("throttle",
            v -> new Throttle(THROTTLE_PLACE.position(v), THROTTLE_PLACE.rotation(v), THROTTLE_PLACE.name(v),
                    v.get(THROTTLE_SLIDE), v.get(THROTTLE_ANGLE)),
            THROTTLE_SLIDE, THROTTLE_ANGLE);

    /** A corner of a crawler track: {@code [y, z]}; TXT writes {@code y/z}. */
    static final ValueCodec<List<TrackPoint>> TRACK_POINTS = ValueCodec.of(
            raw -> Codecs.list(raw).stream().map(PartSchemas::pointFromYaml).toList(),
            text -> List.of(pointFromText(text)),
            points -> points.stream().map(point -> new InlineList(List.of(point.x(), point.y()))).toList())
            .shaped(ValueShape.listOf(ValueShape.tuple(2, ValueShape.number())).note("Corners [y, z]."));
    static final Placement<CrawlerTrack> TRACK_PLACE = Placement.of("crawler_track", CrawlerTrack::position,
            CrawlerTrack::rotation, CrawlerTrack::name);
    static final Field<CrawlerTrack, Boolean> TRACK_REVERSE = Field.of("IsReverse", BOOLEAN, false,
            CrawlerTrack::reverse);
    static final Field<CrawlerTrack, Float> TRACK_SEGMENT = Field.of("SegmentLength", FLOAT, 1.0F,
            CrawlerTrack::segmentLength).txt(floats(0.001F, 1000))
            .alwaysWritten();
    static final Field<CrawlerTrack, Float> TRACK_OFFSET = Field.of("ZOffset", FLOAT, 0.0F, CrawlerTrack::offset);
    static final Field<CrawlerTrack, List<TrackPoint>> TRACK_POINT_LIST = Field.of("TrackList", TRACK_POINTS,
            List.of(), CrawlerTrack::points)
            .alwaysWritten();
    static final EntrySchema<CrawlerTrack> CRAWLER_TRACK = TRACK_PLACE.entry("crawler track",
            v -> new CrawlerTrack(TRACK_PLACE.position(v), TRACK_PLACE.rotation(v), TRACK_PLACE.name(v),
                    v.get(TRACK_REVERSE), v.get(TRACK_SEGMENT), v.get(TRACK_OFFSET), v.get(TRACK_POINT_LIST)),
            TRACK_REVERSE, TRACK_SEGMENT, TRACK_OFFSET, TRACK_POINT_LIST);

    static final Field<RepellingHook, Vec3> HOOK_POSITION = Field.of("Position", Vectors.VEC3, null,
            RepellingHook::position).alias("Pos");
    static final Field<RepellingHook, Integer> HOOK_INTERVAL = Field.of("Interval", INT, 0,
            RepellingHook::interval);
    static final EntrySchema<RepellingHook> REPELLING_HOOK = new EntrySchema<>("repelling hook",
            Section.root(HOOK_POSITION, HOOK_INTERVAL).requires(HOOK_POSITION),
            v -> new RepellingHook(v.get(HOOK_POSITION), v.get(HOOK_INTERVAL)));

    private PartSchemas() {
    }

    private static TrackPoint pointFromYaml(Object raw) {
        List<?> pair = Codecs.require(raw, List.class, "a pair [y, z]");
        if (pair.size() != 2) {
            throw new ContentValueException(
                    "each TrackList entry must be a pair [y, z], got " + pair.size() + " values");
        }
        return new TrackPoint(Codecs.number(pair.get(0)).floatValue(), Codecs.number(pair.get(1)).floatValue());
    }

    private static TrackPoint pointFromText(String text) {
        String[] pair = text.split("\\s*/\\s*");
        return new TrackPoint(Codecs.parseFloat(pair[0]), Codecs.parseFloat(pair[1]));
    }
}
