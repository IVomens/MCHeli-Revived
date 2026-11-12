package com.ivomens.mcheli.bench;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;

/**
 * What the in-game benchmark changes on the server, as it was before: the time of day, the weather and
 * the rules that would change them or spawn mobs during the run, and the player's game mode, flight and
 * place. {@link #prepare} makes the scene still; {@link #restore} puts everything back.
 */
record WorldState(long dayTime, boolean daylightCycle, boolean weatherCycle, boolean mobSpawning, int clearTime,
                  int rainTime, int thunderTime, boolean raining, boolean thundering, GameType gameMode,
                  boolean mayFly, boolean flying, Vec3 position, float yaw, float pitch) {

    private static final long NOON = 6000L;

    static WorldState capture(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        GameRules rules = level.getGameRules();
        ServerLevelData data = (ServerLevelData) level.getLevelData();
        return new WorldState(level.getDayTime(), rules.getBoolean(GameRules.RULE_DAYLIGHT),
                rules.getBoolean(GameRules.RULE_WEATHER_CYCLE), rules.getBoolean(GameRules.RULE_DOMOBSPAWNING),
                data.getClearWeatherTime(), data.getRainTime(), data.getThunderTime(), data.isRaining(),
                data.isThundering(), player.gameMode.getGameModeForPlayer(), player.getAbilities().mayfly,
                player.getAbilities().flying, player.position(), player.getYRot(), player.getXRot());
    }

    /** A still, clear noon with no mob spawns, and a creative player who flies. */
    static void prepare(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        GameRules rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.setDayTime(NOON);
        level.setWeatherParameters(Integer.MAX_VALUE / 2, 0, false, false);
        if (player.gameMode.getGameModeForPlayer() != GameType.CREATIVE) {
            player.setGameMode(GameType.CREATIVE);
        }
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    void restore(ServerPlayer player) {
        restoreWorld(player.serverLevel());
        player.stopRiding();
        player.teleportTo(player.serverLevel(), this.position.x, this.position.y, this.position.z, this.yaw, this.pitch);
        if (player.gameMode.getGameModeForPlayer() != this.gameMode) {
            player.setGameMode(this.gameMode);
        }
        player.getAbilities().mayfly = this.mayFly || this.gameMode.isCreative() || this.gameMode == GameType.SPECTATOR;
        player.getAbilities().flying = this.flying;
        player.onUpdateAbilities();
    }

    void restoreWorld(ServerLevel level) {
        GameRules rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(this.daylightCycle, level.getServer());
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(this.weatherCycle, level.getServer());
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(this.mobSpawning, level.getServer());
        level.setDayTime(this.dayTime);
        level.setWeatherParameters(this.clearTime, this.raining ? this.rainTime : 0, this.raining, this.thundering);
    }
}
