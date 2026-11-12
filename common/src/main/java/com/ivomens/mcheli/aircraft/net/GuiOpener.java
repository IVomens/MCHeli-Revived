package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftMenu;
import com.ivomens.mcheli.config.DebugLog;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/**
 * Server-side opening of the numbered MCHeli GUIs ({@code OpenScreenMessage}). Each id maps to a
 * menu; the matching screen is created on the client by {@code MenuScreenBindings}.
 */
public final class GuiOpener {

    public static final int GUIID_AIRCRAFT = 1;
    public static final int GUIID_INVENTORY = 3;
    public static final int GUIID_DRAFTING = 4;

    private GuiOpener() {
    }

    public static void open(ServerPlayer player, int id) {
        DebugLog.log(player.level(), "GuiOpener.open ID=%d (%d, %d, %d)", id, player.getBlockX(),
                player.getBlockY(), player.getBlockZ());

        switch (id) {
            case GUIID_AIRCRAFT -> {
                AircraftEntity aircraft = switch (player.getVehicle()) {
                    case AircraftEntity ridden -> ridden;
                    case IUavStation station -> station.getControlled();
                    case null, default -> null;
                };
                if (aircraft != null) {
                    Services.MENUS.openMenu(player, new SimpleMenuProvider((cid, inv, p) -> new AircraftMenu(cid, inv, aircraft),
                            Component.literal(aircraft.getGuiInventory().getInventoryName())),
                            buf -> buf.writeInt(aircraft.getId()));
                }
            }
            default -> {
                // The aircraft inventory is opened by the aircraft itself, the drafting table by its
                // block and the UAV station by its entity; the options and the multiplayer manager are
                // client screens.
            }
        }
    }
}
