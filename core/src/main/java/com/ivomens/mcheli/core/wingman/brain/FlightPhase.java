package com.ivomens.mcheli.core.wingman.brain;

/**
 * The phase an autonomous flight is in. The phases group into the state machines of the brain:
 * {@link DepartureBrain} (TAXI_OUT, ALIGN, TAKEOFF_ROLL, CLIMB), {@link RecoveryBrain} (DESCEND,
 * the circuit legs and LANDING), {@link VtolBrain} (VTOL_TAKEOFF, VTOL_LAND) and the mission phases of
 * {@link OrderBrain} and {@link NodeMissionBrain}. The names appear in status lines and recordings.
 */
public enum FlightPhase {
    /** Not autonomous: following a leader or idle. */
    NONE,
    /** Ground taxi from the parking spot to the runway threshold. */
    TAXI_OUT,
    /** Holding on the threshold until the nose lines up with the runway. */
    ALIGN,
    /** Accelerating down the runway. */
    TAKEOFF_ROLL,
    /** Climbing to cruise altitude. */
    CLIMB,
    /** Flying to a FLY_TO node. */
    ENROUTE,
    /** Executing an ATTACK node. */
    ATTACK,
    /** Executing a LOITER node. */
    LOITER,
    /** Descending towards the circuit entry point. */
    DESCEND,
    /** Downwind leg: parallel to the runway, opposite to the landing direction. */
    CIRCUIT_DOWNWIND,
    /** Base leg: turning towards the final approach axis. */
    CIRCUIT_BASE,
    /** Final approach along the glide slope. */
    CIRCUIT_FINAL,
    /** Landing roll: throttle closed, waiting to stop. */
    LANDING,
    /** Ground taxi from the runway to the parking spot. */
    TAXI_IN,
    /** Parked. */
    PARKED,

    /** Flying straight to the mission area. */
    TRANSIT_TO,
    /** On station: CAP/CAS orbit, escort or recon. */
    ON_STATION,
    /** Running a strike pass. */
    STRIKE_PASS,
    /** Returning from the mission area to the home base. */
    RTB,

    /** Vertical takeoff from a helipad (helicopters and VTOL planes), climbing to cruise altitude. */
    VTOL_TAKEOFF,
    /** Vertical landing onto a helipad (helicopters and VTOL planes). */
    VTOL_LAND
}
