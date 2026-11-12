package com.ivomens.mcheli.gametest.fabric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Fabric's headless test server puts the world spawn at the origin, NeoForge's at (0, 60, 0) by
 * default. The tests themselves run at a random spot either way, but anything that sends a player to
 * the world spawn would land somewhere else, so the Fabric run uses NeoForge's spawn.
 */
@Mixin(GameTestServer.class)
abstract class GameTestServerMixin {

    @Unique
    private static final BlockPos NEOFORGE_SPAWN = new BlockPos(0, 60, 0);

    @ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true)
    private static BlockPos mcheli$testGridCorner(BlockPos spawnPos) {
        return BlockPos.ZERO.equals(spawnPos) ? NEOFORGE_SPAWN : spawnPos;
    }
}
