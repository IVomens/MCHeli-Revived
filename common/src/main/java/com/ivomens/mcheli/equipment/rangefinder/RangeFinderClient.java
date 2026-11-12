package com.ivomens.mcheli.equipment.rangefinder;

import com.ivomens.mcheli.platform.Services;

/**
 * What the range finder item asks of the client it is used on. The scope is client state, but the item
 * learns first that it was released; a dedicated server has no scope and does nothing.
 */
public interface RangeFinderClient {

    RangeFinderClient INSTANCE = Services.sided(RangeFinderClient.class, new RangeFinderClient() {
    });

    /** The local player released the range finder: lower the scope right away. */
    default void lowerScope() {
    }
}
