package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.extension.BehaviourType;
import com.ivomens.mcheli.extension.ConfiguredBehaviour;
import com.ivomens.mcheli.extension.ConfiguredMovement;
import com.ivomens.mcheli.extension.ExtensionRegistry;
import com.ivomens.mcheli.extension.MovementType;
import com.ivomens.mcheli.extension.VehicleAddons;
import com.ivomens.mcheli.extension.YamlTags;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The settings that hand a vehicle to other mods: the behaviours it opts in to and the movement model it picks,
 * both by the id a mod registered through the API. What each one's settings mean is up to the mod that
 * registered it, so the schema accepts any map there. YAML only; legacy TXT files cannot name either.
 * <pre>
 * Behaviours:
 *   mymod:winch: { Length: 20 }
 *   mymod:beacon: {}
 * Movement: { Model: mymod:hovercraft, Settings: { HoverHeight: 1.5 } }
 * </pre>
 */
public final class AddonSchema {

    /** Ids are written {@code namespace:path}. */
    private static final String ID_PATTERN = "^[a-z0-9_.-]+:[a-z0-9_./-]+$";
    private static final String MODEL = "Model";
    private static final String SETTINGS = "Settings";
    private static final ValueShape SETTINGS_SHAPE = ValueShape.mapOf(ValueShape.anything());

    /**
     * Behaviours other mods add to the vehicle, by the id the mod registered (namespace:path), each with the
     * settings that mod reads; write {} for none. An id no installed mod registers is reported and ignored.
     */
    static final Field<VehicleFile, List<ConfiguredBehaviour<?>>> BEHAVIOURS = Field.of("Behaviours",
            new BehavioursCodec(), List.of(), (VehicleFile file) -> file.addons().behaviours()).alias("behaviours");
    /**
     * The movement model of another mod that moves the vehicle instead of its kind's own physics: its id
     * (namespace:path), or a map of the Model id and the Settings that mod reads. Only ground vehicles
     * (vehicles/) take one. An id no installed mod registers is reported, and the vehicle moves as its kind does.
     */
    static final Field<VehicleFile, ConfiguredMovement<?>> MOVEMENT = Field.of("Movement", new MovementCodec(), null,
            (VehicleFile file) -> file.addons().movement()).alias("movement");

    private AddonSchema() {
    }

    static VehicleAddons build(Values values, boolean takesMovement) {
        List<ConfiguredBehaviour<?>> behaviours = values.get(BEHAVIOURS);
        ConfiguredMovement<?> movement = takesMovement ? values.get(MOVEMENT) : null;
        return behaviours.isEmpty() && movement == null ? VehicleAddons.NONE
                : new VehicleAddons(behaviours, movement);
    }

