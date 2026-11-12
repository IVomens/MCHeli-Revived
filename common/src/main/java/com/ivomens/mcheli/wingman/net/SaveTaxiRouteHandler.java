package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.ServerMessageHandler;
import com.ivomens.mcheli.wingman.airfield.AirfieldData;
import com.ivomens.mcheli.core.wingman.airfield.TaxiRoute;
import com.ivomens.mcheli.wingman.roster.WingmanAccess;
import com.ivomens.mcheli.wingman.text.WingmanText;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/** Saves a taxi route of a base. A new route belongs to the sender; an existing one keeps its owner. */
public final class SaveTaxiRouteHandler implements ServerMessageHandler<SaveTaxiRouteMessage> {

    @Override
    public void handle(SaveTaxiRouteMessage message, ServerPlayer sender) {
        TaxiRouteData route = message.route();
        if (route.routeId().isEmpty() || message.baseId().isEmpty() || route.parkingId().isEmpty()
                || route.runways().runwayId().isEmpty()) {
            sender.sendSystemMessage(WingmanText.line(ChatFormatting.RED, "taxi.fields_required"));
            return;
        }
        ServerLevel level = sender.serverLevel();
        TaxiRoute existing = AirfieldData.of(level).findRoute(route.routeId());
        if (!WingmanAccess.checkTaxiRouteEdit(sender, existing)) {
            return;
        }
        UUID owner = existing != null ? existing.owner() : sender.getUUID();
        TaxiRunways runways = route.runways();
        AirfieldData.of(level).saveRoute(new TaxiRoute(route.routeId(), message.baseId(), route.parkingId(),
                runways.runwayId(), runways.runwayBId(), withoutBlanks(route.waypointIds()),
                withoutBlanks(route.arrivalWaypointIds()), route.parkingHeading(), runways.arrivalRunwayId(), owner));
        sender.sendSystemMessage(
                WingmanText.line(ChatFormatting.GREEN, "taxi.saved", WingmanText.value(route.routeId())));
    }

    private static List<String> withoutBlanks(List<String> ids) {
        return ids.stream().map(String::trim).filter(id -> !id.isEmpty()).toList();
    }
}
