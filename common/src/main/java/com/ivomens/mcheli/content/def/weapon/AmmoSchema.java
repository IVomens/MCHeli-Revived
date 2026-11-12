package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Ammo;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.Values;

import java.util.List;

import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * YAML {@code Ammo}: magazine, reserve and reload. Legacy TXT adds ammo items one {@code Item} line at a
 * time; see {@link WeaponTxtRules#ammoItem}.
 */
final class AmmoSchema {

    /** Ticks to reload. */
    static final Field<Ammo, Integer> RELOAD_TIME =
            Field.of("ReloadTime", ints(3, 1000), 30, Ammo::reloadTime).txt("reloadtime");
    /** Rounds per magazine; 0 means the maximum ammo. */
    static final Field<Ammo, Integer> MAG_SIZE = Field.of("MagSize", ints(1, 30_000), 0, Ammo::magSize).txt("round");
    /** Rounds carried in reserve, besides the loaded magazine. */
    static final Field<Ammo, Integer> MAX_AMMO = Field.of("MaxAmmo", ints(0, 30_000), 0, Ammo::maxAmmo)
            .txt("maxammo");
    /** Rounds a resupply gives. */
    static final Field<Ammo, Integer> RESUPPLY_COUNT =
            Field.of("ResupplyCount", ints(1, 30_000), 1, Ammo::resupplyCount).txt("suppliednum");
    static final Field<Ammo, List<AmmoItem>> ITEMS =
            Field.of("AmmoItems", AmmoItem.LIST_CODEC, List.of(), Ammo::items);

    static final Section<WeaponDefinition, Ammo> SECTION = Section.of("Ammo", WeaponDefinition::ammo,
            RELOAD_TIME, MAG_SIZE, MAX_AMMO, RESUPPLY_COUNT, ITEMS);

    private AmmoSchema() {
    }

    static Ammo build(Values v) {
        return new Ammo(v.get(RELOAD_TIME), v.get(MAG_SIZE), v.get(MAX_AMMO), v.get(RESUPPLY_COUNT), v.get(ITEMS));
    }
}
