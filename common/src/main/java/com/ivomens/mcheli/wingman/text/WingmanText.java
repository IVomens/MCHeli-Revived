package com.ivomens.mcheli.wingman.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.UUID;

/**
 * The chat lines of the wingman system, as translatable components ({@code mcheli.wingman.*} in the
 * language files). Colours are applied here, so the translations hold plain text.
 */
public final class WingmanText {

    private static final String PREFIX = "mcheli.wingman.";

    private WingmanText() {
    }

    /** A line in one colour. */
    public static MutableComponent line(ChatFormatting colour, String key, Object... args) {
        return Component.translatable(PREFIX + key, args).withStyle(colour);
    }

    /** A highlighted value inside a line. */
    public static MutableComponent value(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.YELLOW);
    }

    /** A usage line. */
    public static Component usage(String usage) {
        return line(ChatFormatting.GRAY, "command.usage", usage);
    }

    /** The first eight characters of a UUID, as status lines show it. */
    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    public static Component entityNotFound() {
        return line(ChatFormatting.RED, "error.entity_not_found");
    }

    public static Component routeNotFound(String route) {
        return line(ChatFormatting.RED, "route.not_found", route);
    }

    public static Component routeSaved(String route, int nodes) {
        return line(ChatFormatting.GREEN, "route.saved", value(route), nodes);
    }

    public static Component routeDeleted(String route) {
        return line(ChatFormatting.GREEN, "route.deleted", value(route));
    }

    public static Component invalidNode(String node) {
        return line(ChatFormatting.RED, "route.invalid_node", node);
    }

    public static Component operatorsOnly() {
        return line(ChatFormatting.RED, "deny.operators_only");
    }

    public static Component routeNotYours(String route) {
        return line(ChatFormatting.RED, "deny.route_not_yours", route);
    }

    public static Component someoneAboard() {
        return line(ChatFormatting.RED, "deny.someone_aboard");
    }

    public static Component notYourWingman() {
        return line(ChatFormatting.RED, "deny.not_your_wingman");
    }

    /** What a recon flight counted, told to players nearby as it heads home. */
    public static Component reconReport(String aircraft, int hostiles, String reason) {
        return line(ChatFormatting.YELLOW, "recon.prefix", aircraft)
                .append(line(ChatFormatting.WHITE, "recon.report", hostiles, reason));
    }
}
