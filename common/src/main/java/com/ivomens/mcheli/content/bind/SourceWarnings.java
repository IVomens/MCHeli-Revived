package com.ivomens.mcheli.content.bind;

import com.ivomens.mcheli.content.source.ContentParseException;
import com.ivomens.mcheli.util.ModLogger;

import java.util.Collection;
import java.util.Locale;

/**
 * Logs problems that do not stop a file from loading, always naming the file, the line (when known) and
 * the key: {@code weapons/foo.yml:12 Ammo.MagSize: 0 is outside 1 to 30000, 1 is used}.
 */
final class SourceWarnings {

    /** Keys further apart than this, or than a third of the typed key's length, are not offered as a fix. */
    private static final int MAX_TYPO_DISTANCE = 2;

    private final String file;

    SourceWarnings(String file) {
        this.file = file;
    }

    void warn(int line, String key, String message) {
        ModLogger.get().warn("{} {}: {}", where(line), key, message);
    }

    void unknownKey(int line, String key, String typed, Collection<String> known) {
        String suggestion = closest(typed, known);
        ModLogger.get().warn("{} unknown key '{}', ignored{}", where(line), key,
                suggestion == null ? "" : " (did you mean '" + suggestion + "'?)");
    }

    private String where(int line) {
        return line == ContentParseException.UNKNOWN_LINE ? this.file : this.file + ":" + line;
    }

    /** The known key closest to a mistyped one, or null when none is close. */
    private static String closest(String typed, Collection<String> known) {
        int allowed = Math.min(MAX_TYPO_DISTANCE, typed.length() / 3);
        String best = null;
        int bestDistance = allowed + 1;
        for (String candidate : known) {
            int distance = distance(typed.toLowerCase(Locale.ROOT), candidate.toLowerCase(Locale.ROOT));
            if (distance < bestDistance || distance == bestDistance && best != null && candidate.compareTo(best) < 0) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return bestDistance <= allowed ? best : null;
    }

    /** Levenshtein distance. */
    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j], current[j - 1]) + 1);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
