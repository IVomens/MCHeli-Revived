package com.ivomens.mcheli.gametest;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.inspector.InspectorEntry;
import com.ivomens.mcheli.inspector.InspectorSection;
import com.ivomens.mcheli.inspector.InspectorSnapshot;
import com.ivomens.mcheli.inspector.InspectorTab;
import com.ivomens.mcheli.inspector.VehicleSnapshots;
import com.ivomens.mcheli.inspector.net.InspectorReplyMessage;
import com.ivomens.mcheli.inspector.net.InspectorRequestHandler;
import com.ivomens.mcheli.inspector.net.InspectorRequestMessage;
import com.ivomens.mcheli.vehicle.component.StateSink;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The vehicle inspector's server view: an operator's request returns the server's values of a spawned
 * vehicle, a second request within the rate limit gets no answer, and a player who is not an operator is
 * refused without a snapshot.
 */
public final class InspectorGameTests {

    private static final int OPERATOR_LEVEL = 2;
    private static final int DAMAGE = 20;
    private static final int FUEL = 123;

    private InspectorGameTests() {
    }

    public static List<TestFunction> inspector() {
        if (!StressTests.SCENARIO.isEmpty()) return List.of();
        return List.of(new TestFunction("mcheli_inspector", "inspector_server_snapshot", "mcheli:empty", 100, 0, true,
                InspectorGameTests::serverSnapshot));
    }

    private static void serverSnapshot(GameTestHelper h) {
        VehicleSpec info = VehicleTypes.registry(VehicleKind.HELICOPTER).get("ah-64").orElse(null);
        if (info == null || !(VehicleTypes.item(info).orElse(null) instanceof AircraftItem item)) {
            h.fail("ah-64 missing");
            return;
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        ServerLevel level = h.getLevel();
        Vec3 pos = h.absoluteVec(new Vec3(8, 1.2, 8));
        AircraftEntity aircraft = item.createAircraft(level, pos.x, pos.y, pos.z, new ItemStack(item));
        level.addFreshEntity(aircraft);
        aircraft.damage().setDamageTaken(DAMAGE);
        aircraft.fuel().setAmount(FUEL);
        aircraft.engine().setThrottle(0.5);

        ServerPlayer operator = h.makeMockServerPlayerInLevel();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        PlayerList players = level.getServer().getPlayerList();
        // PlayerList.op would use the game test server's operator level, which is 0.
        players.getOps().add(new ServerOpListEntry(operator.getGameProfile(), OPERATOR_LEVEL, false));
        List<InspectorReplyMessage> sent = new ArrayList<>();
        InspectorRequestHandler handler = new InspectorRequestHandler((to, reply) -> sent.add(reply));
        try {
            handler.handle(new InspectorRequestMessage(aircraft.getId()), operator);
            expect(sent.size() == 1, "the operator got no answer");
            InspectorReplyMessage reply = sent.getFirst();
            expect(reply.status() == InspectorReplyMessage.Status.OK, "the operator was answered " + reply.status());
            checkValues(reply.snapshot(), aircraft);

            handler.handle(new InspectorRequestMessage(aircraft.getId()), operator);
            expect(sent.size() == 1, "a second request in the same tick was answered");

            InspectorReplyMessage refused = handler.answer(player, aircraft.getId());
            expect(refused != null && refused.status() == InspectorReplyMessage.Status.DENIED,
                    "a player who is not an operator was not refused");
            expect(refused.snapshot().sections().isEmpty() && refused.snapshot().typeId().isEmpty(),
                    "the refusal carried a snapshot");
            h.succeed();
        } finally {
            players.getOps().remove(operator.getGameProfile());
            aircraft.discard();
            operator.discard();
            player.discard();
        }
    }

    private static void checkValues(InspectorSnapshot snapshot, AircraftEntity aircraft) {
        int maxHp = aircraft.damage().maxHp();
        expect(snapshot.entityId() == aircraft.getId(), "wrong entity id " + snapshot.entityId());
        expect(snapshot.typeId().equals("helicopter/ah-64"), "wrong type " + snapshot.typeId());
        expect(!snapshot.truncated(), "the snapshot of an AH-64 was truncated");
        value(snapshot, InspectorTab.OVERVIEW, VehicleSnapshots.STATE, "health", (maxHp - DAMAGE) + " / " + maxHp);
        value(snapshot, InspectorTab.OVERVIEW, VehicleSnapshots.STATE, "fuel",
                FUEL + " / " + aircraft.fuel().capacity());
        value(snapshot, InspectorTab.OVERVIEW, VehicleSnapshots.STATE, "throttle", "0.500");
        value(snapshot, InspectorTab.OVERVIEW, VehicleSnapshots.IDENTITY, "uuid", aircraft.getUUID().toString());
        value(snapshot, InspectorTab.COMPONENTS, "DamageComponent", "damage taken", Integer.toString(DAMAGE));
        value(snapshot, InspectorTab.COMPONENTS, "EngineComponent", "throttle", StateSink.number(0.5));
        List<InspectorSection> weapons = snapshot.sections(InspectorTab.WEAPONS);
        expect(weapons.size() == aircraft.weapons().count(), "weapons: " + weapons.size() + ", vehicle has "
                + aircraft.weapons().count());
        expect(weapons.getFirst().title().equals("#0 " + aircraft.weapons().get(0).firstMount().name()),
                "first weapon is " + weapons.getFirst().title());
        expect(snapshot.sections(InspectorTab.CREW).size() == aircraft.seats().count() + 2,
                "crew sections: " + snapshot.sections(InspectorTab.CREW).size());
        List<InspectorSection> definition = snapshot.sections(InspectorTab.DEFINITION);
        expect(!definition.isEmpty(), "no definition sections");
        expect(definition.stream().flatMap(section -> section.entries().stream()).allMatch(InspectorEntry::marked),
                "the server sent a definition value that is the default");
        expect(definition.stream().anyMatch(section -> !section.entries().isEmpty()), "no changed definition value");
    }

    private static void value(InspectorSnapshot snapshot, InspectorTab tab, String section, String key,
                              String expected) {
        String actual = snapshot.value(tab, section, key).orElse(null);
        expect(expected.equals(actual), tab + " / " + section + " / " + key + " is " + actual + ", not " + expected);
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
