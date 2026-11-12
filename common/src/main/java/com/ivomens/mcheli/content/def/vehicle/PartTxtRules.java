package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.Parts.Camera;
import com.ivomens.mcheli.content.def.vehicle.Parts.Canopy;
import com.ivomens.mcheli.content.def.vehicle.Parts.CrawlerTrack;
import com.ivomens.mcheli.content.def.vehicle.Parts.Hatch;
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
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.Schema;
import com.ivomens.mcheli.core.content.schema.TxtKey.TxtRule;
import com.ivomens.mcheli.core.content.schema.TxtTuple;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.content.schema.Vectors;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static com.ivomens.mcheli.content.def.vehicle.ComponentsSchema.CANOPIES;
import static com.ivomens.mcheli.content.def.vehicle.ComponentsSchema.HATCHES;
import static com.ivomens.mcheli.content.def.vehicle.ComponentsSchema.LIGHT_HATCHES;
import static com.ivomens.mcheli.content.def.vehicle.ComponentsSchema.THROTTLES;
import static com.ivomens.mcheli.content.def.vehicle.ComponentsSchema.WEAPON_BAYS;

/**
 * Legacy TXT lines that add the parts every kind can have. Each names its part after its type and how many
 * of that type came before ({@code hatch2}); the few exceptions are noted. Openings are given in degrees and
 * clamped to -180..180 unless noted; a sliding part slides instead of turning.
 */
final class PartTxtRules {

    /** The opening, in degrees, of a part line that leaves it out. */
    private static final float OPENING = PartSchemas.DEFAULT_OPENING;
    /** Canopies and bay doors keep their opening as a factor of 90 degrees; sliding ones keep 90. */
    private static final float RIGHT_ANGLE = 90.0F;
    private static final int MAX_HOOK_INTERVAL = 100_000;

    /** {@code AddPartCamera = x, y, z[, yawSync[, pitchSync]]}; the camera turns about the y axis. */
    static final TxtTuple<Camera> CAMERA = TxtTuple.of(PartSchemas.CAMERA).minimum(3)
            .fixed(PartSchemas.CAMERA_PLACE.rotation(), new Vec3(0.0, -1.0, 0.0))
            .vector(PartSchemas.CAMERA_PLACE.position())
            .value(PartSchemas.CAMERA_YAW_SYNC).value(PartSchemas.CAMERA_PITCH_SYNC)
            .then(named(PartSchemas.CAMERA_PLACE.name(), "camera", ComponentsSchema.CAMERAS));

    /** {@code AddPartHatch = x, y, z, rx, ry, rz[, opening]}. */
    static final TxtTuple<Hatch> HATCH = TxtTuple.of(PartSchemas.HATCH).minimum(6)
            .vector(PartSchemas.HATCH_PLACE.position()).vector(PartSchemas.HATCH_PLACE.rotation())
            .value(PartSchemas.HATCH_OPENING)
            .then(named(PartSchemas.HATCH_PLACE.name(), "hatch", HATCHES));
    /** {@code AddPartSlideHatch = x, y, z}. */
    static final TxtTuple<Hatch> SLIDING_HATCH = TxtTuple.of(PartSchemas.HATCH).minimum(3)
            .fixed(PartSchemas.HATCH_SLIDING, true)
            .vector(PartSchemas.HATCH_PLACE.position())
            .then(named(PartSchemas.HATCH_PLACE.name(), "hatch", HATCHES));
    /** {@code AddPartLightHatch = x, y, z, rx, ry, rz[, opening]}, opening clamped to -1800..1800. */
    static final TxtTuple<Hatch> LIGHT_HATCH = TxtTuple.of(PartSchemas.LIGHT_HATCH).minimum(6)
            .vector(PartSchemas.LIGHT_HATCH_PLACE.position()).vector(PartSchemas.LIGHT_HATCH_PLACE.rotation())
            .value(PartSchemas.LIGHT_HATCH_OPENING)
            .then(named(PartSchemas.LIGHT_HATCH_PLACE.name(), "light_hatch", LIGHT_HATCHES));

    /** {@code AddPartRotation = x, y, z, rx, ry, rz, speed[, always]}; always turning unless it says false. */
    static final TxtTuple<Rotating> ROTATING = TxtTuple.of(PartSchemas.ROTATING).minimum(7)
            .vector(PartSchemas.ROTATING_PLACE.position()).vector(PartSchemas.ROTATING_PLACE.rotation())
            .value(PartSchemas.ROTATING_SPEED)
            .value(PartSchemas.ROTATING_ALWAYS, true)
            .then(named(PartSchemas.ROTATING_PLACE.name(), "rotpart", THROTTLES));

    /** {@code AddPartSteeringWheel = x, y, z, rx, ry, rz, direction}; it steers about its own position. */
    static final TxtTuple<Wheel> STEERING_WHEEL = TxtTuple.of(PartSchemas.STEERING_WHEEL).minimum(7)
            .vector(PartSchemas.STEERING_PLACE.position()).vector(PartSchemas.STEERING_PLACE.rotation())
            .value(PartSchemas.STEERING_DIRECTION)
            .then((part, file, count) -> {
                part.set(PartSchemas.STEERING_PIVOT, part.get(PartSchemas.STEERING_PLACE.position()));
                named(PartSchemas.STEERING_PLACE.name(), "steering_wheel", ComponentsSchema.STEERING_WHEELS)
                        .apply(part, file, count);
            });

