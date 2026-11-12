package com.ivomens.mcheli.setup.client;

import com.ivomens.mcheli.aircraft.AircraftMenu;
import com.ivomens.mcheli.aircraft.AircraftMenuTypes;
import com.ivomens.mcheli.aircraft.AircraftPanelMenu;
import com.ivomens.mcheli.aircraft.client.AircraftPanelScreen;
import com.ivomens.mcheli.aircraft.client.AircraftTrunkScreen;
import com.ivomens.mcheli.aircraft.client.ClassicAircraftScreen;
import com.ivomens.mcheli.block.ModBlockEntities;
import com.ivomens.mcheli.block.client.DraftingTableScreen;
import com.ivomens.mcheli.setup.client.config.ConfigScreen;
import com.ivomens.mcheli.ui.client.McContainerScreen;
import com.ivomens.mcheli.world.WorldMenuTypes;
import com.ivomens.mcheli.world.uavstation.UavStationMenu;
import com.ivomens.mcheli.world.uavstation.client.UavStationScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** Binds every MCHeli menu type to its screen. Client only. */
public final class MenuScreenBindings {

    private MenuScreenBindings() {
    }

    public static void register(Registrar registrar) {
        registrar.register(ModBlockEntities.DRAFTING_TABLE_MENU.get(), DraftingTableScreen::new);
        registrar.<AircraftPanelMenu, McContainerScreen<AircraftPanelMenu>>register(AircraftMenuTypes.AIRCRAFT.get(),
                (menu, inv, title) -> menu.containerOnly
                        ? new AircraftTrunkScreen(menu, inv, title)
                        : new AircraftPanelScreen(menu, inv, title, MenuScreenBindings::openOptions));
        registrar.<AircraftMenu, ClassicAircraftScreen>register(AircraftMenuTypes.AIRCRAFT_LEGACY.get(),
                (menu, inv, title) -> new ClassicAircraftScreen(menu, inv, title, MenuScreenBindings::openOptions));
        registrar.<UavStationMenu, UavStationScreen>register(WorldMenuTypes.UAV_STATION.get(), (menu, inventory, title) ->
                new UavStationScreen(menu, inventory, title, McheliClient.session().uavFeed()));
    }

    private static void openOptions() {
        ConfigScreen.open(Minecraft.getInstance());
    }

    /** The loader's menu screen registration, e.g. a method reference to its register event. */
    public interface Registrar {
        <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(
                MenuType<? extends M> type, MenuScreens.ScreenConstructor<M, U> factory);
    }
}
