package com.ivomens.mcheli.vehicle.component;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * One named step of a vehicle tick. A step has a server half and a client half; either may do
 * nothing. The name documents the step and shows up when a step throws.
 *
 * @param owner the component whose own tick this step is ({@link #of}), whose {@link TickOrder} the
 *              pipeline checks; null for any other step
 */
public record TickStep(String name, Consumer<TickContext> server, Consumer<TickContext> client,
                       @Nullable VehicleComponent owner) {

    private static final Consumer<TickContext> NOTHING = ctx -> {
    };

    public TickStep(String name, Consumer<TickContext> server, Consumer<TickContext> client) {
        this(name, server, client, null);
    }

    /** Runs the same code on both sides. */
    public static TickStep both(String name, Runnable action) {
        Consumer<TickContext> step = ctx -> action.run();
        return new TickStep(name, step, step);
    }

    public static TickStep server(String name, Runnable action) {
        return new TickStep(name, ctx -> action.run(), NOTHING);
    }

    public static TickStep client(String name, Runnable action) {
        return new TickStep(name, NOTHING, ctx -> action.run());
    }

    public static TickStep sided(String name, Runnable server, Runnable client) {
        return new TickStep(name, ctx -> server.run(), ctx -> client.run());
    }

    /** A component's own tick: its {@link VehicleComponent#serverTick} and {@link VehicleComponent#clientTick}. */
    public static TickStep of(String name, VehicleComponent component) {
        return new TickStep(name, component::serverTick, component::clientTick, component);
    }

    public void run(TickContext ctx, boolean clientSide) {
        (clientSide ? this.client : this.server).accept(ctx);
    }
}
