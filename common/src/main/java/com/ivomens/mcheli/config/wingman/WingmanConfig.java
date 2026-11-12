package com.ivomens.mcheli.config.wingman;

import com.ivomens.mcheli.config.ConfigFile;
import com.ivomens.mcheli.config.Layout;
import com.ivomens.mcheli.config.Option;
import com.ivomens.mcheli.config.SettingsStore;
import com.ivomens.mcheli.util.ModLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The current wingman settings, stored in {@code config/mcheli-wingman.cfg} (its own file, never
 * MCHeli's main config) in the same {@code key = value} format as {@code mcheli.cfg}. The
 * {@code /wingman} sub-commands and the wingman panel change them for the running server only.
 */
public final class WingmanConfig {

    public static final String FILE_NAME = "mcheli-wingman.cfg";
    /** The TOML file older versions wrote; read once when the .cfg does not exist yet. */
    private static final String LEGACY_FILE_NAME = "mcheli-wingman.toml";

    /** At or above this configured range (blocks) the UAV comm-range cut-off is treated as unlimited. */
    public static final int UAV_UNLIMITED_THRESHOLD = WingmanSettings.UAV_UNLIMITED_THRESHOLD;

    private static final SettingsStore<WingmanSettings> STORE = new SettingsStore<>(WingmanSettings::defaults);

    /** File sections and their options, in file order. */
    private static final ConfigFile FILE = new ConfigFile(layout(), List.of(), false, true);

    private WingmanConfig() {
    }

    public static WingmanSettings get() {
        return STORE.current();
    }

    /** Replaces the whole snapshot; for code that pins settings and puts them back. */
    public static void set(WingmanSettings settings) {
        STORE.set(settings);
    }

    public static WingmanSettings update(UnaryOperator<WingmanSettings> change) {
        return STORE.update(change);
    }

    /**
     * Reads {@code mcheli-wingman.cfg} from the config directory (or the old TOML file the first time)
     * and writes the file back with every option listed.
     */
    public static void load(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        Path legacy = configDir.resolve(LEGACY_FILE_NAME);
        WingmanSettings settings = WingmanSettings.defaults();
        if (Files.isRegularFile(file)) {
            settings = read(file);
        } else if (Files.isRegularFile(legacy)) {
            settings = read(legacy);
        }
        STORE.replace(settings, file);
        try {
            FILE.write(file, settings.values());
        } catch (IOException e) {
            ModLogger.error("Could not write {}", file.toAbsolutePath(), e);
        }
    }

    private static WingmanSettings read(Path file) {
        try {
            return WingmanSettings.from(FILE.read(file));
        } catch (IOException e) {
            ModLogger.error("Could not read {}", file.toAbsolutePath(), e);
            return WingmanSettings.defaults();
        }
    }

    private static List<Layout> layout() {
        List<Layout> layout = new ArrayList<>();
        section(layout, "uav", WingmanSettings.UAV_CONTROLLER_RANGE, WingmanSettings.UAV_MAX_DISTANCE,
                WingmanSettings.FORCE_CHUNKLOAD);
        section(layout, "tvmissile", WingmanSettings.TV_MISSILE_CHUNK_LOAD, WingmanSettings.TV_MISSILE_MAX_RANGE);
        section(layout, "formation", WingmanSettings.FORMATION_SIDE_DIST, WingmanSettings.FORMATION_ALT_OFFSET,
                WingmanSettings.FORMATION_REAR_DIST, WingmanSettings.MAX_WINGMEN, WingmanSettings.MIN_ATTACK_ALTITUDE,
                WingmanSettings.MAX_ATTACK_ALTITUDE);
        return layout;
    }

    private static void section(List<Layout> layout, String name, Option<?>... options) {
        layout.add(Layout.text("[" + name + "]"));
        for (Option<?> option : options) {
            layout.add(Layout.entry(option));
        }
        layout.add(Layout.blank());
    }
}
