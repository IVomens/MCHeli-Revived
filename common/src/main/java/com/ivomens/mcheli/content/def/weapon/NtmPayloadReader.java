package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.content.def.weapon.NtmSections.Fire;
import com.ivomens.mcheli.content.def.weapon.NtmSections.MiniNuke;
import com.ivomens.mcheli.content.def.weapon.NtmSections.Mist;
import com.ivomens.mcheli.content.def.weapon.NtmSections.Nt;
import com.ivomens.mcheli.content.def.weapon.NtmSections.Vnt;
import com.ivomens.mcheli.content.def.weapon.NtmSections.VntEffect;
import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ContentValueException;
import com.ivomens.mcheli.core.content.schema.ReadContext;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reads the YAML {@code NTM} section into an {@link NtmPayload}:
 * <pre>
 * NTM:
 *   PayloadType: VNT        # or Type; VNT, NT, NUKE, MININUKE, CHEMICAL, MIST, FIRE
 *   EffectOnly: false       # only the particle effect, with the payload's explosion sound
 *   VNT: { Preset: ..., Effect: LARGE | STANDARD | MEDIUM | LOW | {IsSmall: ..., CloudCount: ...},
 *          Attributes: {...} }
 *   NT: { Resolution: 16, Attributes: [...] }
 *   MiniNuke: SAFE | TOTS | LOW | MEDIUM | HIGH | {UseTorex: ..., BlastRadius: ..., ...}   # or Muke
 *   Mist: { FluidType: ..., CloudCount: ..., Width: ..., Height: ..., AreaSpread: ..., Lifetime: ... }
 *   Fire: { FireType: ..., Count: ..., Width: ..., Height: ..., AreaSpread: ..., Lifetime: ... }
 * </pre>
 * Keys are read in file order: a key written twice (or under two spellings) keeps its last value, and an
 * unknown key is reported where it stands.
 */
final class NtmPayloadReader {

    private static final int DEFAULT_NT_RESOLUTION = 16;

    private static final Map<String, MiniNuke> MINI_NUKE_PRESETS = Map.of(
            "SAFE", MiniNuke.SAFE,
            "TOTS", MiniNuke.TOTS,
            "LOW", MiniNuke.LOW,
            "MEDIUM", MiniNuke.MEDIUM,
            "HIGH", MiniNuke.HIGH);

    private static final Map<String, Supplier<VntEffect>> VNT_EFFECT_PRESETS = Map.of(
            "LARGE", VntEffect::large,
            "STANDARD", VntEffect::standard,
            "MEDIUM", VntEffect::medium,
            "LOW", VntEffect::medium);

    private static final Map<String, Key> VNT_EFFECT_KEYS = keys(
            key("IsSmall", NtmPayloadReader::flag),
            key("CloudCount", NtmPayloadReader::whole),
            key("CloudScale", NtmPayloadReader::decimal),
            key("CloudSpeedMult", NtmPayloadReader::decimal),
            key("WaveScale", NtmPayloadReader::decimal),
            key("DebrisCount", NtmPayloadReader::whole),
            key("DebrisSize", NtmPayloadReader::whole),
            key("DebrisRetry", NtmPayloadReader::whole),
            key("DebrisVelocity", NtmPayloadReader::decimal),
            key("DebrisHorizontalDeviation", NtmPayloadReader::decimal),
            key("DebrisVerticalOffset", NtmPayloadReader::decimal),
            key("SoundRange", NtmPayloadReader::decimal));

    private static final Map<String, Key> FIRE_KEYS = keys(
            key("FireType", value -> Codecs.text(value).trim()),
            alias("Type", "FireType", value -> Codecs.text(value).trim()),
            key("Count", NtmPayloadReader::whole),
            key("Width", NtmPayloadReader::decimal),
            key("Height", NtmPayloadReader::decimal),
            key("AreaSpread", NtmPayloadReader::whole),
            alias("Spread", "AreaSpread", NtmPayloadReader::whole),
            key("Lifetime", NtmPayloadReader::whole),
            key("LifetimeVariance", NtmPayloadReader::whole),
            key("Particles", NtmPayloadReader::flag));

    private static final Map<String, Key> MIST_KEYS = keys(
            key("FluidType", NtmPayloadReader::fluid),
            alias("Fluid", "FluidType", NtmPayloadReader::fluid),
            key("CloudCount", NtmPayloadReader::whole),
            alias("Count", "CloudCount", NtmPayloadReader::whole),
            key("Width", NtmPayloadReader::decimal),
            key("Height", NtmPayloadReader::decimal),
            key("AreaSpread", NtmPayloadReader::whole),
            alias("Spread", "AreaSpread", NtmPayloadReader::whole),
            key("Lifetime", NtmPayloadReader::whole),
            key("LifetimeVariance", NtmPayloadReader::whole));

