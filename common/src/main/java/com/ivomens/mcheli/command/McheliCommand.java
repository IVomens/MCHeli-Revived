package com.ivomens.mcheli.command;

import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.combat.weapon.Weapons;
import com.ivomens.mcheli.config.DebugSettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.content.registry.ContentRegistry;
import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.content.registry.LoadedContent;
import com.ivomens.mcheli.content.source.ContentFormat;
import com.ivomens.mcheli.equipment.throwable.Throwables;
import com.ivomens.mcheli.hud.HudManager;
import com.ivomens.mcheli.multiplay.OperatorRequests;
import com.ivomens.mcheli.multiplay.net.ModListRequestMessage;
import com.ivomens.mcheli.multiplay.net.ScreenshotRequestMessage;
import com.ivomens.mcheli.multiplay.net.ServerSettingsSync;
import com.ivomens.mcheli.multiplay.net.TitleMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.util.ModLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * {@code /mcheli <subcommand>}. Every subcommand requires permission level 2 or an entry for the
 * player in the config's per-command permission list, and the whole command can be disabled in
 * the config.
 */
public final class McheliCommand {

    private static final String CMD_GET_SS = "sendss";
    private static final String CMD_MOD_LIST = "modlist";
    private static final String CMD_RECONFIG = "reconfig";
    private static final String CMD_TITLE = "title";
    private static final String CMD_FILL = "fill";
    private static final String CMD_STATUS = "status";
    private static final String CMD_KILL_ENTITY = "killentity";
    private static final String CMD_REMOVE_ENTITY = "removeentity";
    private static final String CMD_ATTACK_ENTITY = "attackentity";
    private static final String CMD_SHOW_BB = "showboundingbox";
    private static final String CMD_DELAY_BB = "delayhitbox";
    private static final String CMD_LIST = "list";
    private static final String CMD_DUMP_YAML = "dumpyaml";
    private static final String[] ALL_COMMANDS = {
            CMD_GET_SS, CMD_MOD_LIST, CMD_RECONFIG, CMD_TITLE, CMD_FILL, CMD_STATUS, CMD_KILL_ENTITY,
            CMD_REMOVE_ENTITY, CMD_ATTACK_ENTITY, CMD_SHOW_BB, CMD_LIST, CMD_DELAY_BB, CMD_DUMP_YAML
    };
    private static final Pattern UNSAFE_FILE_NAME_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");
    /** Same cap as the vanilla fill command used to have. */
    private static final int FILL_LIMIT = 3000000;
    private static final String[] DAMAGE_NAMES = {
            "player", "inFire", "onFire", "lava", "inWall", "drown", "starve", "cactus", "fall", "outOfWorld",
            "generic", "magic", "wither", "anvil", "fallingBlock"
    };

    private McheliCommand() {}

