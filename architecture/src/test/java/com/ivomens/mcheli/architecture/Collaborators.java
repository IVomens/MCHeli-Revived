package com.ivomens.mcheli.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;

import java.util.ArrayList;
import java.util.List;

/**
 * The collaborators a component's constructor takes, for the fan-out limit (docs/ARCHITECTURE_RULES.md,
 * "Components").
 * <p>
 * A parameter is a collaborator when its type is one of the mod's classes or interfaces that has
 * behaviour of its own: another component, or a narrow port such as {@code AircraftCamera} or a
 * sound player. A parameter is not a collaborator when it is
 * <ul>
 *     <li>the host, a Minecraft {@code Entity} (the vehicle entity included);</li>
 *     <li>a plain value: a primitive, a String, an enum, a record (a definition, a setting), or a
 *     {@code SyncedValue} slot of the entity's synced data;</li>
 *     <li>anything from the settings snapshot ({@code vehicle.settings}) or {@code :core};</li>
 *     <li>a Java or Minecraft type (a callback, an entity data key).</li>
 * </ul>
 * A generic parameter counts when one of its type arguments would: a {@code Supplier<WeaponComponent>}
 * is the weapon component, taken late; a {@code Supplier<CarriageRules>} is a setting.
 */
final class Collaborators {

    /** The most collaborators a component's constructor may take. */
    static final int LIMIT = 7;

    private static final String ENTITY = "net.minecraft.world.entity.Entity";
    private static final String SYNCED_VALUE = ComponentGraph.MOD + ".vehicle.component.SyncedValue";
    private static final String SETTINGS = ComponentGraph.MOD + ".vehicle.settings";
    private static final String CORE = ComponentGraph.MOD + ".core";

    private Collaborators() {
    }

    /** The collaborators of the component: those of its constructor that takes the most. */
    static List<String> of(JavaClass component) {
        List<String> most = List.of();
        for (JavaConstructor constructor : component.getConstructors()) {
            List<String> found = new ArrayList<>();
            for (JavaType parameter : constructor.getParameterTypes()) {
                if (isCollaborator(parameter)) {
                    found.add(parameter.toErasure().getSimpleName());
                }
            }
            if (found.size() > most.size()) {
                most = found;
            }
        }
        return most;
    }

    private static boolean isCollaborator(JavaType type) {
        if (type instanceof JavaParameterizedType parameterized
                && parameterized.getActualTypeArguments().stream().anyMatch(Collaborators::isCollaborator)) {
            return true;
        }
        JavaClass raw = type.toErasure();
        String name = raw.getName();
        if (!name.startsWith(ComponentGraph.MOD + ".") || raw.isPrimitive() || raw.isArray()) {
            return false;
        }
        return !raw.isAssignableTo(ENTITY) && !raw.isRecord() && !raw.isEnum() && !name.equals(SYNCED_VALUE)
                && !raw.getPackageName().startsWith(SETTINGS) && !raw.getPackageName().startsWith(CORE);
    }
}