    /**
     * {@code AddPartWheel = x, y, z[, direction[, rx, ry, rz[, pivotX, pivotY, pivotZ]]]}; direction clamped
     * to -1800..1800, the pivot is the wheel's position unless given.
     */
    static final TxtTuple<Wheel> WHEEL = TxtTuple.of(PartSchemas.WHEEL).minimum(3)
            .vector(PartSchemas.WHEEL_PLACE.position())
            .value(PartSchemas.WHEEL_DIRECTION)
            .vector(PartSchemas.WHEEL_PLACE.rotation())
            .vector(PartSchemas.WHEEL_PIVOT)
            .then((part, file, count) -> {
                if (count < 10) {
                    part.set(PartSchemas.WHEEL_PIVOT, part.get(PartSchemas.WHEEL_PLACE.position()));
                }
                named(PartSchemas.WHEEL_PLACE.name(), "wheel", ComponentsSchema.WHEELS).apply(part, file, count);
            });

    /** {@code AddTrackRoller = x, y, z}. */
    static final TxtTuple<Plain> TRACK_ROLLER = TxtTuple.of(PartSchemas.TRACK_ROLLER).minimum(3)
            .vector(PartSchemas.TRACK_ROLLER_PLACE.position())
            .then(named(PartSchemas.TRACK_ROLLER_PLACE.name(), "track_roller", ComponentsSchema.TRACK_ROLLERS));

    /** {@code AddRepellingHook = x, y, z[, interval]}, every 10 ticks unless given (1 to 100000). */
    static final TxtTuple<RepellingHook> REPELLING_HOOK = TxtTuple.of(PartSchemas.REPELLING_HOOK).minimum(3)
            .vector(PartSchemas.HOOK_POSITION)
            .value(PartSchemas.HOOK_INTERVAL, 10)
            .then((part, file, count) -> part.set(PartSchemas.HOOK_INTERVAL,
                    Math.max(1, Math.min(MAX_HOOK_INTERVAL, part.get(PartSchemas.HOOK_INTERVAL)))));

    private PartTxtRules() {
    }

    static void register(Schema.Builder<VehicleFile> builder) {
        builder.txtRule("addpartcamera", CAMERA.appendingTo(ComponentsSchema.CAMERAS))
                .txtRule("addpartcanopy", (values, text, context) -> canopy(values, text, context, false))
                .txtRule("addpartslidecanopy", (values, text, context) -> canopy(values, text, context, true))
                .txtRule("addparthatch", HATCH.appendingTo(HATCHES))
                .txtRule("addpartslidehatch", SLIDING_HATCH.appendingTo(HATCHES))
                .txtRule("addpartlighthatch", LIGHT_HATCH.appendingTo(LIGHT_HATCHES))
                .txtRule("addpartweaponbay", (values, text, context) -> weaponBay(values, text, context, false))
                .txtRule("addpartslideweaponbay", (values, text, context) -> weaponBay(values, text, context, true))
                .txtRule("addpartrotation", ROTATING.appendingTo(ComponentsSchema.ROTATING_PARTS))
                .txtRule("addpartsteeringwheel", STEERING_WHEEL.appendingTo(ComponentsSchema.STEERING_WHEELS))
                .txtRule("addpartwheel", WHEEL.appendingTo(ComponentsSchema.WHEELS))
                .txtRule("addtrackroller", TRACK_ROLLER.appendingTo(ComponentsSchema.TRACK_ROLLERS))
                .txtRule("addrepellinghook", REPELLING_HOOK.appendingTo(ComponentsSchema.REPELLING_HOOKS))
                .txtRule("addpartthrottle", PartTxtRules::throttle)
                .txtRule("addcrawlertrack", PartTxtRules::crawlerTrack)
                .txtRule("addsearchlight", searchLight(false, false))
                .txtRule("addfixedsearchlight", searchLight(true, false))
                .txtRule("addsteeringsearchlight", searchLight(true, true));
        GearTxtRules.register(builder);
    }

    /** Names the new part {@code prefix} and the size of {@code list} so far. */
    static TxtTuple.Finish named(Field<?, String> name, String prefix, Field<?, ? extends List<?>> list) {
        return (part, file, count) -> part.set(name, prefix + file.get(list).size());
    }

    /**
     * {@code AddPartCanopy = x, y, z, rx, ry, rz[, opening]} and {@code AddPartSlideCanopy = x, y, z}. The
     * first canopy is added twice, as {@code canopy0} and {@code canopy}; the next one is {@code canopy1}.
     */
    private static void canopy(Values values, String text, ReadContext context, boolean sliding) {
        String[] parts = Codecs.splitTxt(text);
        if (parts.length < (sliding ? 3 : 6)) {
            return;
        }
        Vec3 position = Vectors.TXT_FLOATS.fromText(join(parts, 0), null, context);
        Vec3 rotation = sliding ? Vec3.ZERO : Vectors.TXT_FLOATS.fromText(join(parts, 3), null, context);
        float opening = sliding ? RIGHT_ANGLE
                : opening(parts, 6, context) / RIGHT_ANGLE;
        int count = values.get(CANOPIES).size();
        int number = count > 0 ? count - 1 : 0;
        values.append(CANOPIES, new Canopy(position, rotation, "canopy" + number, opening, sliding));
        if (number == 0) {
            values.append(CANOPIES, new Canopy(position, rotation, "canopy", opening, sliding));
        }
    }

