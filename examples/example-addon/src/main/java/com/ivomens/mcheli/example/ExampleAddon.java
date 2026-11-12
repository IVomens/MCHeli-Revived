package com.ivomens.mcheli.example;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.event.WeaponFired;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * An addon built against the MCHeli: Revived API alone. It ships a content pack ({@link ExamplePacks}),
 * counts every shot fired on the server, and stops shots fired by players inside the server's spawn
 * protection when the server config says so ({@link ExampleConfig}). It also keeps a sortie count on every
 * vehicle with the API's vehicle data ({@link ExampleSorties}).
 */
@Mod(ExampleAddon.MOD_ID)
public final class ExampleAddon {

    public static final String MOD_ID = "mcheli_example";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** Logs the running total every this many shots. */
    private static final long LOG_EVERY = 100;
    /** The shots fired since the game started; cancelled ones do not count. */
    private static final AtomicLong SHOTS = new AtomicLong();

    public ExampleAddon(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ExampleConfig.SPEC);

        McheliApi api = McheliApi.get();
        LOGGER.info("MCHeli API {} found", api.apiVersion());
        api.events().register(WeaponFired.class, ExampleAddon::onWeaponFired);
        ExampleSorties.register(api);
    }

    /** The shots fired since the game started that were not cancelled. */
    public static long shotsFired() {
        return SHOTS.get();
    }

    /** Server thread, once per shot, before the shot is made. */
    private static void onWeaponFired(WeaponFired fired) {
        if (ExampleConfig.BLOCK_FIRE_IN_SPAWN_PROTECTION.get() && fired.shooter() instanceof ServerPlayer player
                && fired.level().getServer().isUnderSpawnProtection(fired.level(),
                BlockPos.containing(fired.origin()), player)) {
            fired.setCancelled(true);
            return;
        }
        long total = SHOTS.incrementAndGet();
        if (total % LOG_EVERY == 0) {
            LOGGER.info("{} MCHeli shots fired so far; the last one by {} with {}", total,
                    fired.shooter() != null ? fired.shooter().getName().getString() : "nobody", fired.weaponId());
        }
    }
}
