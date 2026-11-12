package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A weapon behaviour an addon mod registers (docs/API.md, "Weapon behaviours"), as a weapon file names it: an id
 * alone, or a map of the id and the behaviour's own settings.
 * <pre>
 * Guidance: mymod:beam_rider
 * ProjectileEffects:
 * - mymod:emp
 * - Id: mymod:cluster
 *   Count: 6
 * </pre>
 * Whether a mod registers the id is checked when the weapon is built ({@code WeaponBehaviours}); here only the
 * spelling is read. An entry whose id is missing or malformed is reported and skipped.
 *
 * @param settings the keys of the map other than {@code Id}, as YAML read them (nested maps and lists included);
 *                 empty for an id alone
 */
public record BehaviourRef(ResourceLocation id, Map<String, Object> settings) {

    private static final String ID_KEY = "Id";
    private static final ValueShape SHAPE = ValueShape.either(
                    ValueShape.text("^[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+$"), ValueShape.mapOf(ValueShape.anything()))
            .note("A behaviour id of an addon mod, such as mymod:beam_rider, or a map of Id and the behaviour's "
                    + "own settings.");

    /** One behaviour; null when the file names none. */
    public static final ValueCodec<BehaviourRef> CODEC = new ValueCodec<>() {
        @Override
        public BehaviourRef fromYaml(Object raw, BehaviourRef previous, ReadContext context) {
            return read(raw, context);
        }

        @Override
        public BehaviourRef fromText(String text, BehaviourRef previous, ReadContext context) {
            throw new UnsupportedOperationException("weapon behaviours are YAML only");
        }

        @Override
        public Object toYaml(BehaviourRef value) {
            return value == null ? null : value.toYaml();
        }

        @Override
        public ValueShape shape() {
            return SHAPE;
        }
    };

    /** Several behaviours, in the order they run. */
    public static final ValueCodec<List<BehaviourRef>> LIST_CODEC = new ValueCodec<>() {
        @Override
        public List<BehaviourRef> fromYaml(Object raw, List<BehaviourRef> previous, ReadContext context) {
            List<?> entries = raw instanceof List<?> list ? list : List.of(raw);
            List<BehaviourRef> refs = new ArrayList<>();
            for (Object entry : entries) {
                BehaviourRef ref = read(entry, context);
                if (ref != null) {
                    refs.add(ref);
                }
            }
            return List.copyOf(refs);
        }

        @Override
        public List<BehaviourRef> fromText(String text, List<BehaviourRef> previous, ReadContext context) {
            throw new UnsupportedOperationException("weapon behaviours are YAML only");
        }

        @Override
        public Object toYaml(List<BehaviourRef> value) {
            return value.stream().map(BehaviourRef::toYaml).toList();
        }

        @Override
        public ValueShape shape() {
            return ValueShape.oneOrMore(SHAPE);
        }
    };

    public BehaviourRef {
        settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
    }

    @Nullable
    private static BehaviourRef read(@Nullable Object raw, ReadContext context) {
        if (raw instanceof Map<?, ?>) {
            Map<String, Object> map = new LinkedHashMap<>(Codecs.map(raw));
            Object id = map.remove(ID_KEY);
            if (!(id instanceof String text)) {
                context.warn("a weapon behaviour needs an '" + ID_KEY + "' naming it, such as mymod:emp; skipped");
                return null;
            }
            return parse(text, map, context);
        }
        if (raw instanceof String text) {
            return parse(text, Map.of(), context);
        }
        context.warn("expected a weapon behaviour id or a map with an '" + ID_KEY + "' but got "
                + Codecs.describe(raw) + "; skipped");
        return null;
    }

    @Nullable
    private static BehaviourRef parse(String text, Map<String, Object> settings, ReadContext context) {
        ResourceLocation id = text.indexOf(':') > 0 ? ResourceLocation.tryParse(text.trim().toLowerCase(Locale.ROOT))
                : null;
        if (id == null) {
            context.warn("'" + text + "' is not a weapon behaviour id (mod:name); skipped");
            return null;
        }
        return new BehaviourRef(id, settings);
    }

    private Object toYaml() {
        if (this.settings.isEmpty()) {
            return this.id.toString();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(ID_KEY, this.id.toString());
        map.putAll(this.settings);
        return map;
    }
}
