package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import com.ivomens.mcheli.core.content.schema.YamlEntry;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Spent cases a gun ejects:
 * <pre>
 * SpentCasings:
 *   Name: shell        # bullet model name
 *   Acceleration: 0.3  # or Accel; ejection speed, default 0
 *   Yaw: 90.0          # default 0
 *   Pitch: 10.0        # default 0
 *   Scale: 0.8         # default 1
 *   Gravity: -0.05     # default -0.04
 *   Bound: 0.4         # bounciness, default 0.5
 * </pre>
 *
 * @param name the model name, or null when the section leaves it out
 */
public record CasingSpec(String name, float acceleration, float yaw, float pitch, float bound, float gravity,
                         float scale) {

    public static final float DEFAULT_SCALE = 1.0F;
    public static final float DEFAULT_GRAVITY = -0.04F;
    public static final float DEFAULT_BOUND = 0.5F;

    public static final ValueCodec<CasingSpec> CODEC = new ValueCodec<>() {
        @Override
        public CasingSpec fromYaml(Object raw, CasingSpec previous, ReadContext context) {
            YamlEntry entry = YamlEntry.of(raw, context, "spent casings");
            String name = entry.read(ValueCodec.of(value -> Codecs.text(value).toLowerCase(Locale.ROOT),
                    text -> text, value -> value), null, "Name");
            CasingSpec casing = new CasingSpec(name, entry.read(Codecs.FLOAT, 0.0F, "Accel", "Acceleration"),
                    entry.read(Codecs.FLOAT, 0.0F, "Yaw"), entry.read(Codecs.FLOAT, 0.0F, "Pitch"),
                    entry.read(Codecs.FLOAT, DEFAULT_BOUND, "Bound"),
                    entry.read(Codecs.FLOAT, DEFAULT_GRAVITY, "Gravity"),
                    entry.read(Codecs.FLOAT, DEFAULT_SCALE, "Scale"));
            entry.reportUnusedKeys();
            return casing;
        }

        @Override
        public CasingSpec fromText(String text, CasingSpec previous, ReadContext context) {
            throw new UnsupportedOperationException("TXT casings are read by a TXT rule");
        }

        @Override
        public ValueShape shape() {
            return ValueShape.keys().key("Name", ValueShape.text()).key("Acceleration", ValueShape.number())
                    .key("Accel", ValueShape.number()).key("Yaw", ValueShape.number())
                    .key("Pitch", ValueShape.number()).key("Bound", ValueShape.number())
                    .key("Gravity", ValueShape.number()).key("Scale", ValueShape.number()).build();
        }

        @Override
        public Object toYaml(CasingSpec casing) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (casing.name != null) {
                map.put("Name", casing.name);
            }
            map.put("Acceleration", casing.acceleration);
            map.put("Yaw", casing.yaw);
            map.put("Pitch", casing.pitch);
            map.put("Scale", casing.scale);
            map.put("Gravity", casing.gravity);
            map.put("Bound", casing.bound);
            return map;
        }
    };
}
