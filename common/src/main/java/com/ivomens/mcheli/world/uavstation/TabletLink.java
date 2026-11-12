package com.ivomens.mcheli.world.uavstation;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.UUID;

/**
 * The tablet a handheld station was opened from. The station lives only while its owner rides it; when
 * they get off, its pairings go back into the tablet they hold.
 */
record TabletLink(UUID ownerId, InteractionHand hand) {

    /** Copies the station's pairings back into the tablet its owner holds, if they still hold one. */
    void writeBack(Level level, Set<UUID> pairings) {
        if (level.isClientSide) {
            return;
        }
        Player owner = level.getPlayerByUUID(this.ownerId);
        if (owner == null) {
            return;
        }
        ItemStack held = owner.getItemInHand(this.hand);
        if (held.getItem() instanceof UavTabletItem) {
            UavTabletItem.setPairedUavs(held, pairings);
        }
    }
}
