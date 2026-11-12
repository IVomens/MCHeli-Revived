package com.ivomens.mcheli.example.gametest.mixin;

import com.ivomens.mcheli.example.gametest.TestSpawnProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets {@link TestSpawnProtection} protect an area on servers other than a dedicated one (which overrides this). */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {

    @Inject(method = "isUnderSpawnProtection", at = @At("HEAD"), cancellable = true)
    private void mcheliExampleTest$protectTestArea(ServerLevel level, BlockPos pos, Player player,
                                                   CallbackInfoReturnable<Boolean> result) {
        if (TestSpawnProtection.covers(level, pos)) {
            result.setReturnValue(true);
        }
    }
}
