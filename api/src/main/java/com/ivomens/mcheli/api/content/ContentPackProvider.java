package com.ivomens.mcheli.api.content;

/**
 * Lets a mod ship MCHeli content inside its own jar. MCHeli finds every provider with
 * {@link java.util.ServiceLoader} while it loads its content, so a mod registers by listing its class in
 * {@code META-INF/services/com.ivomens.mcheli.api.content.ContentPackProvider}; no code has to run first.
 *
 * <pre>{@code
 * public final class MyPacks implements ContentPackProvider {
 *     public void registerPacks(ContentPackRegistrar packs) {
 *         packs.addPack("mymod", "mcheli_pack"); // the jar's mcheli_pack/ folder, laid out like any pack
 *     }
 * }
 * }</pre>
 *
 * <p><b>Side and thread.</b> Called once on both sides, on the thread that constructs MCHeli, before any
 * content loads and before the registries open. Only register packs here; the game is not set up yet.
 *
 * <p><b>Stability.</b> Stable since API 1.0. Addons implement this interface, so it only changes in a major
 * version. It needs a public constructor without parameters.
 */
@FunctionalInterface
public interface ContentPackProvider {

    /** Adds the mod's packs. */
    void registerPacks(ContentPackRegistrar packs);
}
