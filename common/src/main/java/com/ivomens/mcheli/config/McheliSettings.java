package com.ivomens.mcheli.config;

/**
 * One immutable snapshot of {@code mcheli.cfg}: the raw values, and the typed sections built from
 * them. A change makes a new snapshot with {@link #with}; nothing is ever changed in place.
 */
public record McheliSettings(ConfigValues values, KeySettings keys, ControlSettings controls, RenderSettings render,
                             HudSettings hud, FlightSettings flight, VehicleSettings vehicles, CombatSettings combat,
                             EnergySettings energy, UavSettings uav, NetworkSettings network, LiverySettings liveries,
                             LoadingSettings loading, DebugSettings debug, CommandSettings commands,
                             CreativeTabSettings creativeTabs, AdminSettings admin, HandlingSettings handling,
                             JoystickSettings joystick) {

    public static McheliSettings defaults() {
        return from(ConfigValues.DEFAULTS);
    }

    public static McheliSettings from(ConfigValues v) {
        return new McheliSettings(v, KeySettings.from(v), ControlSettings.from(v), RenderSettings.from(v),
                HudSettings.from(v), FlightSettings.from(v), VehicleSettings.from(v), CombatSettings.from(v),
                EnergySettings.from(v), UavSettings.from(v), NetworkSettings.from(v), LiverySettings.from(v),
                LoadingSettings.from(v), DebugSettings.from(v), CommandSettings.from(v), CreativeTabSettings.from(v),
                AdminSettings.from(v), HandlingSettings.from(v), JoystickSettings.from(v));
    }

    /** The same settings with one option changed. Values from code are trusted and not range checked. */
    public <T> McheliSettings with(Option<T> option, T value) {
        return from(this.values.with(option, value));
    }

    public <T> T get(Option<T> option) {
        return this.values.get(option);
    }
}
