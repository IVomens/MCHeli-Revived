package com.ivomens.mcheli.wingman.block;

import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The marker block's {@code type} state. It reads and writes the same names, in the same order, as an
 * {@code EnumProperty} would; {@link MarkerType} lives in {@code :core}, which does not know Minecraft's
 * {@code StringRepresentable}, so it names its values through {@link MarkerType#serializedName()} instead.
 */
final class MarkerTypeProperty extends Property<MarkerType> {

    private final List<MarkerType> values = List.of(MarkerType.values());
    private final Map<String, MarkerType> byName = Arrays.stream(MarkerType.values())
            .collect(Collectors.toUnmodifiableMap(MarkerType::serializedName, Function.identity()));

    MarkerTypeProperty(String name) {
        super(name, MarkerType.class);
    }

    @Override
    public Collection<MarkerType> getPossibleValues() {
        return this.values;
    }

    @Override
    public String getName(MarkerType value) {
        return value.serializedName();
    }

    @Override
    public Optional<MarkerType> getValue(String name) {
        return Optional.ofNullable(this.byName.get(name));
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MarkerTypeProperty && super.equals(other);
    }

    @Override
    public int generateHashCode() {
        return 31 * super.generateHashCode() + this.values.hashCode();
    }
}
