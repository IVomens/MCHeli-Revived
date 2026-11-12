package com.ivomens.mcheli.wingman.block;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import com.ivomens.mcheli.vehicle.world.AircraftIndex;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.wingman.airfield.AirfieldData;
import com.ivomens.mcheli.core.wingman.airfield.Marker;
import com.ivomens.mcheli.wingman.airfield.Markers;
import com.ivomens.mcheli.core.wingman.airfield.TaxiRoute;
import com.ivomens.mcheli.wingman.net.BaseMarkers;
import com.ivomens.mcheli.wingman.net.MarkerSettings;
import com.ivomens.mcheli.wingman.net.MarkerView;
import com.ivomens.mcheli.wingman.net.NearbyAircraft;
import com.ivomens.mcheli.wingman.net.OpenBaseScreenMessage;
import com.ivomens.mcheli.wingman.net.OpenMarkerScreenMessage;
import com.ivomens.mcheli.wingman.net.TaxiRouteData;
import com.ivomens.mcheli.wingman.net.TaxiRunways;
import com.ivomens.mcheli.wingman.text.WingmanText;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Marker block for bases, runways, parking spots, helipads and taxi waypoints. Right-click opens the
 * marker settings, or the base planner for a BASE marker.
 */
public class WingmanMarkerBlock extends Block implements EntityBlock {

    /** Unassigned waypoints this close to a base are offered in its planner anyway. */
    private static final double NEARBY_WAYPOINT_RADIUS = 500.0;
    /** Aircraft this close to a base are listed in its planner. */
    private static final double NEARBY_AIRCRAFT_RADIUS = 512.0;

    public static final MapCodec<WingmanMarkerBlock> CODEC = simpleCodec(WingmanMarkerBlock::new);

    public static final Property<MarkerType> TYPE = new MarkerTypeProperty("type");

    public WingmanMarkerBlock() {
        this(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(2.0f, 10.0f)
                .sound(SoundType.METAL));
    }

    public WingmanMarkerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        // The block entity defaults to PARKING; keep the model in step until the type is changed.
        registerDefaultState(stateDefinition.any().setValue(TYPE, MarkerType.PARKING));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TYPE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WingmanMarkerTileEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(world instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        WingmanMarkerTileEntity te = getTe(level, pos);
        if (te == null || !(player instanceof ServerPlayer mp)) return InteractionResult.CONSUME;

        if (te.getMarkerType() == MarkerType.BASE) {
            openBaseGui(level, pos, te, mp);
        } else {
            MarkerSettings settings = new MarkerSettings(te.getMarkerType(), te.getMarkerId(), te.getBaseId(),
                    te.getParkingHeading());
            Network.sendToPlayer(mp, new OpenMarkerScreenMessage(pos, settings));
        }
        return InteractionResult.CONSUME;
    }

    /** Sends the base planner: the base's taxi routes, its markers and the aircraft nearby. */
    private static void openBaseGui(ServerLevel world, BlockPos pos, WingmanMarkerTileEntity te, ServerPlayer player) {
        // A BASE marker's own id is the base id.
        String baseId = te.getMarkerId();
        List<TaxiRouteData> routes = AirfieldData.of(world).routesOfBase(baseId).stream()
                .map(WingmanMarkerBlock::routeData)
                .toList();
        List<NearbyAircraft> aircraft = new ArrayList<>();
        for (AircraftEntity e : AircraftIndex.of(world).aircraft()) {
            if (e.isRemoved()) continue;
            double dx = e.getX() - pos.getX(), dz = e.getZ() - pos.getZ();
            if (dx * dx + dz * dz > NEARBY_AIRCRAFT_RADIUS * NEARBY_AIRCRAFT_RADIUS) continue;
            aircraft.add(new NearbyAircraft(e.getUUID(), e.getName().getString()));
        }
        Network.sendToPlayer(player, new OpenBaseScreenMessage(pos, baseId, routes, baseMarkers(world, pos, baseId),
                aircraft));
    }

    private static TaxiRouteData routeData(TaxiRoute route) {
        return new TaxiRouteData(route.routeId(), route.parkingId(),
                new TaxiRunways(route.runwayId(), route.runwayBId(), route.arrivalRunwayId()),
                route.waypointIds(), route.arrivalWaypointIds(), route.parkingHeading());
    }