    private static final Map<String, Key> MINI_NUKE_KEYS = keys(
            key("UseTorex", NtmPayloadReader::flag),
            key("Safe", NtmPayloadReader::flag),
            key("BlastRadius", NtmPayloadReader::decimal),
            key("KillRadius", NtmPayloadReader::decimal),
            key("RadiationLevel", NtmPayloadReader::decimal),
            key("Particle", Codecs::text),
            key("ShrapnelCount", NtmPayloadReader::whole),
            key("Resolution", NtmPayloadReader::whole),
            alias("Res", "Resolution", NtmPayloadReader::whole),
            key("Attributes", NtmPayloadReader::textList),
            alias("Attrib", "Attributes", NtmPayloadReader::textList));

    private final ReadContext context;

    private NtmPayloadReader(ReadContext context) {
        this.context = context;
    }

    static NtmPayload read(Map<String, Object> section, ReadContext context) {
        NtmPayloadReader reader = new NtmPayloadReader(context);
        Map<String, Key> keys = keys(
                key("PayloadType", value -> NtmPayloadType.parse(Codecs.text(value))),
                alias("Type", "PayloadType", value -> NtmPayloadType.parse(Codecs.text(value))),
                key("EffectOnly", NtmPayloadReader::flag),
                key("VNT", value -> reader.vnt(Codecs.map(value))),
                key("NT", value -> nt(Codecs.map(value))),
                key("MiniNuke", reader::miniNuke),
                alias("Muke", "MiniNuke", reader::miniNuke),
                key("Mist", value -> reader.mist(Codecs.map(value))),
                key("Fire", value -> reader.fire(Codecs.map(value))),
                alias("Phosphorus", "Fire", value -> reader.fire(Codecs.map(value))),
                alias("Splash", "Fire", value -> reader.fire(Codecs.map(value))));
        Values values = reader.collect(section, keys, "NTM");
        return new NtmPayload(section, values.get("PayloadType", NtmPayloadType.NONE),
                values.get("EffectOnly", false), values.get("VNT", null), values.get("NT", null),
                values.get("MiniNuke", null), values.get("Mist", null), values.get("Fire", null));
    }

    private Vnt vnt(Map<String, Object> section) {
        Map<String, Object> attributes = section.get("Attributes") instanceof Map<?, ?>
                ? Codecs.map(section.get("Attributes")) : new HashMap<>();
        Map<String, Key> keys = keys(
                // the attributes were read above
                key("Attributes", value -> null),
                key("Preset", value -> value instanceof String preset ? preset : null),
                key("Effect", this::vntEffect));
        Values values = collect(section, keys, "NTM.VNT");
        return new Vnt(attributes, values.get("Preset", null), values.get("Effect", VntEffect.standard()));
    }

    /** A preset name, or a section of keys over the standard effect; anything else is ignored. */
    @Nullable
    private VntEffect vntEffect(Object value) {
        if (value instanceof String preset) {
            Supplier<VntEffect> effect = VNT_EFFECT_PRESETS.get(preset.toUpperCase(Locale.ROOT));
            if (effect == null) {
                throw new ContentValueException("unknown VNT effect '" + preset
                        + "', expected one of LARGE, STANDARD, MEDIUM, LOW");
            }
            return effect.get();
        }
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        Values values = collect(Codecs.map(value), VNT_EFFECT_KEYS, "NTM.VNT.Effect");
        VntEffect base = VntEffect.standard();
        return new VntEffect(values.get("IsSmall", base.small()), values.get("CloudCount", base.cloudCount()),
                values.get("CloudScale", base.cloudScale()), values.get("CloudSpeedMult", base.cloudSpeedMult()),
                values.get("WaveScale", base.waveScale()), values.get("DebrisCount", base.debrisCount()),
                values.get("DebrisSize", base.debrisSize()), values.get("DebrisRetry", base.debrisRetry()),
                values.get("DebrisVelocity", base.debrisVelocity()),
                values.get("DebrisHorizontalDeviation", base.debrisHorizontalDeviation()),
                values.get("DebrisVerticalOffset", base.debrisVerticalOffset()),
                values.get("SoundRange", base.soundRange()));
    }

    /** {@code Resolution} (or {@code Res}, default 16) and {@code Attributes} (or {@code Attrib}). */
    private static Nt nt(Map<String, Object> section) {
        Object resolution = section.containsKey("Resolution") ? section.get("Resolution") : section.get("Res");
        Object attributes = section.containsKey("Attributes") ? section.get("Attributes") : section.get("Attrib");
        return new Nt(attributes == null ? List.of() : Codecs.list(attributes).stream().map(Codecs::text).toList(),
                resolution == null ? DEFAULT_NT_RESOLUTION : whole(resolution));
    }

