package com.ivomens.mcheli.api.vehicle.extension;

import net.minecraft.nbt.CompoundTag;

/**
 * Reads the settings a vehicle file gives a behaviour or movement model. The YAML map arrives as a
 * {@link CompoundTag}: maps become compound tags, lists list tags, whole numbers int (or long) tags, other numbers
 * double tags, text string tags and {@code true}/{@code false} byte tags. A file that gives no settings hands
 * over an empty tag. With a vanilla {@code Codec}, decode the tag with {@code NbtOps}:
 *
 * <pre>{@code
 * SettingsDecoder<WinchSettings> decoder = tag -> WinchSettings.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
 * }</pre>
 *
 * <p><b>Side and thread.</b> Called on both sides, on the thread that loads content, once per vehicle file and
 * content load (start-up, {@code /mcheli reload}, {@code /mcheli validate}). The settings should be immutable:
 * one object is shared by every vehicle of the type.
 *
 * <p><b>Stability.</b> Since API 1.1. Addons implement it, so it only changes in a major version.
 *
 * @param <S> the decoded settings
 */
@FunctionalInterface
public interface SettingsDecoder<S> {

    /**
     * The settings, never null.
     *
     * @throws RuntimeException when the settings are not valid; the message is reported against the vehicle file
     *                          in the content report, and the vehicle loads without the behaviour or model
     */
    S decode(CompoundTag settings);
}