    private static BaseMarkers baseMarkers(ServerLevel world, BlockPos pos, String baseId) {
        Map<MarkerType, List<MarkerView>> byType = new EnumMap<>(MarkerType.class);
        for (Marker m : AirfieldData.of(world).findChildren(baseId)) {
            byType.computeIfAbsent(m.type(), type -> new ArrayList<>()).add(markerView(m));
        }

        // Waypoints are easy to place without setting their parent base; offer nearby ones too.
        List<MarkerView> waypoints = byType.computeIfAbsent(MarkerType.WAYPOINT, type -> new ArrayList<>());
        Set<String> addedWaypointIds = new HashSet<>();
        for (MarkerView waypoint : waypoints) {
            addedWaypointIds.add(waypoint.id());
        }
        for (Marker m : AirfieldData.of(world).markers()) {
            if (m.type() != MarkerType.WAYPOINT || addedWaypointIds.contains(m.id())) continue;
            double dx = m.blockX() - pos.getX();
            double dz = m.blockZ() - pos.getZ();
            if (dx * dx + dz * dz > NEARBY_WAYPOINT_RADIUS * NEARBY_WAYPOINT_RADIUS) continue;
            waypoints.add(markerView(m));
        }
        return new BaseMarkers(markersOf(byType, MarkerType.PARKING), waypoints, markersOf(byType, MarkerType.RUNWAY_A),
                markersOf(byType, MarkerType.RUNWAY_B), markersOf(byType, MarkerType.HELIPAD),
                markersOf(byType, MarkerType.HELIPAD_B));
    }

    private static List<MarkerView> markersOf(Map<MarkerType, List<MarkerView>> byType, MarkerType type) {
        return byType.getOrDefault(type, List.of());
    }

    private static MarkerView markerView(Marker m) {
        return new MarkerView(m.type(), m.id(), Markers.pos(m));
    }

    /**
     * Placement: the block entity only exists once the block is in the level, so the auto-id and
     * registration happen here rather than in {@code onPlace}.
     */
    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (!(world instanceof ServerLevel level)) return;
        WingmanMarkerTileEntity te = getTe(level, pos);
        if (te == null) return;
        if (te.getMarkerId().isEmpty()) {
            te.setMarkerId(autoId(level, te.getMarkerType()));
        }
        AirfieldData.of(level).putMarker(Markers.of(pos, te));
        playerHint(world, pos, te, placer);
    }

    /** Next free-looking default id for a new marker of this type, for example "ra_2". */
    public static String autoId(ServerLevel world, MarkerType type) {
        String prefix = switch (type) {
            case PARKING -> "p";
            case RUNWAY_A -> "ra";
            case RUNWAY_B -> "rb";
            case HELIPAD -> "hp";
            case HELIPAD_B -> "hpb";
            default -> "wp";
        };
        return prefix + "_" + (AirfieldData.of(world).findByType(type).size() + 1);
    }

    /** Tells the placing player (or, if unknown, a player nearby) the new marker's id. */
    private static void playerHint(Level world, BlockPos pos, WingmanMarkerTileEntity te, @Nullable LivingEntity placer) {
        Player target = placer instanceof Player p ? p : null;
        if (target == null) {
            for (Player p : world.players()) {
                if (p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 16 * 16) {
                    target = p;
                    break;
                }
            }
        }
        if (target != null) {
            target.sendSystemMessage(WingmanText.line(ChatFormatting.GRAY, "marker.placed",
                    Component.literal(te.getMarkerType().displayName()), WingmanText.value(te.getMarkerId()),
                    Component.literal("/wingman marker type").withStyle(ChatFormatting.WHITE)));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (world instanceof ServerLevel level && !state.is(newState.getBlock())) {
            AirfieldData.of(level).removeMarker(pos);
        }
        super.onRemove(state, world, pos, newState, movedByPiston);
    }

    private static WingmanMarkerTileEntity getTe(Level world, BlockPos pos) {
        BlockEntity te = world.getBlockEntity(pos);
        return te instanceof WingmanMarkerTileEntity w ? w : null;
    }
}
