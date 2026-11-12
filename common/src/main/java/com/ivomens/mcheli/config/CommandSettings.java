package com.ivomens.mcheli.config;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The {@code /mcheli} command and which players may use its sub-commands without being operators. */
public record CommandSettings(boolean enabled, List<Permission> permissions, boolean clientBenchmark) {

    public static final BoolOption ENABLED = BoolOption.of("EnableCommand", true);
    /** Whether {@code /mcheli benchmark client} may run (single player only in any case). */
    public static final BoolOption CLIENT_BENCHMARK = BoolOption.of("ClientBenchmark", true)
            .describe("ClientBenchmark = true allows /mcheli benchmark client in single player worlds (it spawns"
                    + " and removes vehicles around you). false = the command refuses to run.");
    /** {@code CommandPermission = command : player1, player2}, one line per command. */
    public static final ListOption PERMISSIONS = ListOption.of("CommandPermission", List.of());

    /** One {@code CommandPermission} line. */
    public record Permission(String command, List<String> players) {

        public Permission {
            players = List.copyOf(players);
        }

        /** Empty unless the text is {@code command : players}. */
        static Optional<Permission> parse(String text) {
            String[] parts = text.split(":");
            if (parts.length != 2) {
                return Optional.empty();
            }
            String command = parts[0].toLowerCase(Locale.ROOT).trim();
            if (command.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Permission(command, List.of(parts[1].trim().split("\\s*,\\s*"))));
        }
    }

    public CommandSettings {
        permissions = List.copyOf(permissions);
    }

    static CommandSettings from(ConfigValues v) {
        List<Permission> permissions = v.get(PERMISSIONS).stream()
                .flatMap(entry -> Permission.parse(entry.value()).stream()).toList();
        return new CommandSettings(v.get(ENABLED), permissions, v.get(CLIENT_BENCHMARK));
    }

    /** Whether the config names this player for this sub-command. */
    public boolean allows(String command, String playerName) {
        return this.permissions.stream().filter(permission -> permission.command().equals(command))
                .flatMap(permission -> permission.players().stream())
                .anyMatch(player -> player.equalsIgnoreCase(playerName));
    }
}
