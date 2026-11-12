package com.ivomens.mcheli.example;

import com.ivomens.mcheli.api.event.EventBus;
import com.ivomens.mcheli.api.event.VehicleBoardAttempt;
import com.ivomens.mcheli.api.event.VehicleDamaged;
import com.ivomens.mcheli.api.event.VehiclePlaceAttempt;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Two rules for the server's airspace, through the API's permission and damage events:
 * <ul>
 *   <li>the spawn area is a no-fly zone: players inside the server's spawn protection may not take the
 *       pilot's seat of a helicopter or plane, or place one ({@code noFlyZoneAtSpawn}); together with the
 *       addon's {@code WeaponFired} rule, nobody fights from the air there;</li>
 *   <li>vehicles take half damage from the damage types of one other mod ({@code halfDamageFromMod}, the
 *       mod's namespace), for a server whose other gun mod hits vehicles too hard.</li>
 * </ul>
 * All of it runs on the server thread, where MCHeli fires these events.
 */
public final class ExampleAirspace {

    private ExampleAirspace() {
    }

    static void register(EventBus events) {
        events.register(VehicleBoardAttempt.class, ExampleAirspace::onBoardAttempt);
        events.register(VehiclePlaceAttempt.class, ExampleAirspace::onPlaceAttempt);
        events.register(VehicleDamaged.class, ExampleAirspace::onDamaged);
    }

    /** Only the pilot's seat counts: passengers may still sit in a parked aircraft. */
    private static void onBoardAttempt(VehicleBoardAttempt attempt) {
        if (attempt.seatId() == 0 && isAircraft(attempt.vehicle()) && inNoFlyZone(attempt.player())) {
            attempt.setCancelled(true);
            tellNoFlyZone(attempt.player());
        }
    }

    private static void onPlaceAttempt(VehiclePlaceAttempt attempt) {
        if (isAircraft(attempt.vehicle()) && inNoFlyZone(attempt.player())) {
            attempt.setCancelled(true);
            tellNoFlyZone(attempt.player());
        }
    }

    private static void onDamaged(VehicleDamaged damaged) {
        String mod = ExampleConfig.HALF_DAMAGE_FROM_MOD.get();
        if (!mod.isEmpty() && damaged.source().typeHolder().unwrapKey()
                .filter(type -> type.location().getNamespace().equals(mod)).isPresent()) {
            damaged.setAmount(damaged.amount() * 0.5F);
        }
    }

    private static boolean isAircraft(Vehicle vehicle) {
        return vehicle.kind() == VehicleKind.HELICOPTER || vehicle.kind() == VehicleKind.PLANE;
    }

    private static boolean inNoFlyZone(ServerPlayer player) {
        return ExampleConfig.NO_FLY_ZONE_AT_SPAWN.get() && player.serverLevel().getServer()
                .isUnderSpawnProtection(player.serverLevel(), player.blockPosition(), player);
    }

    private static void tellNoFlyZone(ServerPlayer player) {
        player.displayClientMessage(Component.literal("The spawn area is a no-fly zone.")
                .withStyle(ChatFormatting.RED), true);
    }
}
