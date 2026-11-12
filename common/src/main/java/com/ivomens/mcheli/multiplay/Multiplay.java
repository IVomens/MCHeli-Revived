package com.ivomens.mcheli.multiplay;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.config.DebugLog;
import com.ivomens.mcheli.core.target.TargetType;
import com.ivomens.mcheli.entity.SinglePassenger;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.multiplay.net.MarkPositionMessage;
import com.ivomens.mcheli.multiplay.net.SpotEnemyMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.ship.ShipEntity;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.core.math.Degrees;
import com.ivomens.mcheli.util.math.Rotations;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Team play helpers: spotting and marking for team mates, friendly fire rules and operator commands. */
public final class Multiplay {

    /** Spot filter bits, as configured on spotting weapons. */
    private static final int FILTER_ANIMAL = 1;
    private static final int FILTER_MONSTER = 2;
    private static final int FILTER_PLAYER = 4;
    private static final int FILTER_GROUND_VEHICLE = 8;
    private static final int FILTER_HELICOPTER = 16;
    private static final int FILTER_PLANE = 32;
    private static final double MARK_POINT_RANGE = 300.0;
    /** Height sent to clear the marked point; any mark at or above it clears. */
    public static final int CLEAR_MARK_Y = 1000;

    /** Rows: kind of target (see {@link #spotRow}); columns: spotter without team, spotter in a team. */
    private static final TargetType[][] ENTITY_SPOT_TABLE = new TargetType[][]{
            { TargetType.NONE, TargetType.NONE },
            { TargetType.OTHER_MOB, TargetType.OTHER_MOB },
            { TargetType.MONSTER, TargetType.MONSTER },
            { TargetType.NONE, TargetType.NO_TEAM_PLAYER },
            { TargetType.NONE, TargetType.SAME_TEAM_PLAYER },
            { TargetType.NONE, TargetType.OTHER_TEAM_PLAYER },
            { TargetType.NONE, TargetType.NONE },
            { TargetType.NONE, TargetType.NO_TEAM_PLAYER },
            { TargetType.NONE, TargetType.SAME_TEAM_PLAYER },
            { TargetType.NONE, TargetType.OTHER_TEAM_PLAYER }
    };

