package com.ivomens.mcheli.core.wingman.brain;

/**
 * The state a wingman brain works on: its formation slot, its attack orders and its autopilot.
 *
 * @param slot formation slot behind the leader; only followers use it
 */
public record Assignment(int slot, AttackOrders attack, Autopilot autopilot) {

    /** Flying formation on a leader rather than a task of its own. */
    boolean follows(Perception perception) {
        return !this.autopilot.isAutonomous() && perception.leader() != null;
    }
}
