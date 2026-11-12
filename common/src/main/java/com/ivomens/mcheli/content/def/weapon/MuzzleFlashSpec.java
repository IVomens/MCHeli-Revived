package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.core.content.schema.ArgbColor;
import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ContentValueException;
import com.ivomens.mcheli.core.content.schema.InlineList;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import com.ivomens.mcheli.core.content.schema.YamlEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A muzzle flash or muzzle smoke puff, every key required:
 * <pre>
 * MuzzleFlashes:
 * - Distance: 1.0     # blocks ahead of the muzzle
 *   Size: 2.0
 *   Range: 0.0        # random spread
 *   Age: 3            # ticks
 *   Count: 1
 *   Color: '#FF804020'   # ARGB hex, or [a, r, g, b] from 0 to 1
 * </pre>
 * The channels are not clamped; a legacy TXT flash may be brighter than full.
 */
public record MuzzleFlashSpec(float distance, float size, float range, int age, float a, float r, float g, float b,
                              int count) {

    public static final ValueCodec<List<MuzzleFlashSpec>> LIST_CODEC = new ValueCodec<>() {
        @Override
        public List<MuzzleFlashSpec> fromYaml(Object raw, List<MuzzleFlashSpec> previous, ReadContext context) {
            List<?> entries = Codecs.list(raw);
            List<MuzzleFlashSpec> flashes = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                flashes.add(readEntry(YamlEntry.of(entries.get(i), context, "flash " + (i + 1))));
            }
            return flashes;
        }

        @Override
        public List<MuzzleFlashSpec> fromText(String text, List<MuzzleFlashSpec> previous, ReadContext context) {
            throw new UnsupportedOperationException("TXT muzzle flashes are read by a TXT rule");
        }

        @Override
        public ValueShape shape() {
            return ValueShape.listOf(ValueShape.keys().required("Distance", ValueShape.number())
                    .required("Size", ValueShape.number()).required("Range", ValueShape.number())
                    .required("Age", ValueShape.whole()).required("Count", ValueShape.whole())
                    .required("Color", ArgbColor.CODEC.shape()).build());
        }

        @Override
        public Object toYaml(List<MuzzleFlashSpec> flashes) {
            return flashes.stream().map(MuzzleFlashSpec::toYaml).toList();
        }
    };

    private static final ValueCodec<float[]> CHANNELS = ValueCodec.of(
            MuzzleFlashSpec::channelsFromYaml,
            text -> channelsFromYaml(text),
            channels -> channels);

    private static MuzzleFlashSpec readEntry(YamlEntry entry) {
        float distance = entry.require(Codecs.FLOAT, "Distance");
        float size = entry.require(Codecs.FLOAT, "Size");
        float range = entry.require(Codecs.FLOAT, "Range");
        int age = entry.require(Codecs.INT, "Age");
        int count = entry.require(Codecs.INT, "Count");
        float[] argb = entry.require(CHANNELS, "Color");
        entry.reportUnusedKeys();
        return new MuzzleFlashSpec(distance, size, range, age, argb[0], argb[1], argb[2], argb[3], count);
    }

    private static float[] channelsFromYaml(Object raw) {
        if (raw instanceof List<?> channels) {
            if (channels.size() != 4) {
                throw new ContentValueException("a colour list needs four values [a, r, g, b], got "
                        + channels.size());
            }
            float[] argb = new float[4];
            for (int i = 0; i < 4; i++) {
                argb[i] = Codecs.number(channels.get(i)).floatValue();
            }
            return argb;
        }
        int color = ArgbColor.parseHex(Codecs.text(raw));
        return new float[]{((color >> 24) & 0xFF) / 255.0F, ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F};
    }

    private Map<String, Object> toYaml() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("Distance", this.distance);
        map.put("Size", this.size);
        map.put("Range", this.range);
        map.put("Age", this.age);
        map.put("Count", this.count);
        map.put("Color", colorToYaml());
        return map;
    }

    /** Hex when it reads back to the same channels, otherwise the exact list. */
    private Object colorToYaml() {
        float[] channels = {this.a, this.r, this.g, this.b};
        int argb = 0;
        boolean exact = true;
        for (float channel : channels) {
            int value = (int) Math.round(Math.max(0, Math.min(1, channel)) * 255.0);
            argb = argb << 8 | value;
            exact &= value / 255.0F == channel;
        }
        return exact ? String.format("#%06X", argb) : new InlineList(List.of(this.a, this.r, this.g, this.b));
    }
}
