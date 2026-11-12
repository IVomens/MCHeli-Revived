package com.ivomens.mcheli.client.model;

/** A model file could not be parsed. */
public class ModelFormatException extends RuntimeException {

    public ModelFormatException(String message) {
        super(message);
    }

    public ModelFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
