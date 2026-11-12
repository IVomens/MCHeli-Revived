package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftInventory;
import com.ivomens.mcheli.aircraft.AircraftGuiFactory;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTool;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A player's click on the vehicle: board it (the pilot's seat through {@link PilotBoarding}, or the
 * first free seat behind it), open the canopy, open the cargo, lock or unlock it, or repaint it with
 * the wrench, as far as the owner's lock allows; and the vehicle's screens.
 * <p>
 * The interaction runs on both sides, as vanilla's does: the server boards the player, the client
 * only checks the reach and predicts.
 */
public final class BoardingComponent implements VehicleComponent {

    private final AircraftEntity vehicle;
    private final RestState rest;
    private final SeatsComponent seats;
    private final AppearanceComponent appearance;
    private final OwnershipComponent ownership;
    private final PartsComponent parts;
    private final AircraftInventory cargo;
    private final PilotBoarding pilotBoarding;

    public BoardingComponent(AircraftEntity vehicle, RestState rest, SeatsComponent seats,
            AppearanceComponent appearance, OwnershipComponent ownership, PartsComponent parts,
            AircraftInventory cargo, PilotBoarding pilotBoarding) {
        this.vehicle = vehicle;
        this.rest = rest;
        this.seats = seats;
        this.appearance = appearance;
        this.ownership = ownership;
        this.parts = parts;
        this.cargo = cargo;
        this.pilotBoarding = pilotBoarding;
    }

    /** A player clicked the vehicle; true when the click was used. */
    public boolean interact(Player player, InteractionHand hand) {
        AircraftEntity ac = this.vehicle;
        this.rest.wake();
        VehicleSpec info = ac.spec();
        if (ac.isDestroyed() || info == null || this.seats.hasEnemyOf(player)) {
            return false;
        }
        boolean onClient = ac.level().isClientSide;
        // measured to the hull, so large vehicles can be boarded from any side
        if (onClient && !player.canInteractWithEntity(ac.getBoundingBox(), 1.0)) {
            return false;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof VehicleTool tool) {
            if (tool.cyclesTexture() && !onClient && player.isShiftKeyDown()) {
                this.appearance.nextTexture();
            }
            return false;
        }
        if (player.isShiftKeyDown()) {
            return sneakClick(player, stack);
        }

        if (!info.file().canRide()) {
            return false;
        }
        if (ac.getRiddenByEntity() != null || ac.isUAV()) {
            if (!boardFirstFreeSeat(player) && !this.ownership.mayBoard(player, 1)) {
                refuse(player);
            }
            return true;
        }
        if (player.getVehicle() instanceof SeatEntity || !this.seats.canTake(0)) {
            return false;
        }
        if (!this.ownership.mayBoard(player, 0)) {
            return refuse(player);
        }
        // the first click opens a closed canopy, the next one boards
        if (info.parts().haveCanopy() && this.parts.isCanopyClosed()) {
            this.parts.openCanopy();
            return false;
        }
        if (this.parts.modeSwitchCooldown() > 0) {
            return false;
        }
        this.pilotBoarding.board(player, info);
        return true;
    }

    /**
     * A sneak-click: the owner's with an empty hand locks or unlocks the vehicle (where locking is on),
     * anyone else's opens the cargo, unless the vehicle is locked against them.
     */
    private boolean sneakClick(Player player, ItemStack stack) {
        if (stack.isEmpty() && this.ownership.isOwner(player) && this.ownership.lockingEnabled()) {
            toggleLock(player);
            return true;
        }
        if (!this.ownership.mayDismantle(player)) {
            return refuse(player);
        }
        openInventory(player);
        return false;
    }

    /** Server: the vehicle's screen (fuel, ammo, crew settings). */
    public void openGui(Player player) {
        AircraftEntity ac = this.vehicle;
        if (!ac.level().isClientSide && ac.spec() != null) {
            AircraftGuiFactory.INSTANCE.openGui(player, ac);
        }
    }

    /** Server: the cargo inventory, for vehicles that have one. */
    public void openInventory(Player player) {
        AircraftEntity ac = this.vehicle;
        if (!ac.level().isClientSide && ac.spec() != null && this.cargo.getItemHandler().getSlots() > 0) {
            AircraftGuiFactory.INSTANCE.openContainer(player, ac);
        }
    }

    /** Boards the player into the first free seat they are allowed to take; false if there is none. */
    private boolean boardFirstFreeSeat(Player player) {
        SeatsComponent seats = this.seats;
        int seatId = 1;
        for (SeatEntity seat : seats.all()) {
            if (seat != null && seat.getRiddenByEntity() == null && !seats.isAboard(player) && seats.canTake(seatId)
                    && this.ownership.mayBoard(player, seatId)) {
                if (!this.vehicle.level().isClientSide) {
                    player.startRiding(seat);
                }
                return true;
            }
            seatId++;
        }
        return false;
    }

    /** Whether the player may sit in the seat; the server tells a player who may not why. */
    public boolean admits(Player player, int seatId) {
        if (this.ownership.mayBoard(player, seatId)) {
            return true;
        }
        refuse(player);
        return false;
    }

    /** Tells the player the vehicle is locked; true, so the click counts as used. */
    private boolean refuse(Player player) {
        if (!this.vehicle.level().isClientSide) {
            player.displayClientMessage(this.ownership.refusal(), true);
        }
        return true;
    }

    /** The owner's sneak-click with an empty hand locks or unlocks the vehicle. */
    private void toggleLock(Player player) {
        if (!this.vehicle.level().isClientSide) {
            boolean locked = this.ownership.setLocked(!this.ownership.isLocked());
            player.displayClientMessage(OwnershipComponent.lockChanged(locked), true);
        }
    }
}
