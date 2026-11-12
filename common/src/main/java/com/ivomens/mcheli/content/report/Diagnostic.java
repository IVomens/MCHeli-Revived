package com.ivomens.mcheli.content.report;

import javax.annotation.Nullable;

/**
 * One problem a content load found, as the author sees it in the log: the message names the file, line and key
 * where it knows them.
 *
 * @param severity how bad it is
 * @param pack     the domain of the pack whose file it is about, or null for the load as a whole
 * @param file     the path of the file inside its pack, or null for the load as a whole
 * @param message  the message, as logged
 */
public record Diagnostic(Severity severity, @Nullable String pack, @Nullable String file, String message) {

    /** How bad a problem is. */
    public enum Severity {
        /** The file, or the part of it, was skipped. */
        ERROR,
        /** The file loaded, but not quite as written: a value clamped, a key ignored. */
        WARNING,
        /** Worth knowing, such as an addon replacing a bundled file. */
        INFO;

        /** The label in the report, padded to one width. */
        String label() {
            return switch (this) {
                case ERROR -> "ERROR";
                case WARNING -> "WARN ";
                case INFO -> "INFO ";
            };
        }
    }
}
