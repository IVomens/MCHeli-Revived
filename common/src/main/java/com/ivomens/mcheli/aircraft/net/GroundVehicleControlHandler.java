package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Applies ground vehicle crew input. Ground vehicles release tow chains and have no radar to switch. */
public final class GroundVehicleControlHandler extends ControlInputProcessor<GroundVehicleEntity>
        implements ServerMessageHandler<GroundVehicleControlMessage> {

    @Override
    public void handle(GroundVehicleControlMessage message, ServerPlayer sender) {
        if (sender.getVehicle() instanceof GroundVehicleEntity vehicle) {
            process(vehicle, message.input(), message.extras(), sender);
        }
    }

    @Override
    protected boolean appliesRadar() {
        return false;
    }

    @Override
    protected void handleChain(GroundVehicleEntity vehicle, int chainId, Player player) {
        TowChains.release(vehicle, chainId, player);
    }
}
