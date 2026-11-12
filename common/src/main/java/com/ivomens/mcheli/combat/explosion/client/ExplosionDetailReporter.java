package com.ivomens.mcheli.combat.explosion.client;

import com.ivomens.mcheli.combat.explosion.net.ExplosionDetailMessage;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.networking.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import javax.annotation.Nullable;

/**
 * Tells the server whether this client draws explosions from the blocks they affect: once per
 * connection, and again whenever the particle style changes. The server sends the blocks only then.
 */
public final class ExplosionDetailReporter {

    @Nullable
    private ClientPacketListener toldConnection;
    private boolean toldBlocks;

    /** Every client tick. */
    public void onClientTick(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || minecraft.player == null) {
            this.toldConnection = null;
            return;
        }
        boolean blocks = !Effects.get().drawsWholeExplosions();
        if (connection != this.toldConnection || blocks != this.toldBlocks) {
            Network.sendToServer(new ExplosionDetailMessage(blocks));
            this.toldConnection = connection;
            this.toldBlocks = blocks;
        }
    }
}
