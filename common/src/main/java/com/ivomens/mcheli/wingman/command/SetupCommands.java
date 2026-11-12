package com.ivomens.mcheli.wingman.command;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.vehicle.world.AircraftIndex;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.wingman.airfield.AirfieldData;
import com.ivomens.mcheli.core.wingman.airfield.Marker;
import com.ivomens.mcheli.wingman.airfield.Markers;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import com.ivomens.mcheli.wingman.block.WingmanMarkerBlock;
import com.ivomens.mcheli.wingman.block.WingmanMarkerTileEntity;
import com.ivomens.mcheli.core.wingman.mission.MissionNode;
import com.ivomens.mcheli.wingman.net.MarkerView;
import com.ivomens.mcheli.wingman.net.MissionPlannerMessage;
import com.ivomens.mcheli.wingman.net.MissionRouteView;
import com.ivomens.mcheli.wingman.net.UavView;
import com.ivomens.mcheli.wingman.roster.Wingman;
import com.ivomens.mcheli.wingman.roster.WingmanAccess;
import com.ivomens.mcheli.wingman.text.WingmanText;
import com.ivomens.mcheli.wingman.util.WingmanAircraft;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.ivomens.mcheli.wingman.command.WingmanCommand.asPlayer;
import static com.ivomens.mcheli.wingman.command.WingmanCommand.send;
import static com.ivomens.mcheli.wingman.command.WingmanCommand.usage;
import static com.ivomens.mcheli.wingman.text.WingmanText.line;

/** {@code spawnuav [type]}, {@code marker list|id <id>|type <type>} and {@code gui} (the mission planner). */
final class SetupCommands {

    /** How far the player may be from a marker block they are configuring. */
    private static final double MARKER_PICK_RANGE = 8.0;
    private static final double UAV_SPAWN_DISTANCE = 8.0;
    private static final List<String> MARKER_TYPES = List.of("parking", "runway_a", "runway_b", "waypoint");
    private static final String MARKER_TYPE_USAGE = "/wingman marker type <parking|runway_a|runway_b|waypoint>";

    private SetupCommands() {
    }

