package com.ivomens.mcheli.networking;

import com.ivomens.mcheli.golden.Golden;
import com.ivomens.mcheli.golden.MinecraftBootstrap;
import com.ivomens.mcheli.setup.McheliMessages;
import com.ivomens.mcheli.setup.client.McheliClientMessages;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Codec round trip of every message (docs/ARCHITECTURE.md Part 2, §5.9). Each registered message
 * record is filled with sample values, encoded and decoded, and must come back equal with no bytes
 * left over. Every string, list and array inside a message, nested records included, must have a
 * bound: the test finds the largest length the codec still writes and fails when there is none. The
 * golden file lists each message with its encoded size and the bound of each such field.
 */
class MessageCodecTest {

    /** Longer than any bound we use; a field that still encodes at this length is unbounded. */
    private static final int PROBE_LIMIT = 70_000;

    /** A path step that stands for the first element of a list of strings. */
    private static final int FIRST_ELEMENT = -1;

    private static List<Declared> messages;

    /** A registered type with the record class that declares it. */
    private record Declared(MessageType<?> type, Class<? extends Message> recordClass) {
    }

    @BeforeAll
    static void collect() {
        MinecraftBootstrap.ensure();
        Map<MessageType<?>, Class<? extends Message>> classes = new HashMap<>();
        for (Class<? extends Message> type : messageClasses()) {
            classes.put(declaredType(type), type);
        }
        List<Declared> declared = new ArrayList<>();
        for (MessageType<?> type : McheliMessages.create().types()) {
            Class<? extends Message> recordClass = classes.get(type);
            assertTrue(recordClass != null, type.id() + " is registered but no record declares it as TYPE");
            declared.add(new Declared(type, recordClass));
        }
        declared.sort(Comparator.comparing(entry -> entry.type().id().toString()));
        messages = List.copyOf(declared);
    }

    @Test
    void everyMessageRoundTripsAndEveryLengthIsBounded() {
        Golden.check("network/packets.txt", "{\"schema\":2,\"messages\":" + messages.size()
                + ",\"what\":\"encoded size of a sample of each message, and the largest length each string,"
                + " list and array field accepts\",\"commit\":\"" + Golden.commit() + "\"}", () -> {
            StringBuilder report = new StringBuilder();
            messages.forEach(message -> report.append(describe(message)));
            return report.toString();
        });
    }

    /** Every class implementing {@link Message} is a record with a {@code TYPE}, registered exactly once. */
    @Test
    void everyMessageClassIsARegisteredRecord() {
        List<MessageType<?>> registered = McheliMessages.create().types();
        for (Class<? extends Message> type : messageClasses()) {
            assertTrue(type.isRecord(), type.getName() + " is a message but not a record");
            assertTrue(ServerboundMessage.class.isAssignableFrom(type) != ClientboundMessage.class.isAssignableFrom(type),
                    type.getName() + " must be either serverbound or clientbound");
            MessageType<?> declared = declaredType(type);
            assertEquals(1, registered.stream().filter(candidate -> candidate == declared).count(),
                    type.getName() + ".TYPE is not registered exactly once");
        }
    }

    @Test
    void everyClientboundMessageHasAClientHandler() {
        MessageRegistry registry = McheliMessages.create();
        McheliClientMessages.register(registry);
        assertEquals(List.of(), registry.clientboundWithoutHandler());
    }

    private static String describe(Declared message) {
        MessageType<?> type = message.type();
        StringBuilder text = new StringBuilder(type.id().getPath()).append(" (")
                .append(type.direction().name().toLowerCase()).append(", ")
                .append(message.recordClass().getSimpleName()).append(")\n");
        Record sample = MessageSamples.record(message.recordClass(), 0);
        RegistryFriendlyByteBuf buf = buffer();
        encode(type, sample, buf);
        int bytes = buf.readableBytes();
        Object copy = type.codec().decode(buf);
        assertTrue(MessageSamples.deepEquals(sample, copy), type.id() + " does not survive a round trip: "
                + sample + " -> " + copy);
        assertEquals(0, buf.readableBytes(), type.id() + " leaves bytes unread");
        text.append("  bytes: ").append(bytes).append('\n');
        for (LengthField field : lengthFields(message.recordClass(), "")) {
            int max = largestAccepted(type, sample, field);
            assertTrue(max < PROBE_LIMIT, type.id() + " " + field.name() + " has no bound");
            text.append("  ").append(field.name()).append(": at most ").append(max).append(' ')
                    .append(field.unit()).append('\n');
        }
        return text.toString();
    }

    /** A string, list or array somewhere in a record: the components to follow, then the field itself. */
    private record LengthField(String name, List<Integer> path, Class<?> type, Type genericType) {

