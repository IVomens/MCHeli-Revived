package com.ivomens.mcheli.aircraft.net;

import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Applies helicopter crew input. Helicopters fold their rotor blades and release tow chains, and
 * take neither detached turret aim nor radar switching.
 */
public final class HelicopterControlHandler extends ControlInputProcessor<HelicopterEntity>
        implements ServerMessageHandler<HelicopterControlMessage> {

    /** Fold state values of {@link HelicopterEntity#setFoldBladeStat}. */
    private static final byte BLADES_FOLDING = 1;
    private static final byte BLADES_UNFOLDING = 3;

    @Override
    public void handle(HelicopterControlMessage message, ServerPlayer sender) {
        HelicopterEntity heli = resolveControlled(sender, HelicopterEntity.class);
        if (heli == null) {
            handleStationUnmount(sender, message.input());
            return;
        }
        process(heli, message.input(), message.extras(), sender);
    }

    @Override
    protected boolean appliesDetachedAim() {
        return false;
    }

    @Override
    protected boolean appliesRadar() {
        return false;
    }

    @Override
    protected void handleBladeFold(HelicopterEntity heli, VehicleExtras.BladeStatus blades) {
        switch (blades) {
            case UNFOLD -> heli.blades().setState(BLADES_UNFOLDING);
            case FOLD -> heli.blades().setState(BLADES_FOLDING);
            case NONE -> {
            }
        }
    }

    @Override
    protected void handleChain(HelicopterEntity heli, int chainId, Player player) {
        TowChains.release(heli, chainId, player);
    }
}
