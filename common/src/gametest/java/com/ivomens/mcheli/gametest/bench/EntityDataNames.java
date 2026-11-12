package com.ivomens.mcheli.gametest.bench;

import net.minecraft.network.syncher.EntityDataAccessor;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Names the synced values of an entity by the static {@link EntityDataAccessor} field that declares
 * them ({@code VehicleEntity#THROTTLE}), for the benchmark's breakdown of entity data packets.
 */
final class EntityDataNames {

    private final Map<Class<?>, Map<Integer, String>> byClass = new HashMap<>();

    String name(@Nullable Object entity, int id) {
        if (entity == null) {
            return "(removed entity) #" + id;
        }
        String name = this.byClass.computeIfAbsent(entity.getClass(), EntityDataNames::scan).get(id);
        return name != null ? name : entity.getClass().getSimpleName() + " #" + id;
    }

    private static Map<Integer, String> scan(Class<?> type) {
        Map<Integer, String> names = new HashMap<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != EntityDataAccessor.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    EntityDataAccessor<?> accessor = (EntityDataAccessor<?>) field.get(null);
                    names.putIfAbsent(accessor.id(), c.getSimpleName() + "#" + field.getName());
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // a field we cannot read keeps its number
                }
            }
        }
        return names;
    }
}
