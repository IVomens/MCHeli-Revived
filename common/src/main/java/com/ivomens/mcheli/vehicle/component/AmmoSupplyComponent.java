package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftMenu;
import com.ivomens.mcheli.aircraft.AircraftPanelMenu;
import com.ivomens.mcheli.aircraft.VehiclePeers;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.net.WeaponStatusRequestMessage;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.aircraft.net.AmmoCounts;
import com.ivomens.mcheli.combat.weapon.net.ReloadAcceptedMessage;
import com.ivomens.mcheli.content.registry.ContentBindings;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.registry.McheliCriteria;
import com.ivomens.mcheli.util.EntityIds;
import com.ivomens.mcheli.vehicle.VehicleMotion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Restocking weapons: players pay ammo with the round items from their inventory, the magazines are
 * refilled when the pilot closes the aircraft inventory, and supply vehicles (ammoSupplyRange > 0)
 * restock everything parked around them.
 */
@TickOrder(phase = TickPhase.SYSTEMS)
public final class AmmoSupplyComponent implements VehicleComponent {

    private static final int SUPPLY_OTHERS_INTERVAL = 40;
    /** Ticks after the inventory closes during which the pilot still counts as reloading. */
    private static final int RELOAD_LINGER_TICKS = 20;

    private final AircraftEntity aircraft;
    private final StatusWord status;
    private final WeaponComponent weapons;
    private final LifecycleComponent lifecycle;
    private final BodyComponent body;
    private int reloadLingerTicks;
    private boolean countsRequested;
    private boolean wasMenuOpen;

    public AmmoSupplyComponent(AircraftEntity aircraft, StatusWord status, WeaponComponent weapons,
            LifecycleComponent lifecycle, BodyComponent body) {
        this.aircraft = aircraft;
        this.status = status;
        this.weapons = weapons;
        this.lifecycle = lifecycle;
        this.body = body;
    }

    @Override
    public void describe(StateSink state) {
        state.put("reloading", isReloading());
        state.put("reload linger ticks", this.reloadLingerTicks);
        state.put("counts requested", this.countsRequested);
    }

    /** True while the pilot has the vehicle inventory open, and for a second after closing it. */
    public boolean isReloading() {
        return this.status.get(StatusWord.RELOADING) || this.reloadLingerTicks > 0;
    }

    /** Server: tracks whether the pilot has the vehicle inventory open; closing it reloads every magazine. */
    @Override
    public void serverTick(TickContext ctx) {
        AircraftEntity ac = this.aircraft;
        boolean menuOpen = ac.getRiddenByEntity() instanceof Player pilot && !pilot.isRemoved()
                && isVehicleInventory(pilot.containerMenu, ac);
        this.status.set(StatusWord.RELOADING, menuOpen);
        if (!ac.isDestroyed() && this.wasMenuOpen && !menuOpen) {
            for (int i = 0; i < this.weapons.count(); i++) {
                this.weapons.get(i).reloadMagazine();
            }
            AmmoCounts.sendAll(ac, null);
        }
        this.wasMenuOpen = menuOpen;
        lingerWhileMenuOpen();
    }

    /** The aircraft panel (not the trunk-only view) or the classic screen, open on this aircraft. */
    private static boolean isVehicleInventory(AbstractContainerMenu menu, AircraftEntity aircraft) {
        return switch (menu) {
            case AircraftPanelMenu panel -> panel.aircraft == aircraft && !panel.containerOnly;
            case AircraftMenu classic -> classic.aircraft == aircraft;
            default -> false;
        };
    }

    @Override
    public void clientTick(TickContext ctx) {
        lingerWhileMenuOpen();
    }

    private void lingerWhileMenuOpen() {
        if (this.status.get(StatusWord.RELOADING)) {
            this.reloadLingerTicks = RELOAD_LINGER_TICKS;
        }
        if (this.reloadLingerTicks > 0) {
            this.reloadLingerTicks--;
        }
    }

    /** Client: asks the server once for the weapons' ammo and timers. */
    public void requestCountsOnce() {
        if (!this.countsRequested) {
            this.countsRequested = true;
            Network.sendToServer(new WeaponStatusRequestMessage(this.aircraft.getId()));
        }
    }

    /** The crew's manual reload key; the server confirms it to the player's client. */
    public void manualReload(Player player) {
        boolean reloaded = this.weapons.current(player).manualReload();
        if (reloaded && player instanceof ServerPlayer serverPlayer) {
            Network.sendToPlayer(serverPlayer, new ReloadAcceptedMessage(this.aircraft.getId()));
        }
    }

    /**
     * Refills a weapon's reserve. The server makes the pilot pay with the weapon's round items; the
     * client fills it right away and waits for the server's counts.
     */
    public void supply(WeaponGroup weapon) {
        AircraftEntity ac = this.aircraft;
        if (ac.level().isClientSide) {
            weapon.resupply();
            return;
        }
        if (ac.getRiddenByEntity() instanceof ServerPlayer player) {
            McheliCriteria.SUPPLY_AMMO.trigger(player);
        }
        if (ac.getRiddenByEntity() instanceof Player player && canPlayerSupply(player, weapon)) {
            for (ContentBindings.RoundStack round : ContentBindings.rounds(weapon.spec().id())) {
                takeFromInventory(player, round);
            }
            weapon.resupply();
        }
    }

