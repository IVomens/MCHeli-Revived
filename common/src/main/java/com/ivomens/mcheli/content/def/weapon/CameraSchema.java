package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Camera;
import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ContentValueException;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import com.ivomens.mcheli.core.content.schema.Values;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.DOUBLE;
import static com.ivomens.mcheli.core.content.schema.Codecs.FLOAT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;

/** YAML {@code Camera}: the gunner's view while this weapon is selected. */
final class CameraSchema {

    /** The written names of the sights; any other name leaves the sight as it was. */
    private static final Map<String, SightType> SIGHT_NAMES = Map.of(
            "movesight", SightType.ROCKET,
            "missilesight", SightType.LOCK);

    /** {@code movesight} or {@code missilesight}, any case. */
    static final ValueCodec<SightType> SIGHT = new ValueCodec<>() {
        @Override
        public SightType fromYaml(Object raw, SightType previous, ReadContext context) {
            return fromText(Codecs.text(raw), previous, context);
        }

        @Override
        public SightType fromText(String text, SightType previous, ReadContext context) {
            return SIGHT_NAMES.getOrDefault(text.toLowerCase(Locale.ROOT), previous);
        }

        @Override
        public ValueShape shape() {
            return ValueShape.suggested(List.of("movesight", "missilesight"));
        }

        @Override
        public Object toYaml(SightType sight) {
            return SIGHT_NAMES.entrySet().stream().filter(entry -> entry.getValue() == sight)
                    .map(Map.Entry::getKey).findFirst().orElse(null);
        }
    };

    /**
     * Zoom steps: one number, or a list each clamped to 0..10. Legacy TXT writes {@code Zoom = 1.25, 2, 4}
     * and clamps each step to 0.1..10.
     */
    static final ValueCodec<List<Float>> ZOOM = ValueCodec.of(
            CameraSchema::zoomFromYaml,
            CameraSchema::zoomFromText,
            steps -> steps).shaped(ValueShape.either(ValueShape.number(),
                    new ValueShape.ListOf(ValueShape.number(), 1, null))
            .note("One zoom step, or a list of them; each is kept within 0 to 10."));

    static final Field<Camera, Double> MORTAR_RADAR_MAX_DIST =
            Field.of("MortarRadarMaxDist", DOUBLE, -1.0, Camera::mortarRadarMaxDist).txt("mortarradarmaxdist");
    static final Field<Camera, Boolean> DISPLAY_MORTAR_DISTANCE =
            Field.of("DisplayMortarDistance", BOOLEAN, false, Camera::displayMortarDistance)
                    .txt("displaymortardistance");
    static final Field<Camera, Boolean> FIX_PITCH =
            Field.of("FixPitch", BOOLEAN, false, Camera::fixPitch).txt("fixcamerapitch");
    static final Field<Camera, Float> ROTATION_SPEED_PITCH =
            Field.of("RotationSpeedPitch", floats(0, 100), 1.0F, Camera::rotationSpeedPitch)
                    .txt("camerarotationspeedpitch");
    /** Continuously computed impact point; only rockets and AT and TV missiles support it. */
    static final Field<Camera, Boolean> CCIP = Field.of("CCIP", BOOLEAN, false, Camera::ccip).txt("ccip");
    /** Scale of the CCIP marker. TXT clamps it to 0.1..10. */
    static final Field<Camera, Float> CCIP_FACTOR =
            Field.of("CCIPFactor", FLOAT, 1.0F, Camera::ccipFactor).txt(floats(0.1F, 10), "ccipfactor");
    static final Field<Camera, Boolean> LOCK_ENTITY =
            Field.of("LockEntity", BOOLEAN, false, Camera::lockEntity).txt("lockentity");
    static final Field<Camera, SightType> SIGHT_TYPE =
            Field.of("Sight", SIGHT, SightType.NONE, Camera::sight).txt("sight");
    static final Field<Camera, List<Float>> ZOOM_STEPS = Field.of("Zoom", ZOOM, List.of(1.0F), Camera::zoom)
            .txt("zoom");

    static final Section<WeaponDefinition, Camera> SECTION = Section.of("Camera", WeaponDefinition::camera,
                    MORTAR_RADAR_MAX_DIST, DISPLAY_MORTAR_DISTANCE, FIX_PITCH, ROTATION_SPEED_PITCH, CCIP,
                    CCIP_FACTOR, LOCK_ENTITY, SIGHT_TYPE, ZOOM_STEPS)
            .unsupported("EnableMortarRadar", "CCIPTexture", "FollowLockEntity", "FollowStrength");

    private CameraSchema() {
    }

    static Camera build(Values v) {
        return new Camera(v.get(MORTAR_RADAR_MAX_DIST), v.get(DISPLAY_MORTAR_DISTANCE), v.get(FIX_PITCH),
                v.get(ROTATION_SPEED_PITCH), v.get(CCIP), v.get(CCIP_FACTOR), v.get(LOCK_ENTITY), v.get(SIGHT_TYPE),
                v.get(ZOOM_STEPS));
    }

    private static List<Float> zoomFromYaml(Object raw) {
        if (raw instanceof Number single) {
            return List.of(single.floatValue());
        }
        if (raw instanceof List<?> steps && !steps.isEmpty()) {
            List<Float> zoom = new ArrayList<>();
            for (Object step : steps) {
                zoom.add(Math.max(0.0F, Math.min(10.0F, Codecs.number(step).floatValue())));
            }
            return zoom;
        }
        throw new ContentValueException("expected a number or a list of numbers but got " + Codecs.describe(raw));
    }

    private static List<Float> zoomFromText(String text) {
        List<Float> zoom = new ArrayList<>();
        for (String step : Codecs.splitTxt(text)) {
            float value = Codecs.parseFloat(step);
            zoom.add(value > 10.0F ? 10.0F : Math.max(value, 0.1F));
        }
        return zoom;
    }
}
