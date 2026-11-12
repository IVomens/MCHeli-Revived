package com.ivomens.mcheli.wingman.roster;

import com.ivomens.mcheli.core.wingman.brain.Assignment;
import com.ivomens.mcheli.core.wingman.brain.AttackOrders;
import com.ivomens.mcheli.core.wingman.brain.Autopilot;
import com.ivomens.mcheli.core.wingman.mission.MissionOrder;
import net.minecraft.world.entity.Entity;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * One aircraft on the roster: who it belongs to, whom it follows, and the attack orders and autopilot
 * its brain works on. Changing leader makes a new entry; the owner and the formation slot never change.
 */
public final class Wingman {

    private final @Nullable UUID owner;
    private final @Nullable Entity leader;
    private final int slot;
    private final AttackOrders attack = new AttackOrders();
    private final Autopilot autopilot = new Autopilot();
    private final Assignment assignment;

    private Wingman(@Nullable UUID owner, @Nullable Entity leader, int slot) {
        this.owner = owner;
        this.leader = leader;
        this.slot = slot;
        this.assignment = new Assignment(slot, this.attack, this.autopilot);
    }

    /** A follower in the given slot behind the leader. */
    public static Wingman follower(Entity leader, int slot, @Nullable UUID owner) {
        return new Wingman(owner, leader, slot);
    }

    /** An aircraft without a leader, flying tasks of its own. */
    public static Wingman autonomous(@Nullable UUID owner) {
        return new Wingman(owner, null, 0);
    }

    /** The player who enlisted it; null means only operators may order it. */
    public @Nullable UUID owner() {
        return this.owner;
    }

    /** The leader it flies formation on, or null for an autonomous aircraft. */
    public @Nullable Entity leader() {
        return this.leader;
    }

    public int slot() {
        return this.slot;
    }

    public AttackOrders attack() {
        return this.attack;
    }

    public Autopilot autopilot() {
        return this.autopilot;
    }

    /** What the brain works on. */
    public Assignment assignment() {
        return this.assignment;
    }

    public boolean isAutonomous() {
        return this.autopilot.isAutonomous();
    }

    /** Starts a mission order, dropping any earlier attack. */
    public void startOrder(MissionOrder order) {
        this.autopilot.startOrder(order);
        this.attack.holdFire();
    }

    /** Status word for lists: the flight phase when autonomous, else FOLLOWING or IDLE. */
    public String statusName() {
        if (isAutonomous()) {
            return this.autopilot.phase().name();
        }
        return this.leader != null ? "FOLLOWING" : "IDLE";
    }
}
