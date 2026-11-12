package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import com.ivomens.mcheli.core.content.schema.YamlEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A potion effect a hit gives, optionally only within a distance band:
 * <pre>
 * PotionEffects:
 * - Potion: minecraft:poison   # or Name; a legacy numeric id also works
 *   Duration: 100              # ticks
 *   Amplifier: 1
 *   StartDist: 2               # default 0
 *   EndDist: 10                # default no limit
 * </pre>
 * An entry without a potion is skipped; an unknown potion is reported and skipped.
 *
 * @param effect the effect's registry id
 */
public record PotionEffectSpec(ResourceLocation effect, int duration, int amplifier, int startDist, int endDist) {

    public static final ValueCodec<List<PotionEffectSpec>> LIST_CODEC = new ValueCodec<>() {
        @Override
        public List<PotionEffectSpec> fromYaml(Object raw, List<PotionEffectSpec> previous, ReadContext context) {
            List<?> entries = Codecs.list(raw);
            List<PotionEffectSpec> effects = new ArrayList<>(previous);
            for (int i = 0; i < entries.size(); i++) {
                readEntry(YamlEntry.of(entries.get(i), context, "potion effect " + (i + 1)), context)
                        .ifPresent(effects::add);
            }
            return effects;
        }

        @Override
        public List<PotionEffectSpec> fromText(String text, List<PotionEffectSpec> previous, ReadContext context) {
            throw new UnsupportedOperationException("TXT potion effects are read by a TXT rule");
        }

        @Override
        public ValueShape shape() {
            return ValueShape.listOf(ValueShape.keys().key("Potion", ValueShape.text()).key("Name", ValueShape.text())
                    .key("Duration", ValueShape.whole()).key("Amplifier", ValueShape.whole())
                    .key("StartDist", ValueShape.whole()).key("EndDist", ValueShape.whole()).build())
                    .note("Potion (or Name) is an effect id such as minecraft:poison; Duration is in ticks.");
        }

        @Override
        public Object toYaml(List<PotionEffectSpec> effects) {
            return effects.stream().map(PotionEffectSpec::toYaml).toList();
        }
    };

    /** The registry id of a potion named by id, legacy number or name, if there is such a potion. */
    public static Optional<ResourceLocation> lookUp(String nameOrId) {
        Holder<MobEffect> potion = mobEffect(nameOrId);
        if (potion == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(potion.unwrapKey().map(key -> key.location())
                .orElseGet(() -> BuiltInRegistries.MOB_EFFECT.getKey(potion.value())));
    }

    /** A 1.12 numeric potion id (1 is speed) or a resource location ({@code minecraft:speed}, {@code speed}). */
    @Nullable
    private static Holder<MobEffect> mobEffect(String nameOrId) {
        String s = nameOrId.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            int legacyId = Integer.parseInt(s);
            // 1.12 potion ids start at 1, 1.21 raw registry ids at 0 (same vanilla order)
            return BuiltInRegistries.MOB_EFFECT.getHolder(legacyId - 1).orElse(null);
        } catch (NumberFormatException ignored) {
        }
        ResourceLocation location = ResourceLocation.tryParse(s.toLowerCase(Locale.ROOT));
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT.getHolder(location).orElse(null);
    }

    private static Optional<PotionEffectSpec> readEntry(YamlEntry entry, ReadContext context) {
        String name = entry.read(Codecs.TEXT, null, "Potion", "Name");
        int duration = entry.read(Codecs.INT, 0, "Duration");
        int amplifier = entry.read(Codecs.INT, 0, "Amplifier");
        int startDist = entry.read(Codecs.INT, 0, "StartDist");
        int endDist = entry.read(Codecs.INT, Integer.MAX_VALUE, "EndDist");
        entry.reportUnusedKeys();
        if (name == null) {
            return Optional.empty();
        }
        Optional<ResourceLocation> effect = lookUp(name);
        if (effect.isEmpty()) {
            context.warn("there is no potion effect '" + name + "', the entry is skipped");
        }
        return effect.map(id -> new PotionEffectSpec(id, duration, amplifier, startDist, endDist));
    }

    private Map<String, Object> toYaml() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("Potion", this.effect.toString());
        map.put("Duration", this.duration);
        map.put("Amplifier", this.amplifier);
        map.put("StartDist", this.startDist);
        map.put("EndDist", this.endDist);
        return map;
    }
}
