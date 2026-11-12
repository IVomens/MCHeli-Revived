package com.ivomens.mcheli.registry;

import com.mojang.serialization.Codec;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

/** An advancement trigger without conditions; {@link #trigger(ServerPlayer)} grants it. */
public class SimpleTrigger extends SimpleCriterionTrigger<SimpleListeners.SimpleInstance> {

    private final ResourceLocation id;

    public SimpleTrigger(ResourceLocation id) {
        this.id = id;
    }

    public @NotNull ResourceLocation getId() {
        return this.id;
    }

    @Override
    public @NotNull Codec<SimpleListeners.SimpleInstance> codec() {
        return SimpleListeners.SimpleInstance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, instance -> true);
    }
}
