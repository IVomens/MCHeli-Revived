package com.ivomens.mcheli.combat.weapon;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Stand-in player used by dispenser weapons to "use" an item on blocks on behalf of the shooter.
 */
public class DummyEntityPlayer extends Player {

    public DummyEntityPlayer(Level level, Player player) {
        super(level, player.blockPosition(), player.getYRot(), player.getGameProfile());
    }

    @Override
    public void sendSystemMessage(@NotNull Component component) {}

    @Override
    public void displayClientMessage(@NotNull Component component, boolean actionBar) {}

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }
}
