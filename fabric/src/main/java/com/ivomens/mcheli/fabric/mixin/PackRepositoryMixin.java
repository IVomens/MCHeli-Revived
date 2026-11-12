package com.ivomens.mcheli.fabric.mixin;

import com.google.common.collect.ImmutableSet;
import com.ivomens.mcheli.content.pack.AddonManager;
import com.ivomens.mcheli.fabric.extension.HiddenPack;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The server data side of NeoForge's {@code AddPackFindersEvent}: every data pack repository (the
 * dedicated server's, the integrated server's, world creation's) also lists the addon packs and the
 * generated recipe pack. It is recognised by its vanilla source, as Fabric API recognises it for its
 * own mod data packs. Also leaves hidden packs out of the saved pack lists, as NeoForge does.
 */
@Mixin(PackRepository.class)
abstract class PackRepositoryMixin {

    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Shadow
    @Nullable
    public abstract Pack getPack(String id);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mcheli$addDataPacks(RepositorySource[] sources, CallbackInfo ci) {
        if (this.sources.stream().anyMatch(ServerPacksSource.class::isInstance)) {
            Set<RepositorySource> withAddons = new LinkedHashSet<>(this.sources);
            withAddons.add(AddonManager.packSource(PackType.SERVER_DATA));
            this.sources = withAddons;
        }
    }

    @ModifyReturnValue(method = {"getAvailableIds", "getSelectedIds"}, at = @At("RETURN"))
    private Collection<String> mcheli$withoutHidden(Collection<String> ids) {
        if (ids.stream().noneMatch(this::mcheli$isHidden)) {
            return ids;
        }
        return ids.stream().filter(id -> !this.mcheli$isHidden(id)).collect(ImmutableSet.toImmutableSet());
    }

    @Unique
    private boolean mcheli$isHidden(String id) {
        Pack pack = this.getPack(id);
        return pack != null && HiddenPack.isHidden(pack);
    }
}
