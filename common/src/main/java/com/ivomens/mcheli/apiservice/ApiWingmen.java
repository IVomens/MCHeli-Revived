package com.ivomens.mcheli.apiservice;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.wingman.MissionKind;
import com.ivomens.mcheli.api.wingman.MissionRequest;
import com.ivomens.mcheli.api.wingman.WingmanAttack;
import com.ivomens.mcheli.api.wingman.WingmanOrderResult;
import com.ivomens.mcheli.api.wingman.WingmanState;
import com.ivomens.mcheli.api.wingman.Wingmen;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import com.ivomens.mcheli.core.wingman.brain.AttackMode;
import com.ivomens.mcheli.core.wingman.brain.OrderProgress;
import com.ivomens.mcheli.core.wingman.mission.MissionOrder;
import com.ivomens.mcheli.core.wingman.mission.MissionType;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.wingman.roster.Roster;
import com.ivomens.mcheli.wingman.roster.Wingman;
import com.ivomens.mcheli.wingman.roster.WingmanAccess;
import com.ivomens.mcheli.wingman.util.WingmanAircraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The API's {@link Wingmen}: reads the server's wingman roster and gives the orders the wingman panel's
 * handlers and the {@code /wingman} commands give, through the same roster and access rules, without the chat
 * lines those send.
 */
final class ApiWingmen implements Wingmen {

    @Override
    public List<WingmanState> all(MinecraftServer server) {
        List<WingmanState> states = new ArrayList<>();
        WingmanService.of(server).roster().snapshot().forEach((id, wingman) -> states.add(state(server, id, wingman)));
        return List.copyOf(states);
    }

    @Override
    public List<WingmanState> orderableBy(ServerPlayer player) {
        List<WingmanState> states = new ArrayList<>();
        WingmanService.of(player).roster().snapshot().forEach((id, wingman) -> {
            if (WingmanAccess.canOrder(player, wingman)) {
                states.add(state(player.server, id, wingman));
            }
        });
        return List.copyOf(states);
    }

    @Override
    public List<WingmanState> following(@Nullable Entity leader) {
        if (leader == null || !(leader.level() instanceof ServerLevel level)) {
            return List.of();
        }
        List<WingmanState> states = new ArrayList<>();
        WingmanService.of(level).roster().snapshot().forEach((id, wingman) -> {
            if (wingman.leader() == leader) {
                states.add(state(level.getServer(), id, wingman));
            }
        });
        return List.copyOf(states);
    }

    @Override
    public Optional<WingmanState> get(MinecraftServer server, UUID aircraftId) {
        Wingman wingman = WingmanService.of(server).roster().get(aircraftId);
        return wingman != null ? Optional.of(state(server, aircraftId, wingman)) : Optional.empty();
    }

    /** The panel's follow button ({@code WingmanFollowHandler}) with the checks of {@code /wingman follow}. */
    @Override
    public WingmanOrderResult follow(ServerPlayer commander, Entity aircraft) {
        Entity leader = commander.getVehicle();
        if (!(leader instanceof AircraftEntity)) {
            return WingmanOrderResult.NOT_IN_AIRCRAFT;
        }
        if (aircraft.isRemoved() || aircraft.level() != commander.level()) {
            return WingmanOrderResult.NOT_FOUND;
        }
        if (!(aircraft instanceof AircraftEntity) || aircraft == leader || aircraft.hasIndirectPassenger(commander)
                || commander.hasIndirectPassenger(aircraft)) {
            return WingmanOrderResult.INVALID;
        }
        WingmanService wingmen = WingmanService.of(commander);
        int slot = wingmen.roster().countFollowing(leader);
        if (slot >= WingmanConfig.get().maxWingmen()) {
            return WingmanOrderResult.FORMATION_FULL;
        }
        UUID id = aircraft.getUUID();
        if (!mayEnlist(commander, wingmen.roster(), id)) {
            return WingmanOrderResult.NOT_ALLOWED;
        }
        return wingmen.access().follow(commander, id, leader, slot) ? WingmanOrderResult.DONE
                : WingmanOrderResult.NOT_ALLOWED;
    }

    /** {@code WingmanStopHandler}. */
    @Override
    public WingmanOrderResult release(ServerPlayer commander, UUID aircraftId) {
        Roster roster = WingmanService.of(commander).roster();
        return order(commander, aircraftId, wingman -> roster.remove(aircraftId));
    }

    @Override
    public WingmanOrderResult attack(ServerPlayer commander, UUID aircraftId, Entity target) {
        UUID targetId = target.getUUID();
        return order(commander, aircraftId, wingman -> wingman.attack().set(AttackMode.MANUAL, targetId));
    }

    /** {@code WingmanAttackModeHandler}, auto attack. */
    @Override
    public WingmanOrderResult autoAttack(ServerPlayer commander, UUID aircraftId) {
        return order(commander, aircraftId, wingman -> wingman.attack().set(AttackMode.AUTO, null));
    }

    /** {@code WingmanAttackModeHandler}, hold fire. */
    @Override
    public WingmanOrderResult holdFire(ServerPlayer commander, UUID aircraftId) {
        return order(commander, aircraftId, wingman -> wingman.attack().set(AttackMode.NONE, null));
    }

