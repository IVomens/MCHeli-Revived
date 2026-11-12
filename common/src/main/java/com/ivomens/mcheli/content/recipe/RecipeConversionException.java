package com.ivomens.mcheli.content.recipe;

/** A legacy recipe that cannot be converted; the message says why. */
public class RecipeConversionException extends Exception {

    public RecipeConversionException(String message) {
        super(message);
    }
}
