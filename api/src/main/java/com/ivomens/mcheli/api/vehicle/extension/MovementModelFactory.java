package com.ivomens.mcheli.api.vehicle.extension;

/**
 * Makes the movement model of one vehicle from the settings of its vehicle file.
 *
 * <p><b>Side and thread.</b> Called on both sides, on the vehicle level's thread, the first time a vehicle whose
 * file picks the model moves, and again after a content reload replaced the file.
 *
 * <p><b>Stability.</b> Since API 1.1. Addons implement it, so it only changes in a major version.
 *
 * @param <S> the decoded settings
 */
@FunctionalInterface
public interface MovementModelFactory<S> {

    /** A new model for one vehicle, never null; it may keep state of its own between ticks. */
    MovementModel create(S settings);
}
