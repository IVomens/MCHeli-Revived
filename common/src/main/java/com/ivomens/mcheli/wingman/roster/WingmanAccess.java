package com.ivomens.mcheli.wingman.roster;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.core.wingman.airfield.TaxiRoute;
import com.ivomens.mcheli.wingman.mission.MissionRoutes;
import com.ivomens.mcheli.wingman.text.WingmanText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Who may order a wingman or change a saved route. Owners and operators always may; a wingman in
 * formation also takes orders from whoever rides its leader. Objects without an owner (saved before
 * owners were recorded) are left to operators, and an aircraft with another player aboard cannot be
 * made into someone's wingman.
 */
public final class WingmanAccess {

    public static final int OPERATOR_LEVEL = 2;

    private final Roster roster;
    private final MissionRoutes routes;

    public WingmanAccess(Roster roster, MissionRoutes routes) {
        this.roster = roster;
        this.routes = routes;
    }

    public static boolean isOperator(Player player) {
        return player.hasPermissions(OPERATOR_LEVEL);
    }

    /** Whether the player may change or delete an object with this owner. */
    public static boolean canEdit(Player player, @Nullable UUID owner) {
        return isOperator(player) || player.getUUID().equals(owner);
    }

    public static boolean canOrder(Player player, Wingman wingman) {
        return canEdit(player, wingman.owner()) || wingman.leader() != null && player.getVehicle() == wingman.leader();
    }

    /** Sends the red "not allowed" line. */
    public static void deny(ServerPlayer player, Component message) {
        player.sendSystemMessage(message);
    }

    /** True for operators; everyone else is told so. */
    public static boolean requireOperator(ServerPlayer player) {
        if (isOperator(player)) {
            return true;
        }
        deny(player, WingmanText.operatorsOnly());
        return false;
    }

    /**
     * The wingman of an aircraft about to get an order. An aircraft that is not a wingman yet becomes
     * one owned by the player. Returns null, after telling the player, when the wingman belongs to
     * someone else.
     */
    public @Nullable Wingman claim(ServerPlayer player, UUID aircraftId) {
        Wingman wingman = this.roster.get(aircraftId);
        if (wingman == null) {
            if (!checkUnclaimed(player, aircraftId)) {
                return null;
            }
            wingman = Wingman.autonomous(player.getUUID());
            this.roster.put(aircraftId, wingman);
            return wingman;
        }
        return checkOrder(player, wingman) ? wingman : null;
    }

    /**
     * Puts the aircraft in formation behind the leader. A wingman that changes leader keeps its owner.
     * Returns false, after telling the player, when it is someone else's wingman.
     */
    public boolean follow(ServerPlayer player, UUID aircraftId, Entity leader, int slot) {
        Wingman previous = this.roster.get(aircraftId);
        if (previous != null ? !checkOrder(player, previous) : !checkUnclaimed(player, aircraftId)) {
            return false;
        }
        UUID owner = previous != null ? previous.owner() : player.getUUID();
        this.roster.put(aircraftId, Wingman.follower(leader, slot, owner));
        return true;
    }

    /** An existing wingman the player may order; null if there is none or it is not theirs. */
    public @Nullable Wingman existing(ServerPlayer player, UUID aircraftId) {
        Wingman wingman = this.roster.get(aircraftId);
        return wingman != null && checkOrder(player, wingman) ? wingman : null;
    }

    /** Every wingman the player may order. Operators get all of them. */
    public List<Wingman> orderable(Player player) {
        List<Wingman> result = new ArrayList<>();
        for (Wingman wingman : this.roster.snapshot().values()) {
            if (canOrder(player, wingman)) {
                result.add(wingman);
            }
        }
        return result;
    }

    /** Whether the player may overwrite or delete the named mission route. Missing routes are fair game. */
    public boolean checkRouteEdit(ServerPlayer player, String name) {
        if (!this.routes.exists(name) || canEdit(player, this.routes.owner(name))) {
            return true;
        }
        deny(player, WingmanText.routeNotYours(name));
        return false;
    }

    /** Taxi routes belong to whoever saved them; a missing route can be created by anyone. */
    public static boolean checkTaxiRouteEdit(ServerPlayer player, @Nullable TaxiRoute route) {
        if (route == null || canEdit(player, route.owner())) {
            return true;
        }
        deny(player, WingmanText.routeNotYours(route.routeId()));
        return false;
    }

    /**
     * An aircraft that is not a wingman yet can only be claimed by someone who could fly it: not while
     * another player sits in it, and not when it is locked to its owner (and their team).
     */
    private static boolean checkUnclaimed(ServerPlayer player, UUID aircraftId) {
        if (isOperator(player) || !(player.serverLevel().getEntity(aircraftId) instanceof AircraftEntity aircraft)) {
            return true;
        }
        if (aircraft.ownership().isLocked() && !aircraft.ownership().mayBoard(player, 0)) {
            deny(player, aircraft.ownership().refusal());
            return false;
        }
        if (hasOtherPlayerAboard(aircraft, player)) {
            deny(player, WingmanText.someoneAboard());
            return false;
        }
        return true;
    }

    private static boolean hasOtherPlayerAboard(AircraftEntity aircraft, Player player) {
        if (aircraft.getRiddenByEntity() instanceof Player rider && rider != player) {
            return true;
        }
        for (SeatEntity seat : aircraft.seats().all()) {
            if (seat != null && seat.getRiddenByEntity() instanceof Player rider && rider != player) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkOrder(ServerPlayer player, Wingman wingman) {
        if (canOrder(player, wingman)) {
            return true;
        }
        deny(player, WingmanText.notYourWingman());
        return false;
    }
}
