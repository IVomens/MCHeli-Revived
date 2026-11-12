package com.ivomens.mcheli.event;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.event.Cancellable;
import com.ivomens.mcheli.api.event.McheliEvent;
import com.ivomens.mcheli.api.event.VehicleBoardAttempt;
import com.ivomens.mcheli.api.event.VehicleDismantleAttempt;
import com.ivomens.mcheli.api.event.VehicleLockAttempt;
import com.ivomens.mcheli.api.event.VehiclePlaceAttempt;
import com.ivomens.mcheli.api.event.VehicleScreen;
import com.ivomens.mcheli.api.event.VehicleScreenOpenAttempt;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The API's permission events: a player is about to board, place, dismantle, open or lock a vehicle, and
 * listeners may say no. Each method answers whether the action may go ahead; with nobody listening, or for a
 * player that is not a server player (a client's prediction), the answer is yes without building an event. The
 * callers run them on the server, after MCHeli's own checks, and undo any prediction of a refused action.
 */
public final class PermissionEvents {

    private PermissionEvents() {
    }

    private static ApiEventBus bus() {
        return (ApiEventBus) McheliApi.get().events();
    }

    public static boolean mayBoard(Vehicle vehicle, Player player, int seatId) {
        ApiEventBus bus = bus();
        if (!bus.hasListeners(VehicleBoardAttempt.class) || !(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        return allowed(bus, VehicleBoardAttempt.class, new Board(serverPlayer, vehicle, seatId));
    }

    public static boolean mayPlace(Vehicle vehicle, Player player, ItemStack item) {
        ApiEventBus bus = bus();
        if (!bus.hasListeners(VehiclePlaceAttempt.class) || !(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        return allowed(bus, VehiclePlaceAttempt.class, new Place(serverPlayer, vehicle, item));
    }

    public static boolean mayDismantle(Vehicle vehicle, Player player) {
        ApiEventBus bus = bus();
        if (!bus.hasListeners(VehicleDismantleAttempt.class) || !(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        return allowed(bus, VehicleDismantleAttempt.class, new Dismantle(serverPlayer, vehicle));
    }

    public static boolean mayOpen(Vehicle vehicle, Player player, VehicleScreen screen) {
        ApiEventBus bus = bus();
        if (!bus.hasListeners(VehicleScreenOpenAttempt.class) || !(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        return allowed(bus, VehicleScreenOpenAttempt.class, new OpenScreen(serverPlayer, vehicle, screen));
    }

    public static boolean mayChangeLock(Vehicle vehicle, Player player, boolean locking, boolean claimsOwnership) {
        ApiEventBus bus = bus();
        if (!bus.hasListeners(VehicleLockAttempt.class) || !(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        return allowed(bus, VehicleLockAttempt.class, new Lock(serverPlayer, vehicle, locking, claimsOwnership));
    }

    private static <E extends McheliEvent & Cancellable> boolean allowed(ApiEventBus bus, Class<E> type, E event) {
        return !bus.post(type, event).isCancelled();
    }

    /** Every attempt is allowed until a listener cancels it; the last listener's choice counts. */
    private abstract static class Attempt implements Cancellable {

        private final ServerPlayer player;
        private final Vehicle vehicle;
        private boolean cancelled;

        Attempt(ServerPlayer player, Vehicle vehicle) {
            this.player = player;
            this.vehicle = vehicle;
        }

        public ServerPlayer player() {
            return this.player;
        }

        public Vehicle vehicle() {
            return this.vehicle;
        }

        @Override
        public boolean isCancelled() {
            return this.cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    private static final class Board extends Attempt implements VehicleBoardAttempt {

        private final int seatId;

        Board(ServerPlayer player, Vehicle vehicle, int seatId) {
            super(player, vehicle);
            this.seatId = seatId;
        }

        @Override
        public int seatId() {
            return this.seatId;
        }
    }

    private static final class Place extends Attempt implements VehiclePlaceAttempt {

        private final ItemStack item;

        Place(ServerPlayer player, Vehicle vehicle, ItemStack item) {
            super(player, vehicle);
            this.item = item;
        }

        @Override
        public ItemStack item() {
            return this.item;
        }
    }

    private static final class Dismantle extends Attempt implements VehicleDismantleAttempt {

        Dismantle(ServerPlayer player, Vehicle vehicle) {
            super(player, vehicle);
        }
    }

    private static final class OpenScreen extends Attempt implements VehicleScreenOpenAttempt {

        private final VehicleScreen screen;

        OpenScreen(ServerPlayer player, Vehicle vehicle, VehicleScreen screen) {
            super(player, vehicle);
            this.screen = screen;
        }

        @Override
        public VehicleScreen screen() {
            return this.screen;
        }
    }

    private static final class Lock extends Attempt implements VehicleLockAttempt {

        private final boolean locking;
        private final boolean claimsOwnership;

        Lock(ServerPlayer player, Vehicle vehicle, boolean locking, boolean claimsOwnership) {
            super(player, vehicle);
            this.locking = locking;
            this.claimsOwnership = claimsOwnership;
        }

        @Override
        public boolean locking() {
            return this.locking;
        }

        @Override
        public boolean claimsOwnership() {
            return this.claimsOwnership;
        }
    }
}
