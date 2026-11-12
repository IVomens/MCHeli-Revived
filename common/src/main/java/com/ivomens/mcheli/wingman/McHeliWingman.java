package com.ivomens.mcheli.wingman;

import com.ivomens.mcheli.combat.projectile.FlightLimits;
import com.ivomens.mcheli.combat.projectile.ProjectilePaths;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.wingman.block.WingmanMarkerBlock;
import com.ivomens.mcheli.wingman.block.WingmanMarkerTileEntity;
import com.ivomens.mcheli.wingman.command.WingmanCommand;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.Supplier;

/**
 * Bootstrap of the wingman subsystem (formations, the autopilot, missions, markers and chunk leases):
 * its registrations, and the game events, which {@code CommonEventHooks} hands to the methods below and
 * these to the server's {@link WingmanService}. Client levels are ignored here.
 */
public final class McHeliWingman {

    public static final String MODID = "mcheli";

    public static final Logger logger = LogManager.getLogger("McHeliWingman");

    public static final Supplier<WingmanMarkerBlock> MARKER_BLOCK =
            Services.REGISTRY.register(Registries.BLOCK, "wingman_marker", WingmanMarkerBlock::new);

    public static final Supplier<BlockItem> MARKER_ITEM =
            Services.REGISTRY.register(Registries.ITEM, "wingman_marker", () -> new BlockItem(MARKER_BLOCK.get(), new Item.Properties()));

    public static final Supplier<BlockEntityType<WingmanMarkerTileEntity>> MARKER_TE =
            Services.REGISTRY.register(Registries.BLOCK_ENTITY_TYPE, "wingman_marker_te",
                    () -> BlockEntityType.Builder.of(WingmanMarkerTileEntity::new, MARKER_BLOCK.get()).build(null));

    /** Items that belong in MCHeli's creative tab. */
    public static final List<Supplier<? extends Item>> CREATIVE_TAB_ITEMS = List.of(MARKER_ITEM);

    private McHeliWingman() {}

    /**
     * Called once from MCHeli's common init (both sides): loads the config and hooks projectiles that
     * keep chunks loaded up to their server's chunk loader.
     */
    public static void init() {
        WingmanConfig.load(Services.PLATFORM.getConfigDir());
        ProjectilePaths.addListener(projectile -> {
            if (projectile.level() instanceof ServerLevel level && !projectile.isRemoved()) {
                WingmanService.of(level).requestProjectilePath(projectile);
            }
        });
        FlightLimits.setSteeredMissileRange(() -> WingmanConfig.get().tvMissileMaxRange());
        logger.info("McHeli Wingman initialized");
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        WingmanCommand.register(dispatcher);
    }

    // Game events, called from CommonEventHooks

    /** The server's wingmen go with it, so the next world opened in the same game starts clean. */
    public static void onServerStopped(MinecraftServer server) {
        WingmanService.of(server).stop();
    }

    public static void onServerTick(MinecraftServer server) {
        WingmanService.of(server).onServerTick(server);
    }

    public static void onLevelTickStart(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            WingmanService.of(serverLevel).onLevelTickStart(serverLevel);
        }
    }

    public static void onLevelTickEnd(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            WingmanService.of(serverLevel).onLevelTickEnd(serverLevel);
        }
    }

    public static void onLevelUnload(LevelAccessor level) {
        if (level instanceof ServerLevel serverLevel) {
            WingmanService.of(serverLevel).onLevelUnload(serverLevel);
        }
    }

    /** An entity has joined a level and no other handler cancelled it. */
    public static void onEntityJoined(Entity entity, Level level) {
        if (level instanceof ServerLevel serverLevel) {
            WingmanService.of(serverLevel).onEntityJoined(entity, level);
        }
    }

    public static void onEntityLeave(Entity entity, Level level) {
        if (level instanceof ServerLevel serverLevel) {
            WingmanService.of(serverLevel).onEntityLeave(entity, level);
        }
    }

    public static void onPlayerLogout(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            WingmanService.of(serverPlayer).onPlayerLogout(serverPlayer);
        }
    }

    public static void onLivingDeath(LivingEntity entity) {
        if (entity.level() instanceof ServerLevel level) {
            WingmanService.of(level).onLivingDeath(entity);
        }
    }
}
