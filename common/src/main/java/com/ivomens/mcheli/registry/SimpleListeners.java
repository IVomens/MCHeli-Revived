package com.ivomens.mcheli.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;

import java.util.Optional;

/**
 * Criterion instance of the MCHeli "simple" triggers (no conditions besides the optional vanilla
 * player predicate). Listener bookkeeping is handled by {@link SimpleCriterionTrigger}.
 */
public class SimpleListeners {

    public record SimpleInstance(Optional<ContextAwarePredicate> player) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<SimpleInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(SimpleInstance::player)
        ).apply(i, SimpleInstance::new));
    }
}
