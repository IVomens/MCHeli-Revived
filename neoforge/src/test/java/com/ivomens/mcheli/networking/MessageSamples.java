package com.ivomens.mcheli.networking;

import com.ivomens.mcheli.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Sample values for message records, built from the record components' types. Values are fixed per
 * component position, never random, so the golden file is stable. Lists and arrays get two elements.
 */
final class MessageSamples {

    private static final int SAMPLE_LENGTH = 2;

    private MessageSamples() {
    }

    /** A record of {@code type} with a sample in every component. */
    static Record record(Class<?> type, int seed) {
        RecordComponent[] components = type.getRecordComponents();
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            values[i] = sample(components[i].getType(), components[i].getGenericType(), seed + i + 1);
        }
        return construct(type, values);
    }

    /** {@code original} with component {@code index} replaced by {@code value}. */
    static Record with(Record original, int index, Object value) {
        RecordComponent[] components = original.getClass().getRecordComponents();
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            values[i] = i == index ? value : get(components[i], original);
        }
        return construct(original.getClass(), values);
    }

    static Object get(RecordComponent component, Object owner) {
        try {
            var accessor = component.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(owner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static Object sample(Class<?> type, Type genericType, int seed) {
        if (type == int.class || type == Integer.class) return 1000 + seed * 7;
        if (type == long.class || type == Long.class) return 5_000_000_000L + seed;
        if (type == short.class || type == Short.class) return (short) (300 + seed);
        if (type == byte.class || type == Byte.class) return (byte) (10 + seed);
        if (type == float.class || type == Float.class) return 1.5F + seed * 0.25F;
        if (type == double.class || type == Double.class) return -2.75 + seed * 1.125;
        if (type == boolean.class || type == Boolean.class) return seed % 2 == 1;
        if (type == String.class) return "sample-" + seed;
        if (type == UUID.class) return new UUID(0x1234L, seed);
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants[seed % constants.length];
        }
        if (type == Vec3.class) return new Vec3(seed + 0.5, -seed - 0.25, seed * 2.0);
        if (type == BlockPos.class) return new BlockPos(seed, 64 + seed, -seed);
        if (type == ResourceLocation.class) return Ids.of("sample_" + seed);
        if (type == ItemStack.class) return new ItemStack(Items.IRON_INGOT, 1 + seed % 16);
        if (type == Component.class) return Component.literal("sample text " + seed);
        if (type == Optional.class) return Optional.of(sample(typeArgument(genericType), typeArgument(genericType), seed));
        if (type.isArray()) return array(type.getComponentType(), SAMPLE_LENGTH, seed);
        if (type == List.class) return list(typeArgument(genericType), SAMPLE_LENGTH, seed);
        if (type.isRecord()) return record(type, seed * 10);
        throw new IllegalArgumentException("No sample for " + genericType.getTypeName() + "; add one to MessageSamples");
    }

    static Object array(Class<?> component, int length, int seed) {
        Object array = Array.newInstance(component, length);
        for (int i = 0; i < length; i++) {
            Array.set(array, i, sample(component, component, seed + i));
        }
        return array;
    }

    static List<Object> list(Class<?> element, int length, int seed) {
        Object first = sample(element, element, seed);
        List<Object> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            // One sample repeated: a long list is about its length, and records may be slow to build.
            list.add(i < SAMPLE_LENGTH ? sample(element, element, seed + i) : first);
        }
        return list;
    }

    static Class<?> typeArgument(Type genericType) {
        if (genericType instanceof ParameterizedType parameterized) {
            Type argument = parameterized.getActualTypeArguments()[0];
            if (argument instanceof Class<?> element) return element;
            if (argument instanceof ParameterizedType nested) return (Class<?>) nested.getRawType();
        }
        throw new IllegalArgumentException("No element type in " + genericType.getTypeName());
    }

    /** Equality that looks inside records, arrays, lists and item stacks. */
    static boolean deepEquals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof ItemStack x && b instanceof ItemStack y) return ItemStack.matches(x, y);
        if (a.getClass().isArray() && b.getClass().isArray()) {
            int length = Array.getLength(a);
            if (length != Array.getLength(b)) return false;
            for (int i = 0; i < length; i++) {
                if (!deepEquals(Array.get(a, i), Array.get(b, i))) return false;
            }
            return true;
        }
        if (a instanceof List<?> x && b instanceof List<?> y) {
            if (x.size() != y.size()) return false;
            for (int i = 0; i < x.size(); i++) {
                if (!deepEquals(x.get(i), y.get(i))) return false;
            }
            return true;
        }
        if (a instanceof Optional<?> x && b instanceof Optional<?> y) {
            return x.isPresent() == y.isPresent() && (x.isEmpty() || deepEquals(x.get(), y.get()));
        }
        if (a instanceof Record && a.getClass() == b.getClass()) {
            return Arrays.stream(a.getClass().getRecordComponents())
                    .allMatch(component -> deepEquals(get(component, a), get(component, b)));
        }
        return Objects.equals(a, b);
    }

    private static Record construct(Class<?> type, Object[] values) {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        try {
            var constructor = type.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return (Record) constructor.newInstance(values);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build " + type.getName(), e);
        }
    }
}
