package com.ivomens.mcheli.api.vehicle.extension;

import net.minecraft.resources.ResourceLocation;

/**
 * What a {@link VehicleExtensionProvider} registers its behaviour types and movement models with. An id is a
 * {@link ResourceLocation} in the registering mod's namespace, such as {@code mymod:winch}; vehicle files name it
 * in their {@code Behaviours} section or {@code Movement} key (docs/ADDON_AUTHORING.md).
 *
 * <p>Thread: only valid during {@link VehicleExtensionProvider#register}. Stability: since API 1.1; implemented by
 * MCHeli only, so a minor version may add methods.
 */
public interface VehicleExtensionRegistrar {

    /**
     * Registers a behaviour type. A vehicle file opts in with {@code Behaviours: { <id>: { ...settings } }}; each
     * vehicle of that type then gets its own behaviour from {@code factory}.
     *
     * @param id       the id vehicle files use
     * @param timing   where in the vehicle tick the behaviour's tick runs
     * @param settings reads the settings map of the vehicle file once per content load
     * @param factory  makes the behaviour of one vehicle
     * @param <S>      the decoded settings
     * @throws IllegalArgumentException when the id is already registered (as a behaviour), or an argument is null
     */
    <S> void behaviour(ResourceLocation id, BehaviourTiming timing, SettingsDecoder<S> settings,
                       BehaviourFactory<S> factory);

    /**
     * Registers a movement model. A vehicle file picks it with {@code Movement: <id>}, or with
     * {@code Movement: { Model: <id>, Settings: { ... } }}; each vehicle of that type then gets its own model from
     * {@code factory}. In API 1.1 only files of the {@code VEHICLE} kind ({@code vehicles/}) can pick one.
     *
     * @param id       the id vehicle files use
     * @param settings reads the {@code Settings} map of the vehicle file once per content load
     * @param factory  makes the model of one vehicle
     * @param <S>      the decoded settings
     * @throws IllegalArgumentException when the id is already registered (as a movement model), or an argument is
     *                                  null
     */
    <S> void movementModel(ResourceLocation id, SettingsDecoder<S> settings, MovementModelFactory<S> factory);
}
