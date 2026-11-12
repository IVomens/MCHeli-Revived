package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.setup.client.ClientEventHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's {@code RenderNameTagEvent}: the crew of the aircraft the player rides show no name tags. */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldShowName(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean mcheli$nameTag(EntityRenderer<?> renderer, Entity entity, Operation<Boolean> original) {
        return !ClientEventHooks.shouldHideNameTag(entity) && original.call(renderer, entity);
    }
}
