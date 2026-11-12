package com.ivomens.mcheli.world.uavstation;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.vehicle.uav.UAVTracker;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Opens the UAV station pairing/control screen. The paired-UAV snapshot is computed on the server in
 * {@link #openGui} and sent to the client with the menu opening data.
 */
public class UavStationGuiFactory {

    public static final UavStationGuiFactory INSTANCE = new UavStationGuiFactory();

    protected UavStationGuiFactory() {
    }

    public void openGui(Player player, UavStationEntity station) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(station);
        if (!(player instanceof ServerPlayer sp)) return;
        List<UavStationGuiData.UavEntry> entries = buildSnapshot(player, station);
        Services.MENUS.openMenu(sp, new SimpleMenuProvider((id, inv, p) -> new UavStationMenu(id, inv, station, entries),
                Component.literal("UAV Station")), buf -> {
            buf.writeInt(station.getId());
            UavStationGuiData.writeEntries(buf, entries);
        });
    }

    /** Server-side: gather display info for each paired UAV (loaded entity, or last-known position). */
    private static List<UavStationGuiData.UavEntry> buildSnapshot(Player player, UavStationEntity station) {
        List<UavStationGuiData.UavEntry> list = new ArrayList<>();
        if (!(player.level() instanceof ServerLevel level)) {
            return list;
        }
        for (UUID id : station.getPairedUavs()) {
            if (level.getEntity(id) instanceof AircraftEntity ac && !ac.isRemoved()) {
                VehicleSpec info = ac.spec();
                String itemName = "";
                if (info != null && VehicleTypes.item(info).orElse(null) != null) {
                    itemName = BuiltInRegistries.ITEM.getKey(VehicleTypes.item(info).orElse(null)).toString();
                }
                String name = info != null ? info.file().displayName().name() : "UAV";
                boolean reachable = withinReach(station, ac.getX(), ac.getZ(), info != null ? info.file().uav().range()
                        : -1);
                list.add(new UavStationGuiData.UavEntry(id, true, itemName, name, (int) ac.getX(), (int) ac.getY(),
                        (int) ac.getZ(), ac.damage().hp(), ac.damage().maxHp(), reachable, ac.isDestroyed()));
            } else {
                // Unloaded: only the chunk it was last seen in is known.
                ChunkPos chunk = UAVTracker.getUAVPos(level, id);
                int x = chunk != null ? chunk.getMiddleBlockX() : 0;
                int z = chunk != null ? chunk.getMiddleBlockZ() : 0;
                boolean reachable = chunk != null && withinReach(station, x, z, -1);
                list.add(new UavStationGuiData.UavEntry(id, false, "", "UAV (offline)", x, 0, z, 0, 0, reachable, false));
            }
        }
        return list;
    }

    private static boolean withinReach(UavStationEntity station, double x, double z, int uavRange) {
        int range = uavRange >= 0 ? uavRange : WingmanConfig.get().uavControllerRange();
        if (range < 0 || range >= WingmanConfig.UAV_UNLIMITED_THRESHOLD) {
            return true;
        }
        double dx = x - station.getX();
        double dz = z - station.getZ();
        return dx * dx + dz * dz <= (double) range * range;
    }
}