    static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("spawnuav")
                        .executes(ctx -> asPlayer(ctx, (player, wingmen) -> listUavTypes(player)))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> asPlayer(ctx, (player, wingmen) -> {
                                    if (WingmanAccess.requireOperator(player)) {
                                        spawnUav(player, StringArgumentType.getString(ctx, "type"));
                                    }
                                }))))
                .then(Commands.literal("marker")
                        .executes(ctx -> usage(ctx, "/wingman marker <list|id <id>|type <type>>"))
                        .then(Commands.literal("list").executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                                listMarkers(player))))
                        .then(Commands.literal("id")
                                .executes(ctx -> usage(ctx, "/wingman marker id <id>"))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                                                setMarkerId(player, StringArgumentType.getString(ctx, "id"))))))
                        .then(Commands.literal("type")
                                .executes(ctx -> usage(ctx, MARKER_TYPE_USAGE))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) ->
                                                SharedSuggestionProvider.suggest(MARKER_TYPES, builder))
                                        .executes(ctx -> asPlayer(ctx, (player, wingmen) ->
                                                setMarkerType(player, StringArgumentType.getString(ctx, "type")))))))
                .then(Commands.literal("gui").executes(ctx -> asPlayer(ctx, SetupCommands::openPlanner)));
    }

    private static void listUavTypes(ServerPlayer player) {
        List<String> planes = new ArrayList<>();
        List<String> helicopters = new ArrayList<>();
        for (VehicleSpec info : VehicleTypes.registry(VehicleKind.PLANE).values()) {
            if (info.isUAV()) {
                planes.add(info.name());
            }
        }
        for (VehicleSpec info : VehicleTypes.registry(VehicleKind.HELICOPTER).values()) {
            if (info.isUAV()) {
                helicopters.add(info.name());
            }
        }
        if (planes.isEmpty() && helicopters.isEmpty()) {
            send(player, line(ChatFormatting.GRAY, "uav.none"));
            return;
        }
        send(player, line(ChatFormatting.YELLOW, "uav.header"));
        planes.forEach(name -> send(player, line(ChatFormatting.GRAY, "uav.plane", name)));
        helicopters.forEach(name -> send(player, line(ChatFormatting.GRAY, "uav.helicopter", name)));
    }

    /** Spawns the named aircraft type a few blocks in front of the player, as a plane or a helicopter. */
    private static void spawnUav(ServerPlayer player, String typeName) {
        ServerLevel level = player.serverLevel();
        double yawRad = Math.toRadians(player.getYRot());
        double x = player.getX() - Math.sin(yawRad) * UAV_SPAWN_DISTANCE;
        double y = player.getY() + 1;
        double z = player.getZ() + Math.cos(yawRad) * UAV_SPAWN_DISTANCE;

        for (AircraftEntity entity : new AircraftEntity[]{new PlaneEntity(level), new HelicopterEntity(level)}) {
            // sets the synced type (so the client loads the right model) and loads the aircraft info
            entity.identity().setTypeName(typeName);
            VehicleSpec info = entity.spec();
            if (info == null) {
                entity.discard();
                continue;
            }
            // default texture, as when spawned from the item
            entity.appearance().setTexture(VehicleTypes.nextTexture(info));
            entity.moveTo(x, y, z, player.getYRot(), 0);
            level.addFreshEntity(entity);
            send(player, line(ChatFormatting.GREEN, "uav.spawned", entity.kind().directory(),
                    WingmanText.shortId(entity.getUUID()) + "..."));
            return;
        }
        send(player, line(ChatFormatting.RED, "uav.unknown", typeName));
    }

    private static void listMarkers(ServerPlayer player) {
        List<Marker> markers = AirfieldData.of(player.serverLevel()).markers();
        if (markers.isEmpty()) {
            send(player, line(ChatFormatting.GRAY, "marker.none"));
            return;
        }
        send(player, line(ChatFormatting.YELLOW, "marker.header"));
        for (Marker m : markers) {
            Component id = m.id().isEmpty() ? Component.translatable("mcheli.wingman.marker.no_id")
                    : Component.literal(m.id());
            send(player, line(ChatFormatting.GRAY, "marker.type", m.type().name())
                    .append(line(ChatFormatting.WHITE, "marker.id", id))
                    .append(line(ChatFormatting.GRAY, "marker.at", m.blockX(), m.blockY(), m.blockZ())));
        }
    }

    private static void setMarkerId(ServerPlayer player, String id) {
        BlockPos pos = lookedAtBlock(player);
        if (pos == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        AirfieldData airfield = AirfieldData.of(level);
        airfield.renameMarker(pos, id);
        if (level.getBlockEntity(pos) instanceof WingmanMarkerTileEntity marker) {
            marker.setMarkerId(id);
            airfield.putMarker(Markers.of(pos, marker));
        }
        send(player, line(ChatFormatting.GREEN, "marker.id_set", WingmanText.value(id)));
    }

    private static void setMarkerType(ServerPlayer player, String typeName) {
        BlockPos pos = lookedAtBlock(player);
        if (pos == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(pos) instanceof WingmanMarkerTileEntity marker)) {
            send(player, line(ChatFormatting.RED, "marker.not_marker"));
            return;
        }
        MarkerType type;
        try {
            type = MarkerType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            send(player, line(ChatFormatting.RED, "marker.unknown_type", typeName)
                    .append(line(ChatFormatting.GRAY, "marker.known_types")));
            return;
        }
        marker.setMarkerType(type);
        if (marker.getMarkerId().isEmpty()) {
            marker.setMarkerId(WingmanMarkerBlock.autoId(level, type));
        }
        AirfieldData.of(level).putMarker(Markers.of(pos, marker));
        send(player, line(ChatFormatting.GREEN, "marker.type_set", Component.literal(type.displayName()))
                .append(line(ChatFormatting.GRAY, "marker.id_suffix", WingmanText.value(marker.getMarkerId()))));
    }

    /** The block the player looks at within reach, or null (with a message). */
    private static @Nullable BlockPos lookedAtBlock(ServerPlayer player) {
        HitResult hit = player.pick(MARKER_PICK_RANGE, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            send(player, line(ChatFormatting.RED, "marker.look_at"));
            return null;
        }
        return blockHit.getBlockPos();
    }

    /** Opens the mission planner with every aircraft, route and marker of the level. */
    private static void openPlanner(ServerPlayer player, WingmanService wingmen) {
        ServerLevel level = player.serverLevel();
        Map<UUID, Wingman> roster = wingmen.roster().snapshot();
        List<UavView> uavs = new ArrayList<>();
        for (AircraftEntity aircraft : AircraftIndex.of(level).aircraft()) {
            if (!aircraft.isRemoved()) {
                uavs.add(uavView(aircraft, roster.get(aircraft.getUUID())));
            }
        }

        List<MissionRouteView> routes = new ArrayList<>();
        for (String name : wingmen.missionRoutes().names()) {
            List<MissionNode> nodes = wingmen.missionRoutes().get(name);
            routes.add(new MissionRouteView(name,
                    nodes != null ? nodes.stream().map(MissionNode::toString).toList() : List.of()));
        }

        List<MarkerView> markers = AirfieldData.of(level).markers().stream()
                .map(m -> new MarkerView(m.type(), m.id(), Markers.pos(m)))
                .toList();

        // the player's position is for the planner's "My Pos" button
        Network.sendToPlayer(player, new MissionPlannerMessage(uavs, routes, markers, player.position()));
    }

    private static UavView uavView(AircraftEntity aircraft, @Nullable Wingman wingman) {
        String name = WingmanAircraft.getAircraftName(aircraft);
        if (wingman == null) {
            return new UavView(aircraft.getUUID(), name, "UNASSIGNED", 0, 0);
        }
        var mission = wingman.autopilot().mission();
        return new UavView(aircraft.getUUID(), name, wingman.statusName(), mission != null ? mission.index() : 0,
                mission != null ? mission.size() : 0);
    }
}
