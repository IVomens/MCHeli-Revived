package com.ivomens.mcheli.content.emit;

import com.ivomens.mcheli.core.content.schema.EntrySchema;
import com.ivomens.mcheli.core.content.schema.Member;
import com.ivomens.mcheli.core.content.schema.ValueCodec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The descriptions of the JSON Schema files, taken from the declarations themselves: the Javadoc of the constant
 * a setting, section or entry is declared as, in the {@code content.def} sources of {@code :common} and
 * {@code :core}. A section constant without a
 * comment of its own ({@code AmmoSchema.SECTION}) takes the first sentence of its class comment.
 * <p>
 * Declarations are matched by identity: every static {@link Member}, {@link EntrySchema} or {@link ValueCodec}
 * constant of the classes below {@code content/def} is read by reflection and mapped to its comment. A setting
 * without a comment of its own is described by the comment of its codec, when that has one.
 */
final class SchemaDocs implements Function<Object, String> {

    /** The definition packages: the Minecraft-bound ones of {@code :common}, the pure ones of {@code :core}. */
    private static final List<String> DEF_PACKAGES = List.of("com/ivomens/mcheli/content/def",
            "com/ivomens/mcheli/core/content/def");
    private static final Pattern CLASS_DOC = Pattern.compile(
            "/\\*\\*((?:(?!\\*/).)*)\\*/\\s*(?:@\\w+\\s*)*(?:public\\s+|final\\s+|abstract\\s+)*(?:class|record|enum)\\s",
            Pattern.DOTALL);

    private final Map<Object, String> docs = new IdentityHashMap<>();

    private SchemaDocs() {
    }

    /**
     * Reads the definition packages below the source folders. The files are read in the order of their path
     * below their definition package, whichever module holds them, so a declaration shared by two files keeps
     * the comment of the same one.
     *
     * @param sources the {@code src/main/java} folders of {@code :common} and {@code :core}
     */
    static SchemaDocs read(List<Path> sources) {
        SchemaDocs docs = new SchemaDocs();
        Map<String, Path[]> files = new TreeMap<>();
        for (Path root : sources) {
            for (String defPackage : DEF_PACKAGES) {
                Path dir = root.resolve(defPackage);
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (Stream<Path> found = Files.walk(dir)) {
                    found.filter(path -> path.toString().endsWith(".java"))
                            .forEach(file -> files.put(dir.relativize(file).toString(), new Path[]{root, file}));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        for (Path[] file : files.values()) {
            try {
                docs.readClass(file[0], file[1]);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return docs;
    }

    @Override
    public String apply(Object declaration) {
        return this.docs.get(declaration);
    }

    private void readClass(Path sources, Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        String className = sources.relativize(file).toString().replace(file.getFileSystem().getSeparator(), ".")
                .replaceAll("\\.java$", "");
        Class<?> type;
        try {
            type = Class.forName(className);
        } catch (ClassNotFoundException | LinkageError e) {
            return;
        }
        for (java.lang.reflect.Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || !(Member.class.isAssignableFrom(field.getType())
                    || EntrySchema.class.isAssignableFrom(field.getType())
                    || ValueCodec.class.isAssignableFrom(field.getType()))) {
                continue;
            }
            Object value = constant(field);
            if (value == null || this.docs.containsKey(value)) {
                continue;
            }
            String doc = fieldDoc(source, field.getName());
            if (doc == null && field.getName().equals("SECTION")) {
                doc = firstSentence(classDoc(source));
            }
            if (doc != null) {
                this.docs.put(value, doc);
            }
        }
    }

    private static Object constant(java.lang.reflect.Field field) {
        try {
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static String fieldDoc(String source, String name) {
        Pattern pattern = Pattern.compile("/\\*\\*((?:(?!\\*/).)*)\\*/\\s*(?:(?:public|private|protected|static|final)"
                + "\\s+)+[^=;{}]*?\\b" + Pattern.quote(name) + "\\s*=", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? clean(matcher.group(1)) : null;
    }

    private static String classDoc(String source) {
        Matcher matcher = CLASS_DOC.matcher(source);
        return matcher.find() ? clean(matcher.group(1)) : null;
    }

    /** The first sentence, without the {@code YAML 'Ammo':} its class comment starts with. */
    private static String firstSentence(String text) {
        if (text == null) {
            return null;
        }
        int end = text.indexOf(". ");
        String sentence = (end < 0 ? text : text.substring(0, end + 1))
                .replaceFirst("^YAML '[^']+'(?:,? (?:and|or) '[^']+')*:\\s*", "");
        return sentence.isEmpty() ? null : Character.toUpperCase(sentence.charAt(0)) + sentence.substring(1);
    }

    /** Javadoc as plain text: no stars, tags, block examples or HTML. */
    static String clean(String javadoc) {
        String text = javadoc.replaceAll("(?m)^\\s*\\*", " ")
                .replaceAll("(?s)<pre>.*?</pre>", " ")
                .replaceAll("(?s)\\s@(param|return|see|throws)\\b.*", " ");
        for (String tag : List.of("code", "literal")) {
            text = text.replaceAll("\\{@" + tag + "\\s+([^}]*)}", "'$1'");
        }
        text = text.replaceAll("\\{@link(?:plain)?\\s+[^}\\s]+\\s+([^}]+)}", "$1")
                .replaceAll("\\{@link(?:plain)?\\s+(?:[\\w.]*#)?([^}\\s(]+)[^}]*}", "$1")
                .replaceAll("<[^>]+>", " ")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .strip();
        return text.isEmpty() ? null : text;
    }
}
