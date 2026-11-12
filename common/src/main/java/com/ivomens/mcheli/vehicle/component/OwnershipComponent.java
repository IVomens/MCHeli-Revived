package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.vehicle.settings.LockRules;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Who placed the vehicle, and whether they locked it. A locked vehicle seats a pilot only if they are the
 * owner, on the owner's team ({@code LockAllowsTeam}) or an operator, and the other seats too with
 * {@code LockPassengerSeats}; only the owner and operators may pick it up or open its cargo. Locking does
 * not change how the vehicle takes damage.
 * <p>
 * Vehicles saved before owners were recorded, and vehicles a dispenser placed, have no owner and are
 * never locked. The owner is saved only when there is one, so such saves keep their keys unchanged.
 * <p>
 * The owner, their name and the lock rules in force are synced, so a client predicts the same answers
 * (to show the lock and to keep its own boarding prediction in step) from what the server decided.
 */
public final class OwnershipComponent implements VehicleComponent {

    /** Bits of the synced lock state. */
    private static final int LOCKED = 1;
    private static final int TEAM_ALLOWED = 2;
    private static final int PASSENGERS_LOCKED = 4;
    private static final int LOCKING_ENABLED = 8;
    private static final int LEVEL_SHIFT = 4;
    private static final int LEVEL_MASK = 0x7;

    private final SyncedValue<Optional<UUID>> owner;
    private final SyncedValue<String> ownerName;
    private final SyncedValue<Integer> lockState;
    private final Supplier<LockRules> rules;
    /** Whether the owner locked it; {@link #lockState} adds whether locking is switched on. */
    private boolean locked;

    public OwnershipComponent(SyncedValue<Optional<UUID>> owner, SyncedValue<String> ownerName,
            SyncedValue<Integer> lockState, Supplier<LockRules> rules) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.lockState = lockState;
        this.rules = rules;
    }

    public Optional<UUID> owner() {
        return this.owner.get();
    }

    /** The owner's name when they last placed or locked it; empty without an owner. */
    public String ownerName() {
        return this.ownerName.get();
    }

    public boolean hasOwner() {
        return this.owner.get().isPresent();
    }

    public boolean isOwner(Player player) {
        return this.owner.get().filter(player.getUUID()::equals).isPresent();
    }

    /** Server: the player placed the vehicle (or claimed one without an owner). */
    public void setOwner(Player player) {
        setOwner(player.getUUID(), player.getGameProfile().getName());
    }

    /** Server: the player with this id and name owns the vehicle now; they need not be online. */
    public void setOwner(UUID id, String name) {
        this.owner.set(Optional.of(id));
        this.ownerName.set(name);
        publish();
    }

    /** Server: the vehicle has no owner any more, and so is not locked. */
    public void clearOwner() {
        this.owner.set(Optional.empty());
        this.ownerName.set("");
        this.locked = false;
        publish();
    }

    /** Whether the vehicle is locked now: the owner locked it and locking is switched on. */
    public boolean isLocked() {
        return (this.lockState.get() & LOCKED) != 0;
    }

    /** Whether the server lets owners lock their vehicles ({@code VehicleLocking}). */
    public boolean lockingEnabled() {
        return (this.lockState.get() & LOCKING_ENABLED) != 0;
    }

    /** What a player who may not use the vehicle is told. */
    public Component refusal() {
        return Component.translatable("message.mcheli.vehicle.locked_by", ownerName()).withStyle(ChatFormatting.RED);
    }

    /** What the player who locked or unlocked the vehicle is told. */
    public static Component lockChanged(boolean locked) {
        return Component.translatable(locked ? "message.mcheli.vehicle.locked" : "message.mcheli.vehicle.unlocked");
    }

    /** Server: locks or unlocks a vehicle with an owner; returns whether it is locked now. */
    public boolean setLocked(boolean lock) {
        this.locked = lock && hasOwner();
        publish();
        return isLocked();
    }

    /** Whether the player may take the seat (0 is the pilot's). */
    public boolean mayBoard(Player player, int seatId) {
        int state = this.lockState.get();
        if ((state & LOCKED) == 0 || seatId > 0 && (state & PASSENGERS_LOCKED) == 0) {
            return true;
        }
        return isOwner(player) || (state & TEAM_ALLOWED) != 0 && isOnOwnersTeam(player) || isAdmin(player, state);
    }

    /** Whether the player may pick the vehicle up, break it down by hand or open its cargo. */
    public boolean mayDismantle(Player player) {
        return !isLocked() || isOwner(player) || isAdmin(player, this.lockState.get());
    }

    /** Whether the player may lock or unlock it: its owner, or an operator. */
    public boolean mayChangeLock(Player player) {
        return isOwner(player) || isAdmin(player, this.lockState.get());
    }

    private boolean isOnOwnersTeam(Player player) {
        PlayerTeam team = player.getScoreboard().getPlayersTeam(ownerName());
        return team != null && team == player.getTeam();
    }

    private boolean isAdmin(Player player, int state) {
        if (player.level().isClientSide) {
            return player.hasPermissions(state >> LEVEL_SHIFT & LEVEL_MASK);
        }
        return this.rules.get().isAdmin(player);
    }

    /** Server: syncs the lock and the rules in force, which the config can change at any time. */
    public void publish() {
        LockRules rules = this.rules.get();
        int state = rules.lockingEnabled() ? LOCKING_ENABLED : 0;
        if (this.locked && rules.lockingEnabled() && hasOwner()) {
            state |= LOCKED;
        }
        if (rules.teamMayBoard()) {
            state |= TEAM_ALLOWED;
        }
        if (rules.passengerSeatsLocked()) {
            state |= PASSENGERS_LOCKED;
        }
        state |= (Math.min(rules.adminLevel(), LEVEL_MASK) & LEVEL_MASK) << LEVEL_SHIFT;
        this.lockState.set(state);
    }

    @Override
    public void save(CompoundTag tag) {
        Optional<UUID> id = this.owner.get();
        if (id.isPresent()) {
            tag.putUUID(VehicleNbtCodec.OWNER, id.get());
            tag.putString(VehicleNbtCodec.OWNER_NAME, ownerName());
            tag.putBoolean(VehicleNbtCodec.LOCKED, this.locked);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        @Nullable UUID id = tag.hasUUID(VehicleNbtCodec.OWNER) ? tag.getUUID(VehicleNbtCodec.OWNER) : null;
        this.owner.set(Optional.ofNullable(id));
        this.ownerName.set(id != null ? tag.getString(VehicleNbtCodec.OWNER_NAME) : "");
        this.locked = id != null && tag.getBoolean(VehicleNbtCodec.LOCKED);
        publish();
    }
}
