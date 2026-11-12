package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.networking.ServerMessageHandler;
import net.minecraft.server.level.ServerPlayer;

/** Retargets a guided missile; only the player who fired it may. */
public final class MissileTargetHandler implements ServerMessageHandler<MissileTargetMessage> {

    @Override
    public void handle(MissileTargetMessage message, ServerPlayer sender) {
        if (ServerMessageHandler.entityNear(sender, message.missileId()) instanceof ProjectileEntity missile
                && missile.shooter() == sender) {
            missile.setTarget(ServerMessageHandler.entityNear(sender, message.targetId()));
        }
    }
}
