package com.ivomens.mcheli.extension;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Map;

/**
 * Turns the YAML values SnakeYAML reads into the NBT tags an addon's settings decoder gets: maps become compound
 * tags, lists list tags, whole numbers int (or long, when too large) tags, other numbers double tags, text string
 * tags and flags byte tags. Null values are left out.
 */
public final class YamlTags {

    private YamlTags() {
    }

    /**
     * The settings map, or an empty tag for null.
     *
     * @throws IllegalArgumentException when the value is not a map, or a list mixes kinds of value
     */
    public static CompoundTag settings(Object yaml) {
        if (yaml == null) {
            return new CompoundTag();
        }
        if (!(yaml instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("expected a map of settings");
        }
        return compound(map);
    }

    private static CompoundTag compound(Map<?, ?> map) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Tag value = tag(entry.getValue());
            if (value != null) {
                tag.put(String.valueOf(entry.getKey()), value);
            }
        }
        return tag;
    }

    private static Tag tag(Object yaml) {
        return switch (yaml) {
            case null -> null;
            case Map<?, ?> map -> compound(map);
            case List<?> list -> list(list);
            case Boolean flag -> ByteTag.valueOf(flag);
            case Integer whole -> IntTag.valueOf(whole);
            case Long whole -> LongTag.valueOf(whole);
            case Number number when isWhole(number) -> LongTag.valueOf(number.longValue());
            case Number number -> DoubleTag.valueOf(number.doubleValue());
            default -> StringTag.valueOf(String.valueOf(yaml));
        };
    }

    private static boolean isWhole(Number number) {
        return number instanceof Short || number instanceof Byte || number instanceof java.math.BigInteger;
    }

    private static ListTag list(List<?> list) {
        ListTag tag = new ListTag();
        for (Object item : list) {
            Tag value = tag(item);
            if (value != null && !tag.addTag(tag.size(), value)) {
                throw new IllegalArgumentException("a list mixes kinds of value: " + list);
            }
        }
        return tag;
    }
}
