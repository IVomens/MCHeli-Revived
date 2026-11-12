package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

/** The menus of an aircraft; their screens are bound on the client by {@code MenuScreenBindings}. */
public final class AircraftMenuTypes {

    /** Aircraft panel (fuel, parachutes, weapons, trunk) or trunk-only view; see {@link AircraftPanelMenu}. */
    public static final Supplier<MenuType<AircraftPanelMenu>> AIRCRAFT = Services.REGISTRY.register(
            Registries.MENU, "aircraft", () -> Services.MENUS.createMenuType(AircraftPanelMenu::new));
    /** Classic aircraft GUI (fuel + parachute slots, reload button, command line). */
    public static final Supplier<MenuType<AircraftMenu>> AIRCRAFT_LEGACY = Services.REGISTRY.register(
            Registries.MENU, "aircraft_legacy", () -> Services.MENUS.createMenuType(AircraftMenu::new));

    private AircraftMenuTypes() {
    }

    /** Loading the class registers the menu types; call once while the mod is constructed, on both sides. */
    public static void init() {
    }
}
