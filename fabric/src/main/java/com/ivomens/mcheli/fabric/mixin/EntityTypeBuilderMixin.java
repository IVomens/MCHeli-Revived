package com.ivomens.mcheli.fabric.mixin;

import com.ivomens.mcheli.util.Ids;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.types.Type;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Building an entity type looks up its data fixer schema only to log an error when there is none,
 * which there never is for a mod's entities. NeoForge logs that at debug level; MCHeli skips the
 * lookup for its own types, so a Fabric server log does not open with thirty false errors.
 */
@Mixin(EntityType.Builder.class)
abstract class EntityTypeBuilderMixin {

    @WrapOperation(method = "build", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/Util;fetchChoiceType(Lcom/mojang/datafixers/DSL$TypeReference;Ljava/lang/String;)Lcom/mojang/datafixers/types/Type;"))
    private Type<?> mcheli$noSchemaForOwnTypes(DSL.TypeReference type, String key, Operation<Type<?>> original) {
        return key.startsWith(Ids.MOD_ID + ":") ? null : original.call(type, key);
    }
}
