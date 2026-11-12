package com.ivomens.mcheli.content.bind;

import com.ivomens.mcheli.content.source.ContentParseException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the line of a key path such as {@code Seats[2].Camera.Pos} in the raw YAML text, so parse errors
 * can point at a line. SnakeYAML's maps do not keep line numbers, so this walks the text: each key is looked
 * up below the line of its parent, and {@code [n]} picks the n-th (1-based) list item.
 *
 * <p>It is a best-effort helper for error messages; when the text does not match it returns the line of the
 * deepest part it could find, or {@link ContentParseException#UNKNOWN_LINE}.
 */
public final class YamlLineLocator {

    private static final Pattern PATH_PART = Pattern.compile("([^.\\[\\]]+)|\\[(\\d+)]");

    private YamlLineLocator() {}

    public static int find(List<String> lines, String keyPath) {
        int foundLine = -1;
        int searchFrom = 0;
        int parentIndent = -1;
        Matcher part = PATH_PART.matcher(keyPath);
        while (part.find()) {
            boolean isListIndex = part.group(2) != null;
            int next = isListIndex
                    ? findListItem(lines, Integer.parseInt(part.group(2)), searchFrom, parentIndent)
                    : findKey(lines, part.group(1), searchFrom, parentIndent);
            if (next < 0) {
                break;
            }
            foundLine = next;
            if (isListIndex) {
                // The item's first key sits on the dash line itself; its other keys are indented past the dash.
                searchFrom = next;
                parentIndent = indent(lines.get(next));
            } else {
                searchFrom = next + 1;
                parentIndent = contentIndent(lines.get(next));
            }
        }
        return foundLine < 0 ? ContentParseException.UNKNOWN_LINE : foundLine + 1;
    }

    /** A key of the section that starts at {@code from}: at the indent of its first key, not deeper. */
    private static int findKey(List<String> lines, String key, int from, int parentIndent) {
        int keyIndent = -1;
        for (int i = from; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isBlankOrComment(line)) {
                continue;
            }
            if (i > from && indent(line) <= parentIndent) {
                return -1;
            }
            if (keyIndent < 0) {
                keyIndent = contentIndent(line);
            }
            String content = stripListDash(line.trim());
            boolean named = content.startsWith(key + ":") || content.startsWith("\"" + key + "\":");
            if (named && contentIndent(line) == keyIndent) {
                return i;
            }
        }
        return -1;
    }

    private static int findListItem(List<String> lines, int oneBasedIndex, int from, int parentIndent) {
        int seen = 0;
        int itemIndent = -1;
        for (int i = from; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isBlankOrComment(line)) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.startsWith("-")) {
                if (indent(line) <= parentIndent) {
                    return -1;
                }
                continue;
            }
            if (itemIndent < 0) {
                itemIndent = indent(line);
            } else if (indent(line) < itemIndent) {
                return -1;
            }
            if (indent(line) == itemIndent && ++seen == oneBasedIndex) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isBlankOrComment(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    private static String stripListDash(String trimmed) {
        return trimmed.startsWith("- ") ? trimmed.substring(2).trim() : trimmed;
    }

    private static int indent(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    /** Indent of the line's key, counting a leading list dash as indentation. */
    private static int contentIndent(String line) {
        int i = indent(line);
        if (line.startsWith("- ", i)) {
            i += 2;
            while (i < line.length() && line.charAt(i) == ' ') {
                i++;
            }
        }
        return i;
    }
}