        String unit() {
            return type == String.class ? "characters" : type == byte[].class ? "bytes" : type.isArray() ? "entries" : "elements";
        }
    }

    /** Every variable-length field of {@code type}, following nested records and the first element of lists. */
    private static List<LengthField> lengthFields(Class<?> type, String prefix) {
        List<LengthField> fields = new ArrayList<>();
        RecordComponent[] components = type.getRecordComponents();
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            String name = prefix + component.getName();
            Class<?> componentType = component.getType();
            if (componentType == String.class || componentType.isArray() || componentType == List.class) {
                fields.add(new LengthField(name, List.of(i), componentType, component.getGenericType()));
            }
            Class<?> nested = componentType == List.class ? MessageSamples.typeArgument(component.getGenericType())
                    : componentType;
            if (componentType == List.class && nested == String.class) {
                fields.add(new LengthField(name + "[]", List.of(i, FIRST_ELEMENT), String.class, String.class));
            }
            if (nested.isRecord()) {
                String nestedPrefix = name + (componentType == List.class ? "[]." : ".");
                for (LengthField inner : lengthFields(nested, nestedPrefix)) {
                    List<Integer> path = Stream.concat(Stream.of(i), inner.path().stream()).toList();
                    fields.add(new LengthField(inner.name(), path, inner.type(), inner.genericType()));
                }
            }
        }
        return fields;
    }

    /** Binary search for the largest length the codec still encodes. */
    private static int largestAccepted(MessageType<?> type, Record sample, LengthField field) {
        int low = 0;
        int high = PROBE_LIMIT;
        if (encodes(type, sample, field, high)) {
            return high;
        }
        while (high - low > 1) {
            int middle = (low + high) >>> 1;
            if (encodes(type, sample, field, middle)) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static boolean encodes(MessageType<?> type, Record sample, LengthField field, int length) {
        Object value;
        if (field.type() == String.class) {
            value = "x".repeat(length);
        } else if (field.type().isArray()) {
            value = MessageSamples.array(field.type().getComponentType(), length, 1);
        } else {
            value = MessageSamples.list(MessageSamples.typeArgument(field.genericType()), length, 1);
        }
        Record message = replace(sample, field.path(), value);
        try {
            encode(type, message, buffer());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** {@code root} with the value at {@code path} replaced; a path through a list changes its first element. */
    private static Record replace(Record root, List<Integer> path, Object value) {
        int index = path.get(0);
        if (path.size() == 1) {
            return MessageSamples.with(root, index, value);
        }
        Object child = MessageSamples.get(root.getClass().getRecordComponents()[index], root);
        Object replaced;
        if (child instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list);
            List<Integer> rest = path.subList(1, path.size());
            copy.set(0, rest.equals(List.of(FIRST_ELEMENT)) ? value : replace((Record) list.get(0), rest, value));
            replaced = copy;
        } else {
            replaced = replace((Record) child, path.subList(1, path.size()), value);
        }
        return MessageSamples.with(root, index, replaced);
    }

    @SuppressWarnings("unchecked")
    private static <M extends Message> void encode(MessageType<M> type, Object message, RegistryFriendlyByteBuf buf) {
        type.codec().encode(buf, (M) message);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static MessageType<?> declaredType(Class<? extends Message> type) {
        try {
            Field field = type.getField("TYPE");
            assertTrue(Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()),
                    type.getName() + ".TYPE must be static final");
            return (MessageType<?>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(type.getName() + " has no public TYPE constant", e);
        }
    }

    /** Every concrete class implementing {@link Message} in the compiled main classes. */
    @SuppressWarnings("unchecked")
    private static List<Class<? extends Message>> messageClasses() {
        List<Class<? extends Message>> found = new ArrayList<>();
        String roots = System.getProperty("architecture.mainClasses", "build/classes/java/main");
        for (String root : roots.split(java.io.File.pathSeparator)) {
            Path base = Path.of(root);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(base)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                    String name = base.relativize(file).toString().replace(java.io.File.separatorChar, '.');
                    if (name.contains(".mixin.")) {
                        continue; // mixin classes cannot be loaded directly, and are never messages
                    }
                    if (name.contains(".flywheel.")) {
                        continue; // the optional Flywheel backend needs Flywheel, which tests do not load
                    }
                    Class<?> type = Class.forName(name.substring(0, name.length() - ".class".length()), false,
                            MessageCodecTest.class.getClassLoader());
                    if (Message.class.isAssignableFrom(type) && !type.isInterface()
                            && !Modifier.isAbstract(type.getModifiers())) {
                        found.add((Class<? extends Message>) type);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        assertTrue(!found.isEmpty(), "no message classes found under " + roots);
        return found;
    }
}
