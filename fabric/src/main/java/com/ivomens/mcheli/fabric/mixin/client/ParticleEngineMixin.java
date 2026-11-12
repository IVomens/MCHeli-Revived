package com.ivomens.mcheli.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Vanilla only draws the particle render types of its own fixed list; particles of any other type
 * (MCHeli's smoke, fire and exhaust sprites) tick but never show. NeoForge draws every type, the
 * vanilla ones first in their order, then the others; this does the same. Unlike NeoForge, vanilla
 * draws all particles in one pass after the translucent blocks, so MCHeli's few opaque sprites are not
 * drawn earlier, behind water.
 */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {

    @Shadow
    @Final
    private Map<ParticleRenderType, Queue<Particle>> particles;

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/particle/ParticleEngine;RENDER_ORDER:Ljava/util/List;"))
    private List<ParticleRenderType> mcheli$allRenderTypes(List<ParticleRenderType> vanillaOrder) {
        List<ParticleRenderType> others = new ArrayList<>();
        for (ParticleRenderType type : this.particles.keySet()) {
            if (!vanillaOrder.contains(type)) {
                others.add(type);
            }
        }
        if (others.isEmpty()) {
            return vanillaOrder;
        }
        // NeoForge orders the types it does not know by identity hash
        others.sort(Comparator.comparingInt(System::identityHashCode));
        List<ParticleRenderType> all = new ArrayList<>(vanillaOrder.size() + others.size());
        all.addAll(vanillaOrder);
        all.addAll(others);
        return all;
    }
}
