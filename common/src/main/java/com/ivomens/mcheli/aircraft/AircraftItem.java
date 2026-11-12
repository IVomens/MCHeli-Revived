package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.registry.McheliCriteria;
import com.ivomens.mcheli.util.McheliItem;
import com.ivomens.mcheli.vehicle.component.FuelFluids;
import com.ivomens.mcheli.vehicle.world.VehicleLimits;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.SpongeBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Item form of an aircraft. Right-clicking a block places the aircraft there (optionally only on
 * sponge), right-clicking an empty minecart places it riding the cart.
 */
public abstract class AircraftItem extends McheliItem {

    private static final double PLACE_REACH = 5.0;
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    /** Weapon display names for the tooltip, built once per aircraft info instead of every frame. */
    @Nullable
    private VehicleSpec tooltipCacheInfo;
    private List<String> tooltipWeaponLines = Collections.emptyList();
    private boolean tooltipIsUAV;
    private boolean tooltipIsSmallUAV;
    private boolean tooltipIsTargetDrone;

    public AircraftItem(Item.Properties properties) {
        super(properties);
    }

    public static void registerDispenseBehavior(Item item) {
        DispenserBlock.registerBehavior(item, new AircraftDispenseBehavior());
    }

    private void refreshTooltipCache(Level level, ItemStack stack, VehicleSpec info) {
        if (this.tooltipCacheInfo == info) {
            return;
        }
        AircraftEntity ac = createAircraft(level, -1.0D, -1.0D, -1.0D, stack);
        if (ac == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        StringBuilder weaponNames = new StringBuilder();
        int count = 0;
        WeaponGroup[] weapons = ac.weapons().all();
        for (WeaponGroup ws : weapons) {
            weaponNames.append(ws.displayName());
            if (++count < weapons.length) {
                weaponNames.append(", ");
            }
            if (count % 4 == 0) {
                lines.add(weaponNames.toString());
                weaponNames.setLength(0);
            }
        }
        if (!weaponNames.isEmpty()) {
            lines.add(weaponNames.toString());
        }
        this.tooltipWeaponLines = lines;
        this.tooltipIsUAV = ac.isUAV();
        this.tooltipIsSmallUAV = ac.uavLink().isSmall();
        this.tooltipIsTargetDrone = ac.isTargetDrone();
        this.tooltipCacheInfo = info;
        // the probe entity was never added to the level
        ac.discard();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flagIn) {
        // tooltips are built on the client, and the probe aircraft below needs the client level
        Entity viewer = LocalClient.get().player();
        if (viewer == null) {
            return;
        }
        Level level = viewer.level();
        VehicleSpec info = this.spec();
        if (info != null) {
            this.refreshTooltipCache(level, stack, info);
        }

        if (info != null && this.tooltipCacheInfo == info) {
            addVehicleLines(tooltip, info);
        }

        super.appendHoverText(stack, context, tooltip, flagIn);
    }

    /** The vehicle's category, weapons, features, capacities and author. */
    private void addVehicleLines(List<Component> tooltip, VehicleSpec info) {
        VehicleFile file = info.file();
        tooltip.add(Component.literal("Category: " + file.category()).withStyle(ChatFormatting.YELLOW));
        for (String line : this.tooltipWeaponLines) {
            tooltip.add(Component.literal(line));
        }
        addFlag(tooltip, file.features().ejectionSeat(), "[EjectionSeat]", ChatFormatting.RED);
        addFlag(tooltip, file.regeneration(), "[Regenerating]", ChatFormatting.GREEN);
        addFlag(tooltip, file.ammoSupplyRange() > 0, "[Ammo Supplier]", ChatFormatting.AQUA);
        addFlag(tooltip, file.fuel().supplyRange() > 0, "[Fuel Supplier]", ChatFormatting.AQUA);
        addFlag(tooltip, file.repairOtherVehicles().range() > 0, "[Repair]", ChatFormatting.AQUA);
        addFlag(tooltip, file.invulnerable(), "[Invulnerable]", ChatFormatting.WHITE);
        addFlag(tooltip, file.features().aps().enabled(), "[APS]", ChatFormatting.GOLD);
        addFlag(tooltip, file.features().rwr().enabled(), "[RWR]", ChatFormatting.GOLD);
        addValue(tooltip, "Health: ", file.maxHp(), ChatFormatting.GREEN);
        addValue(tooltip, "Trunk size: ", file.features().inventorySize(), ChatFormatting.WHITE);
        addValue(tooltip, "Fuel tank storage: ", file.fuel().maxFuel(), ChatFormatting.WHITE);
        tooltip.add(Component.literal("Fuel consumption").withStyle(ChatFormatting.GREEN));
        file.fuel().types().forEach((fuel, factor) -> {
            Fluid fluid = FuelFluids.byName(fuel);
            if (fluid != null) {
                tooltip.add(Component.literal("- ")
                        .append(Services.FLUID.getDisplayName(fluid).copy().withStyle(consumptionColor(factor)))
                        .append(Component.literal(String.format(" : %.2f", factor))));
            }
        });
        if (this.tooltipIsUAV) {
            String small = this.tooltipIsSmallUAV ? "Small " : "";
            String targetDrone = this.tooltipIsTargetDrone ? "Targeting " : "";
            tooltip.add(Component.literal("[" + small + targetDrone + "UAV]").withStyle(ChatFormatting.BLUE));
        }
        String author = VehicleTypes.author(info);
        if (!author.isBlank()) {
            tooltip.add(Component.literal("By " + author).withStyle(ChatFormatting.BLUE));
        }
    }

    private static void addValue(List<Component> tooltip, String label, int value, ChatFormatting color) {
        tooltip.add(Component.literal(label).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(value)).withStyle(color)));
    }

    private static void addFlag(List<Component> tooltip, boolean present, String label, ChatFormatting color) {
        if (present) {
            tooltip.add(Component.literal(label).withStyle(color));
        }
    }

    /** Blue for very efficient fuels, through green and yellow, to red for thirsty ones. */
    private static ChatFormatting consumptionColor(float factor) {
        if (factor == 1.0F) {
            return ChatFormatting.WHITE;
        } else if (factor < 0.5F) {
            return ChatFormatting.BLUE;
        } else if (factor < 1.0F) {
            return ChatFormatting.GREEN;
        } else if (factor <= 1.5F) {
            return ChatFormatting.YELLOW;
        }
        return ChatFormatting.RED;
    }

    @Nullable
    public abstract VehicleSpec spec();

    @Nullable
    public abstract AircraftEntity createAircraft(Level level, double x, double y, double z, ItemStack itemStack);

    /** Creates the aircraft on top of the block, facing the given yaw snapped to 90 degrees; null if it does not fit. */
    @Nullable
    public AircraftEntity onTileClick(ItemStack itemStack, Level level, float rotationYaw, int x, int y, int z) {
        AircraftEntity aircraft = this.createAircraft(level, x + 0.5F, y + 1.0F, z + 0.5F, itemStack);
        if (aircraft == null) {
            return null;
        }
        aircraft.initRotationYaw(((Mth.floor(rotationYaw * 4.0F / 360.0F + 0.5) & 3) - 1) * 90);
        return level.noCollision(aircraft, aircraft.getBoundingBox().deflate(0.1)) ? aircraft : null;
    }

    @Override
    public String toString() {
        VehicleSpec info = this.spec();
        return info != null ? super.toString() + "(" + info.kind().directory() + ":" + info.name() + ")" :
                super.toString() + "(null)";
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Vec3 eye = new Vec3(player.getX(), player.getY() + player.getEyeHeight(), player.getZ());
        float yawRad = -player.getYRot() * DEG_TO_RAD;
        float pitchRad = -player.getXRot() * DEG_TO_RAD;
        float horizontal = -Mth.cos(pitchRad);
        Vec3 reach = eye.add(Mth.sin(yawRad - (float) Math.PI) * horizontal * PLACE_REACH, Mth.sin(pitchRad) * PLACE_REACH,
                Mth.cos(yawRad - (float) Math.PI) * horizontal * PLACE_REACH);
        BlockHitResult hit = level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != HitResult.Type.BLOCK || isEyeInsideEntity(level, player, eye)) {
            return InteractionResultHolder.pass(stack);
        }

        BlockPos hitPos = hit.getBlockPos();
        if (McheliConfig.vehicles().placeableOnSpongeOnly() && !isValidSpongePlacement(level, hitPos)) {
            return InteractionResultHolder.fail(stack);
        }
        this.spawnAircraft(stack, level, player, hitPos);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Placing is skipped while the player's eye is inside something pickable in reach (to use it instead). */
    private static boolean isEyeInsideEntity(Level level, Player player, Vec3 eye) {
        Vec3 look = player.getViewVector(1.0F);
        List<Entity> nearby = level.getEntities(player, player.getBoundingBox()
                .expandTowards(look.x * PLACE_REACH, look.y * PLACE_REACH, look.z * PLACE_REACH).inflate(1.0F, 1.0F, 1.0F));
        for (Entity entity : nearby) {
            if (entity.isPickable()) {
                float radius = entity.getPickRadius();
                if (entity.getBoundingBox().inflate(radius, radius, radius).contains(eye)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * With PlaceableOnSpongeOnly, boats (float-only aircraft) go on water above a sponge, everything
     * else directly onto a sponge.
     */
    private boolean isValidSpongePlacement(Level level, BlockPos hitPos) {
        VehicleSpec info = this.spec();
        if (info == null || !info.file().physics().canFloat() || info.file().physics().canMoveOnGround()) {
            return level.getBlockState(hitPos).getBlock() instanceof SpongeBlock;
        }
        if (level.getBlockState(hitPos.below()).getBlock() != Blocks.SPONGE) {
            return false;
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (level.getBlockState(hitPos.offset(x, 0, z)).getBlock() != Blocks.WATER) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Places the aircraft from the item, unless it is creative-only or a target drone (those need a UAV station). */
    @Nullable
    public AircraftEntity spawnAircraft(ItemStack itemStack, Level level, Player player, BlockPos pos) {
        AircraftEntity aircraft = this.onTileClick(itemStack, level, player.getYRot(), pos.getX(), pos.getY(), pos.getZ());
        if (aircraft == null) {
            return null;
        }
        if (aircraft.spec() != null && aircraft.spec().file().creativeOnly()
                && !player.getAbilities().instabuild) {
            return null;
        }
        if (aircraft.isTargetDrone()) {
            if (level.isClientSide) {
                player.sendSystemMessage(Component.literal("Please use the UAV station"));
            }
            return null;
        }
        if (level instanceof ServerLevel serverLevel && !mayPlace(serverLevel, player, aircraft)) {
            return null;
        }
        if (!level.isClientSide) {
            VehicleItemData.read(itemStack, aircraft);
            aircraft.ownership().setOwner(player);
            level.addFreshEntity(aircraft);
            if (player instanceof ServerPlayer serverPlayer) {
                McheliCriteria.PUT_AIRCRAFT.trigger(serverPlayer);
            }
        }
        if (!player.getAbilities().instabuild) {
            itemStack.shrink(1);
        }
        return aircraft;
    }

    /**
     * Whether the server's vehicle limits let the player place one more. If not, the player is told which
     * limit stops them, and gets their inventory back as the server has it (their client already used the item).
     */
    private static boolean mayPlace(ServerLevel level, Player player, AircraftEntity aircraft) {
        Optional<Component> refusal = VehicleLimits.refusal(level, player, aircraft);
        if (refusal.isEmpty()) {
            return true;
        }
        player.displayClientMessage(refusal.get().copy().withStyle(ChatFormatting.RED), true);
        player.containerMenu.sendAllDataToRemote();
        return false;
    }

    /** Right-clicking an empty minecart places the aircraft riding it. */
    public void rideEntity(ItemStack item, Entity target, Player player) {
        if (!McheliConfig.vehicles().placeableOnSpongeOnly() && target instanceof Minecart && target.getPassengers().isEmpty()) {
            BlockPos pos = BlockPos.containing(target.getX(), target.getY() + 2, target.getZ());
            AircraftEntity aircraft = this.spawnAircraft(item, player.level(), player, pos);
            if (!player.level().isClientSide && aircraft != null) {
                aircraft.startRiding(target);
            }
        }
    }

}
