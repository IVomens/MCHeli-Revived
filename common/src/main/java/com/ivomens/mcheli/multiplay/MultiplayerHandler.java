package com.ivomens.mcheli.multiplay;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Operator commands the server runs (clearing every aircraft, saving an uploaded screenshot) and their log
 * lines. What the operators asked for is kept per server by {@link OperatorRequests}.
 */
public final class MultiplayerHandler {

    private static final DateTimeFormatter SCREENSHOT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");

    private MultiplayerHandler() {
    }

    /** Removes every aircraft in the player's dimension (requires command permission level 2). */
    public static void destroyAllAircraft(Player player) {
        if (!player.hasPermissions(2) || !(player.level() instanceof ServerLevel level)) return;
        List<Entity> aircraft = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AircraftEntity) {
                aircraft.add(entity);
            }
        }
        aircraft.forEach(Entity::discard);
    }

    public static void saveScreenshot(String playerName, byte[] data) throws IOException {
        File dir = new File("screenshots_op");
        dir.mkdir();

        String fileName = playerName.replaceAll("[^A-Za-z0-9_\\-]", "_") + "_" + SCREENSHOT_TIME_FORMAT.format(LocalDateTime.now()) + ".png";
        File file = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file); DataOutputStream dos = new DataOutputStream(fos)) {
            dos.write(data);
        }
        logInfo("[mcheli]Screenshot saved: %s", file.getAbsolutePath());
    }

    public static void logInfo(String format, Object... args) {
        ModLogger.log(String.format(format, args));
    }

    public static void logError(String format, Object... args) {
        ModLogger.error(String.format(format, args));
    }
}
