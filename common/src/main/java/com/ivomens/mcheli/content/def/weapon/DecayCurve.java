package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ContentValueException;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import com.ivomens.mcheli.core.content.schema.YamlEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a bullet's damage falls off with the distance it travelled. Only the segmented curve exists: past
 * each segment's start distance the damage is multiplied by its factor.
 * <pre>
 * BulletDecay:
 *   Type: Segmented
 *   Segments:
 *   - [10.0, 0.9]    # from 10 blocks on, 90 % damage
 *   - [50.0, 0.5]
 * </pre>
 * Legacy TXT writes {@code BulletDecay = Segmented, 10, 0.9, 50, 0.5}, and may repeat the line; YAML holds
 * one curve.
 */
public record DecayCurve(List<Segment> segments) {

    public static final String SEGMENTED = "Segmented";

    /** The curves of a weapon; YAML adds one, the emitter writes the first. */
    public static final ValueCodec<List<DecayCurve>> LIST_CODEC = new ValueCodec<>() {
        @Override
        public List<DecayCurve> fromYaml(Object raw, List<DecayCurve> previous, ReadContext context) {
            YamlEntry entry = YamlEntry.of(raw, context, "bullet decay");
            String type = entry.read(Codecs.TEXT, SEGMENTED, "Type");
            Object segments = entry.raw("Segments");
            entry.reportUnusedKeys();
            if (!SEGMENTED.equalsIgnoreCase(type) || !(segments instanceof List<?> pairs)) {
                return previous;
            }
            List<Segment> curve = new ArrayList<>();
            for (Object pair : pairs) {
                if (pair instanceof List<?> values && values.size() >= 2) {
                    curve.add(new Segment(Codecs.number(values.get(0)).floatValue(),
                            Codecs.number(values.get(1)).floatValue()));
                }
            }
            List<DecayCurve> curves = new ArrayList<>(previous);
            curves.add(new DecayCurve(curve));
            return curves;
        }

        @Override
        public List<DecayCurve> fromText(String text, List<DecayCurve> previous, ReadContext context) {
            throw new UnsupportedOperationException("TXT bullet decay is read by a TXT rule");
        }

        @Override
        public ValueShape shape() {
            return ValueShape.keys().key("Type", ValueShape.text())
                    .key("Segments", ValueShape.listOf(new ValueShape.ListOf(ValueShape.number(), 2, null)))
                    .build().note("Type Segmented; each segment is [start distance, damage factor].");
        }

        @Override
        public Object toYaml(List<DecayCurve> curves) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("Type", SEGMENTED);
            map.put("Segments", curves.getFirst().segments.stream()
                    .map(segment -> List.of(segment.startDistance, segment.damageMultiplier))
                    .toList());
            return map;
        }
    };

    public DecayCurve {
        segments = List.copyOf(segments);
    }

    /** A legacy TXT {@code BulletDecay} line: the type, then start distance and factor pairs. */
    public static DecayCurve fromTxt(String text) {
        String[] parts = Codecs.splitTxt(text);
        if (!parts[0].equals(SEGMENTED)) {
            throw new ContentValueException("unknown bullet decay type '" + parts[0] + "', only '" + SEGMENTED
                    + "' exists");
        }
        List<Segment> segments = new ArrayList<>();
        for (int i = 1; i + 1 < parts.length; i += 2) {
            segments.add(new Segment(Codecs.parseFloat(parts[i]), Codecs.parseFloat(parts[i + 1])));
        }
        return new DecayCurve(segments);
    }

    public record Segment(float startDistance, float damageMultiplier) {
    }
}
