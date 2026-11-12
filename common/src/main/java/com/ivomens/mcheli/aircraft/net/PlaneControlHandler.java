package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.plane.PlaneEntity;
import net.minecraft.server.level.ServerPlayer;

/** Applies plane crew input. Planes have VTOL modes, and fold their wings when they have no hatch. */
public final class PlaneControlHandler extends ControlInputProcessor<PlaneEntity>
        implements ServerMessageHandler<PlaneControlMessage> {

    @Override
    public void handle(PlaneControlMessage message, ServerPlayer sender) {
        PlaneEntity plane = resolveControlled(sender, PlaneEntity.class);
        if (plane == null) {
            handleStationUnmount(sender, message.input());
            return;
        }
        process(plane, message.input(), VehicleExtras.NONE, sender);
    }

    @Override
    protected void handleHatch(PlaneEntity plane, ControlInput.HatchSwitch hatch) {
        if (hatch == ControlInput.HatchSwitch.NONE) {
            return;
        }
        boolean unfold = hatch == ControlInput.HatchSwitch.UNFOLD;
        if (hasHatch(plane)) {
            plane.parts().foldHatch(unfold);
        } else {
            plane.wings().fold(unfold);
        }
    }

    @Override
    protected void handleVtolSwitch(PlaneEntity plane, ControlInput.VtolSwitch vtol) {
        switch (vtol) {
            case VTOL_OFF -> plane.nozzles().switchTo(false);
            case VTOL_ON -> plane.nozzles().switchTo(true);
            case NONE -> {
            }
        }
    }
}