    /** Whether the entity may use the aircraft command screen. */
    public static boolean checkCommandPermission(CommandSourceStack source, String cmd) {
        if (source.hasPermission(2)) {
            return true;
        }
        return source.getEntity() instanceof Player player && !cmd.isEmpty()
                && McheliConfig.commands().allows(cmd, player.getGameProfile().getName());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sub(String name) {
        return Commands.literal(name)
                .requires(s -> McheliConfig.commands().enabled() && checkCommandPermission(s, name));
    }

    private static void msg(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("mcheli")
                .requires(s -> McheliConfig.commands().enabled());
        root.then(screenshotCommand())
                .then(modListCommand())
                .then(reconfigCommand())
                .then(titleCommand(ctx))
                .then(fillCommand(ctx))
                .then(statusCommand())
                .then(sub(CMD_KILL_ENTITY).then(Commands.argument("class", StringArgumentType.word())
                        .executes(c -> executeForEntities(c, Entity::kill, "killed"))))
                .then(sub(CMD_REMOVE_ENTITY).then(Commands.argument("class", StringArgumentType.word())
                        .executes(c -> executeForEntities(c, Entity::discard, "removed"))))
                .then(attackEntityCommand())
                .then(sub(CMD_SHOW_BB).then(Commands.argument("enable", BoolArgumentType.bool())
                        .executes(c -> showBoundingBoxes(c.getSource(), BoolArgumentType.getBool(c, "enable")))))
                .then(sub(CMD_LIST).executes(c -> {
                    msg(c.getSource(), "/mcheli command list : " + String.join(", ", ALL_COMMANDS));
                    return 1;
                }))
                .then(hitboxDelayCommand())
                .then(dumpYamlCommand());
        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> screenshotCommand() {
        return sub(CMD_GET_SS).then(Commands.argument("player", EntityArgument.player())
                .executes(c -> {
                    ServerPlayer player = EntityArgument.getPlayer(c, "player");
                    OperatorRequests.of(c.getSource().getServer()).requestScreenshot(player);
                    Network.sendToPlayer(player, new ScreenshotRequestMessage());
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> modListCommand() {
        return sub(CMD_MOD_LIST).then(Commands.argument("players", EntityArgument.players())
                .executes(c -> {
                    ServerPlayer requester = c.getSource().getPlayer();
                    OperatorRequests requests = OperatorRequests.of(c.getSource().getServer());
                    int count = 0;
                    for (ServerPlayer player : EntityArgument.getPlayers(c, "players")) {
                        int requestId = requests.requestModList(requester);
                        Network.sendToPlayer(player, new ModListRequestMessage(requestId));
                        count++;
                    }
                    return count;
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reconfigCommand() {
        return sub(CMD_RECONFIG).executes(c -> {
            McheliConfig.reload();
            ServerSettingsSync.sendToAll();
            msg(c.getSource(), LocalClient.get().isSingleplayer() ? "Reload mcheli.cfg" : "Reload server side mcheli.cfg");
            return 1;
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> titleCommand(CommandBuildContext ctx) {
        return sub(CMD_TITLE).then(Commands.argument("time", IntegerArgumentType.integer(1, 180))
                .then(Commands.argument("position", IntegerArgumentType.integer(0, 5))
                        .then(Commands.argument("message", ComponentArgument.textComponent(ctx))
                                .executes(c -> {
                                    int showTime = IntegerArgumentType.getInteger(c, "time");
                                    int pos = IntegerArgumentType.getInteger(c, "position");
                                    Component text = ComponentArgument.getComponent(c, "message");
                                    Network.sendToAll(new TitleMessage(text, 20 * showTime, pos));
                                    return 1;
                                }))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> fillCommand(CommandBuildContext ctx) {
        return sub(CMD_FILL).then(Commands.argument("from", BlockPosArgument.blockPos())
                .then(Commands.argument("to", BlockPosArgument.blockPos())
                        .then(Commands.argument("block", BlockStateArgument.block(ctx))
                                .executes(c -> executeFill(c, "replace"))
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"replace", "destroy", "keep", "override"}, b))
                                        .executes(c -> executeFill(c, StringArgumentType.getString(c, "mode")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> statusCommand() {
        return sub(CMD_STATUS)
                .then(Commands.literal("entity")
                        .executes(c -> executeStatusEntity(c.getSource(), 0))
                        .then(Commands.argument("min", IntegerArgumentType.integer(0))
                                .executes(c -> executeStatusEntity(c.getSource(), IntegerArgumentType.getInteger(c, "min")))))
                .then(Commands.literal("tile")
                        .executes(c -> executeStatusTile(c.getSource(), 0))
                        .then(Commands.argument("min", IntegerArgumentType.integer(0))
                                .executes(c -> executeStatusTile(c.getSource(), IntegerArgumentType.getInteger(c, "min")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> attackEntityCommand() {
        return sub(CMD_ATTACK_ENTITY).then(Commands.argument("class", StringArgumentType.word())
                .then(Commands.argument("damage", FloatArgumentType.floatArg())
                        .executes(c -> executeAttackEntity(c, ""))
                        .then(Commands.argument("source", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(DAMAGE_NAMES, b))
                                .executes(c -> executeAttackEntity(c, StringArgumentType.getString(c, "source"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> hitboxDelayCommand() {
        return sub(CMD_DELAY_BB)
                .executes(c -> showHitboxDelay(c.getSource()))
                .then(Commands.argument("ticks", IntegerArgumentType.integer(0, DebugSettings.MAX_HIT_BOX_DELAY))
                        .executes(c -> setHitboxDelay(c.getSource(), IntegerArgumentType.getInteger(c, "ticks"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> dumpYamlCommand() {
        return sub(CMD_DUMP_YAML)
                .executes(c -> handleDumpYaml(c.getSource(), "mcheli_yaml_dump"))
                .then(Commands.argument("outputDir", StringArgumentType.string())
                        .executes(c -> handleDumpYaml(c.getSource(), StringArgumentType.getString(c, "outputDir"))));
    }

    private static int showBoundingBoxes(CommandSourceStack source, boolean enable) {
        McheliConfig.update(config -> config.with(DebugSettings.DEBUG_BOUNDING_BOX, enable));
        ServerSettingsSync.sendToAll();
        msg(source, enable ? "Enabled bounding box [F3 + b]" : "Disabled bounding box");
        McheliConfig.save();
        return 1;
    }

    private static int setHitboxDelay(CommandSourceStack source, int ticks) {
        McheliConfig.update(config -> config.with(DebugSettings.HIT_BOX_DELAY_TICK, ticks));
        McheliConfig.save();
        return showHitboxDelay(source);
    }

    private static int showHitboxDelay(CommandSourceStack source) {
        msg(source, "Current delay of hitbox = " + McheliConfig.debug().hitBoxDelayTick() + " [0 ~ "
                + DebugSettings.MAX_HIT_BOX_DELAY + "]");
        return 1;
    }

    /** Writes every non-YAML, non-builtin content definition back out as YAML files. */
    private static int handleDumpYaml(CommandSourceStack source, String outDir) throws CommandSyntaxException {
        Path base = Paths.get(outDir);
        int written = 0;
        try {
            Files.createDirectories(base);
            written += dump(base, ContentType.HELICOPTER, VehicleTypes.registry(VehicleKind.HELICOPTER));
            written += dump(base, ContentType.PLANE, VehicleTypes.registry(VehicleKind.PLANE));
            written += dump(base, ContentType.SHIP, VehicleTypes.registry(VehicleKind.SHIP));
            written += dump(base, ContentType.TANK, VehicleTypes.registry(VehicleKind.TANK));
            written += dump(base, ContentType.VEHICLE, VehicleTypes.registry(VehicleKind.VEHICLE));
            written += dump(base, ContentType.WEAPON, Weapons.registry());
            written += dump(base, ContentType.THROWABLE, Throwables.registry());
            written += dump(base, ContentType.HUD, HudManager.registry());
            msg(source, "Dumped " + written + " YAML files to " + base.toAbsolutePath());
        } catch (IOException e) {
            throw new SimpleCommandExceptionType(Component.literal("Failed to dump YAML: " + e.getMessage())).create();
        }
        return written;
    }

    private static boolean isDumpable(ContentEntry<?> entry) {
        String format = entry.format();
        return !format.equals(ContentFormat.BUILTIN) && !format.equals(ContentFormat.YAML);
    }

    private static int dump(Path base, ContentType type, ContentRegistry<?, ?> registry) throws IOException {
        Path dir = base.resolve(type.dirName);
        Files.createDirectories(dir);
        ContentFormat<?> format = ContentFormat.of(type);
        int count = 0;
        for (LoadedContent<?, ?> loaded : registry.loaded()) {
            ContentEntry<?> entry = loaded.entry();
            if (!isDumpable(entry)) {
                continue;
            }
            try {
                String fileName = UNSAFE_FILE_NAME_CHARS.matcher(entry.name()).replaceAll("_") + ".yml";
                Files.writeString(dir.resolve(fileName), format.emit(entry));
                count++;
            } catch (RuntimeException | IOException error) {
                ModLogger.error("Failed to dump {} (class: {}): {}", entry.name(),
                        loaded.value().getClass().getSimpleName(),
                        String.valueOf(error.getCause() != null ? error.getCause() : error.getMessage()));
            }
        }
        return count;
    }

    /** Every non-player entity of the level whose class name contains the given text (case-insensitive). */
    private static List<Entity> matchingEntities(CommandSourceStack source, String className) {
        String needle = className.toLowerCase(Locale.ROOT);
        List<Entity> matches = new ArrayList<>();
        for (Entity entity : source.getLevel().getAllEntities()) {
            if (entity != null && !(entity instanceof Player) && entity.getClass().getName().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(entity);
            }
        }
        return matches;
    }

    private static int executeForEntities(CommandContext<CommandSourceStack> c, Consumer<Entity> action, String verb) {
        String className = StringArgumentType.getString(c, "class");
        List<Entity> matches = matchingEntities(c.getSource(), className);
        matches.forEach(action);
        msg(c.getSource(), matches.size() + " entity " + verb + "(" + className + ").");
        return matches.size();
    }

    private static int executeAttackEntity(CommandContext<CommandSourceStack> c, String damageName) {
        CommandSourceStack source = c.getSource();
        String className = StringArgumentType.getString(c, "class");
        float damage = FloatArgumentType.getFloat(c, "damage");
        DamageSources ds = source.getLevel().damageSources();
        DamageSource damageSource = switch (damageName.toLowerCase(Locale.ROOT)) {
            case "player" -> source.getEntity() instanceof Player p ? ds.playerAttack(p) : ds.generic();
            case "anvil" -> ds.anvil(null);
            case "cactus" -> ds.cactus();
            case "drown" -> ds.drown();
            case "fall" -> ds.fall();
            case "fallingblock" -> ds.fallingBlock(null);
            case "infire" -> ds.inFire();
            case "inwall" -> ds.inWall();
            case "lava" -> ds.lava();
            case "magic" -> ds.magic();
            case "onfire" -> ds.onFire();
            case "starve" -> ds.starve();
            case "wither" -> ds.wither();
            case "outofworld" -> ds.fellOutOfWorld();
            default -> ds.generic();
        };

        int attacked = 0;
        for (Entity entity : matchingEntities(source, className)) {
            entity.hurt(damageSource, damage);
            attacked++;
        }
        msg(source, attacked + " entity attacked(" + className + ", damage=" + damage + ").");
        return attacked;
    }

    private static int executeStatusEntity(CommandSourceStack source, int minCount) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Entity entity : source.getLevel().getAllEntities()) {
            counts.merge(entity.getClass().getName(), 1, Integer::sum);
        }
        return printClassCounts(source, minCount, "Server loaded Entity List", counts);
    }

    private static int executeStatusTile(CommandSourceStack source, int minCount) {
        Map<String, Integer> counts = new TreeMap<>();
        for (ChunkHolder holder : source.getLevel().getChunkSource().chunkMap.getChunks()) {
            LevelChunk chunk = holder.getTickingChunk();
            if (chunk != null) {
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    counts.merge(blockEntity.getClass().getName(), 1, Integer::sum);
                }
            }
        }
        return printClassCounts(source, minCount, "Server loaded Tile Entity List", counts);
    }

    /** Prints (and logs) every class with at least {@code minCount} instances, sorted by name. */
    private static int printClassCounts(CommandSourceStack source, int minCount, String title, Map<String, Integer> counts) {
        msg(source, "--- " + title + " ---");
        boolean any = false;
        for (Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= minCount) {
                String line = " " + entry.getKey() + " : " + entry.getValue();
                ModLogger.log(line);
                msg(source, line);
                any = true;
            }
        }
        if (!any) {
            msg(source, "none");
        }
        return counts.size();
    }

    private static int executeFill(CommandContext<CommandSourceStack> c, String mode) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        ServerLevel level = source.getLevel();
        BlockPos a = BlockPosArgument.getBlockPos(c, "from");
        BlockPos b = BlockPosArgument.getBlockPos(c, "to");
        BlockInput input = BlockStateArgument.getBlock(c, "block");

        int x1 = Math.min(a.getX(), b.getX()), x2 = Math.max(a.getX(), b.getX());
        int y1 = Math.min(a.getY(), b.getY()), y2 = Math.max(a.getY(), b.getY());
        int z1 = Math.min(a.getZ(), b.getZ()), z2 = Math.max(a.getZ(), b.getZ());

        if (y1 < level.getMinBuildHeight() || y2 >= level.getMaxBuildHeight()) {
            throw new SimpleCommandExceptionType(Component.translatable("argument.pos.outofworld")).create();
        }
        long blockNum = (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        if (blockNum > FILL_LIMIT) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.fill.toobig", FILL_LIMIT, blockNum)).create();
        }

        int changed = fillLoaded(level, input, mode, BoundingBox.fromCorners(a, b));

        if (changed == 0) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.fill.failed")).create();
        }
        int total = changed;
        source.sendSuccess(() -> Component.translatable("commands.fill.success", total), true);
        return total;
    }

    /**
     * Places the block in every loaded position of the box ({@code keep}: only into air, {@code override}:
     * not into air, {@code destroy}: breaking what was there); nothing is loaded for the fill.
     */
    private static int fillLoaded(ServerLevel level, BlockInput input, String mode, BoundingBox box) {
        boolean keep = mode.equals("keep");
        boolean destroy = mode.equals("destroy");
        boolean override = mode.equals("override");
        int changed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    pos.set(x, y, z);
                    if (!level.hasChunkAt(pos) || (level.isEmptyBlock(pos) ? override : keep)) {
                        continue;
                    }
                    if (destroy) {
                        level.destroyBlock(pos, false);
                    }
                    Clearable.tryClear(level.getBlockEntity(pos));
                    if (input.place(level, pos, Block.UPDATE_ALL)) {
                        changed++;
                    }
                }
            }
        }
        return changed;
    }
}
