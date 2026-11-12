package com.ivomens.mcheli.content.source;

import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A content file could not be parsed. Carries enough context for an addon author to find the problem:
 * the 1-based line number (or {@link #UNKNOWN_LINE}) and the key path inside the file, e.g.
 * {@code Seats[2].Camera.Pos} for YAML or {@code addweapon} for TXT.
 *
 * <p>The file name is added by {@link ContentLoader.ContentEntry#parse()} when the error is logged.
 */
public class ContentParseException extends RuntimeException {

    public static final int UNKNOWN_LINE = -1;

    private static final long serialVersionUID = 4338814389788695295L;
    private static final Pattern CLASS_CAST_MESSAGE =
            Pattern.compile("class (\\S+) cannot be cast to class (\\S+)");

    private final int lineNo;
    @Nullable
    private final String keyPath;

    public ContentParseException(String message, int lineNo) {
        this(message, null, lineNo, null);
    }

    public ContentParseException(String message, @Nullable String keyPath, int lineNo, @Nullable Throwable cause) {
        super(message, cause);
        this.keyPath = keyPath;
        this.lineNo = lineNo;
    }

    /** Wraps a failure that happened while handling {@code key}, turning it into a readable message. */
    public static ContentParseException forKey(String key, Throwable cause) {
        if (cause instanceof ContentParseException parseException) {
            return parseException.under(key);
        }
        return new ContentParseException(describe(cause), key, UNKNOWN_LINE, cause);
    }

    /** Returns a copy of this exception nested one level deeper, below {@code parentKey}. */
    public ContentParseException under(String parentKey) {
        String path;
        if (this.keyPath == null) {
            path = parentKey;
        } else if (this.keyPath.startsWith("[")) {
            path = parentKey + this.keyPath;
        } else {
            path = parentKey + "." + this.keyPath;
        }
        return new ContentParseException(getMessage(), path, this.lineNo, getCause());
    }

    /** Returns a copy of this exception that points at the given line. */
    public ContentParseException atLine(int line) {
        return new ContentParseException(getMessage(), this.keyPath, line, getCause());
    }

    /**
     * Turns the usual exceptions thrown by a bad value (wrong YAML type, bad number, missing value) into a
     * message an addon author can act on.
     */
    public static String describe(Throwable error) {
        String message = error.getMessage();
        if (error instanceof ClassCastException && message != null) {
            Matcher matcher = CLASS_CAST_MESSAGE.matcher(message);
            if (matcher.find()) {
                return "wrong value type, expected " + yamlTypeName(matcher.group(2)) + " but got "
                        + yamlTypeName(matcher.group(1));
            }
        }
        if (error instanceof NumberFormatException) {
            return "not a valid number (" + message + ")";
        }
        if (error instanceof NullPointerException) {
            return "missing or empty value";
        }
        if (error instanceof ArrayIndexOutOfBoundsException) {
            return "not enough values";
        }
        return message != null ? message : error.getClass().getSimpleName();
    }

    private static String yamlTypeName(String javaClassName) {
        String simpleName = javaClassName.substring(javaClassName.lastIndexOf('.') + 1);
        return switch (simpleName) {
            case "Integer", "Long", "BigInteger" -> "a whole number";
            case "Float", "Double", "Number" -> "a number";
            case "Boolean" -> "true/false";
            case "String" -> "a text value";
            case "Map", "HashMap", "LinkedHashMap" -> "a map of keys";
            case "List", "ArrayList" -> "a list";
            default -> simpleName;
        };
    }

    /** The 1-based line number, or {@link #UNKNOWN_LINE}. */
    public int getLineNo() {
        return this.lineNo;
    }

    /** The key path inside the file, or null when the error is not tied to a key. */
    @Nullable
    public String getKeyPath() {
        return this.keyPath;
    }
}
