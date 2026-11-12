package com.ivomens.mcheli.equipment.launcher;

import com.ivomens.mcheli.util.McheliItem;
import net.minecraft.world.item.Item;

/** Missile ammunition of the handheld launchers. */
public class LightWeaponAmmoItem extends McheliItem {

    public LightWeaponAmmoItem() {
        super(new Item.Properties().stacksTo(2));
    }
}