    private MiniNuke miniNuke(Object value) {
        if (value instanceof String preset) {
            MiniNuke nuke = MINI_NUKE_PRESETS.get(preset.toUpperCase(Locale.ROOT).trim());
            if (nuke == null) {
                throw new ContentValueException("unknown mini nuke preset '" + preset
                        + "', expected one of SAFE, TOTS, LOW, MEDIUM, HIGH");
            }
            return nuke;
        }
        if (!(value instanceof Map<?, ?>)) {
            throw new ContentValueException("expected a mini nuke preset or a section of keys but got "
                    + Codecs.describe(value));
        }
        Values values = collect(Codecs.map(value), MINI_NUKE_KEYS, "NTM.MiniNuke");
        MiniNuke base = MiniNuke.DEFAULTS;
        return new MiniNuke(values.get("UseTorex", base.useTorex()), values.get("Safe", base.safe()),
                values.get("BlastRadius", base.blastRadius()), values.get("KillRadius", base.killRadius()),
                values.get("RadiationLevel", base.radiationLevel()), values.get("Particle", base.particle()),
                values.get("ShrapnelCount", base.shrapnelCount()), values.get("Resolution", base.resolution()),
                values.get("Attributes", base.attributes()));
    }

    private Mist mist(Map<String, Object> section) {
        Values values = collect(section, MIST_KEYS, "NTM.Mist");
        Mist base = Mist.DEFAULTS;
        return new Mist(values.get("FluidType", base.fluidType()), values.get("CloudCount", base.cloudCount()),
                values.get("Width", base.width()), values.get("Height", base.height()),
                values.get("AreaSpread", base.areaSpread()), values.get("Lifetime", base.lifetime()),
                values.get("LifetimeVariance", base.lifetimeVariance()));
    }

    private Fire fire(Map<String, Object> section) {
        Values values = collect(section, FIRE_KEYS, "NTM.Fire");
        Fire base = Fire.DEFAULTS;
        return new Fire(values.get("FireType", base.fireType()), values.get("Count", base.count()),
                values.get("Width", base.width()), values.get("Height", base.height()),
                values.get("AreaSpread", base.areaSpread()), values.get("Lifetime", base.lifetime()),
                values.get("LifetimeVariance", base.lifetimeVariance()), values.get("Particles", base.particles()));
    }

    /** Reads the section's keys in file order; a key whose reader returns null leaves the value unset. */
    private Values collect(Map<String, Object> section, Map<String, Key> keys, String path) {
        Map<String, Object> values = new HashMap<>();
        section.forEach((name, raw) -> {
            Key key = keys.get(name);
            if (key == null) {
                this.context.warn("unknown key '" + path + "." + name + "', ignored");
                return;
            }
            Object value = key.reader().apply(raw);
            if (value != null) {
                values.put(key.setting(), value);
            }
        });
        return new Values(values);
    }

    /**
     * A key of a section.
     *
     * @param name    the key as written
     * @param setting the setting it gives; aliases give the setting of their main spelling
     * @param reader  reads the written value, or returns null to ignore it
     */
    private record Key(String name, String setting, Function<Object, Object> reader) {
    }

    private static Key key(String name, Function<Object, Object> reader) {
        return new Key(name, name, reader);
    }

    private static Key alias(String name, String setting, Function<Object, Object> reader) {
        return new Key(name, setting, reader);
    }

    private static Map<String, Key> keys(Key... keys) {
        Map<String, Key> byName = new LinkedHashMap<>();
        for (Key key : keys) {
            byName.put(key.name(), key);
        }
        return byName;
    }

    /** The values a section set, by setting. */
    private record Values(Map<String, Object> bySetting) {

        @SuppressWarnings("unchecked")
        <T> T get(String setting, T fallback) {
            return (T) this.bySetting.getOrDefault(setting, fallback);
        }
    }

    private static boolean flag(Object value) {
        return Codecs.require(value, Boolean.class, "true/false");
    }

    private static int whole(Object value) {
        return Codecs.number(value).intValue();
    }

    private static float decimal(Object value) {
        return Codecs.number(value).floatValue();
    }

    private static String fluid(Object value) {
        return Codecs.text(value).toUpperCase(Locale.ROOT).trim();
    }

    /** A list of names; any other value is ignored. */
    @Nullable
    private static List<String> textList(Object value) {
        return value instanceof List<?> list ? list.stream().map(Object::toString).toList() : null;
    }
}
