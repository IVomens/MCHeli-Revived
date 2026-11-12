package com.ivomens.mcheli.content.command;

import com.ivomens.mcheli.content.pack.AddonManager;
import com.ivomens.mcheli.content.pack.AddonPack;
import com.ivomens.mcheli.content.registry.ContentValidation;
import com.ivomens.mcheli.content.report.ContentLog;
import com.ivomens.mcheli.content.report.ContentReport;
import com.ivomens.mcheli.content.report.Diagnostic;
import com.ivomens.mcheli.content.report.Diagnostic.Severity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Optional;

/**
 * {@code /mcheli validate [pack]}, for operators: reads the loaded content packs again from disk, as the game read
 * them at start-up, and shows how many problems of each kind they have, the worst few, and where the full report
 * is ({@code logs/mcheli-validate.log}). Loaded content does not change; see {@link ContentValidation}.
 */
public final class ValidateCommand {

    /** Messages shown in chat; the report has every one. */
    private static final int SHOWN = 8;
    /** Characters of a message shown in chat. */
    private static final int MESSAGE_LENGTH = 240;
    private static final int OPERATOR = 2;

    private ValidateCommand() {
    }

    /** Adds {@code validate} to the {@code /mcheli} command. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mcheli").then(Commands.literal("validate")
                .requires(source -> source.hasPermission(OPERATOR))
                .executes(context -> run(context.getSource(), null))
                .then(Commands.argument("pack", StringArgumentType.greedyString())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                AddonManager.getLoadedAddons().stream().map(AddonPack::getDomain), builder))
                        .executes(context -> run(context.getSource(),
                                StringArgumentType.getString(context, "pack").trim())))));
    }

    private static int run(CommandSourceStack source, @Nullable String pack) {
        Optional<ContentReport> result = ContentValidation.run(pack);
        if (result.isEmpty()) {
            source.sendFailure(Component.translatableWithFallback("commands.mcheli.validate.unknown_pack",
                    "No loaded content pack has the domain '%s'", pack));
            return 0;
        }
        ContentReport report = result.get();
        Path file = ContentLog.publish(report, ContentLog.VALIDATE_REPORT);
        source.sendSuccess(() -> summary(report), false);
        for (Diagnostic diagnostic : report.worst(SHOWN)) {
            source.sendSuccess(() -> line(diagnostic), false);
        }
        source.sendSuccess(() -> Component.translatableWithFallback("commands.mcheli.validate.report",
                "Full report: %s", path(file)), false);
        return (int) report.count(Severity.ERROR);
    }

    private static Component summary(ContentReport report) {
        long errors = report.count(Severity.ERROR);
        long warnings = report.count(Severity.WARNING);
        return Component.translatableWithFallback("commands.mcheli.validate.summary",
                "%s: %s of %s files loaded from %s packs; %s errors, %s warnings, %s files replaced",
                report.title(), report.filesLoaded(), report.filesRead(), report.packs().size(),
                Component.literal(String.valueOf(errors)).withStyle(errors > 0 ? ChatFormatting.RED
                        : ChatFormatting.GREEN),
                Component.literal(String.valueOf(warnings)).withStyle(warnings > 0 ? ChatFormatting.YELLOW
                        : ChatFormatting.GREEN),
                report.overrides().size());
    }

    private static Component line(Diagnostic diagnostic) {
        String message = diagnostic.message().lines().findFirst().orElse("");
        if (message.length() > MESSAGE_LENGTH) {
            message = message.substring(0, MESSAGE_LENGTH) + "...";
        }
        ChatFormatting colour = diagnostic.severity() == Severity.ERROR ? ChatFormatting.RED : ChatFormatting.YELLOW;
        return Component.literal(" " + diagnostic.severity().name() + " ").withStyle(colour)
                .append(Component.literal(message).withStyle(ChatFormatting.GRAY));
    }

    /** The report's path; clicking copies it, since a server cannot open files on the player's computer. */
    private static MutableComponent path(Path file) {
        String absolute = file.toAbsolutePath().normalize().toString();
        return Component.literal(ContentLog.VALIDATE_REPORT).withStyle(style -> style
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, absolute))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(absolute))));
    }
}
