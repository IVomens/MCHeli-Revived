package com.ivomens.mcheli.api.event;

/**
 * An event whose listeners can stop what it announces. Every listener runs, including those after one that
 * cancelled, and the last state set counts, so a later listener can also undo a cancellation.
 *
 * <p>Stable since API 1.0.
 */
public interface Cancellable {

    boolean isCancelled();

    void setCancelled(boolean cancelled);
}
