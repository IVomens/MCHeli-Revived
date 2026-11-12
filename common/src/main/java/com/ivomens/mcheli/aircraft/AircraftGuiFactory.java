package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.api.event.VehicleScreen;
import com.ivomens.mcheli.event.PermissionEvents;
import com.ivomens.mcheli.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;

/** Opens the aircraft panel ({@link AircraftPanelMenu}) or its trunk-only view from the server. */
public class AircraftGuiFactory {

    public static final AircraftGuiFactory INSTANCE = new AircraftGuiFactory();

    protected AircraftGuiFactory() {
    }

    /**
     * Whether the GUI may open: the vehicle is alive, in the player's dimension and, for the pilot
     * GUI, flown by them. Control packets can race a seat change, so a refusal is not an error.
     */
    private static boolean canOpen(Player player, AircraftEntity entity, boolean pilotGui) {
        return entity != null && entity.isAlive() && player.level() == entity.level()
                && (!pilotGui || entity.isPilot(player));
    }

    public void openGui(Player player, AircraftEntity entity) {
        Objects.requireNonNull(player);
        if (canOpen(player, entity, true)) {
            open(player, new AircraftGuiData(entity, false));
        }
    }

    public void openContainer(Player player, AircraftEntity entity) {
        Objects.requireNonNull(player);
        if (canOpen(player, entity, false)) {
            open(player, new AircraftGuiData(entity, true));
        }
    }

    /** Opens the menu, unless the API's listeners refuse it. */
    private void open(Player player, AircraftGuiData data) {
        if (!(player instanceof ServerPlayer sp)) return;
        AircraftEntity ac = data.guiHolder();
        if (!PermissionEvents.mayOpen(ac, sp, data.containerOnly() ? VehicleScreen.CARGO : VehicleScreen.PANEL)) {
            return;
        }
        Component title = Component.translatable(ac.identity().translationKey());
        Services.MENUS.openMenu(sp, new SimpleMenuProvider((id, inv, p) -> new AircraftPanelMenu(id, inv, ac, data.containerOnly()), title),
                buf -> {
                    buf.writeInt(ac.getId());
                    buf.writeBoolean(data.containerOnly());
                });
    }
}
