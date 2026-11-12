package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.ship.ShipEntity;
import net.minecraft.server.level.ServerPlayer;

/** Applies ship crew input. Ships have VTOL modes, and fold their wings when they have no hatch. */
public final class ShipControlHandler extends ControlInputProcessor<ShipEntity>
        implements ServerMessageHandler<ShipControlMessage> {

    @Override
    public void handle(ShipControlMessage message, ServerPlayer sender) {
        ShipEntity ship = resolveControlled(sender, ShipEntity.class);
        if (ship == null) {
            handleStationUnmount(sender, message.input());
            return;
        }
        process(ship, message.input(), VehicleExtras.NONE, sender);
    }

    @Override
    protected void handleHatch(ShipEntity ship, ControlInput.HatchSwitch hatch) {
        if (hatch == ControlInput.HatchSwitch.NONE) {
            return;
        }
        boolean unfold = hatch == ControlInput.HatchSwitch.UNFOLD;
        if (hasHatch(ship)) {
            ship.parts().foldHatch(unfold);
        } else {
            ship.wings().fold(unfold);
        }
    }

    @Override
    protected void handleVtolSwitch(ShipEntity ship, ControlInput.VtolSwitch vtol) {
        switch (vtol) {
            case VTOL_OFF -> ship.nozzles().switchTo(false);
            case VTOL_ON -> ship.nozzles().switchTo(true);
            case NONE -> {
            }
        }
    }
}
