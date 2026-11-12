package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Effects;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.Values;

import java.util.List;

/**
 * YAML {@code Effects}: what a hit does beyond damage. Legacy TXT adds both one line at a time,
 * {@code AddPotionEffect} and {@code BulletDecay}.
 */
final class EffectsSchema {

    static final Field<Effects, List<PotionEffectSpec>> POTION_EFFECTS =
            Field.of("PotionEffects", PotionEffectSpec.LIST_CODEC, List.of(), Effects::potionEffects);
    static final Field<Effects, List<DecayCurve>> BULLET_DECAY =
            Field.of("BulletDecay", DecayCurve.LIST_CODEC, List.of(), Effects::bulletDecay);

    static final Section<WeaponDefinition, Effects> SECTION = Section.of("Effects", WeaponDefinition::effects,
            POTION_EFFECTS, BULLET_DECAY);

    private EffectsSchema() {
    }

    static Effects build(Values v) {
        return new Effects(v.get(POTION_EFFECTS), v.get(BULLET_DECAY));
    }
}
