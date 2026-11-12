package com.ivomens.mcheli.gametest.api;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.client.HudAnchor;
import com.ivomens.mcheli.api.client.HudWidget;
import com.ivomens.mcheli.api.client.HudWidgetContext;
import com.ivomens.mcheli.api.client.HudWidgets;
import com.ivomens.mcheli.api.client.McheliClientApi;
import com.ivomens.mcheli.api.event.Subscription;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.apiservice.client.ClientApiService;
import com.ivomens.mcheli.hud.client.widget.HudLayout;
import com.ivomens.mcheli.hud.client.widget.HudWidgetLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The HUD widgets of the client API on a real helicopter: widgets registered through {@link McheliClientApi} reach
 * the HUD's widget layer, see the vehicle and the pilot's seat, are filtered by kind, laid out in their slot,
 * dropped after they throw, and gone once unsubscribed. A game test server has no screen, so the layer draws on a
 * {@link TextCanvas}; what the real screen makes of the calls is the HUD's business, and the unit test
 * {@code HudWidgetTraceTest} records the calls themselves.
 */
public final class HudWidgetGameTests {

    private static final String BATCH = "mcheli_api_hud";
    private static final String HELICOPTER = "ah-64";
    private static final String MARKER = "PILOT WIDGET";

    private HudWidgetGameTests() {
    }

    public static List<TestFunction> widgets() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return List.of();
        }
        return List.of(new TestFunction(BATCH, "api_hud_widgets_draw_for_the_pilot", "mcheli:empty", 300, 0, true,
                HudWidgetGameTests::widgetsDrawForThePilot));
    }

    private static void widgetsDrawForThePilot(GameTestHelper h) {
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

        HudWidgets widgets = McheliClientApi.get().hudWidgets();
        check(widgets == ClientApiService.widgets() && widgets == McheliClientApi.get().hudWidgets(),
                "the client API is not the one service");
        Probe pilot = new Probe(HudAnchor.TOP_LEFT, null);
        Probe tankOnly = new Probe(HudAnchor.TOP_LEFT, VehicleKind.TANK);
        Probe broken = new Probe(HudAnchor.RIGHT, null);
        broken.fails = true;
        tankOnly.order = -1;
        List<Subscription> subscriptions = new ArrayList<>();
        subscriptions.add(widgets.register(id("pilot"), pilot));
        subscriptions.add(widgets.register(id("tank_only"), tankOnly));
        subscriptions.add(widgets.register(id("broken"), broken));
        check(throwsOnDuplicate(widgets), "a second widget with the same id was accepted");

        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (ac.seats().seatIdOf(player) < 0 && tick[0] % 10 == 5) {
                ac.boarding().interact(player, InteractionHand.MAIN_HAND);
            }
        });
        h.succeedWhen(() -> {
            check(ac.seats().seatIdOf(player) == 0, "the player is not the pilot yet");
            HudWidgetLayer layer = new HudWidgetLayer(ClientApiService.widgets());
            TextCanvas canvas = new TextCanvas();
            for (int frame = 0; frame < 2; frame++) {
                layer.draw(canvas, ac, player, ac.seats().seatIdOf(player), 0.5F, HudLayout.FLIGHT, 0xFF7FFFD4, 1F);
            }
            check(pilot.rendered == 2 && pilot.vehicleSeen == ac && pilot.viewerSeen == player && pilot.seatSeen == 0,
                    "the pilot widget saw " + pilot.rendered + " frames, " + pilot.vehicleSeen + ", seat "
                            + pilot.seatSeen);
            check(canvas.texts().stream().filter(MARKER::equals).count() == 2, "texts " + canvas.texts());
            check(tankOnly.rendered == 0 && tankOnly.asked > 0, "the tank widget showed in a helicopter");
            // the hidden tank widget comes first by order but leaves the top of the slot to the pilot widget
            check(canvas.translations().stream().anyMatch(t -> Arrays.equals(t, new double[]{20, 4})),
                    "the pilot widget is not at the top of its slot");
            check(broken.rendered == 1, "a widget that threw was drawn " + broken.rendered + " times");

            subscriptions.forEach(Subscription::unsubscribe);
            TextCanvas after = new TextCanvas();
            layer.draw(after, ac, player, 0, 0.5F, HudLayout.FLIGHT, 0xFF7FFFD4, 1F);
            check(pilot.rendered == 2 && !after.texts().contains(MARKER), "an unsubscribed widget was drawn");
            ac.discard();
            player.discard();
        });
    }

    private static boolean throwsOnDuplicate(HudWidgets widgets) {
        try {
            widgets.register(id("pilot"), new Probe(HudAnchor.BOTTOM, null)).unsubscribe();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mcheli_gametest", "hud_" + path);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** A widget that writes down what it was shown. */
    private static final class Probe implements HudWidget {

        private final HudAnchor anchor;
        private final VehicleKind onlyKind;
        private boolean fails;
        private int order;
        private int asked;
        private int rendered;
        private Object vehicleSeen;
        private Object viewerSeen;
        private int seatSeen = -2;

        Probe(HudAnchor anchor, VehicleKind onlyKind) {
            this.anchor = anchor;
            this.onlyKind = onlyKind;
        }

        @Override
        public HudAnchor anchor() {
            return this.anchor;
        }

        @Override
        public int order() {
            return this.order;
        }

        @Override
        public int width() {
            return 80;
        }

        @Override
        public int height() {
            return HudWidgetContext.READOUT_HEIGHT;
        }

        @Override
        public boolean isShown(HudWidgetContext context) {
            this.asked++;
            return this.onlyKind == null || context.kind() == this.onlyKind;
        }

        @Override
        public void render(HudWidgetContext context) {
            this.rendered++;
            this.vehicleSeen = context.vehicle();
            this.viewerSeen = context.viewer();
            this.seatSeen = context.seat();
            if (this.fails) {
                throw new IllegalStateException("thrown on purpose by a game test");
            }
            context.readout(0, 0, context.width(), "TEST", MARKER, context.color());
        }
    }
}
