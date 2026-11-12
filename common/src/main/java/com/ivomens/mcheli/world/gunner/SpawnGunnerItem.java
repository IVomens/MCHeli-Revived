package com.ivomens.mcheli.world.gunner;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.VehicleTool;
import com.ivomens.mcheli.sound.ModSounds;
import com.ivomens.mcheli.util.McheliItem;
import com.ivomens.mcheli.world.gunner.client.SpawnGunnerHud;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/** Seats an AI gunner on the seat or vehicle the player points at. */
public class SpawnGunnerItem extends McheliItem implements VehicleTool {

    private static final double REACH = 5.0;

    private int primaryColor = 0xFFFFFF;
    private int secondaryColor = 0xFFFFFF;
    /** Which side the gunner shoots at, see {@link GunnerEntity#targetType}. */
    private int targetType;

    public void setPrimaryColor(int primaryColor) {
        this.primaryColor = primaryColor;
    }

    public void setSecondaryColor(int secondaryColor) {
        this.secondaryColor = secondaryColor;
    }

    public void setTargetType(int targetType) {
        this.targetType = targetType;
    }

    public SpawnGunnerItem(Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Item tint for the two-layer spawn egg model (registered through the item color handler). */
    public static int getColorFromItemStack(ItemStack stack, int tintIndex) {
        SpawnGunnerItem item = (SpawnGunnerItem) stack.getItem();
        return tintIndex == 0 ? item.primaryColor : item.secondaryColor;
    }

    private static boolean intersects(Entity entity, Vec3 from, Vec3 to) {
        return entity.getBoundingBox().clip(from, to).isPresent();
    }

    /**
     * Finds what the player is pointing at, preferring gunners, then free seats, then vehicles. A seat or
     * vehicle already manned by a gunner resolves to that gunner. Also drives the {@link SpawnGunnerHud}.
     */
    @Nullable
    public static Entity findTarget(Level level, Player player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(REACH));
        AABB searchBox = player.getBoundingBox().inflate(REACH);

        Entity target = null;
        for (GunnerEntity gunner : level.getEntitiesOfClass(GunnerEntity.class, searchBox)) {
            if (intersects(gunner, eye, end) && isCloser(player, gunner, target)) {
                target = gunner;
            }
        }
        if (target != null) {
            return target;
        }

        for (SeatEntity seat : level.getEntitiesOfClass(SeatEntity.class, searchBox)) {
            if (seat.getParent() != null && seat.getParent().spec() != null && intersects(seat, eye, end) &&
                    isCloser(player, seat, target)) {
                target = seat.getRiddenByEntity() instanceof GunnerEntity gunner ? gunner : seat;
            }
        }
        if (target != null) {
            return target;
        }

        for (AircraftEntity vehicle : level.getEntitiesOfClass(AircraftEntity.class, searchBox)) {
            if (!vehicle.isUAV() && vehicle.spec() != null && intersects(vehicle, eye, end) &&
                    isCloser(player, vehicle, target)) {
                target = vehicle.getRiddenByEntity() instanceof GunnerEntity gunner ? gunner : vehicle;
            }
        }
        return target;
    }

    private static boolean isCloser(Player player, Entity candidate, @Nullable Entity current) {
        return current == null || player.distanceToSqr(candidate) < player.distanceToSqr(current);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player,
                                                           @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Entity target = findTarget(level, player);
        if (target instanceof GunnerEntity gunner) {
            gunner.interact(player, hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (target == null) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.literal("Right click to seat."));
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            seatGunner(level, player, target);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private void seatGunner(Level level, Player player, Entity seat) {
        GunnerEntity gunner = new GunnerEntity(level, seat.getX(), seat.getY(), seat.getZ());
        // face the nearest cardinal direction
        gunner.setYRot(((Mth.floor(player.getYRot() * 4.0F / 360.0F + 0.5) & 3) - 1) * 90);
        gunner.setCreative(player.getAbilities().instabuild);
        gunner.setTargetType(targetType);
        gunner.setOwnerUUID(player.getUUID().toString());
        PlayerTeam team = level.getScoreboard().getPlayersTeam(player.getScoreboardName());
        if (team != null) {
            gunner.setTeamName(team.getName());
        }

        level.addFreshEntity(gunner);
        gunner.startRiding(seat);
        gunner.playSound(ModSounds.getSound("wrench"), 1.0F, 3.0F);
        AircraftEntity vehicle = seat instanceof AircraftEntity aircraft ? aircraft : ((SeatEntity) seat).getParent();
        player.sendSystemMessage(Component.literal("The gunner was put on ")
                .append(Component.literal(vehicle.spec().file().displayName().name()).withStyle(ChatFormatting.GOLD))
                .append(" seat " + (vehicle.seats().seatIdOf(gunner) + 1) + " by ")
                .append(player.getDisplayName()));
    }
}
