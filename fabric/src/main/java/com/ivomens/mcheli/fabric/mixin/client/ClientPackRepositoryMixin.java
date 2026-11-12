package com.ivomens.mcheli.fabric.mixin.client;

import com.ivomens.mcheli.content.pack.AddonManager;
import com.ivomens.mcheli.setup.client.item.LegacyModelLoader;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The client resources side of NeoForge's {@code AddPackFindersEvent}: the client's resource pack
 * repository, recognised by its vanilla source, also lists the addon packs and the generated item
 * models (see {@code PackRepositoryMixin} for the data packs).
 */
@Mixin(PackRepository.class)
abstract class ClientPackRepositoryMixin {

    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mcheli$addResourcePacks(RepositorySource[] sources, CallbackInfo ci) {
        if (this.sources.stream().anyMatch(ClientPackSource.class::isInstance)) {
            Set<RepositorySource> withAddons = new LinkedHashSet<>(this.sources);
            withAddons.add(AddonManager.packSource(PackType.CLIENT_RESOURCES));
            withAddons.add(LegacyModelLoader.packSource());
            this.sources = withAddons;
        }
    }
}
