package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Opens a numbered screen; the aircraft inventory only for the aircraft the sender rides or controls. */
public final class OpenScreenHandler implements ServerMessageHandler<OpenScreenMessage> {

    @Override
    public void handle(OpenScreenMessage message, ServerPlayer sender) {
        int screenId = message.screenId();
        if (screenId < 0) {
            return;
        }
        if (screenId != GuiOpener.GUIID_INVENTORY) {
            GuiOpener.open(sender, screenId);
            return;
        }
        AircraftEntity aircraft = RiddenVehicle.of(sender);
        if (aircraft != null) {
            aircraft.displayInventory(sender);
        }
    }
}
