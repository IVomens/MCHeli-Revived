package com.ivomens.mcheli.wingman.control;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.wingman.airfield.AirfieldData;
import com.ivomens.mcheli.core.wingman.brain.Controls;
import com.ivomens.mcheli.core.wingman.brain.LeaderState;
import com.ivomens.mcheli.core.wingman.brain.Perception;
import com.ivomens.mcheli.core.wingman.brain.PilotCommand;
import com.ivomens.mcheli.core.wingman.brain.RecordedControls;
import com.ivomens.mcheli.core.wingman.brain.Tuning;
import com.ivomens.mcheli.core.wingman.brain.WingmanBrain;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import com.ivomens.mcheli.config.wingman.WingmanSettings;
import com.ivomens.mcheli.wingman.roster.Roster;
import com.ivomens.mcheli.wingman.roster.Wingman;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Flies one server's wingmen, a level at a time, through the steps of {@link WingmanBrain}:
 * <ol>
 *   <li>Before the level's entities tick, every wingman is perceived and its brain decides (control,
 *       then plan). The decision is queued on the aircraft's scripted pilot, which applies it as the
 *       first phase of the aircraft's own tick.</li>
 *   <li>After the level's entities have ticked, followers whose leader is gone are released, weapons are
 *       released, and the brain's corrections are applied through the scripted pilot's hold phase.</li>
 * </ol>
 * Only MCHeli aircraft are flown; any other entity on the roster is left alone.
 */
public final class WingmanTicker {

    private static final Logger LOG = LogManager.getLogger("McHeliWingman");
    private static final int CACHE_PRUNE_INTERVAL = 200;

    private final Roster roster;
    private final WingmanCombat combat;
    private final AutopilotRiders riders = new AutopilotRiders();
    /** This tick's decisions by aircraft id, while they are being made. */
    private final Map<UUID, RecordedControls> decisions = new HashMap<>();
    /** Aircraft given controls this tick; those whose chunk did not tick take them after the level. */
    private final List<AircraftEntity> queued = new ArrayList<>();

    public WingmanTicker(Roster roster) {
        this.roster = roster;
        this.combat = new WingmanCombat(roster);
    }

    /** The server's current wingman settings, as the brain uses them. */
    static Tuning tuning() {
        WingmanSettings s = WingmanConfig.get();
        return new Tuning(s.formationSideDist(), s.formationAltOffset(), s.formationRearDist(),
                s.minAttackAltitude(), s.maxAttackAltitude());
    }

    /** Before the level's entities tick: every wingman decides, and its aircraft is given the controls. */
    public void beforeEntities(ServerLevel level) {
        this.riders.beginTick();
        if (!this.roster.isEmpty()) {
            Map<UUID, Wingman> entries = this.roster.snapshot();
            this.combat.beginTick(entries);
            entries.forEach((id, wingman) -> control(level, id, wingman));
        }
        this.riders.releaseInactive(level);
        if (!this.roster.isEmpty()) {
            this.roster.snapshot().forEach((id, wingman) -> plan(level, id, wingman));
        }
        this.decisions.forEach((id, controls) -> queue(level, id, controls.command()));
        this.decisions.clear();
    }

    private void control(ServerLevel level, UUID id, Wingman wingman) {
        AircraftEntity aircraft = aircraftIn(level, id);
        if (aircraft == null) {
            return;
        }
        RecordedControls controls = Observer.recordedControls(aircraft);
        this.decisions.put(id, controls);
        WingmanBrain.control(perceive(level, aircraft, wingman), controls, wingman.assignment());
    }

    private void plan(ServerLevel level, UUID id, Wingman wingman) {
        if (!wingman.isAutonomous()) {
            return;
        }
        AircraftEntity aircraft = aircraftIn(level, id);
        RecordedControls controls = this.decisions.get(id);
        if (aircraft != null && controls != null) {
            WingmanBrain.plan(perceive(level, aircraft, wingman), controls, wingman.assignment());
        }
    }

