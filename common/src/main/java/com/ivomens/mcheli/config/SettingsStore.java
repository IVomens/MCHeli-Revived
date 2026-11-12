package com.ivomens.mcheli.config;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Holds the current snapshot of an immutable settings type and the file it came from. Readers take
 * {@link #current()} and keep using that snapshot; a load or a change swaps in a new one atomically,
 * so nobody ever sees half of an update.
 *
 * @param <S> the settings type
 */
public final class SettingsStore<S> {

    private record State<S>(S settings, @Nullable Path file) {
    }

    private final Supplier<S> defaults;
    private final AtomicReference<State<S>> state = new AtomicReference<>();

    /** @param defaults the settings before anything is loaded; built on first use */
    public SettingsStore(Supplier<S> defaults) {
        this.defaults = defaults;
    }

    public S current() {
        return state().settings();
    }

    /** The file the settings were loaded from, if any. */
    public Optional<Path> file() {
        return Optional.ofNullable(state().file());
    }

    /** Replaces the settings and remembers the file they belong to. */
    public void replace(S settings, @Nullable Path file) {
        this.state.set(new State<>(settings, file));
    }

    /** Replaces the settings and keeps the file. */
    public void set(S settings) {
        update(previous -> settings);
    }

    /** Applies a change to the current settings; the change may run more than once under contention. */
    public S update(UnaryOperator<S> change) {
        state();
        return this.state.updateAndGet(previous -> new State<>(change.apply(previous.settings()), previous.file()))
                .settings();
    }

    private State<S> state() {
        State<S> current = this.state.get();
        if (current == null) {
            this.state.compareAndSet(null, new State<>(this.defaults.get(), null));
            current = this.state.get();
        }
        return current;
    }
}
