package com.ivomens.mcheli.fabric.mixin;

import com.ivomens.mcheli.fabric.extension.PersistentDataHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's entity persistent data: created on first use, saved and loaded under {@code NeoForgeData}. */
@Mixin(Entity.class)
abstract class EntityMixin implements PersistentDataHolder {

    @Unique
    private CompoundTag mcheli$persistentData;

    @Override
    public CompoundTag mcheli$getPersistentData() {
        if (this.mcheli$persistentData == null) {
            this.mcheli$persistentData = new CompoundTag();
        }
        return this.mcheli$persistentData;
    }

    @Inject(method = "saveWithoutId", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V"))
    private void mcheli$savePersistentData(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        if (this.mcheli$persistentData != null) {
            compound.put(KEY, this.mcheli$persistentData.copy());
        }
    }

    @Inject(method = "load", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V"))
    private void mcheli$loadPersistentData(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains(KEY, Tag.TAG_COMPOUND)) {
            this.mcheli$persistentData = compound.getCompound(KEY);
        }
    }
}
