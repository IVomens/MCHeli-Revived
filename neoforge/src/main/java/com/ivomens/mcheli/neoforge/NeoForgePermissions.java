package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.platform.Permissions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContextKey;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.Map;
import java.util.UUID;

/**
 * Answers through NeoForge's permission API, so a permission mod can grant {@code mcheli.admin}. The node's
 * default, used when no permission mod is installed, is the operator level the caller passes in (the
 * config's {@code AdminCommandPermission}), handed over as a dynamic context.
 */
public class NeoForgePermissions implements Permissions {

    private static final PermissionDynamicContextKey<Integer> LEVEL =
            new PermissionDynamicContextKey<>(Integer.class, "level", String::valueOf);
    private static final Map<String, PermissionNode<Boolean>> NODES = Map.of(ADMIN, node(ADMIN));

    private static PermissionNode<Boolean> node(String name) {
        PermissionNode<Boolean> node = new PermissionNode<>("mcheli", name, PermissionTypes.BOOLEAN,
                NeoForgePermissions::byOperatorLevel, LEVEL);
        node.setInformation(Component.literal("mcheli." + name),
                Component.literal("MCHeli operator tools (see docs/SERVER_ADMIN.md)"));
        return node;
    }

    private static Boolean byOperatorLevel(ServerPlayer player, UUID id, PermissionDynamicContext<?>... context) {
        int level = 2;
        for (PermissionDynamicContext<?> entry : context) {
            if (entry.getDynamic().equals(LEVEL) && entry.getValue() instanceof Integer value) {
                level = value;
            }
        }
        return player != null && player.hasPermissions(level);
    }

    /** Registers the nodes with the permission API; it asks for them while the server starts. */
    static void register(IEventBus bus) {
        bus.addListener((PermissionGatherEvent.Nodes event) -> event.addNodes(NODES.values()
                .toArray(new PermissionNode<?>[0])));
    }

    @Override
    public boolean has(ServerPlayer player, String node, int level) {
        PermissionNode<Boolean> permission = NODES.get(node);
        if (permission == null) {
            return player.hasPermissions(level);
        }
        try {
            return PermissionAPI.getPermission(player, permission, LEVEL.createContext(level));
        } catch (RuntimeException notReady) {
            // the API is only set up once a server starts, and refuses nodes nobody registered
            return player.hasPermissions(level);
        }
    }
}