    /** The id a key or value names, or null with a warning when it is not written {@code namespace:path}. */
    @Nullable
    private static ResourceLocation id(Object raw, String what, ReadContext context) {
        String text = String.valueOf(raw);
        ResourceLocation id = text.indexOf(':') > 0 ? ResourceLocation.tryParse(text) : null;
        if (id == null) {
            context.warn("'" + text + "' is not a " + what + " id; write it as namespace:path, such as mymod:"
                    + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_") + "; it is ignored");
        }
        return id;
    }

    private static String known(Set<ResourceLocation> ids) {
        return ids.isEmpty() ? "no installed mod registers any" : "registered: " + ids;
    }

    /** Reads {@code Behaviours}: a map of ids to settings maps. */
    private static final class BehavioursCodec implements ValueCodec<List<ConfiguredBehaviour<?>>> {

        @Override
        public List<ConfiguredBehaviour<?>> fromYaml(Object raw, List<ConfiguredBehaviour<?>> previous,
                                                     ReadContext context) {
            if (!(raw instanceof Map<?, ?> map)) {
                context.warn("expected a map of behaviour ids to their settings, it is ignored");
                return List.of();
            }
            ExtensionRegistry registry = ExtensionRegistry.get();
            List<ConfiguredBehaviour<?>> behaviours = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                ResourceLocation id = id(entry.getKey(), "behaviour", context);
                if (id == null) {
                    continue;
                }
                Optional<BehaviourType<?>> type = registry.behaviour(id);
                if (type.isEmpty()) {
                    context.warn("unknown behaviour '" + id + "' (" + known(registry.behaviourIds())
                            + "), it is ignored");
                    continue;
                }
                try {
                    behaviours.add(type.get().configure(YamlTags.settings(entry.getValue()), entry.getValue()));
                } catch (RuntimeException e) {
                    context.warn("behaviour '" + id + "' rejected its settings (" + e.getMessage()
                            + "), it is left out");
                }
            }
            return List.copyOf(behaviours);
        }

        @Override
        public List<ConfiguredBehaviour<?>> fromText(String text, List<ConfiguredBehaviour<?>> previous,
                                                     ReadContext context) {
            return previous;
        }

        @Override
        public Object toYaml(List<ConfiguredBehaviour<?>> value) {
            Map<String, Object> written = new LinkedHashMap<>();
            for (ConfiguredBehaviour<?> behaviour : value) {
                written.put(behaviour.id().toString(), behaviour.yaml() != null ? behaviour.yaml() : Map.of());
            }
            return written;
        }

        @Override
        public ValueShape shape() {
            return ValueShape.mapOf(SETTINGS_SHAPE)
                    .note("Behaviour ids (namespace:path) to their settings maps; the mod that adds a behaviour "
                            + "documents its settings.");
        }
    }

    /** Reads {@code Movement}: an id, or a map of {@code Model} and {@code Settings}. */
    private static final class MovementCodec implements ValueCodec<ConfiguredMovement<?>> {

        @Override
        public ConfiguredMovement<?> fromYaml(Object raw, ConfiguredMovement<?> previous, ReadContext context) {
            Object model = raw;
            Object settings = null;
            if (raw instanceof Map<?, ?> map) {
                for (Object key : map.keySet()) {
                    if (!MODEL.equals(key) && !SETTINGS.equals(key)) {
                        context.warn("unknown key '" + key + "' (a movement model takes Model and Settings), "
                                + "it is ignored");
                    }
                }
                model = map.get(MODEL);
                settings = map.get(SETTINGS);
                if (model == null) {
                    context.warn("Model is missing, so the vehicle moves as its kind does");
                    return null;
                }
            }
            ResourceLocation id = id(model, "movement model", context);
            if (id == null) {
                return null;
            }
            ExtensionRegistry registry = ExtensionRegistry.get();
            Optional<MovementType<?>> type = registry.movement(id);
            if (type.isEmpty()) {
                context.warn("unknown movement model '" + id + "' (" + known(registry.movementIds())
                        + "), the vehicle moves as its kind does");
                return null;
            }
            try {
                return type.get().configure(YamlTags.settings(settings), settings);
            } catch (RuntimeException e) {
                context.warn("movement model '" + id + "' rejected its settings (" + e.getMessage()
                        + "), the vehicle moves as its kind does");
                return null;
            }
        }

        @Override
        public ConfiguredMovement<?> fromText(String text, ConfiguredMovement<?> previous, ReadContext context) {
            return previous;
        }

        @Override
        public Object toYaml(ConfiguredMovement<?> value) {
            if (value.yaml() == null) {
                return value.id().toString();
            }
            Map<String, Object> written = new LinkedHashMap<>();
            written.put(MODEL, value.id().toString());
            written.put(SETTINGS, value.yaml());
            return written;
        }

        @Override
        public ValueShape shape() {
            ValueShape id = ValueShape.text(ID_PATTERN);
            return ValueShape.either(id, ValueShape.keys().required(MODEL, id).key(SETTINGS, SETTINGS_SHAPE).build())
                    .note("A movement model id (namespace:path), or Model and the Settings of the mod that adds it.");
        }
    }
}
