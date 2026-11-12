package com.ivomens.mcheli.core.content.schema;

/**
 * A value in a content file cannot be used. The readers add the file, line and key, and the file is
 * skipped, as it always was for a value of the wrong type.
 */
public class ContentValueException extends RuntimeException {

    private static final long serialVersionUID = -6011519021364316787L;

    public ContentValueException(String message) {
        super(message);
    }
}
