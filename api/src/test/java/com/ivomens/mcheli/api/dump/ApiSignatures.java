package com.ivomens.mcheli.api.dump;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The public signatures of the API, one per line, as committed in {@code api/api.txt}: every public type with
 * its supertypes, and its public and protected fields, constructors and methods with their generic types.
 * A line that disappears or changes breaks addons compiled against the old API; a new line does not.
 *
 * <p>Run as a program ({@code ./gradlew :api:updateApiDump}) it rewrites the file, but only when
 * {@code api_version} was bumped as the change needs: a major bump when a line was removed or changed, at
 * least a minor one when lines were only added. {@link ApiSignaturesTest} fails the build while the file is
 * out of date.
 */
public final class ApiSignatures {

    static final String HEADER = "# mcheli-revived-api ";

    private ApiSignatures() {
    }

    /** Usage: {@code ApiSignatures <api.txt> <api version> <class folders separated by the path separator>}. */
    public static void main(String[] args) throws IOException {
        Path file = Path.of(args[0]);
        String version = args[1];
        Set<String> current = dump(classFolders(args[2]));
        if (Files.isRegularFile(file)) {
            Committed committed = Committed.read(file);
            String problem = versionProblem(committed, current, version);
            if (problem != null) {
                System.err.println(problem);
                System.exit(1);
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add(HEADER + version);
        lines.add("# The public signatures of the API. Written by ./gradlew :api:updateApiDump; see docs/API.md.");
        lines.addAll(current);
        Files.write(file, lines, StandardCharsets.UTF_8);
        System.out.println("Wrote " + current.size() + " signatures of API " + version + " to " + file);
    }

    static List<Path> classFolders(String paths) {
        return Arrays.stream(paths.split(File.pathSeparator)).filter(p -> !p.isBlank()).map(Path::of).toList();
    }

    /**
     * Why {@code version} is not enough for the change from the committed dump to {@code current}, or null
     * when it is.
     */
    static String versionProblem(Committed committed, Set<String> current, String version) {
        Set<String> removed = new TreeSet<>(committed.signatures());
        removed.removeAll(current);
        Set<String> added = new TreeSet<>(current);
        added.removeAll(committed.signatures());
        int[] before = parse(committed.version());
        int[] now = parse(version);
        if (!removed.isEmpty() && now[0] <= before[0]) {
            return "Signatures were removed or changed, which breaks addons, but api_version " + version
                    + " is not a new major version after " + committed.version() + ":\n  "
                    + String.join("\n  ", removed);
        }
        boolean newMinor = now[0] > before[0] || now[0] == before[0] && now[1] > before[1];
        if (!added.isEmpty() && !newMinor) {
            return "Signatures were added, so api_version " + version + " has to be a new minor version after "
                    + committed.version() + ":\n  " + String.join("\n  ", added);
        }
        if (compare(now, before) < 0) {
            return "api_version " + version + " is older than the committed " + committed.version();
        }
        return null;
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }

    /** {@code major.minor.patch}, ignoring a pre-release suffix. */
    static int[] parse(String version) {
        String[] parts = version.split("[-+]", 2)[0].split("\\.");
        int[] numbers = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            numbers[i] = Integer.parseInt(parts[i]);
        }
        return numbers;
    }

    /** The signatures of the public classes compiled into the folders, sorted. */
    static Set<String> dump(List<Path> classFolders) throws IOException {
        Set<String> lines = new TreeSet<>();
        for (Path folder : classFolders) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            List<Path> classFiles;
            try (Stream<Path> files = Files.walk(folder)) {
                classFiles = files.filter(f -> f.toString().endsWith(".class")).toList();
            }
            for (Path classFile : classFiles) {
                String relative = folder.relativize(classFile).toString().replace(File.separatorChar, '/');
                String name = relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
                if (name.endsWith("package-info") || name.endsWith("module-info")) {
                    continue;
                }
                Class<?> type = load(name);
                if (isVisible(type)) {
                    describe(type, lines);
                }
            }
        }
        return lines;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, ApiSignatures.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Compiled API class " + name + " is not on the class path", e);
        }
    }

    /** Public, and inside public types all the way out; local, anonymous and synthetic classes are not API. */
    private static boolean isVisible(Class<?> type) {
        if (type.isSynthetic() || type.isAnonymousClass() || type.isLocalClass()) {
            return false;
        }
        for (Class<?> c = type; c != null; c = c.getDeclaringClass()) {
            if (!Modifier.isPublic(c.getModifiers())) {
                return false;
            }
        }
        return true;
    }

    private static void describe(Class<?> type, Set<String> lines) {
        String name = type.getTypeName();
        int typeModifiers = type.isInterface() ? type.getModifiers() & ~Modifier.ABSTRACT : type.getModifiers();
        lines.add(name + " type " + modifiers(typeModifiers) + kind(type) + typeParameters(type.getTypeParameters()));
        Type superclass = type.getGenericSuperclass();
        if (superclass != null && superclass != Object.class && type.getSuperclass() != Enum.class
                && type.getSuperclass() != Record.class) {
            lines.add(name + " extends " + superclass.getTypeName());
        }
        for (Type implemented : type.getGenericInterfaces()) {
            lines.add(name + " implements " + implemented.getTypeName());
        }
        for (Field field : type.getDeclaredFields()) {
            if (isApi(field.getModifiers(), field.isSynthetic())) {
                lines.add(name + " field " + modifiers(field.getModifiers()) + field.getGenericType().getTypeName()
                        + " " + field.getName());
            }
        }
        if (!type.isInterface()) {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (isApi(constructor.getModifiers(), constructor.isSynthetic())) {
                    lines.add(name + " constructor " + modifiers(constructor.getModifiers())
                            + typeParameters(constructor.getTypeParameters()) + "("
                            + types(constructor.getGenericParameterTypes()) + ")"
                            + throwsClause(constructor.getGenericExceptionTypes()));
                }
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (isApi(method.getModifiers(), method.isSynthetic() || method.isBridge())) {
                lines.add(name + " method " + (method.isDefault() ? "default " : "") + modifiers(method.getModifiers())
                        + typeParameters(method.getTypeParameters()) + method.getGenericReturnType().getTypeName()
                        + " " + method.getName() + "(" + types(method.getGenericParameterTypes()) + ")"
                        + throwsClause(method.getGenericExceptionTypes()));
            }
        }
    }

    private static boolean isApi(int modifiers, boolean synthetic) {
        return !synthetic && (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers));
    }

    private static String kind(Class<?> type) {
        if (type.isAnnotation()) {
            return "@interface";
        }
        if (type.isInterface()) {
            return "interface";
        }
        if (type.isEnum()) {
            return "enum";
        }
        return type.isRecord() ? "record" : "class";
    }

    private static String modifiers(int modifiers) {
        String text = Modifier.toString(modifiers & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.STATIC
                | Modifier.FINAL | Modifier.ABSTRACT));
        return text.isEmpty() ? "" : text + " ";
    }

    private static String typeParameters(TypeVariable<?>[] variables) {
        if (variables.length == 0) {
            return "";
        }
        return "<" + Arrays.stream(variables)
                .map(v -> v.getName() + " extends " + types(v.getBounds()).replace(", ", " & "))
                .collect(Collectors.joining(", ")) + "> ";
    }

    private static String types(Type[] types) {
        return Arrays.stream(types).map(Type::getTypeName).collect(Collectors.joining(", "));
    }

    private static String throwsClause(Type[] exceptions) {
        return exceptions.length == 0 ? "" : " throws " + types(exceptions);
    }

    /** A committed dump: the API version in its header and its signature lines. */
    record Committed(String version, Set<String> signatures) {

        static Committed read(Path file) throws IOException {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.getFirst().startsWith(HEADER)) {
                throw new IllegalStateException(file + " does not start with '" + HEADER + "<version>'");
            }
            String version = lines.getFirst().substring(HEADER.length()).trim();
            Set<String> signatures = lines.stream().skip(1).filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .collect(Collectors.toCollection(TreeSet::new));
            return new Committed(version, signatures);
        }
    }
}
