package com.ivomens.mcheli.core.content.def.throwable;

import com.ivomens.mcheli.core.content.schema.ArgbColor;
import com.ivomens.mcheli.core.content.schema.ContentDefinition;
import com.ivomens.mcheli.core.content.schema.LocalizedName;

import java.util.List;

/**
 * A hand grenade or smoke grenade, from {@code assets/mcheli/throwable/}. {@link ThrowableSchema} documents
 * every setting; times are in ticks whatever the file format.
 */
public record ThrowableDefinition(
        String name,
        String author,
        LocalizedName displayName,
        int itemId,
        int stackSize,
        Sound sound,
        Recipe recipe,
        int power,
        float acceleration,
        float accelerationInWater,
        float dispenseAcceleration,
        int explosion,
        int delayFuse,
        float bound,
        int timeFuse,
        boolean flaming,
        float proximityFuseDist,
        float accuracy,
        int aliveTime,
        int bomblet,
        float bombletSpread,
        float gravity,
        float gravityInWater,
        Smoke smoke) implements ContentDefinition {

    /** The sound of the explosion. */
    public record Sound(float volume, float pitch) {
    }

    /** The crafting recipe: one recipe per pattern line, in the format {@code ContentRecipes} reads. */
    public record Recipe(boolean shaped, List<String> pattern) {

        public Recipe {
            pattern = List.copyOf(pattern);
        }
    }

    /** The smoke cloud a smoke grenade leaves. */
    public record Smoke(boolean disabled, float size, int count, ArgbColor color, Velocity velocity) {
    }

    /** How fast smoke particles leave the grenade. */
    public record Velocity(float vertical, float horizontal) {
    }
}