    /**
     * {@code AddPartWeaponBay = weapons, x, y, z, rx, ry, rz[, opening]} and
     * {@code AddPartSlideWeaponBay = weapons, x, y, z}; weapons in lower case, separated by {@code /}.
     */
    private static void weaponBay(Values values, String text, ReadContext context, boolean sliding) {
        String[] parts = Codecs.splitTxt(text);
        if (parts.length < (sliding ? 4 : 7)) {
            return;
        }
        Vec3 position = Vectors.TXT_FLOATS.fromText(join(parts, 1), null, context);
        Vec3 rotation = sliding ? Vec3.ZERO : Vectors.TXT_FLOATS.fromText(join(parts, 4), null, context);
        float opening = sliding ? RIGHT_ANGLE : opening(parts, 7, context) / RIGHT_ANGLE;
        String name = "wb" + values.get(WEAPON_BAYS).size();
        values.append(WEAPON_BAYS, new WeaponBay(position, rotation, name,
                parts[0].trim().toLowerCase(Locale.ROOT), opening, sliding));
    }

    /** {@code AddPartThrottle = x, y, z, rx, ry, rz, angle[, slideX[, slideY[, slideZ]]]}. */
    private static void throttle(Values values, String text, ReadContext context) {
        String[] parts = Codecs.splitTxt(text);
        if (parts.length < 7) {
            return;
        }
        Vec3 slide = new Vec3(optional(parts, 7), optional(parts, 8), optional(parts, 9));
        values.append(THROTTLES, new Throttle(Vectors.TXT_FLOATS.fromText(join(parts, 0), null, context),
                Vectors.TXT_FLOATS.fromText(join(parts, 3), null, context), "throttle" + values.get(THROTTLES).size(),
                slide, Codecs.parseFloat(parts[6])));
    }

    /**
     * {@code AddCrawlerTrack = reverse, segmentLength, offset, y/z, y/z, ...}: a track along the given
     * outline. A track with fewer than four points draws nothing but keeps its place.
     */
    private static void crawlerTrack(Values values, String text, ReadContext context) {
        String[] parts = Codecs.splitTxt(text);
        float segment = PartSchemas.TRACK_SEGMENT.txtCodec().fromText(parts[1], null, context);
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 3; i < parts.length; i++) {
            points.addAll(PartSchemas.TRACK_POINTS.fromText(parts[i], null, context));
        }
        String name = "crawler_track" + values.get(ComponentsSchema.CRAWLER_TRACKS).size();
        values.append(ComponentsSchema.CRAWLER_TRACKS, new CrawlerTrack(Vec3.ZERO, Vec3.ZERO, name,
                Codecs.BOOLEAN.fromText(parts[0], false, context), segment, Codecs.parseFloat(parts[2]), points));
    }

    /**
     * {@code AddSearchLight = x, y, z, colorStart, colorEnd, height, width[, yaw[, pitch[, steeringRotation]]]};
     * colours are hex ARGB. {@code AddFixedSearchLight} points the fixed way, {@code AddSteeringSearchLight}
     * also turns with the steering.
     */
    private static TxtRule searchLight(boolean fixed, boolean steering) {
        return (values, text, context) -> {
            String[] parts = Codecs.splitTxt(text);
            if (parts.length < 7) {
                return;
            }
            Function<Integer, Float> number = index -> parts.length > index ? Codecs.parseFloat(parts[index]) : 0.0F;
            values.append(ComponentsSchema.SEARCH_LIGHTS, new SearchLight(
                    Vectors.VEC3.fromText(join(parts, 0), null, context),
                    VehicleCodecs.HEX_COLOR.fromText(parts[3], null, context),
                    VehicleCodecs.HEX_COLOR.fromText(parts[4], null, context), Codecs.parseFloat(parts[5]),
                    Codecs.parseFloat(parts[6]), number.apply(7), number.apply(8), number.apply(9), fixed, steering));
        };
    }

    /** The opening at {@code index}, clamped to -180..180, or 90 degrees when the line ends before it. */
    static float opening(String[] parts, int index, ReadContext context) {
        return parts.length > index ? Codecs.clamp(Codecs.parseFloat(parts[index]), -180.0F, 180.0F, context)
                : OPENING;
    }

    static String join(String[] parts, int from) {
        if (parts.length < from + 3) {
            throw new ContentValueException("not enough values");
        }
        return String.join(", ", parts[from], parts[from + 1], parts[from + 2]);
    }

    private static float optional(String[] parts, int index) {
        return parts.length > index ? Codecs.parseFloat(parts[index]) : 0.0F;
    }
}
