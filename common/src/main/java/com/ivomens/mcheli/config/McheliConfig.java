package com.ivomens.mcheli.config;

import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.ModLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

/**
 * The current {@code mcheli.cfg} and the server settings this client last received. Both are
 * immutable snapshots: read one with {@link #get()} (or a section shortcut such as {@link #keys()}),
 * and change it with {@link #update}, which swaps in a new snapshot.
 */
public final class McheliConfig {

    private static final SettingsStore<McheliSettings> CONFIG = new SettingsStore<>(McheliSettings::defaults);
    private static final SettingsStore<ServerSettings> SERVER = new SettingsStore<>(() -> ServerSettings.DEFAULTS);

    private McheliConfig() {
    }

    public static McheliSettings get() {
        return CONFIG.current();
    }

    /** Replaces the whole snapshot; for code that pins settings and puts them back. */
    public static void set(McheliSettings settings) {
        CONFIG.set(settings);
    }

    public static McheliSettings update(UnaryOperator<McheliSettings> change) {
        return CONFIG.update(change);
    }

    /**
     * Reads the config file; a relative {@code config/...} name resolves against the loader's config
     * directory. A missing or unreadable file leaves the defaults.
     */
    public static void load(String fileName) {
        load(Path.of(resolvePath(fileName)));
    }

    public static void load(Path file) {
        if (!Files.isRegularFile(file)) {
            ModLogger.log(file.toAbsolutePath() + " not found.");
            CONFIG.replace(McheliSettings.defaults(), file);
            return;
        }
        try {
            CONFIG.replace(new McheliConfigFile(Services.PLATFORM.getModVersion()).read(file), file);
            ModLogger.log("loaded " + file.toAbsolutePath());
        } catch (IOException e) {
            ModLogger.error("Could not read {}", file.toAbsolutePath(), e);
            CONFIG.replace(McheliSettings.defaults(), file);
        }
    }

    /** Reads the file the config was loaded from again, then writes it back. */
    public static void reload() {
        CONFIG.file().ifPresent(McheliConfig::load);
        save();
    }

    /** Writes the current snapshot back to the file it was loaded from. */
    public static void save() {
        CONFIG.file().ifPresent(file -> {
            try {
                new McheliConfigFile(Services.PLATFORM.getModVersion()).write(file, get());
                ModLogger.log("update " + file.toAbsolutePath());
            } catch (IOException e) {
                ModLogger.log(file.toAbsolutePath() + " cannot open.");
            }
        });
    }

    public static ServerSettings server() {
        return SERVER.current();
    }

    /** Client: the settings the server just sent. */
    public static void applyServerSettings(ServerSettings settings) {
        SERVER.set(settings);
    }

    public static KeySettings keys() {
        return get().keys();
    }

    public static ControlSettings controls() {
        return get().controls();
    }

    public static RenderSettings render() {
        return get().render();
    }

    public static HudSettings hud() {
        return get().hud();
    }

    public static FlightSettings flight() {
        return get().flight();
    }

    public static VehicleSettings vehicles() {
        return get().vehicles();
    }

    public static CombatSettings combat() {
        return get().combat();
    }

    public static EnergySettings energy() {
        return get().energy();
    }

    public static UavSettings uav() {
        return get().uav();
    }

    public static NetworkSettings network() {
        return get().network();
    }

    public static LiverySettings liveries() {
        return get().liveries();
    }

    public static LoadingSettings loading() {
        return get().loading();
    }

    public static DebugSettings debug() {
        return get().debug();
    }

    public static CommandSettings commands() {
        return get().commands();
    }

    public static CreativeTabSettings creativeTabs() {
        return get().creativeTabs();
    }

    public static AdminSettings admin() {
        return get().admin();
    }

    public static HandlingSettings handling() {
        return get().handling();
    }

    public static JoystickSettings joystick() {
        return get().joystick();
    }

    public static boolean useModernHud() {
        return hud().useModernHud();
    }

    public static boolean useModernParticles() {
        return render().useModernParticles();
    }

    /** Modern HUD colour as opaque ARGB. */
    public static int hudColor() {
        return hud().colorArgb();
    }

    public static LiveryPolicy liveryPolicy() {
        return liveries().policy();
    }

    /** Whether the entity's class name contains {@code name} (after the leading "class "). */

    private static String resolvePath(String cfgFile) {
        File file = new File(cfgFile);
        if (file.isAbsolute()) {
            return file.getPath();
        }
        String relative = cfgFile.startsWith("./") ? cfgFile.substring(2) : cfgFile;
        try {
            if (relative.startsWith("config/") || relative.startsWith("config\\")) {
                return Services.PLATFORM.getConfigDir().resolve(relative.substring("config/".length())).toString();
            }
            return Services.PLATFORM.getGameDir().resolve(relative).toString();
        } catch (Throwable t) {
            // the loader's paths are not available in unit tests
            return relative;
        }
    }
}
