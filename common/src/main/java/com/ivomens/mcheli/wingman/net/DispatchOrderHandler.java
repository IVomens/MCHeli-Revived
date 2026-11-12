package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.wingman.airfield.AirfieldData;
import com.ivomens.mcheli.core.wingman.airfield.TaxiRoute;
import com.ivomens.mcheli.core.wingman.mission.MissionOrder;
import com.ivomens.mcheli.wingman.roster.Wingman;
import com.ivomens.mcheli.wingman.text.WingmanText;
import com.ivomens.mcheli.wingman.util.WingmanAircraft;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Sends an aircraft on a mission order. The aircraft becomes the sender's wingman if it is nobody's
 * yet; another player's wingman refuses (see {@link com.ivomens.mcheli.wingman.roster.WingmanAccess#claim}).
 */
public final class DispatchOrderHandler implements ServerMessageHandler<DispatchOrderMessage> {

    @Override
    public void handle(DispatchOrderMessage message, ServerPlayer sender) {
        Entity aircraft = sender.serverLevel().getEntity(message.wingmanId());
        if (aircraft == null) {
            sender.sendSystemMessage(WingmanText.entityNotFound());
            return;
        }
        if (message.missionTypes().isEmpty()) {
            sender.sendSystemMessage(WingmanText.line(ChatFormatting.RED, "order.no_valid_types"));
            return;
        }
        Wingman wingman = WingmanService.of(sender).access().claim(sender, message.wingmanId());
        if (wingman == null) {
            return;
        }
        MissionOrder order = order(message);

        // a freshly spawned aircraft has no fuel and would return to base right away
        WingmanAircraft.fillFuel(aircraft);

        wingman.startOrder(order);
        assignDepartureRoute(sender, wingman, message.departureRouteId());
        wingman.attack().setWeaponType(order.primaryWeapon());

        String types = message.missionTypes().stream().map(Enum::name).collect(Collectors.joining(","));
        sender.sendSystemMessage(WingmanText.line(ChatFormatting.GREEN, "order.dispatched_to",
                WingmanText.value(types), WingmanText.shortId(message.wingmanId()) + "..."));
    }

    private static MissionOrder order(DispatchOrderMessage message) {
        StrikePlan strike = message.strike();
        FlightPlan flight = message.flight();
        MissionOrder.Builder order = MissionOrder.builder();
        message.missionTypes().forEach(order::type);
        message.weapons().stream()
                .map(String::trim)
                .filter(weapon -> !weapon.isEmpty())
                .map(weapon -> weapon.toLowerCase(Locale.ROOT))
                .forEach(order::weapon);
        return order.target(strike.targetX(), strike.targetZ())
                .orbitRadius(strike.orbitRadius())
                .orbitAttack(strike.orbitAttack())
                .strikePasses(strike.strikePasses())
                .baseId(flight.baseId())
                .ferryDestBase(flight.ferryDestBase())
                .arrivalRouteId(flight.arrivalRouteId())
                .cruiseAlt(flight.cruiseAlt())
                .timeLimitSeconds(flight.timeLimitSeconds())
                .useVstol(flight.useVstol())
                .build();
    }

    /** Taxi out along the chosen route; without one (or with an unknown one) the wingman starts airborne. */
    private static void assignDepartureRoute(ServerPlayer sender, Wingman wingman, String routeId) {
        wingman.autopilot().departFrom("", "");
        if (routeId.isEmpty()) {
            return;
        }
        TaxiRoute route = AirfieldData.of(sender.serverLevel()).findRoute(routeId);
        if (route == null) {
            sender.sendSystemMessage(WingmanText.line(ChatFormatting.RED, "order.route_not_found_airborne", routeId));
            return;
        }
        wingman.autopilot().departFrom(route.parkingId(), routeId);
        sender.sendSystemMessage(WingmanText.line(ChatFormatting.GREEN, "order.taxi_route",
                WingmanText.value(route.routeId()), WingmanText.value(route.parkingId())));
    }
}
