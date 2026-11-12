package com.ivomens.mcheli.vehicle.settings;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.config.McheliSettings;
import com.ivomens.mcheli.config.ServerSettings;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import com.ivomens.mcheli.config.wingman.WingmanSettings;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Publishes the {@link VehicleSettingsSnapshot} of the settings in force. It is the one place the
 * vehicle layer meets the config: everything below it gets a snapshot handed in.
 * <p>
 * The snapshot is derived from the three config stores ({@code mcheli.cfg}, the server settings and
 * {@code mcheli-wingman.cfg}), which are immutable snapshots themselves and are replaced, never
 * changed, by every load, {@code /mcheli reconfig}, config screen save and server settings sync. A
 * snapshot is built once for each combination of them: {@link #current()} hands out the published
 * one while its sources are still the ones in force, and builds and publishes a new one atomically
 * the first time it is asked after any of them was replaced. A reader therefore never sees half of a
 * reload, and the vehicles' per-tick reads allocate nothing.
 * <p>
 * Like the stores it is fed from ({@code SettingsStore}, ARCHITECTURE.md Part 2 §3.2), it holds an
 * immutable value in a final field, and losing it only costs the time to build it again.
 */
public final class VehicleSettingsHolder {

    /** A snapshot and the store snapshots it was built from, compared by identity. */
    private record Published(McheliSettings config, ServerSettings server, WingmanSettings wingman,
                             VehicleSettingsSnapshot snapshot) {

        boolean isOf(McheliSettings currentConfig, ServerSettings currentServer, WingmanSettings currentWingman) {
            return this.config == currentConfig && this.server == currentServer && this.wingman == currentWingman;
        }
    }

    private static final AtomicReference<Published> PUBLISHED = new AtomicReference<>();

    private VehicleSettingsHolder() {
    }

    /** The snapshot of the settings in force now. */
    public static VehicleSettingsSnapshot current() {
        McheliSettings config = McheliConfig.get();
        ServerSettings server = McheliConfig.server();
        WingmanSettings wingman = WingmanConfig.get();
        Published published = PUBLISHED.get();
        if (published != null && published.isOf(config, server, wingman)) {
            return published.snapshot();
        }
        Published fresh = new Published(config, server, wingman, VehicleSettingsSnapshot.of(config, server, wingman));
        PUBLISHED.set(fresh);
        return fresh.snapshot();
    }
}
