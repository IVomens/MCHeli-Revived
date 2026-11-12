package com.ivomens.mcheli.config;

/**
 * Tools for server owners: vehicle ownership and locking, limits on how many vehicles may be placed, and
 * who may use the admin commands. Server side. See docs/SERVER_ADMIN.md.
 *
 * @param limits the placement limits; 0 means unlimited
 */
public record AdminSettings(boolean vehicleLocking, boolean lockAllowsTeam, boolean lockPassengerSeats,
                            Limits limits, boolean opsBypassLimits, boolean removeDropsItem,
                            int commandPermission) {

    /** The most vehicles of each sort placing a vehicle may bring about; 0 means unlimited. */
    public record Limits(int perPlayer, int perChunk, int perLevel, int helicopters, int planes, int ships,
                         int tanks, int groundVehicles) {
    }

    private static final int MAX_LIMIT = 100_000;

    public static final BoolOption VEHICLE_LOCKING = BoolOption.of("VehicleLocking", true)
            .describe("VehicleLocking = true lets the player who placed a vehicle lock it (/mcheli lock, or sneak +"
                    + " use with an empty hand). A locked vehicle can only be flown by its owner, their team"
                    + " (LockAllowsTeam) and operators, and only they may pick it up. false = vehicles are never locked.");
    public static final BoolOption LOCK_ALLOWS_TEAM = BoolOption.of("LockAllowsTeam", true)
            .describe("LockAllowsTeam = true lets players on the owner's scoreboard team board a locked vehicle.");
    public static final BoolOption LOCK_PASSENGER_SEATS = BoolOption.of("LockPassengerSeats", false)
            .describe("LockPassengerSeats = true locks the passenger and gunner seats as well; false (default) only"
                    + " locks the pilot seat.");
    public static final IntOption MAX_PER_PLAYER = limit("MaxVehiclesPerPlayer",
            "MaxVehiclesPerPlayer = most vehicles one player may own in loaded chunks (0 = unlimited)");
    public static final IntOption MAX_PER_CHUNK = limit("MaxVehiclesPerChunk",
            "MaxVehiclesPerChunk = most vehicles in one chunk (0 = unlimited)");
    public static final IntOption MAX_PER_LEVEL = limit("MaxVehiclesPerLevel",
            "MaxVehiclesPerLevel = most loaded vehicles in one dimension (0 = unlimited)");
    public static final IntOption MAX_HELICOPTERS = limit("MaxHelicoptersPerLevel",
            "MaxHelicoptersPerLevel = most loaded helicopters in one dimension (0 = unlimited)");
    public static final IntOption MAX_PLANES = limit("MaxPlanesPerLevel",
            "MaxPlanesPerLevel = most loaded planes in one dimension (0 = unlimited)");
    public static final IntOption MAX_SHIPS = limit("MaxShipsPerLevel",
            "MaxShipsPerLevel = most loaded ships in one dimension (0 = unlimited)");
    public static final IntOption MAX_TANKS = limit("MaxTanksPerLevel",
            "MaxTanksPerLevel = most loaded tanks in one dimension (0 = unlimited)");
    public static final IntOption MAX_GROUND_VEHICLES = limit("MaxGroundVehiclesPerLevel",
            "MaxGroundVehiclesPerLevel = most loaded vehicles of the 'vehicles' kind (cars, guns) in one dimension"
                    + " (0 = unlimited)");
    public static final BoolOption OPS_BYPASS_LIMITS = BoolOption.of("OpsBypassLimits", true)
            .describe("OpsBypassLimits = true lets operators (see AdminCommandPermission) place vehicles beyond"
                    + " the limits above.");
    public static final BoolOption REMOVE_DROPS_ITEM = BoolOption.of("RemoveDropsItem", false)
            .describe("RemoveDropsItem = true makes /mcheli vehicles remove drop the vehicle's item and cargo;"
                    + " false (default) removes vehicles without a trace.");
    public static final IntOption COMMAND_PERMISSION = IntOption.of("AdminCommandPermission", 2).range(0, 4)
            .describe("AdminCommandPermission = operator level needed for /mcheli perf, /mcheli vehicles, unlocking"
                    + " other players' vehicles and bypassing the limits (0-4, default 2). On NeoForge a"
                    + " permission mod can grant the node mcheli.admin instead.");

    static AdminSettings from(ConfigValues v) {
        Limits limits = new Limits(v.get(MAX_PER_PLAYER), v.get(MAX_PER_CHUNK), v.get(MAX_PER_LEVEL),
                v.get(MAX_HELICOPTERS), v.get(MAX_PLANES), v.get(MAX_SHIPS), v.get(MAX_TANKS),
                v.get(MAX_GROUND_VEHICLES));
        return new AdminSettings(v.get(VEHICLE_LOCKING), v.get(LOCK_ALLOWS_TEAM), v.get(LOCK_PASSENGER_SEATS), limits,
                v.get(OPS_BYPASS_LIMITS), v.get(REMOVE_DROPS_ITEM), v.get(COMMAND_PERMISSION));
    }

    private static IntOption limit(String key, String comment) {
        return IntOption.of(key, 0).range(0, MAX_LIMIT).describe(comment);
    }
}
