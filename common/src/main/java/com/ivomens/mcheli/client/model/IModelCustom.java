package com.ivomens.mcheli.client.model;

/**
 * A renderable, pre-baked model. Render methods emit through the current
 * {@link com.ivomens.mcheli.client.render.LegacyGL} frame and therefore must only be called on the
 * client render thread inside {@code LegacyGL.begin()/end()}.
 */
public interface IModelCustom {

    /** Short format name for diagnostics, such as {@code obj} or {@code mqo}. */
    String getType();

    void renderAll();

    /** Renders the named part ({@code $body}, {@code $rotor0}, ...); case-insensitive, no-op if missing. */
    void renderPart(String partName);
}