    /** Monsters are recognised by class name, which also catches modded mobs; cached per class. */
    private static final ClassValue<Boolean> IS_MONSTER_CLASS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return type.toString().toLowerCase(Locale.ROOT).contains("monster");
        }
    };

    private Multiplay() {
    }

    public static boolean canSpotEntityWithFilter(int filter, Entity entity) {
        if (entity instanceof PlaneEntity || entity instanceof ShipEntity) return (filter & FILTER_PLANE) != 0;
        if (entity instanceof HelicopterEntity) return (filter & FILTER_HELICOPTER) != 0;
        if (entity instanceof GroundVehicleEntity || entity instanceof TankEntity) {
            return (filter & FILTER_GROUND_VEHICLE) != 0;
        }
        if (entity instanceof Player) return (filter & FILTER_PLAYER) != 0;
        if (!(entity instanceof LivingEntity)) return false;
        return (filter & (isMonster(entity) ? FILTER_MONSTER : FILTER_ANIMAL)) != 0;
    }

    public static boolean isMonster(Entity entity) {
        return IS_MONSTER_CLASS.get(entity.getClass());
    }

    /**
     * How {@code target} shows up for {@code user}, or {@link TargetType#NONE} if it cannot be spotted.
     *
     * @param checkSee also require a clear line of sight from the given position
     */
    public static TargetType canSpotEntity(Entity user, double posX, double posY, double posZ, Entity target,
                                           boolean checkSee) {
        if (!(user instanceof LivingEntity spotter)) return TargetType.NONE;

        int column = spotter.getTeam() == null ? 0 : 1;
        TargetType type = ENTITY_SPOT_TABLE[spotRow(spotter, target)][column];
        if (checkSee && type != TargetType.NONE) {
            Vec3 from = new Vec3(posX, posY, posZ);
            Vec3 to = new Vec3(target.getX(), target.getY() + target.getEyeHeight(), target.getZ());
            BlockHitResult hit = target.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, target));
            if (hit.getType() == HitResult.Type.BLOCK) {
                type = TargetType.NONE;
            }
        }
        return type;
    }

    /** Row of {@link #ENTITY_SPOT_TABLE}; players and manned aircraft only count inside a team. */
    private static int spotRow(LivingEntity spotter, Entity target) {
        if (spotter.getTeam() == null) {
            if (target instanceof Player || target instanceof AircraftEntity) return 0;
            if (target instanceof LivingEntity) return isMonster(target) ? 2 : 1;
            return 0;
        }
        if (target instanceof Player player) {
            return spotter.isAlliedTo(player) ? 4 : 5;
        }
        if (target instanceof AircraftEntity ac) {
            Player pilot = ac.seats().firstPlayer();
            if (pilot == null) return 6;
            return spotter.isAlliedTo(pilot) ? 8 : 9;
        }
        if (target instanceof LivingEntity) return isMonster(target) ? 2 : 1;
        return 0;
    }

    /** Operator command: sends every player in the operator's dimension to his spawn point. */
    public static void jumpSpawnPoint(Player player) {
        DebugLog.log(false, "JumpSpawnPoint");
        if (!(player instanceof ServerPlayer operator) || !operator.hasPermissions(2)) return;
        MinecraftServer server = operator.server;
        for (ServerPlayer target : new ArrayList<>(server.getPlayerList().getPlayers())) {
            if (target.level() != operator.level()) continue;
            BlockPos spawn = target.getRespawnPosition();
            if (spawn == null || target.getRespawnDimension() != target.level().dimension()) {
                spawn = target.serverLevel().getSharedSpawnPos();
            }
            String command = String.format(Locale.ROOT, "tp %s %.1f %.1f %.1f", target.getGameProfile().getName(),
                    spawn.getX() + 0.5, spawn.getY() + 0.1, spawn.getZ() + 0.5);
            server.getCommands().performPrefixedCommand(operator.createCommandSourceStack(), command);
        }
    }

    /** Operator command: deals all online players round-robin into the existing teams at random. */
    public static void shuffleTeam(Player player) {
        Collection<PlayerTeam> teams = player.level().getScoreboard().getPlayerTeams();
        int teamCount = teams.size();
        DebugLog.log(false, "ShuffleTeam:%d teams", teamCount);
        if (teamCount <= 0 || !(player instanceof ServerPlayer operator) || !operator.hasPermissions(2)) return;

        MinecraftServer server = operator.server;
        List<String> players = new ArrayList<>(Arrays.asList(server.getPlayerList().getPlayerNamesArray()));
        Collections.shuffle(players);
        List<String> teamNames = new ArrayList<>();
        for (PlayerTeam team : teams) {
            teamNames.add(team.getName());
        }
        Collections.shuffle(teamNames);

        // One command per player: "team join" takes a single name (or a selector), not a list.
        for (int i = 0; i < players.size(); i++) {
            String command = "team join " + teamNames.get(i % teamCount) + " " + players.get(i);
            DebugLog.log(false, "ShuffleTeam:" + command);
            server.getCommands().performPrefixedCommand(operator.createCommandSourceStack(), command);
        }
    }

    /**
     * Spots every matching entity inside a cone in front of {@code player} and shares them with his
     * team for {@code markTime} seconds. Server side only.
     *
     * @return whether anything was spotted
     */
    public static boolean spotEntity(LivingEntity player, float roll, double posX, double posY,
                                     double posZ, int targetFilter, float spotLength, int markTime, float angle) {
        if (player.level().isClientSide()) return false;

        Vec3 look = Rotations.rotate(0.0, 0.0, 1.0, -player.getYRot(), -player.getXRot(), -roll);
        List<Entity> candidates = player.level().getEntities(player, player.getBoundingBox().inflate(spotLength),
                entity -> canSpotEntityWithFilter(targetFilter, entity));
        IntArrayList spotted = new IntArrayList();
        double maxDistSq = spotLength * spotLength;
        float maxAngle = angle * 2.0F;

        for (Entity entity : candidates) {
            TargetType type = canSpotEntity(player, posX, posY, posZ, entity, true);
            if (type == TargetType.NONE || type == TargetType.SAME_TEAM_PLAYER) continue;
            double distSq = entity.distanceToSqr(posX, posY, posZ);
            if (distSq <= 1.0 || distSq >= maxDistSq) continue;

            double dx = entity.getX() - posX;
            double dy = entity.getY() - posY;
            double dz = entity.getZ() - posZ;
            double horizontalAngle = Degrees.between(look.x, look.z, dx, dz);
            double verticalAngle = Math.abs(Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))) + player.getXRot());
            if (horizontalAngle < maxAngle && verticalAngle < maxAngle) {
                spotted.add(entity.getId());
            }
        }

        if (spotted.isEmpty()) return false;
        sendSpottedEntitiesToTeam(player, markTime, spotted.toIntArray());
        return true;
    }

    private static void sendSpottedEntitiesToTeam(LivingEntity player, int count, int[] entityId) {
        MinecraftServer server = Services.PLATFORM.getServer();
        if (server == null) return;
        for (ServerPlayer notifyPlayer : server.getPlayerList().getPlayers()) {
            if (player == notifyPlayer || player.isAlliedTo(notifyPlayer)) {
                if (entityId.length > 0 && count > 0) {
                    Network.sendToPlayer(notifyPlayer, SpotEnemyMessage.of(count, entityId));
                }
            }
        }
    }

    /**
     * Marks the block the player looks at for his team, or clears the mark if he looks at nothing.
     *
     * @return whether a block was marked
     */
    public static boolean markPoint(Player player, double posX, double posY, double posZ) {
        Vec3 from = new Vec3(posX, posY, posZ);
        Vec3 look = Rotations.direction(player.getYRot(), player.getXRot());
        Vec3 to = from.add(look.x * MARK_POINT_RANGE, look.y * MARK_POINT_RANGE, look.z * MARK_POINT_RANGE);
        BlockHitResult hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            sendMarkPointToSameTeam(player, 0, CLEAR_MARK_Y, 0);
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        sendMarkPointToSameTeam(player, pos.getX(), pos.getY() + 2, pos.getZ());
        return true;
    }

    public static void sendMarkPointToSameTeam(Player player, int x, int y, int z) {
        MinecraftServer server = Services.PLATFORM.getServer();
        if (server == null) return;
        for (ServerPlayer notifyPlayer : server.getPlayerList().getPlayers()) {
            if (player == notifyPlayer || player.isAlliedTo(notifyPlayer)) {
                Network.sendToPlayer(notifyPlayer, new MarkPositionMessage(new BlockPos(x, y, z)));
            }
        }
    }
}
