package com.ivomens.mcheli.inspector.client;

import com.ivomens.mcheli.inspector.InspectorEntry;
import com.ivomens.mcheli.inspector.InspectorSection;
import com.ivomens.mcheli.inspector.InspectorSnapshot;
import com.ivomens.mcheli.inspector.InspectorTab;
import com.ivomens.mcheli.inspector.SnapshotDiff;
import com.ivomens.mcheli.inspector.SnapshotDiff.Verdict;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The lines of the inspector's table: a header per section, then a row per entry with the client's value and,
 * when the server's snapshot is there, the server's value and how the two compare.
 */
final class InspectorRows {

    /** One line of the table. A header has no values; its label is the section title. */
    record Row(boolean header, String label, String detail, String client, @Nullable String server,
               Verdict verdict, boolean marked) {

        static Row header(InspectorSection section) {
            return new Row(true, section.title(), section.detail(), "", null, Verdict.ONE_SIDED, false);
        }
    }

    /** The server's value of a definition setting it leaves out because it holds the default. */
    static final String DEFAULT = "(default)";

    private InspectorRows() {
    }

    /** The rows of {@code sections}, compared against the server's snapshot when there is one. */
    static List<Row> of(List<InspectorSection> sections, @Nullable InspectorSnapshot server, boolean headers) {
        List<Row> rows = new ArrayList<>();
        for (InspectorSection section : sections) {
            if (headers) {
                rows.add(Row.header(section));
            }
            for (InspectorEntry entry : section.entries()) {
                rows.add(row(section, entry, server));
            }
        }
        return rows;
    }

    private static Row row(InspectorSection section, InspectorEntry entry, @Nullable InspectorSnapshot server) {
        if (server == null) {
            return new Row(false, entry.key(), entry.detail(), entry.value(), null, Verdict.ONE_SIDED, entry.marked());
        }
        String other = SnapshotDiff.serverValue(server, section, entry).orElse(null);
        if (other == null && section.tab() == InspectorTab.DEFINITION && server.section(section.tab(), section.title())
                .isPresent()) {
            // the server sends only the values that differ from the defaults
            Verdict verdict = entry.marked() ? Verdict.MISMATCH : Verdict.SAME;
            return new Row(false, entry.key(), entry.detail(), entry.value(), DEFAULT, verdict, entry.marked());
        }
        Verdict verdict = SnapshotDiff.compare(entry.key(), entry.value(), other);
        return new Row(false, entry.key(), entry.detail(), entry.value(), other, verdict, entry.marked());
    }

    /** How many rows are real mismatches. */
    static int mismatches(List<Row> rows) {
        return (int) rows.stream().filter(row -> row.verdict() == Verdict.MISMATCH).count();
    }
}
