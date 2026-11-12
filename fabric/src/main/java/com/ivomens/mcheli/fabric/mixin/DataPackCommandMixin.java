package com.ivomens.mcheli.fabric.mixin;

import com.ivomens.mcheli.fabric.extension.HiddenPack;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.commands.DataPackCommand;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;

/** {@code /datapack list enabled} leaves hidden packs out, as on NeoForge. */
@Mixin(DataPackCommand.class)
abstract class DataPackCommandMixin {

    @ModifyExpressionValue(method = "listEnabledPacks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/repository/PackRepository;getSelectedPacks()Ljava/util/Collection;"))
    private static Collection<Pack> mcheli$withoutHidden(Collection<Pack> packs) {
        return packs.stream().filter(pack -> !HiddenPack.isHidden(pack)).toList();
    }
}
