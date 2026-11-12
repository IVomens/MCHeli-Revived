package com.ivomens.mcheli.config;

import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.world.level.Level;

/** Messages that are only logged while the config's debug logging is on. */
public final class DebugLog {

    private DebugLog() {
    }

    public static void log(boolean isRemote, String format, Object... data) {
        if (McheliConfig.debug().debugLog()) {
            ModLogger.logSided(isRemote, format, data);
        }
    }

    /** Logs with the side of the level, or as the server's when there is none. */
    public static void log(Level world, String format, Object... data) {
        if (world == null) {
            log(false, "[UnknownWorld] " + format, data);
        } else {
            log(world.isClientSide(), "[World] " + format, data);
        }
    }
}
