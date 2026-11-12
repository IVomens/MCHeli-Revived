package com.ivomens.mcheli.example.gametest;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.client.McheliClientApi;
import com.ivomens.mcheli.api.event.Subscription;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.apiservice.client.ClientApiService;
import com.ivomens.mcheli.example.ExampleAddon;
import com.ivomens.mcheli.example.ExampleClockWidget;
import com.ivomens.mcheli.hud.client.widget.HudLayout;
import com.ivomens.mcheli.hud.client.widget.HudWidgetLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's HUD widget, {@link ExampleClockWidget}, registered through the client API from the addon's jar and
 * drawn by MCHeli's widget layer for the pilot of a helicopter. The addon registers it at client set-up, which a
 * game test server never runs, so the test registers it under an id of its own. The server has no screen: the
 * layer draws on a {@link TextCanvas}.
 */
@GameTestHolder(ExampleAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExampleHudWidgetGameTests {

    private static final String HELICOPTER = "ah-64";

    private ExampleHudWidgetGameTests() {
    }

    /** The clock reads the world's time of day, with tick 0 at 06:00. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void clockReadsTheTimeOfDay(GameTestHelper h) {
        check(ExampleClockWidget.clock(0).equals("06:00"), "tick 0 is " + ExampleClockWidget.clock(0));
        check(ExampleClockWidget.clock(6500).equals("12:30"), "tick 6500 is " + ExampleClockWidget.clock(6500));
        check(ExampleClockWidget.clock(18000).equals("00:00"), "tick 18000 is " + ExampleClockWidget.clock(18000));
        check(ExampleClockWidget.clock(-1000).equals("05:00"), "tick -1000 is " + ExampleClockWidget.clock(-1000));
        h.succeed();
    }

    /** The pilot sees the clock in his HUD; once unsubscribed it is gone. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void clockWidgetDrawsForThePilot(GameTestHelper h) {
        h.setBlock(new BlockPos(8, 0, 8), Blocks.STONE);
        ServerLevel level = h.getLevel();
        if (!(VehicleTypes.get(VehicleKind.HELICOPTER, HELICOPTER).flatMap(VehicleTypes::item).orElse(null)
                instanceof AircraftItem item)) {
            h.fail(HELICOPTER + " has no item");
            return;
        }
        Vec3 pos = h.absoluteVec(new Vec3(8, 1.5, 8));
        AircraftEntity ac = item.createAircraft(level, pos.x, pos.y, pos.z, new ItemStack(item));
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.moveTo(pos.x + 2, pos.y, pos.z);
        level.addFreshEntity(ac);

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ExampleAddon.MOD_ID, "clock_test");
        Subscription subscription = McheliClientApi.get().hudWidgets().register(id, new ExampleClockWidget());
        HudWidgetLayer layer = new HudWidgetLayer(ClientApiService.widgets());

        // not in a seat yet: the clock is for the pilot only
        TextCanvas outside = new TextCanvas();
        layer.draw(outside, ac, player, ac.seats().seatIdOf(player), 0F, HudLayout.FLIGHT, 0xFF7FFFD4, 1F);
        check(!outside.texts().contains("LOCAL"), "the clock showed for a player in no seat");

        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (ac.seats().seatIdOf(player) < 0 && tick[0] % 10 == 5) {
                ac.boarding().interact(player, InteractionHand.MAIN_HAND);
            }
        });
        h.succeedWhen(() -> {
            check(ac.seats().seatIdOf(player) == 0, "the player is not the pilot yet");
            TextCanvas canvas = new TextCanvas();
            String expected = ExampleClockWidget.clock(level.getDayTime());
            layer.draw(canvas, ac, player, 0, 0F, HudLayout.FLIGHT, 0xFF7FFFD4, 1F);
            check(canvas.texts().contains("LOCAL") && canvas.texts().contains(expected),
                    "the clock drew " + canvas.texts() + ", expected LOCAL and " + expected);
            check(canvas.shapes() > 0, "the clock drew no box or gauge");

            subscription.unsubscribe();
            TextCanvas after = new TextCanvas();
            layer.draw(after, ac, player, 0, 0F, HudLayout.FLIGHT, 0xFF7FFFD4, 1F);
            check(!after.texts().contains("LOCAL"), "the clock still drew after unsubscribing");
            ac.discard();
            player.discard();
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