    /** {@code WingmanWeaponHandler}. */
    @Override
    public WingmanOrderResult setWeaponType(ServerPlayer commander, UUID aircraftId, @Nullable String weaponType) {
        String type = weaponType == null || weaponType.isEmpty() ? null : weaponType;
        return order(commander, aircraftId, wingman -> wingman.attack().setWeaponType(type));
    }

    /** {@code DispatchOrderHandler}, starting airborne. */
    @Override
    public WingmanOrderResult dispatch(ServerPlayer commander, UUID aircraftId, MissionRequest mission) {
        Entity aircraft = commander.serverLevel().getEntity(aircraftId);
        if (aircraft == null) {
            return WingmanOrderResult.NOT_FOUND;
        }
        if (!(aircraft instanceof AircraftEntity)) {
            return WingmanOrderResult.INVALID;
        }
        WingmanService wingmen = WingmanService.of(commander);
        if (!mayEnlist(commander, wingmen.roster(), aircraftId)) {
            return WingmanOrderResult.NOT_ALLOWED;
        }
        Wingman wingman = wingmen.access().claim(commander, aircraftId);
        if (wingman == null) {
            return WingmanOrderResult.NOT_ALLOWED;
        }
        MissionOrder order = order(mission);
        // a freshly spawned aircraft has no fuel and would return to base right away
        WingmanAircraft.fillFuel(aircraft);
        wingman.startOrder(order);
        wingman.autopilot().departFrom("", "");
        wingman.attack().setWeaponType(order.primaryWeapon());
        return WingmanOrderResult.DONE;
    }

    /** {@code /wingman order abort} and the panel's abort button together. */
    @Override
    public WingmanOrderResult abort(ServerPlayer commander, UUID aircraftId) {
        return order(commander, aircraftId, wingman -> {
            wingman.autopilot().abortOrder();
            wingman.autopilot().abortMission();
        });
    }

    /** Gives an order to an existing wingman the commander may order, as {@code WingmanAccess.existing} does. */
    private static WingmanOrderResult order(ServerPlayer commander, UUID aircraftId, Consumer<Wingman> order) {
        Wingman wingman = WingmanService.of(commander).roster().get(aircraftId);
        if (wingman == null) {
            return WingmanOrderResult.NOT_A_WINGMAN;
        }
        if (!WingmanAccess.canOrder(commander, wingman)) {
            return WingmanOrderResult.NOT_ALLOWED;
        }
        order.accept(wingman);
        return WingmanOrderResult.DONE;
    }

    /** Whether the access rules let the commander order or claim the aircraft; checked silently first. */
    private static boolean mayEnlist(ServerPlayer commander, Roster roster, UUID aircraftId) {
        Wingman existing = roster.get(aircraftId);
        return existing != null ? WingmanAccess.canOrder(commander, existing)
                : WingmanAccess.claimRefusal(commander, aircraftId) == null;
    }

    private static MissionOrder order(MissionRequest mission) {
        MissionOrder.Builder order = MissionOrder.builder();
        mission.kinds().forEach(kind -> order.type(MissionType.valueOf(kind.name())));
        if (mission.weapons().isEmpty()) {
            order.defaultWeapons();
        } else {
            mission.weapons().forEach(order::weapon);
        }
        return order.target(mission.targetX(), mission.targetZ())
                .baseId(mission.baseId())
                .orbitRadius(mission.orbitRadius())
                .cruiseAlt(mission.cruiseAltitude())
                .strikePasses(mission.strikePasses())
                .timeLimitSeconds(mission.timeLimitSeconds())
                .build();
    }

    private static WingmanState state(MinecraftServer server, UUID id, Wingman wingman) {
        UUID target = wingman.attack().mode() == AttackMode.MANUAL ? wingman.attack().manualTarget() : null;
        return new State(id, aircraft(server, id), wingman.owner(), wingman.leader(), wingman.slot(),
                attack(wingman.attack().mode()), target, wingman.attack().weaponType(), wingman.statusName(),
                missionKinds(wingman.autopilot().order()));
    }

    @Nullable
    private static Vehicle aircraft(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof Vehicle vehicle) {
                return vehicle;
            }
        }
        return null;
    }

    private static WingmanAttack attack(AttackMode mode) {
        return switch (mode) {
            case NONE -> WingmanAttack.HOLD_FIRE;
            case MANUAL -> WingmanAttack.TARGET;
            case AUTO -> WingmanAttack.AUTO;
        };
    }

    private static Set<MissionKind> missionKinds(@Nullable OrderProgress progress) {
        if (progress == null) {
            return Set.of();
        }
        Set<MissionKind> kinds = EnumSet.noneOf(MissionKind.class);
        progress.order().missionTypes().forEach(type -> kinds.add(MissionKind.valueOf(type.name())));
        return Set.copyOf(kinds);
    }

    private record State(UUID aircraftId, @Nullable Vehicle aircraft, @Nullable UUID owner, @Nullable Entity leader,
                         int formationSlot, WingmanAttack attack, @Nullable UUID attackTarget,
                         @Nullable String weaponType, String status, Set<MissionKind> missionKinds)
            implements WingmanState {
    }
}
