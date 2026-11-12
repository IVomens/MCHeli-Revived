package com.ivomens.mcheli.admin;

import com.ivomens.mcheli.admin.perf.TickCost;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.vehicle.world.AircraftIndex;
import com.ivomens.mcheli.vehicle.world.VehicleLimits;
import com.ivomens.mcheli.wingman.WingmanService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@code /mcheli perf}: what MCHeli costs the server, per level: its vehicles by kind, the projectiles in
 * flight, the time per tick spent ticking them over the last 30 seconds (mean and 95th percentile), the
 * chunks its UAV, wingman and projectile leases keep loaded, and the five vehicles that cost the most, with
 * a position to click to get there. Network traffic is not counted.
 */
final class PerfCommand {

    private static final int TOP = 5;

    private PerfCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> perf() {
        return Commands.literal("perf").executes(context -> report(context.getSource()));
    }

    /** Sends the report; returns the number of lines sent. */
    static int report(CommandSourceStack source) {
        List<Component> lines = new ArrayList<>();
        lines.add(AdminText.text("perf.header", TickCost.WINDOW / 20).withStyle(ChatFormatting.GOLD));
        List<TickCost.EntityCost> top = new ArrayList<>();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            TickCost cost = TickCost.of(level);
            cost.closeTicksUpTo(level.getGameTime());
            addLevel(lines, level, cost);
            top.addAll(cost.mostExpensive(TOP));
        }
        top.sort(Comparator.comparingDouble(TickCost.EntityCost::meanMs).reversed());
        lines.add(AdminText.text("perf.top", TOP).withStyle(ChatFormatting.GOLD));
        if (top.isEmpty()) {
            lines.add(AdminText.text("perf.none"));
        }
        for (TickCost.EntityCost entry : top.subList(0, Math.min(TOP, top.size()))) {
            MutableComponent line = AdminText.text("perf.vehicle", AdminText.millis(entry.meanMs()));
            if (entry.entity() instanceof AircraftEntity vehicle) {
                line.append(" ").append(AdminText.describe(vehicle));
            }
            lines.add(line.append(" ").append(AdminText.position(entry.entity())));
        }
        lines.forEach(line -> source.sendSuccess(() -> line, false));
        return lines.size();
    }

    private static void addLevel(List<Component> lines, ServerLevel level, TickCost cost) {
        AircraftIndex index = AircraftIndex.of(level);
        MutableComponent kinds = Component.empty();
        for (VehicleKind kind : VehicleKind.values()) {
            int count = VehicleLimits.countOfKind(index, kind);
            if (count > 0) {
                kinds.append(" ").append(Component.translatable("mcheli.kind." + kind.directory())).append(": " + count);
            }
        }
        lines.add(AdminText.text("perf.level", level.dimension().location().toString(), index.aircraft().size(),
                cost.projectilesLastTick(), WingmanService.of(level).forcedChunks(level)).append(kinds));
        TickCost.Stats vehicles = cost.vehicles();
        TickCost.Stats projectiles = cost.projectiles();
        lines.add(AdminText.text("perf.time", AdminText.millis(vehicles.meanMs()), AdminText.millis(vehicles.p95Ms()),
                AdminText.millis(projectiles.meanMs()), AdminText.millis(projectiles.p95Ms()), vehicles.ticks()));
    }
}