    private void queue(ServerLevel level, UUID id, PilotCommand command) {
        AircraftEntity aircraft = aircraftIn(level, id);
        if (aircraft != null && !command.isEmpty()) {
            aircraft.scriptedPilot().queue(vehicle -> command.replayOn(new AircraftControls(vehicle)));
            this.queued.add(aircraft);
        }
    }

    /** After the level's entities ticked: release lost followers, fire, then hold. */
    public void afterEntities(ServerLevel level) {
        for (AircraftEntity aircraft : this.queued) {
            aircraft.scriptedPilot().takeControls();
        }
        this.queued.clear();
        if (this.roster.isEmpty()) {
            return;
        }
        Map<UUID, Wingman> entries = this.roster.snapshot();
        this.combat.beginTick(entries);
        if (level.getGameTime() % CACHE_PRUNE_INTERVAL == 0) {
            this.combat.pruneCaches();
        }
        entries.forEach((id, wingman) -> afterMove(level, id, wingman));
        if (!this.roster.isEmpty()) {
            this.roster.snapshot().forEach((id, wingman) -> hold(level, id, wingman));
        }
    }

    private void afterMove(ServerLevel level, UUID id, Wingman wingman) {
        if (!checkLeader(id, wingman)) {
            return;
        }
        AircraftEntity aircraft = aircraftIn(level, id);
        if (aircraft == null) {
            return;
        }
        if (wingman.attack().isAttacking()) {
            this.combat.engage(level, aircraft, wingman);
        }
        aircraft.scriptedPilot().hold(vehicle -> WingmanBrain.afterMove(perceive(level, vehicle, wingman),
                new AircraftControls(vehicle), wingman.assignment()));
    }

    private void hold(ServerLevel level, UUID id, Wingman wingman) {
        if (!wingman.isAutonomous()) {
            return;
        }
        AircraftEntity aircraft = aircraftIn(level, id);
        if (aircraft != null) {
            aircraft.scriptedPilot().hold(vehicle -> WingmanBrain.hold(perceive(level, vehicle, wingman),
                    new AircraftControls(vehicle), wingman.assignment()));
        }
    }

    /**
     * Followers are released when their leader is gone, and stop attacking when nobody flies the leader
     * any more (otherwise they would chase targets and get lost). Autonomous aircraft need no leader.
     *
     * @return false if the wingman was released
     */
    private boolean checkLeader(UUID id, Wingman wingman) {
        if (wingman.isAutonomous()) {
            return true;
        }
        Entity leader = wingman.leader();
        if (leader == null || leader.isRemoved()) {
            this.roster.remove(id);
            LOG.info("[Wingman] {} released, leader gone", id);
            return false;
        }
        if (wingman.attack().isAttacking() && !(AircraftControls.pilotOf(leader) instanceof Player)) {
            wingman.attack().holdFire();
            LOG.info("[Wingman] {} holding fire, leader has no pilot", id);
        }
        return true;
    }

    private Perception perceive(ServerLevel level, AircraftEntity aircraft, Wingman wingman) {
        Entity leaderEntity = wingman.leader();
        LeaderState leader = leaderEntity != null
                ? Observer.leader(leaderEntity, decidedControls(leaderEntity)) : null;
        return new Perception(WingmanSenses.shortId(aircraft), Observer.airframe(aircraft), leader,
                new WingmanSenses(level, aircraft, wingman, this.combat, this.riders), AirfieldData.of(level),
                tuning(), WingmanBrainLog.INSTANCE);
    }

    private @Nullable Controls decidedControls(Entity entity) {
        return this.decisions.get(entity.getUUID());
    }

    private static @Nullable AircraftEntity aircraftIn(ServerLevel level, UUID id) {
        return level.getEntity(id) instanceof AircraftEntity aircraft && !aircraft.isRemoved() ? aircraft : null;
    }

    /** Forgets the riders and combat state of a server that stopped. */
    public void clear() {
        this.riders.clear();
        this.combat.clear();
        this.decisions.clear();
        this.queued.clear();
    }
}
