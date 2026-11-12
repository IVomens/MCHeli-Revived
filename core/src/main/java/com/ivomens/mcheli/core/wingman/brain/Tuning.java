package com.ivomens.mcheli.core.wingman.brain;

/**
 * The server's wingman settings that shape flying: formation geometry and the attack altitude band.
 *
 * @param sideDistance      lateral distance between ranks of the V; 0 flies in trail
 * @param altitudeOffset    height of the formation above the leader
 * @param rearDistance      distance of each rank behind the one ahead
 * @param minAttackAltitude absolute attack floor; 0 for none
 * @param maxAttackAltitude absolute attack ceiling; 0 for none
 */
public record Tuning(double sideDistance, double altitudeOffset, double rearDistance, double minAttackAltitude,
                     double maxAttackAltitude) {

    /**
     * Position of a formation slot. With a side distance the wingmen form a V, alternating right and
     * left with one rank per pair; with side distance 0 they fly in trail, one rear distance apart.
     */
    public Point slotPosition(LeaderState leader, int slot) {
        Point lead = leader.position();
        double y = lead.y() + this.altitudeOffset;

        double yawRad = Math.toRadians(leader.yaw());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        if (this.sideDistance == 0.0) {
            int rank = slot + 1;
            return new Point(lead.x() - forwardX * this.rearDistance * rank, y,
                    lead.z() - forwardZ * this.rearDistance * rank);
        }

        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        int rank = slot / 2 + 1;
        double side = (slot % 2 == 0) ? 1.0 : -1.0;
        return new Point(
                lead.x() + rightX * this.sideDistance * side * rank - forwardX * this.rearDistance * rank,
                y,
                lead.z() + rightZ * this.sideDistance * side * rank - forwardZ * this.rearDistance * rank);
    }

    /**
     * Pushes a movement target out of the leader's clearance circle (the larger formation distance), so
     * attack runs do not fly through the leader.
     */
    public Point clearOfLeader(Point target, LeaderState leader) {
        if (leader == null) {
            return target;
        }
        double clearance = Math.max(this.sideDistance, this.rearDistance);
        if (clearance <= 0) {
            return target;
        }
        Point lead = leader.position();
        double dx = target.x() - lead.x();
        double dz = target.z() - lead.z();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist >= clearance || dist <= 0.01) {
            return target;
        }
        double scale = clearance / dist;
        return new Point(lead.x() + dx * scale, target.y(), lead.z() + dz * scale);
    }

    /** Minimum spacing between wingmen of the same leader. */
    public double minimumSpacing() {
        return this.sideDistance > 0 ? this.sideDistance : this.rearDistance;
    }

    /** Attack altitude above a target, limited by the configured floor and ceiling. */
    public double attackAltitude(double targetY, double offset) {
        double y = targetY + offset;
        if (this.minAttackAltitude > 0) {
            y = Math.max(y, this.minAttackAltitude);
        }
        if (this.maxAttackAltitude > 0) {
            y = Math.min(y, this.maxAttackAltitude);
        }
        return y;
    }
}
