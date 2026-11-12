package com.ivomens.mcheli.world.gunner;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.entityview.AiGunner;
import com.ivomens.mcheli.registry.McheliItems;
import com.ivomens.mcheli.sound.ModSounds;
import com.ivomens.mcheli.world.WorldEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collections;

/** AI gunner that rides a vehicle seat and fires its weapon at monsters or at players of other teams. */
public class GunnerEntity extends LivingEntity implements AiGunner {

    private static final EntityDataAccessor<String> TEAM_NAME = SynchedEntityData.defineId(GunnerEntity.class,
            EntityDataSerializers.STRING);
    /** The {@link #targetType} of a gunner that shoots monsters. */
    static final int TARGET_MONSTERS = 0;

    /** Placed in creative: invulnerable, and nothing is dropped when removed. */
    private boolean creative;
    private String ownerUUID = "";
    /** 0 shoots monsters, anything else shoots players of other teams. */
    private int targetType;
    private final GunnerGunnery gunnery = new GunnerGunnery(this);
    private final GunnerCountermeasures countermeasures = new GunnerCountermeasures(this);
    private int despawnCount;
    private int idleCount;
    private int idleRotation;

    public int targetType() {
        return this.targetType;
    }

    public void setOwnerUUID(String ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public void setTargetType(int targetType) {
        this.targetType = targetType;
    }

    public GunnerEntity(EntityType<? extends GunnerEntity> type, Level level) {
        super(type, level);
    }

    public GunnerEntity(Level level) {
        this(WorldEntityTypes.GUNNER.get(), level);
    }

    public GunnerEntity(Level level, double x, double y, double z) {
        this(level);
        this.setPos(x, y, z);
    }

    /** Attributes for {@code EntityAttributeCreationEvent}. */
    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TEAM_NAME, "");
    }

    public String getTeamName() {
        return this.entityData.get(TEAM_NAME);
    }

    public void setTeamName(String name) {
        this.entityData.set(TEAM_NAME, name);
    }

    @Nullable
    @Override
    public PlayerTeam getTeam() {
        String name = this.getTeamName();
        return name.isEmpty() ? null : this.level().getScoreboard().getPlayerTeam(name);
    }

    @Override
    public @NotNull Component getDisplayName() {
        PlayerTeam team = this.getTeam();
        return team != null ?
                PlayerTeam.formatNameForTeam(team, Component.literal(team.getName() + " Gunner")) :
                Component.literal("");
    }

    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        return this.creative && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /** Right-clicking a gunner takes it off its seat, if the player is allowed to. */
    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        if (level().isClientSide || getVehicle() == null) {
            return InteractionResult.PASS;
        }
        if (!player.getAbilities().instabuild) {
            if (this.creative) {
                player.sendSystemMessage(Component.literal("Creative mode only."));
                return InteractionResult.PASS;
            }
            if (getTeam() != null && !isAlliedTo(player)) {
                player.sendSystemMessage(Component.literal("You are other team."));
                return InteractionResult.PASS;
            }
        }
        removeFromAircraft(player);
        return InteractionResult.SUCCESS;
    }

    public void removeFromAircraft(Player player) {
        if (!this.level().isClientSide) {
            player.playSound(ModSounds.getSound("wrench"), 1.0F, 1.0F);
            AircraftEntity ac = this.getAc();
            String name = "";
            if (ac != null && ac.spec() != null) {
                name = " on " + ac.spec().file().displayName().name() + " seat " + (ac.seats().seatIdOf(this) + 1);
            }

            this.discard();
            player.sendSystemMessage(Component.literal("Remove gunner" + name + " by ")
                    .append(player.getDisplayName()).append("."));
            this.stopRiding();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && !isRemoved()) {
            tickServer();
        }

        this.countermeasures.tickCooldown();
        this.gunnery.tickCountdown();

        if (this.idleCount > 0) {
            this.idleCount--;
        }
    }

    private void tickServer() {
        Entity vehicle = getVehicle();
        if (vehicle != null && vehicle.isRemoved()) {
            stopRiding();
            vehicle = null;
        }

        AircraftEntity host = null;
        if (vehicle instanceof AircraftEntity aircraft) {
            host = aircraft;
        } else if (vehicle instanceof SeatEntity seat && seat.getParent() != null) {
            host = seat.getParent();
        } else if (despawnCount < 20) {
            despawnCount++;
        } else if (vehicle == null || tickCount > 100) {
            // a gunner left without a seat disappears after a second
            discard();
        }

        if (host != null) {
            this.gunnery.fightFrom(host);
            this.countermeasures.defend(host);
        }

        // without a target the gunner looks around now and then
        if (this.gunnery.target() == null) {
            if (idleCount == 0) {
                idleCount = (3 + random.nextInt(5)) * 20;
                idleRotation = random.nextInt(5) - 2;
            }
            setYRot(getYRot() + idleRotation / 2.0F);
        } else {
            idleCount = 60;
        }
    }

    @Nullable
    public AircraftEntity getAc() {
        Entity vehicle = this.getVehicle();
        if (vehicle instanceof AircraftEntity ac) {
            return ac;
        }
        return vehicle instanceof SeatEntity seat ? seat.getParent() : null;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("Creative", this.creative);
        nbt.putString("OwnerUUID", this.ownerUUID);
        nbt.putString("TeamName", this.getTeamName());
        nbt.putInt("TargetType", this.targetType);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.creative = nbt.getBoolean("Creative");
        this.ownerUUID = nbt.getString("OwnerUUID");
        this.setTeamName(nbt.getString("TeamName"));
        this.targetType = nbt.getInt("TargetType");
    }

    @Override
    public boolean canChangeDimensions(@NotNull Level oldLevel, @NotNull Level newLevel) {
        return false;
    }

    /** Killed or removed for good (not unloaded): give the spawn item back unless placed in creative. */
    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (!this.level().isClientSide && !this.isRemoved() && reason.shouldDestroy() && !this.creative) {
            if (this.targetType == TARGET_MONSTERS) {
                this.spawnAtLocation(McheliItems.SPAWN_GUNNER_VS_MONSTER.get(), 1);
            } else {
                this.spawnAtLocation(McheliItems.SPAWN_GUNNER_VS_PLAYER.get(), 1);
            }
        }

        super.remove(reason);
    }

    @Override
    public boolean hurt(@NotNull DamageSource ds, float amount) {
        if (ds.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            this.discard();
        }

        return super.hurt(ds, amount);
    }

    @Override
    public @NotNull ItemStack getItemBySlot(@NotNull EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(@NotNull EquipmentSlot slot, @NotNull ItemStack stack) {
    }

    @Override
    public @NotNull Iterable<ItemStack> getArmorSlots() {
        return Collections.emptyList();
    }

    @Override
    public @NotNull HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public boolean isCreative() {
        return this.creative;
    }

    /** Placed by a player in creative mode: spares its vehicle ammunition and damage. */
    public void setCreative(boolean creative) {
        this.creative = creative;
    }
}