    /** Removes the round's items from the player; water and lava buckets leave an empty bucket behind. */
    private static void takeFromInventory(Player player, ContentBindings.RoundStack round) {
        List<ItemStack> items = player.getInventory().items;
        int remaining = round.count();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && ItemStack.isSameItem(stack, round.stack())) {
                if (stack.getItem() == Items.WATER_BUCKET || stack.getItem() == Items.LAVA_BUCKET) {
                    if (stack.getCount() == 1) {
                        player.getInventory().setItem(i, new ItemStack(Items.BUCKET, 1));
                        remaining--;
                    }
                } else if (stack.getCount() > remaining) {
                    stack.shrink(remaining);
                    remaining = 0;
                } else {
                    remaining -= stack.getCount();
                    stack.setCount(0);
                    items.set(i, ItemStack.EMPTY);
                }
            }
            if (remaining <= 0) {
                break;
            }
        }
    }

    public boolean canPlayerSupply(Player player, WeaponGroup weapon) {
        if (!VehicleMotion.isParkedForSupply(this.aircraft) || weapon.reserveAmmo() + weapon.ammo() >= weapon.maxAmmo()) {
            return false;
        }
        for (ContentBindings.RoundStack round : ContentBindings.rounds(weapon.spec().id())) {
            if (countMissing(player, round) > 0) {
                return false;
            }
        }
        return true;
    }

    public List<ItemStack> missingAmmo(Player player, WeaponGroup weapon) {
        List<ItemStack> missing = new ArrayList<>();
        for (ContentBindings.RoundStack round : ContentBindings.rounds(weapon.spec().id())) {
            int needed = countMissing(player, round);
            if (needed > 0) {
                missing.add(round.stack().copyWithCount(needed));
            }
        }
        return missing;
    }

    private static int countMissing(Player player, ContentBindings.RoundStack round) {
        int needed = round.count();
        for (ItemStack stack : player.getInventory().items) {
            if (needed <= 0) {
                break;
            }
            if (!stack.isEmpty() && ItemStack.isSameItem(stack, round.stack())) {
                needed -= stack.getCount();
            }
        }
        return needed;
    }

    /** Every weapon's ammo, magazine and reserve together; loading puts it in the reserve and refills the magazine. */
    @Override
    public void save(CompoundTag tag) {
        WeaponComponent weapons = this.weapons;
        int[] ammo = new int[weapons.count()];
        for (int i = 0; i < ammo.length; i++) {
            ammo[i] = weapons.get(i).reserveAmmo() + weapons.get(i).ammo();
        }
        tag.put(VehicleNbtCodec.WEAPON_AMMO, new IntArrayTag(ammo));
    }

    @Override
    public void load(CompoundTag tag) {
        WeaponComponent weapons = this.weapons;
        int[] ammo = tag.getIntArray(VehicleNbtCodec.WEAPON_AMMO);
        for (int i = 0; i < ammo.length; i++) {
            weapons.get(i).setReserveAmmo(ammo[i]);
            weapons.get(i).reloadMagazine();
        }
    }

    /** Server: supply vehicles add a tenth of the magazine size to every weapon of the vehicles parked around them. */
    public void supplyNearby() {
        AircraftEntity ac = this.aircraft;
        VehicleSpec info = ac.spec();
        float range = info != null ? info.file().ammoSupplyRange() : 0.0F;
        if (range <= 0.0F || this.lifecycle.age() % SUPPLY_OTHERS_INTERVAL != 0) {
            return;
        }
        List<AircraftEntity> nearby = ac.level().getEntitiesOfClass(AircraftEntity.class,
                this.body.hullBox().inflate(range, range, range));
        for (AircraftEntity other : nearby) {
            if (!EntityIds.same(ac, other) && VehicleMotion.isParkedForSupply(other)) {
                restock(other);
            }
        }
    }

    private static void restock(AircraftEntity other) {
        WeaponComponent armory = VehiclePeers.armory(other);
        for (int weaponId = 0; weaponId < armory.count(); weaponId++) {
            WeaponGroup weapon = armory.get(weaponId);
            int total = weapon.reserveAmmo() + weapon.ammo();
            if (total >= weapon.maxAmmo()) {
                continue;
            }
            weapon.setReserveAmmo(total + Math.max(weapon.maxAmmo() / 10, 1));
            if (total != weapon.reserveAmmo() + weapon.ammo()) {
                if (weapon.ammo() <= 0) {
                    weapon.reloadMagazine();
                }
                AmmoCounts.send(other, armory.playerUsing(weaponId), weaponId);
            }
        }
    }
}
