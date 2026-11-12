package com.ivomens.mcheli.fx.client.legacy;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Block dust kicked up by rotor wash / impacts: a terrain particle that keeps exactly the given motion. */
public class BlockDustParticle extends TerrainParticle {

    protected BlockDustParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed,
                                    double zSpeed, BlockState state) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, state);
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
    }

    public void setScale(float scale) {
        this.quadSize = 0.1F * scale;
    }

    public static class Factory {

        /** {@code params[0]} is the block state id; invisible blocks make no dust. */
        @Nullable
        public BlockDustParticle createParticle(ClientLevel level, double x, double y, double z,
                                                    double xSpeed, double ySpeed, double zSpeed, int... params) {
            if (params.length == 0) {
                return null;
            }
            BlockState state = Block.stateById(params[0]);
            return state.getRenderShape() == RenderShape.INVISIBLE ? null
                    : new BlockDustParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, state);
        }
    }
}
