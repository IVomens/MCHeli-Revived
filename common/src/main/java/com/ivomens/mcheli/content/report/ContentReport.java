package com.ivomens.mcheli.content.report;

import com.ivomens.mcheli.content.report.Diagnostic.Severity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What one content load (or one {@code /mcheli validate} run) found: the packs in load order, how many files were
 * read and loaded, which files replaced others, and every problem logged while reading, grouped by pack and file.
 * The loader writes it to {@code logs/mcheli-content.log} and logs {@link #summary} in the main log.
 *
 * @param title       what ran, such as {@code Content load}
 * @param packs       the packs in load order: a later pack's file replaces an earlier pack's file of the same name
 * @param filesRead   content files read
 * @param filesLoaded files that loaded; the others were skipped and have an error
 * @param overrides   files that replaced others, in load order
 * @param diagnostics every problem, in the order it was found
 */
public record ContentReport(String title, List<Pack> packs, int filesRead, int filesLoaded,
                            List<FileOverride> overrides, List<Diagnostic> diagnostics) {

    /**
     * A pack as the report lists it.
     *
     * @param domain the pack's domain, which messages name it by
     * @param source the folder or archive it was read from
     */
    public record Pack(String domain, String source) {
    }

    public ContentReport {
        packs = List.copyOf(packs);
        overrides = List.copyOf(overrides);
        diagnostics = List.copyOf(diagnostics);
    }

    public long count(Severity severity) {
        return this.diagnostics.stream().filter(diagnostic -> diagnostic.severity() == severity).count();
    }

    /** The problems, errors first and then in the order they were found, at most {@code limit}. */
    public List<Diagnostic> worst(int limit) {
        List<Diagnostic> worst = new ArrayList<>();
        for (Severity severity : Severity.values()) {
            for (Diagnostic diagnostic : this.diagnostics) {
                if (diagnostic.severity() == severity && worst.size() < limit) {
                    worst.add(diagnostic);
                }
            }
        }
        return worst;
    }

    /** One line for the main log or a chat message. */
    public String summary(String reportFile) {
        return this.title + ": " + this.filesLoaded + " of " + this.filesRead + " files loaded from "
                + this.packs.size() + (this.packs.size() == 1 ? " pack, " : " packs, ") + count(Severity.ERROR)
                + " errors, " + count(Severity.WARNING) + " warnings, " + this.overrides.size()
                + (this.overrides.size() == 1 ? " file replaced" : " files replaced") + ". Full report: " + reportFile;
    }

    /** The whole report as text. */
    public String text() {
        StringBuilder text = new StringBuilder();
        text.append(this.title).append('\n');
        text.append("Files: ").append(this.filesRead).append(" read, ").append(this.filesLoaded).append(" loaded, ")
                .append(this.filesRead - this.filesLoaded).append(" skipped\n");
        text.append("Problems: ").append(count(Severity.ERROR)).append(" errors, ").append(count(Severity.WARNING))
                .append(" warnings\n");
        text.append("\nPacks, in load order (a later pack's file replaces an earlier pack's file of the same name):\n");
        for (int i = 0; i < this.packs.size(); i++) {
            Pack pack = this.packs.get(i);
            text.append(String.format("  %2d. %s  (%s)%n", i + 1, pack.domain(), pack.source()));
        }
        text.append("\nReplaced files:\n");
        if (this.overrides.isEmpty()) {
            text.append("  none\n");
        }
        this.overrides.forEach(override -> text.append("  ").append(override.describe()).append('\n'));
        text.append("\nProblems by pack and file:\n");
        if (this.diagnostics.isEmpty()) {
            text.append("  none\n");
        }
        for (Map.Entry<String, List<Diagnostic>> group : byFile().entrySet()) {
            text.append("\n").append(group.getKey()).append('\n');
            for (Diagnostic diagnostic : group.getValue()) {
                text.append("  ").append(diagnostic.severity().label()).append(' ')
                        .append(diagnostic.message().replace("\n", "\n        ")).append('\n');
            }
        }
        return text.toString();
    }

    /** The problems grouped by file in the order the files were read; the load as a whole first. */
    private Map<String, List<Diagnostic>> byFile() {
        Map<String, List<Diagnostic>> groups = new LinkedHashMap<>();
        for (Diagnostic diagnostic : this.diagnostics) {
            if (diagnostic.file() == null) {
                groups.computeIfAbsent("(the load as a whole)", key -> new ArrayList<>()).add(diagnostic);
            }
        }
        for (Diagnostic diagnostic : this.diagnostics) {
            if (diagnostic.file() != null) {
                String key = "[" + Objects.requireNonNullElse(diagnostic.pack(), "?") + "] " + diagnostic.file();
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(diagnostic);
            }
        }
        return groups;
    }

    /** Writes the report, replacing the file's earlier contents. */
    public void write(Path file, String header) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, header + "\n" + text(), StandardCharsets.UTF_8);
    }
}
