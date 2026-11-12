package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Damage multipliers by target, YAML {@code DamageFactor}:
 * <pre>
 * DamageFactor:
 *   Player: 0.5
 *   Helicopter: 2.5     # or Heli
 *   Tank: 5.0           # also Plane, Ship, Vehicle, or an entity class name
 * </pre>
 * Legacy TXT adds one target per line, {@code DamageFactor = heli, 2}, clamped to 0..1000000; an unknown
 * target is ignored there. YAML keeps any other name as an entity class name, resolved when the weapon is
 * used.
 */
public final class DamageFactors {

    public static final String PLAYER = "Player";
    public static final String HELICOPTER = "Helicopter";
    public static final String PLANE = "Plane";
    public static final String SHIP = "Ship";
    public static final String TANK = "Tank";
    public static final String VEHICLE = "Vehicle";

    /** The written target names and the target each one means. */
    private static final Map<String, String> TARGET_NAMES = Map.of(
            "player", PLAYER,
            "heli", HELICOPTER,
            "helicopter", HELICOPTER,
            "plane", PLANE,
            "ship", SHIP,
            "tank", TANK,
            "vehicle", VEHICLE);

    private static final float MAX_TXT_FACTOR = 1_000_000.0F;

    /**
     * Factors are read as written; the YAML reader used to drop the fraction, which made a converted
     * {@code DamageFactor = player, 0.5} line come back as 0.
     */
    public static final ValueCodec<Map<String, Float>> CODEC = new ValueCodec<>() {
        @Override
        public Map<String, Float> fromYaml(Object raw, Map<String, Float> previous, ReadContext context) {
            Map<String, Float> factors = new LinkedHashMap<>();
            Codecs.map(raw).forEach((target, factor) -> factors.put(yamlTarget(target),
                    Codecs.number(factor).floatValue()));
            return factors.isEmpty() ? previous : factors;
        }

        @Override
        public Map<String, Float> fromText(String text, Map<String, Float> previous, ReadContext context) {
            throw new UnsupportedOperationException("TXT damage factors are read by a TXT rule");
        }

        @Override
        public ValueShape shape() {
            return ValueShape.mapOf(ValueShape.number()).note("Target to damage multiplier: Player, Helicopter "
                    + "(or Heli), Plane, Ship, Tank, Vehicle, or an entity class name.");
        }

        @Override
        public Object toYaml(Map<String, Float> factors) {
            return new LinkedHashMap<>(factors);
        }
    };

    private DamageFactors() {
    }

    /** A legacy TXT {@code DamageFactor} line added to {@code factors}; fewer than two values are ignored. */
    static Map<String, Float> withTxtLine(Map<String, Float> factors, String text, ReadContext context) {
        String[] parts = Codecs.splitTxt(text);
        String target = parts.length >= 2 ? TARGET_NAMES.get(parts[0].toLowerCase(Locale.ROOT)) : null;
        if (target == null) {
            return factors;
        }
        Map<String, Float> changed = new LinkedHashMap<>(factors);
        changed.put(target, Codecs.clamp(Codecs.parseFloat(parts[1]), 0.0F, MAX_TXT_FACTOR, context));
        return changed;
    }

    /** YAML names are case sensitive: {@code Heli} means a helicopter, {@code heli} a class named so. */
    private static String yamlTarget(String written) {
        return written.equals("Heli") ? HELICOPTER : written;
    }
}
