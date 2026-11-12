package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.tank.TankEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Applies tank crew input. The driver's client turns the hull itself and reports its heading; the
 * steering keys still go to the server, which publishes them so other players see the tracks and
 * wheels steer and hear them. The brake also stops a tank that stands still from turning on the spot.
 */
public final class TankControlHandler extends ControlInputProcessor<TankEntity>
        implements ServerMessageHandler<TankControlMessage> {

    /** Below this throttle and squared ground speed a braking tank stops turning on the spot. */
    private static final double STANDSTILL_THROTTLE = 0.03;
    private static final double STANDSTILL_SPEED_SQ = 0.01;

    @Override
    public void handle(TankControlMessage message, ServerPlayer sender) {
        TankEntity tank = resolveControlled(sender, TankEntity.class);
        if (tank == null) {
            handleStationUnmount(sender, message.input());
            return;
        }
        process(tank, message.input(), VehicleExtras.NONE, sender);
    }

    @Override
    protected void handlePilotControls(TankEntity tank, ControlSwitches switches, Player player) {
        if (!tank.isPilot(player)) {
            return;
        }
        tank.keys().holdThrottleUp(switches.throttleUp());
        tank.keys().holdThrottleDown(switches.throttleDown());
        tank.keys().holdMoveLeft(switches.moveLeft());
        tank.keys().holdMoveRight(switches.moveRight());
        double dx = tank.getX() - tank.xo;
        double dz = tank.getZ() - tank.zo;
        if (switches.useBrake() && tank.engine().throttle() <= STANDSTILL_THROTTLE
                && dx * dx + dz * dz < STANDSTILL_SPEED_SQ) {
            tank.keys().holdMoveLeft(false);
            tank.keys().holdMoveRight(false);
        }
        tank.keys().setBrake(switches.useBrake());
    }
}
