package com.ivomens.mcheli.core.content.schema;

import java.util.function.Function;

/**
 * How one setting is spelled in each format: the YAML value SnakeYAML hands over (a String, Number,
 * Boolean, List or Map), the text after {@code =} on a legacy TXT line, and the YAML the emitter writes.
 * Writing a value and reading it back gives the same value.
 * <p>
 * {@code previous} is the value before this line or key, for the few legacy settings that ignore a value
 * they do not recognise instead of resetting it.
 *
 * @param <T> the value type stored in the definition
 */
public interface ValueCodec<T> {

    T fromYaml(Object raw, T previous, ReadContext context);

    T fromText(String text, T previous, ReadContext context);

    /** The YAML value to write, or null when the value cannot be written and is left out. */
    Object toYaml(T value);

    /** What the YAML value may look like, for the JSON Schema files; anything unless the codec says. */
    default ValueShape shape() {
        return ValueShape.anything();
    }

    /** The same codec, stating what its YAML value may look like. */
    default ValueCodec<T> shaped(ValueShape yamlShape) {
        ValueCodec<T> codec = this;
        return new ValueCodec<>() {
            @Override
            public T fromYaml(Object raw, T previous, ReadContext context) {
                return codec.fromYaml(raw, previous, context);
            }

            @Override
            public T fromText(String text, T previous, ReadContext context) {
                return codec.fromText(text, previous, context);
            }

            @Override
            public Object toYaml(T value) {
                return codec.toYaml(value);
            }

            @Override
            public ValueShape shape() {
                return yamlShape;
            }
        };
    }

    /** A codec whose readers need neither the previous value nor the context. */
    static <T> ValueCodec<T> of(Function<Object, T> yaml, Function<String, T> text, Function<T, Object> write) {
        return new ValueCodec<>() {
            @Override
            public T fromYaml(Object raw, T previous, ReadContext context) {
                return yaml.apply(raw);
            }

            @Override
            public T fromText(String value, T previous, ReadContext context) {
                return text.apply(value);
            }

            @Override
            public Object toYaml(T value) {
                return write.apply(value);
            }
        };
    }

    /**
     * The same spelling with a conversion between the written value and the stored one, for example the
     * mark time that the game keeps one tick longer than written.
     */
    default <R> ValueCodec<R> map(Function<T, R> decode, Function<R, T> encode) {
        ValueCodec<T> written = this;
        return new ValueCodec<>() {
            @Override
            public R fromYaml(Object raw, R previous, ReadContext context) {
                return decode.apply(written.fromYaml(raw, previous == null ? null : encode.apply(previous), context));
            }

            @Override
            public R fromText(String text, R previous, ReadContext context) {
                return decode.apply(written.fromText(text, previous == null ? null : encode.apply(previous), context));
            }

            @Override
            public Object toYaml(R value) {
                return written.toYaml(encode.apply(value));
            }

            @Override
            public ValueShape shape() {
                return written.shape();
            }
        };
    }
}
