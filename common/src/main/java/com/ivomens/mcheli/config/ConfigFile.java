package com.ivomens.mcheli.config;

import com.ivomens.mcheli.util.ModLogger;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Reads and writes one of the mod's {@code key = value} files. Keys are matched ignoring case, a line
 * needs exactly one {@code =} to count, and the last line for a key wins (list keys collect every
 * line). Lines that name no option, comments included, are skipped and are not written back: the
 * written file is always the {@link Layout} filled with the current values.
 *
 * @param layout   what the file looks like, in order; its entries are the options that can be read
 * @param flags    options written as a bare word on their own line, which turns them on
 * @param versioned whether a {@code MOD_Version} line gates options by {@link Option#validSince()}
 * @param unquote  whether double quotes are removed from values, for the TOML files of old versions
 */
public record ConfigFile(List<Layout> layout, List<BoolOption> flags, boolean versioned, boolean unquote) {

    private static final String VERSION_KEY = "MOD_Version";
    /** A file without a version line is treated as older than every gated option. */
    private static final String UNKNOWN_VERSION = "0.0.0";

    /** A problem found on one line, numbered from 1. */
    @FunctionalInterface
    public interface Problems {
        void report(int line, String problem);
    }

    public ConfigFile {
        layout = List.copyOf(layout);
        flags = List.copyOf(flags);
    }

    /** Reads a file, logging every value that could not be used as written. */
    public ConfigValues read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return read(lines, (line, problem) -> ModLogger.warn("{} line {}: {}", file.getFileName(), line, problem));
    }

    public ConfigValues read(List<String> lines, Problems problems) {
        List<Option<?>> options = options();
        ConfigValues values = ConfigValues.DEFAULTS;
        String fileVersion = UNKNOWN_VERSION;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            BoolOption flag = flag(line);
            if (flag != null) {
                values = values.with(flag, true);
                continue;
            }
            String[] keyValue = line.split("=");
            if (keyValue.length != 2) {
                continue;
            }
            String key = keyValue[0].trim();
            String raw = keyValue[1].trim();
            if (this.unquote) {
                raw = raw.replace("\"", "");
            }
            if (this.versioned && key.equalsIgnoreCase(VERSION_KEY)) {
                fileVersion = raw;
                continue;
            }
            int lineNumber = i + 1;
            for (Option<?> option : options) {
                if (option.matches(key) && (!this.versioned || isAtLeast(fileVersion, option.validSince()))) {
                    values = apply(values, option, key, raw, problem -> problems.report(lineNumber, problem));
                    break;
                }
            }
        }
        return values;
    }

    public void write(Path file, ConfigValues values) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            render(values).forEach(writer::println);
        }
    }

    /** The lines of the file for these values. */
    public List<String> render(ConfigValues values) {
        List<String> lines = new ArrayList<>();
        render(this.layout, values, lines);
        return lines;
    }

    /** Every option the file reads, in layout order. */
    public List<Option<?>> options() {
        return entries(this.layout).toList();
    }

    private static Stream<Option<?>> entries(List<Layout> pieces) {
        return pieces.stream().flatMap(piece -> switch (piece) {
            case Layout.Entry entry -> Stream.of(entry.option());
            case Layout.When when -> entries(when.then());
            case Layout.Text text -> Stream.empty();
        });
    }

    private static void render(List<Layout> pieces, ConfigValues values, List<String> lines) {
        for (Layout piece : pieces) {
            switch (piece) {
                case Layout.Text text -> lines.add(text.line());
                case Layout.Entry entry -> renderEntry(entry.option(), values, lines);
                case Layout.When when -> {
                    if (when.condition().test(values)) {
                        render(when.then(), values, lines);
                    }
                }
            }
        }
    }

    private static <T> void renderEntry(Option<T> option, ConfigValues values, List<String> lines) {
        option.comment().forEach(comment -> lines.add(";" + comment));
        lines.addAll(option.lines(values.get(option)));
    }

    private static <T> ConfigValues apply(ConfigValues values, Option<T> option, String key, String raw,
                                          Consumer<String> problems) {
        T current = values.isSet(option) ? values.get(option) : option.unreadValue();
        return values.with(option, option.read(current, key, raw, problems));
    }

    private BoolOption flag(String line) {
        String word = line.trim();
        return this.flags.stream().filter(flag -> flag.matches(word)).findFirst().orElse(null);
    }

    /**
     * Whether a file of version {@code fileVersion} is at least {@code since}. Versions that are not
     * three numbers do not gate anything.
     */
    static boolean isAtLeast(String fileVersion, String since) {
        if (fileVersion.length() < 5 || since.length() < 5) {
            return true;
        }
        String[] fileParts = fileVersion.split("\\.");
        String[] sinceParts = since.split("\\.");
        if (fileParts.length != 3 || sinceParts.length != 3) {
            return true;
        }
        try {
            for (int i = 0; i < 3; i++) {
                int fileNumber = versionNumber(fileParts[i]);
                int sinceNumber = versionNumber(sinceParts[i]);
                if (fileNumber != sinceNumber) {
                    return fileNumber > sinceNumber;
                }
            }
        } catch (NumberFormatException e) {
            return true;
        }
        return true;
    }

    /** "4-beta" counts as 4. */
    private static int versionNumber(String part) {
        return Integer.parseInt(part.replaceAll("[a-zA-Z-_]", "").trim());
    }
}
