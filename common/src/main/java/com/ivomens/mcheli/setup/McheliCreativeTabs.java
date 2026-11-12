package com.ivomens.mcheli.setup;

import com.ivomens.mcheli.config.CreativeTabSettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.platform.Services;
import net.minecraft.core.registries.Registries;

/** MCHeli's creative tabs: one for the items and one per vehicle kind. */
public final class McheliCreativeTabs {

    public static final CreativeTab ITEMS = CreativeTab.create("MCHeli CE Items");
    public static final CreativeTab HELICOPTERS = CreativeTab.create("MCHeli CE Helicopters");
    public static final CreativeTab PLANES = CreativeTab.create("MCHeli CE Planes");
    public static final CreativeTab SHIPS = CreativeTab.create("MCHeli CE Ships");
    public static final CreativeTab TANKS = CreativeTab.create("MCHeli CE Tanks");
    public static final CreativeTab VEHICLES = CreativeTab.create("MCHeli CE Vehicles");
    public static final CreativeTab RECIPE_ITEMS = CreativeTab.create("MCHeli CE Recipe Items");

    private McheliCreativeTabs() {
    }

    /** Registers the tabs; call once while the mod is constructed. */
    public static void register() {
        register("items", ITEMS);
        register("helicopters", HELICOPTERS);
        register("planes", PLANES);
        register("ships", SHIPS);
        register("tanks", TANKS);
        register("vehicles", VEHICLES);
        register("recipe_items", RECIPE_ITEMS);
    }

    private static void register(String name, CreativeTab tab) {
        Services.REGISTRY.register(Registries.CREATIVE_MODE_TAB, name, tab::getTab);
    }

    /** Applies the icons the config fixes; once every mod has been loaded. */
    public static void applyConfiguredIcons() {
        CreativeTabSettings icons = McheliConfig.creativeTabs();
        ITEMS.setFixedIconItem(icons.items());
        HELICOPTERS.setFixedIconItem(icons.helicopters());
        PLANES.setFixedIconItem(icons.planes());
        SHIPS.setFixedIconItem(icons.ships());
        TANKS.setFixedIconItem(icons.tanks());
        VEHICLES.setFixedIconItem(icons.vehicles());
    }
}
