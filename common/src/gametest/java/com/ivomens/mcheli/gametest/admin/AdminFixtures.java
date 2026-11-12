package com.ivomens.mcheli.gametest.admin;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.config.McheliSettings;
import com.ivomens.mcheli.config.Option;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.function.Supplier;

/** What the server owner tool tests share: named players, operators, vehicles and pinned config values. */
final class AdminFixtures {

    static final int OPERATOR_LEVEL = 2;

    private AdminFixtures() {
    }

    /**
     * A player without a client, like {@code GameTestHelper.makeMockServerPlayerInLevel}, but with its own name:
     * teams are kept by name, and every mock player of the helper has the same one.
     */
    static ServerPlayer player(GameTestHelper h, String name) {
        ServerLevel level = h.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name),
                false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
                cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    static void makeOperator(ServerPlayer player) {
        // PlayerList.op would use the game test server's operator level, which is 0
        player.server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), OPERATOR_LEVEL,
                false));
    }

    static void removeOperator(ServerPlayer player) {
        player.server.getPlayerList().getOps().remove(player.getGameProfile());
    }

    static AircraftItem item(VehicleKind kind, String name) {
        VehicleSpec info = VehicleTypes.registry(kind).get(name).orElse(null);
        if (info == null || !(VehicleTypes.item(info).orElse(null) instanceof AircraftItem item)) {
            throw new GameTestAssertException(name + " is not loaded");
        }
        return item;
    }

    /** A stone floor for the test's vehicles. */
    static void floor(GameTestHelper h) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /** A vehicle at the test-relative position, owned by {@code owner} when given, already in the level. */
    static AircraftEntity spawn(GameTestHelper h, AircraftItem item, Vec3 relative, ServerPlayer owner) {
        Vec3 pos = h.absoluteVec(relative);
        AircraftEntity vehicle = item.createAircraft(h.getLevel(), pos.x, pos.y, pos.z, new ItemStack(item));
        if (vehicle == null) {
            throw new GameTestAssertException("could not create " + item);
        }
        if (owner != null) {
            vehicle.ownership().setOwner(owner);
        }
        h.getLevel().addFreshEntity(vehicle);
        return vehicle;
    }

    /** Runs {@code body} with one config value changed, and puts the config back afterwards. */
    static <T, R> R withConfig(Option<T> option, T value, Supplier<R> body) {
        McheliSettings before = McheliConfig.get();
        McheliConfig.set(before.with(option, value));
        try {
            return body.get();
        } finally {
            McheliConfig.set(before);
        }
    }

    static void expect(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
