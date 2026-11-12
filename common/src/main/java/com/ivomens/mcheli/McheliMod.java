package com.ivomens.mcheli;

import com.ivomens.mcheli.aircraft.AircraftEntityTypes;
import com.ivomens.mcheli.aircraft.AircraftMenuTypes;
import com.ivomens.mcheli.aircraft.FuelRefillRecipe;
import com.ivomens.mcheli.aircraft.VehicleDamageTargets;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.block.ModBlockEntities;
import com.ivomens.mcheli.combat.CombatEntityTypes;
import com.ivomens.mcheli.combat.weapon.Weapons;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.content.pack.AddonManager;
import com.ivomens.mcheli.content.pack.BuiltinAddonPack;
import com.ivomens.mcheli.content.registry.ContentRegistries;
import com.ivomens.mcheli.equipment.EquipmentEntityTypes;
import com.ivomens.mcheli.equipment.rangefinder.RangeFinderRechargeRecipe;
import com.ivomens.mcheli.equipment.throwable.Throwables;
import com.ivomens.mcheli.hud.HudManager;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.registry.McheliCriteria;
import com.ivomens.mcheli.setup.BlockAndItemRegistration;
import com.ivomens.mcheli.setup.McheliCreativeTabs;
import com.ivomens.mcheli.setup.McheliMessages;
import com.ivomens.mcheli.sound.SoundPatcherUtil;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.wingman.McHeliWingman;
import com.ivomens.mcheli.world.WorldEntityTypes;
import com.ivomens.mcheli.world.WorldMenuTypes;

import java.nio.file.Path;

/**
 * The mod's entry point, the same on every loader. The loader calls {@link #init()} while the mod is
 * constructed, {@link #registerBlocks()} and {@link #registerItems()} while those registries are open,
 * and {@link #onLoadComplete()} once every mod has loaded. The client adds its part through
 * {@code McheliClient}.
 */
public final class McheliMod {

    private static final String CONFIG_FILE = "config/mcheli.cfg";

    private McheliMod() {
    }

    /**
     * Loads the config and the content packs and sets up registration. Call once while the mod is
     * constructed, before any registry is opened.
     */
    public static void init() {
        ModLogger.log("MC Ver:1.21.1 MOD Ver:" + Services.PLATFORM.getModVersion());
        Path source = Services.PLATFORM.getModSource();
        ModLogger.log("SourcePath: " + (source != null ? source.toFile().getPath() : ""));
        ModLogger.log("CurrentDirectory:" + Services.PLATFORM.getGameDir().toAbsolutePath());

        SoundPatcherUtil.patchSounds();
        McheliConfig.load(CONFIG_FILE);
        McheliConfig.save();
        if (BuiltinAddonPack.instance().ensureExtracted()) {
            McheliConfig.save();
        }
        registerContentTypes();
        ContentRegistries.loadContents(AddonManager.getAddonDir());

        registerContent();
    }

    /** Hands each feature's content registries to the content pipeline. */
    public static void registerContentTypes() {
        HudManager.registerContent();
        Weapons.registerContent(VehicleDamageTargets.BY_NAME);
        VehicleTypes.registerContent();
        Throwables.registerContent();
    }

    /** Fixed registry entries; the blocks and items follow when their registries open. */
    private static void registerContent() {
        AircraftEntityTypes.init();
        EquipmentEntityTypes.init();
        WorldEntityTypes.init();
        CombatEntityTypes.init();
        Network.register(McheliMessages.registry());
        ModBlockEntities.init();
        AircraftMenuTypes.init();
        WorldMenuTypes.init();
        FuelRefillRecipe.register();
        RangeFinderRechargeRecipe.register();
        McheliCriteria.registerTriggers();
        McHeliWingman.init();
        McheliCreativeTabs.register();
    }

    /** Creates and registers the blocks. Call while the block registry is open. */
    public static void registerBlocks() {
        BlockAndItemRegistration.registerBlocks();
    }

    /** Creates and registers the fixed and the content-defined items. Call while the item registry is open. */
    public static void registerItems() {
        BlockAndItemRegistration.registerItems();
    }

    /** Once every mod has been loaded; call on the main thread. */
    public static void onLoadComplete() {
        McheliCreativeTabs.applyConfiguredIcons();
        Weapons.onItemsRegistered();
    }
}
