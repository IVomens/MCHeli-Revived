package com.ivomens.mcheli.block;

import net.minecraft.world.level.block.state.properties.EnumProperty;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PropertyDirection8 extends EnumProperty<EnumDirection8> {

    protected PropertyDirection8(String name, Collection<EnumDirection8> allowedValues) {
        super(name, EnumDirection8.class, allowedValues);
    }

    public static PropertyDirection8 create(String name) {
        return create(name, d -> true);
    }

    public static PropertyDirection8 create(String name, Predicate<EnumDirection8> filter) {
        return create(name, Arrays.stream(EnumDirection8.values()).filter(filter).collect(Collectors.toList()));
    }

    public static PropertyDirection8 create(String name, Collection<EnumDirection8> allowedValues) {
        return new PropertyDirection8(name, allowedValues);
    }
}
