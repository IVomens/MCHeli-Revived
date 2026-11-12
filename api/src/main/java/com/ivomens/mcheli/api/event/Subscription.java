package com.ivomens.mcheli.api.event;

/**
 * A registered listener. Thread-safe. Stable since API 1.0; implemented by MCHeli only.
 */
public interface Subscription {

    /** Removes the listener; it gets no event that starts afterwards. Calling it again does nothing. */
    void unsubscribe();
}
