package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.setup.client.item.ItemColorRegistration;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.item.ItemColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's {@code RegisterColorHandlersEvent.Item}, fired where the game builds its item colours. */
@Mixin(ItemColors.class)
abstract class ItemColorsMixin {

    @Inject(method = "createDefault", at = @At("RETURN"))
    private static void mcheli$registerColors(BlockColors blockColors, CallbackInfoReturnable<ItemColors> cir) {
        ItemColorRegistration.register(cir.getReturnValue());
    }
}
