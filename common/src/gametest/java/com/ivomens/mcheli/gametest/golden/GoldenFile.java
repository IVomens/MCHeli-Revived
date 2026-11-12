package com.ivomens.mcheli.gametest.golden;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A recorded trajectory as stored on disk: a gzipped CSV whose first line is {@code # } and a JSON
 * header, then the column names, then one row per tick.
 *
 * @param header  the JSON header, without the leading {@code # }
 * @param columns the column line
 * @param rows    one line per tick
 */
record GoldenFile(String header, String columns, List<String> rows) {

    /** The part of the path below the golden folder: {@code <type>/<definition>.<script>.csv.gz}. */
    static String relativePath(String type, String definition, String script) {
        return type + "/" + definition + "." + script + ".csv.gz";
    }

    static GoldenFile read(Path file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            String columns = reader.readLine();
            if (header == null || !header.startsWith("# ") || columns == null) {
                throw new IOException(file + " is not a golden trajectory");
            }
            List<String> rows = new ArrayList<>();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                rows.add(line);
            }
            return new GoldenFile(header.substring(2), columns, rows);
        }
    }

    /** Writes the file; GZIPOutputStream leaves the timestamp at zero, so equal data gives equal bytes. */
    void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8))) {
            writer.write("# " + this.header + "\n");
            writer.write(this.columns + "\n");
            for (String row : this.rows) {
                writer.write(row + "\n");
            }
        }
    }

    /** Same columns and rows; the header may differ in the commit it was recorded at. */
    boolean sameData(GoldenFile other) {
        return this.columns.equals(other.columns) && this.rows.equals(other.rows);
    }
}
