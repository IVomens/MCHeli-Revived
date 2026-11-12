package com.ivomens.mcheli.multiplay.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The players in one column per team, with each team's friendly fire setting, the list objective's
 * scores and pings. Held on the scoreboard key it is the in-game player list; the multiplayer manager
 * draws it beside its buttons.
 */
public final class TeamList {

    private static final ResourceLocation[] PING = {
            ResourceLocation.withDefaultNamespace("icon/ping_5"), ResourceLocation.withDefaultNamespace("icon/ping_4"),
            ResourceLocation.withDefaultNamespace("icon/ping_3"), ResourceLocation.withDefaultNamespace("icon/ping_2"),
            ResourceLocation.withDefaultNamespace("icon/ping_1"), ResourceLocation.withDefaultNamespace("icon/ping_unknown")};
    private static final int PING_UNKNOWN = 5;
    private static final int[] PING_LIMITS = {150, 300, 600, 1000};
    private static final int ROW_HEIGHT = 9;
    private static final int MAX_COLUMN_WIDTH = 150;
    private static final int COLUMN_BACKGROUND = 0x80000000;
    private static final int ROW_BACKGROUND = 0x20FFFFFF;
    /** Teams sorted by name, with "no team" ({@code null}) first. */
    private static final Comparator<PlayerTeam> TEAM_ORDER =
            Comparator.nullsFirst(Comparator.comparing(PlayerTeam::getName));

    private TeamList() {
    }

    public static int columnWidth(Minecraft mc) {
        int availableWidth = mc.getWindow().getGuiScaledWidth() - 40;
        int width = availableWidth * 3 / 4 / (mc.level.getScoreboard().getPlayerTeams().size() + 1);
        return Math.min(width, MAX_COLUMN_WIDTH);
    }

    public static int columnLeft(Minecraft mc, int columnCount, int column) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        return (int) ((double) screenWidth / 2 + (columnWidth(mc) + 10) * (-columnCount / 2.0 + column));
    }

    /**
     * Draws one column per team.
     *
     * @param manager leave the leftmost column free for the manager's buttons
     */
    public static void draw(GuiGraphics g, Minecraft mc, Font font, boolean manager) {
        if (mc.level == null || mc.player == null || mc.getConnection() == null) return;
        List<PlayerTeam> teams = new ArrayList<>();
        teams.add(null);
        teams.addAll(mc.level.getScoreboard().getPlayerTeams());
        teams.sort(TEAM_ORDER);
        List<PlayerInfo> players = new ArrayList<>(mc.getConnection().getOnlinePlayers());

        int offset = manager ? 1 : 0;
        for (int i = 0; i < teams.size(); i++) {
            drawColumn(g, mc, font, players, teams.get(i), i + offset, teams.size() + offset);
        }
    }

    private static void drawColumn(GuiGraphics g, Minecraft mc, Font font, List<PlayerInfo> players, PlayerTeam team,
                                   int column, int columnCount) {
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
        int rows = Math.max((players.size() / 5 + 1) * 5, 10);
        int width = columnWidth(mc);
        int left = columnLeft(mc, columnCount, column);
        int top = mc.getWindow().getGuiScaledHeight() / 2 - (rows * ROW_HEIGHT + 10) / 2;

        g.fill(left - 1, top - 1 - 2 * ROW_HEIGHT, left + width, top + ROW_HEIGHT * rows, COLUMN_BACKGROUND);
        Component name = team == null ? Component.translatable("mcheli.multiplay.no_team") : Component.literal(team.getName());
        Component teamName = PlayerTeam.formatNameForTeam(team, name);
        g.drawString(font, teamName, left + width / 2 - font.width(teamName) / 2, top - 2 * ROW_HEIGHT, -1);
        Component friendlyFire = Component.translatable("mcheli.multiplay.friendly_fire_state",
                CommonComponents.optionStatus(team == null || team.isAllowFriendlyFire()));
        g.drawString(font, friendlyFire, left + width / 2 - font.width(friendlyFire) / 2, top - ROW_HEIGHT, -1);

        int line = 0;
        for (int i = 0; i < rows; i++) {
            int rowY = top + i * ROW_HEIGHT;
            g.fill(left, rowY, left + width - 1, rowY + 8, ROW_BACKGROUND);
            if (i >= players.size()) continue;

            PlayerInfo info = players.get(i);
            String player = info.getProfile().getName();
            PlayerTeam playerTeam = scoreboard.getPlayersTeam(player);
            boolean inThisTeam = playerTeam == null ? team == null : team != null && playerTeam.isAlliedTo(team);
            if (!inThisTeam) continue;

            int y = top + line * ROW_HEIGHT;
            line++;
            g.drawString(font, player, left, y, -1);
            int pingX = left + width - 12;
            if (objective != null) {
                drawScore(g, font, scoreboard, objective, player, left + font.width(player) + 5, pingX - 5, y);
            }
            g.blitSprite(PING[pingIcon(info.getLatency())], pingX, y, 10, 8);
        }
    }

    private static void drawScore(GuiGraphics g, Font font, Scoreboard scoreboard, Objective objective, String player,
                                  int nameEnd, int scoreEnd, int y) {
        if (scoreEnd - nameEnd > 5) {
            int points = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(player), objective).get();
            String score = ChatFormatting.YELLOW + "" + points;
            g.drawString(font, score, scoreEnd - font.width(score), y, 0xFFFFFF);
        }
    }

    private static int pingIcon(int latency) {
        if (latency < 0) {
            return PING_UNKNOWN;
        }
        for (int icon = 0; icon < PING_LIMITS.length; icon++) {
            if (latency < PING_LIMITS[icon]) {
                return icon;
            }
        }
        return PING_LIMITS.length;
    }
}
