package com.ivomens.mcheli.util;

import com.ivomens.mcheli.platform.Services;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ModLogger {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss:SSS");
    private static final Logger logger = LogManager.getLogger("mcheli");

    /**
     * Returns the active logger instance (never null).
     */
    public static Logger get() {
        return logger;
    }

    public static void info(String msg, Object... params) {
        logger.info(msg, params);
    }

    public static void warn(String msg, Object... params) {
        logger.warn(msg, params);
    }

    public static void error(String msg, Object... params) {
        logger.error(msg, params);
    }

    public static void debug(String msg, Object... params) {
        logger.debug(msg, params);
    }

    public static void trace(String msg, Object... params) {
        logger.trace(msg, params);
    }

    public static void error(String msg, Throwable t) {
        logger.error(msg, t);
    }

    public static void warn(String msg, Throwable t) {
        logger.warn(msg, t);
    }

    /**
     * Logs a formatted informational message with side and timestamp context.
     * Both {@code {}} placeholders and legacy {@code %s/%d} format specifiers are accepted.
     */
    public static void log(String format, Object... data) {
        logger.info("[{}][mcheli]{} {}", getTime(), sideTag(Services.PLATFORM.isClient()), format(format, data));
    }

    /**
     * Logs a formatted informational message with world context.
     */
    public static void log(Level world, String format, Object... data) {
        logger.info("[{}][mcheli]{} {}", getTime(), worldTag(world), format(format, data));
    }

    /**
     * Logs a formatted informational message with entity context.
     */
    public static void log(Entity entity, String format, Object... data) {
        if (entity != null) {
            log(entity.level(), format, data);
        } else {
            log((Level) null, format, data);
        }
    }

    /** Logs a message tagged with the side it happened on. */
    public static void logSided(boolean isRemote, String format, Object... data) {
        logger.info("{} {}", sideTag(isRemote), format(format, data));
    }

    private static String format(String format, Object... data) {
        if (data == null || data.length == 0) {
            return format;
        }
        if (format.indexOf('%') >= 0) {
            try {
                return String.format(format, data);
            } catch (RuntimeException ignored) {
            }
        }
        StringBuilder sb = new StringBuilder(format.length() + 16 * data.length);
        int argIndex = 0;
        int i = 0;
        while (i < format.length()) {
            int idx = format.indexOf("{}", i);
            if (idx < 0 || argIndex >= data.length) {
                sb.append(format, i, format.length());
                break;
            }
            sb.append(format, i, idx).append(data[argIndex++]);
            i = idx + 2;
        }
        return sb.toString();
    }

    private static String getTime() {
        return LocalTime.now().format(TIME_FORMAT);
    }

    private static String sideTag(boolean isRemote) {
        return isRemote ? "[Client]" : "[Server]";
    }

    private static String worldTag(Level world) {
        if (world == null) return "[UnknownWorld]";
        return world.isClientSide() ? "[ClientWorld]" : "[ServerWorld]";
    }
}
