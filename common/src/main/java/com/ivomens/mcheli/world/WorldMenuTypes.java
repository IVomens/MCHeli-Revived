package com.ivomens.mcheli.world;

import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.world.uavstation.UavStationMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

/** The menus of the world entities; their screens are bound on the client by {@code MenuScreenBindings}. */
public final class WorldMenuTypes {

    public static final Supplier<MenuType<UavStationMenu>> UAV_STATION = Services.REGISTRY.register(
            Registries.MENU, "uav_station", () -> Services.MENUS.createMenuType(UavStationMenu::new));

    private WorldMenuTypes() {
    }

    /** Loading the class registers the menu types; call once while the mod is constructed, on both sides. */
    public static void init() {
    }
}
