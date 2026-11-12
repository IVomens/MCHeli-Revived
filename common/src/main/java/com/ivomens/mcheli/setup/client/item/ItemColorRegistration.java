package com.ivomens.mcheli.setup.client.item;

import com.ivomens.mcheli.registry.McheliItems;
import com.ivomens.mcheli.world.gunner.SpawnGunnerItem;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Item;

/** Tints of the gunner spawn items. */
public class ItemColorRegistration {

    public static void register(ItemColors itemColors) {
        Item monster = ItemModelRenderers.asItem(McheliItems.SPAWN_GUNNER_VS_MONSTER.get());
        Item player = ItemModelRenderers.asItem(McheliItems.SPAWN_GUNNER_VS_PLAYER.get());
        if (monster == null || player == null) {
            return;
        }
        // item tints are ARGB; the gunner colours are plain RGB
        itemColors.register((stack, tintIndex) -> FastColor.ARGB32.opaque(SpawnGunnerItem.getColorFromItemStack(stack, tintIndex)),
                monster, player);
    }
}
