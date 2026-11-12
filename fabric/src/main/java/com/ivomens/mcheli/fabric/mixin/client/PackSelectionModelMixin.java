package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.fabric.extension.HiddenPack;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** The pack selection screen does not list hidden packs, as on NeoForge. */
@Mixin(PackSelectionModel.class)
abstract class PackSelectionModelMixin {

    @Shadow
    @Final
    List<Pack> selected;

    @Shadow
    @Final
    List<Pack> unselected;

    @Inject(method = {"<init>", "findNewPacks"}, at = @At("TAIL"))
    private void mcheli$withoutHidden(CallbackInfo ci) {
        this.selected.removeIf(HiddenPack::isHidden);
        this.unselected.removeIf(HiddenPack::isHidden);
    }
}
