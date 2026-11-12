package com.ivomens.mcheli.core.content.schema;

/**
 * Where a value is being read. A codec reports problems that do not stop the file from loading, such as a
 * clamped number, through it; the reader adds the file, line and key to the message.
 */
@FunctionalInterface
public interface ReadContext {

    void warn(String message);
}
