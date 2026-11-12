package com.ivomens.mcheli.api.vehicle.extension;

/**
 * Makes the behaviour of one vehicle from the settings of its vehicle file.
 *
 * <p><b>Side and thread.</b> Called on both sides, on the vehicle level's thread, on the first tick of a vehicle
 * whose file names the behaviour, and again after a content reload replaced the file.
 *
 * <p><b>Stability.</b> Since API 1.1. Addons implement it, so it only changes in a major version.
 *
 * @param <S> the decoded settings
 */
@FunctionalInterface
public interface BehaviourFactory<S> {

    /**
     * A new behaviour for one vehicle, never null.
     *
     * @param settings what the {@link SettingsDecoder} made of the file's settings
     * @param context  the vehicle the behaviour belongs to; the same object is handed to every hook
     */
    VehicleBehaviour create(S settings, BehaviourContext context);
}
