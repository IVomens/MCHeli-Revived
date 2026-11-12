package com.ivomens.mcheli.combat.projectile.behaviour;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Turns the settings a weapon file writes next to a behaviour's id, as YAML read them, into the
 * {@link CompoundTag} the behaviour's factory decodes (with a {@code Codec} and {@code NbtOps}, typically): whole
 * numbers become int tags (long tags when they do not fit), other numbers double tags, booleans byte tags, text
 * string tags, lists list tags and maps compound tags.
 */
public final class BehaviourSettings {

    private BehaviourSettings() {
    }

    /**
     * A new tag of the settings; each factory gets its own, so it may keep or change it.
     *
     * @throws IllegalArgumentException for a value NBT cannot hold: a list of mixed kinds of value, or nothing
     */
    public static CompoundTag toTag(Map<String, Object> settings) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            tag.put(String.valueOf(entry.getKey()), toTag(entry.getKey(), entry.getValue()));
        }
        return tag;
    }

    @SuppressWarnings("unchecked")
    private static Tag toTag(Object key, Object value) {
        return switch (value) {
            case Boolean flag -> ByteTag.valueOf(flag);
            case Integer number -> IntTag.valueOf(number);
            case Long number -> LongTag.valueOf(number);
            case BigInteger number -> LongTag.valueOf(number.longValue());
            case Number number -> DoubleTag.valueOf(number.doubleValue());
            case String text -> StringTag.valueOf(text);
            case Map<?, ?> map -> toTag((Map<String, Object>) map);
            case List<?> list -> listTag(key, list);
            case null -> throw new IllegalArgumentException("setting '" + key + "' has no value");
            default -> StringTag.valueOf(value.toString());
        };
    }

    private static ListTag listTag(Object key, List<?> values) {
        ListTag list = new ListTag();
        for (Object value : values) {
            if (!list.addTag(list.size(), toTag(key, value))) {
                throw new IllegalArgumentException("setting '" + key + "' mixes kinds of value in one list");
            }
        }
        return list;
    }
}
