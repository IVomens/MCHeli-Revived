package com.ivomens.mcheli.multiplay.client;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.multiplay.net.DestroyAllAircraftMessage;
import com.ivomens.mcheli.multiplay.net.JumpToSpawnPointMessage;
import com.ivomens.mcheli.multiplay.net.RunCommandMessage;
import com.ivomens.mcheli.multiplay.net.ShuffleTeamsMessage;
import com.ivomens.mcheli.multiplay.net.TogglePvpMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.McScreen;
import com.ivomens.mcheli.ui.client.Toggle;
import com.ivomens.mcheli.ui.client.Widgets;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * The multiplayer manager: operator commands beside the team columns (PVP, team shuffle, a new team,
 * the spawn jump, destroying every vehicle), and a page for creating a team with a name, a colour and
 * friendly fire, sent as vanilla team commands. The server checks every command.
 */
public final class MultiplayManagerScreen extends McScreen {

    private enum Page { MAIN, CREATE_TEAM }

    private static final ChatFormatting[] COLORS = {ChatFormatting.RESET, ChatFormatting.BLACK,
            ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA, ChatFormatting.DARK_RED,
            ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY, ChatFormatting.DARK_GRAY,
            ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.RED,
            ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW};
    private static final ResourceLocation TEAM_PANEL = ResourceLocation.fromNamespaceAndPath(Ids.MOD_ID,
            "textures/gui/mp_new_team.png");
    private static final int TEAM_PANEL_WIDTH = 222;
    private static final int TEAM_PANEL_HEIGHT = 200;
    private static final int MAX_NAME_LENGTH = 16;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MIN_BUTTON_WIDTH = 80;
    private static final int BUTTONS_TOP = 80;

    private Page page = Page.MAIN;
    private int teamCount;
    private Toggle pvp;
    private Button createTeam;
    private EditBox teamName;
    private int colorIndex;
    private boolean friendlyFire = true;

    public MultiplayManagerScreen() {
        super(Component.translatable("mcheli.multiplay.title"));
    }

    @Override
    protected void build() {
        this.teamCount = this.minecraft.level.getScoreboard().getPlayerTeams().size();
        if (this.page == Page.MAIN) {
            this.buildMain();
        } else {
            this.buildCreateTeam();
        }
    }

    private void buildMain() {
        int width = Math.max(MIN_BUTTON_WIDTH, TeamList.columnWidth(this.minecraft) * 3 / 4);
        Layout buttons = this.column(TeamList.columnLeft(this.minecraft, this.teamCount + 1, 0) / 4, BUTTONS_TOP);
        this.pvp = buttons.add(Widgets.toggle(Component.translatable("mcheli.multiplay.pvp"), width, BUTTON_HEIGHT,
                () -> McheliConfig.server().enablePVP(), enabled -> Network.sendToServer(new TogglePvpMessage())));
        buttons.add(this.command("shuffle", width, () -> Network.sendToServer(new ShuffleTeamsMessage())));
        buttons.add(Widgets.button(Component.translatable("mcheli.multiplay.new_team"), width, BUTTON_HEIGHT,
                () -> this.show(Page.CREATE_TEAM)));
        buttons.add(this.command("jump_spawn", width, () -> Network.sendToServer(new JumpToSpawnPointMessage())));
        buttons.add(this.command("destroy_all", width, () -> Network.sendToServer(new DestroyAllAircraftMessage())));
    }

    private Button command(String name, int width, Runnable onPress) {
        return Widgets.button(Component.translatable("mcheli.multiplay." + name),
                Component.translatable("mcheli.multiplay." + name + ".tooltip"), width, BUTTON_HEIGHT, onPress);
    }

    private void buildCreateTeam() {
        int x = this.width / 2;
        int y = this.height / 2;
        this.teamName = this.add(Widgets.text(160, BUTTON_HEIGHT, MAX_NAME_LENGTH,
                Component.translatable("mcheli.multiplay.team_name")));
        this.teamName.setPosition(x - 80, y - 55);
        this.teamName.setTextColor(-1);
        this.setInitialFocus(this.teamName);

        this.add(Widgets.button(Component.literal("<"), 40, BUTTON_HEIGHT, () -> this.cycleColor(-1)))
                .setPosition(x - 80, y - 20);
        this.add(Widgets.button(Component.literal(">"), 40, BUTTON_HEIGHT, () -> this.cycleColor(1)))
                .setPosition(x + 40, y - 20);
        this.add(Widgets.toggle(Component.translatable("mcheli.multiplay.friendly_fire"), 160, BUTTON_HEIGHT,
                () -> this.friendlyFire, value -> this.friendlyFire = value)).setPosition(x - 80, y + 20);
        Layout actions = this.row(x - 80, y + 60);
        this.createTeam = actions.add(Widgets.button(Component.translatable("mcheli.multiplay.ok"), 80, BUTTON_HEIGHT, () -> {
            this.createTeam(this.teamName.getValue());
            this.show(Page.MAIN);
        }));
        actions.add(Widgets.button(Component.translatable("gui.cancel"), 80, BUTTON_HEIGHT, () -> this.show(Page.MAIN)));
        this.updateCreateButton();
    }

    private void show(Page target) {
        this.page = target;
        this.rebuild();
    }

    private void cycleColor(int step) {
        this.colorIndex = Math.floorMod(this.colorIndex + step, COLORS.length);
    }

    private void createTeam(String name) {
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || name.contains(" ")) {
            return;
        }
        String color = COLORS[this.colorIndex].getName().toLowerCase(Locale.ROOT);
        sendCommand("team add " + name);
        sendCommand("team modify " + name + " color " + color);
        sendCommand("team modify " + name + " friendlyFire " + this.friendlyFire);
    }

    private static void sendCommand(String command) {
        Network.sendToServer(new RunCommandMessage(command));
    }

    private void updateCreateButton() {
        String name = this.teamName.getValue();
        this.createTeam.active = !name.isEmpty() && name.length() <= MAX_NAME_LENGTH;
    }

    /** The layout depends on the number of team columns. */
    @Override
    public void tick() {
        if (this.minecraft.level.getScoreboard().getPlayerTeams().size() != this.teamCount) {
            this.rebuild();
        } else if (this.page == Page.MAIN) {
            this.pvp.refresh();
        } else {
            this.updateCreateButton();
        }
    }

    /** A focused team name takes every key, except Escape, which goes back a page or closes. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.page == Page.CREATE_TEAM) {
                this.show(Page.MAIN);
            } else {
                this.onClose();
            }
            return true;
        }
        if (this.getFocused() instanceof EditBox box) {
            box.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** The team columns are drawn over the world without darkening it. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderContents(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        TeamList.draw(graphics, this.minecraft, this.font, true);
        // Text is batched; drawn now, the team panel covers the columns' names.
        graphics.flush();
        if (this.page == Page.MAIN) {
            return;
        }
        int x = this.width / 2;
        int y = this.height / 2;
        graphics.blit(TEAM_PANEL, (this.width - TEAM_PANEL_WIDTH) / 2, (this.height - TEAM_PANEL_HEIGHT) / 2, 0, 0,
                TEAM_PANEL_WIDTH, TEAM_PANEL_HEIGHT);
        graphics.drawCenteredString(this.font, Component.translatable("mcheli.multiplay.create_team"), x, y - 85, -1);
        graphics.drawCenteredString(this.font, Component.translatable("mcheli.multiplay.team_name"), x, y - 70, -1);
        graphics.drawCenteredString(this.font, Component.translatable("mcheli.multiplay.team_color")
                .withStyle(COLORS[this.colorIndex]), x, y - 13, -1);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
