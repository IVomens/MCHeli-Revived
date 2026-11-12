package com.ivomens.mcheli.fabric.mixin;

import com.ivomens.mcheli.fabric.FabricPackHelper;
import com.ivomens.mcheli.fabric.extension.HiddenPack;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** NeoForge's {@code Pack.isHidden}: true for the packs whose metadata MCHeli made hidden. */
@Mixin(Pack.class)
abstract class PackMixin implements HiddenPack {

    @Shadow
    @Final
    private Pack.Metadata metadata;

    @Override
    public boolean mcheli$isHidden() {
        return FabricPackHelper.isHidden(this.metadata);
    }
}
