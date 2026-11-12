package com.ivomens.mcheli.content.report;

import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.ModLogger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Where content reports go: the full report to a file in the game's {@code logs} folder, one summary line to the
 * main log.
 */
public final class ContentLog {

    /** The report of the last content load. */
    public static final String LOAD_REPORT = "logs/mcheli-content.log";
    /** The report of the last {@code /mcheli validate}. */
    public static final String VALIDATE_REPORT = "logs/mcheli-validate.log";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ContentLog() {
    }

    /** The report file below the game directory. */
    public static Path file(String relativePath) {
        return Services.PLATFORM.getGameDir().resolve(relativePath);
    }

    /**
     * Writes the full report to {@code relativePath} below the game directory and logs its summary.
     *
     * @return the file written
     */
    public static Path publish(ContentReport report, String relativePath) {
        Path file = file(relativePath);
        try {
            report.write(file, "MCHeli: Revived " + Services.PLATFORM.getModVersion() + ", "
                    + LocalDateTime.now().format(TIME));
        } catch (IOException e) {
            ModLogger.get().warn("Could not write the content report to '{}': {}", file, e.toString());
        }
        ModLogger.get().info(report.summary(relativePath));
        return file;
    }
}
