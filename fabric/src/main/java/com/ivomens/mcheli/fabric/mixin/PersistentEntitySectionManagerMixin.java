package com.ivomens.mcheli.fabric.mixin;

import com.ivomens.mcheli.fabric.event.McheliEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every entity a server level adds, spawned or loaded from disk, passes through here; NeoForge fires its
 * join event at the start of this method.
 */
@Mixin(PersistentEntitySectionManager.class)
abstract class PersistentEntitySectionManagerMixin {

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void mcheli$entityJoin(EntityAccess access, boolean worldGenSpawned, CallbackInfoReturnable<Boolean> cir) {
        if (access instanceof Entity entity) {
            McheliEvents.ENTITY_JOIN.invoker().onLevelChange(entity, entity.level());
        }
    }
}
