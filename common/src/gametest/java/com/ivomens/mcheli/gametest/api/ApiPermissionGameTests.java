package com.ivomens.mcheli.gametest.api;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.AircraftPanelMenu;
import com.ivomens.mcheli.api.event.Cancellable;
import com.ivomens.mcheli.api.event.VehicleBoardAttempt;
import com.ivomens.mcheli.api.event.VehicleDismantleAttempt;
import com.ivomens.mcheli.api.event.VehicleLockAttempt;
import com.ivomens.mcheli.api.event.VehiclePlaceAttempt;
import com.ivomens.mcheli.api.event.VehicleScreen;
import com.ivomens.mcheli.api.event.VehicleScreenOpenAttempt;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.ivomens.mcheli.gametest.api.ApiTestScene.check;

/**
 * The API 1.1 permission events, each refused once and then allowed: boarding the pilot's seat and a passenger
 * seat, opening the pilot's panel, locking, placing a vehicle from its item and breaking one down. A refused
 * action leaves no trace: no rider, no menu, no lock, the item kept, the vehicle in place.
 */
public final class ApiPermissionGameTests {

    private static final String BATCH = "mcheli_api_permissions";
    private static final String HELICOPTER = "ah-64";

    private ApiPermissionGameTests() {
    }

    public static List<TestFunction> permissions() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return List.of();
        }
        return List.of(new TestFunction(BATCH, "api_permission_events_refuse_then_allow", "mcheli:empty", 600, 0,
                true, ApiPermissionGameTests::refuseThenAllow));
    }

    private enum Step { PILOT, PANEL, PASSENGER, LOCK, PLACE, DISMANTLE, DONE }

    /**
     * One step after the other, each first with the listener refusing, then allowing. The steps that need
     * clicks repeat them every half second, as a canopy may have to open first.
     */
    private static void refuseThenAllow(GameTestHelper h) {
        ApiTestScene scene = new ApiTestScene(h).floor();
        AircraftEntity ac = scene.spawn(VehicleKind.HELICOPTER, HELICOPTER, new Vec3(8, 1, 8));
        ServerPlayer pilot = h.makeMockServerPlayerInLevel();
        ServerPlayer passenger = h.makeMockServerPlayerInLevel();
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        Vec3 near = h.absoluteVec(new Vec3(11, 1, 8));
        for (ServerPlayer player : List.of(pilot, passenger, owner)) {
            player.moveTo(near.x, near.y, near.z);
        }
        Gate gate = new Gate();
        scene.listen(VehicleBoardAttempt.class, e -> gate.ask(Step.PILOT, e, e.vehicle() == ac && e.seatId() == 0
                && e.player() == pilot));
        scene.listen(VehicleBoardAttempt.class, e -> gate.ask(Step.PASSENGER, e, e.vehicle() == ac
                && e.seatId() > 0 && e.player() == passenger));
        scene.listen(VehicleScreenOpenAttempt.class, e -> gate.ask(Step.PANEL, e, e.vehicle() == ac
                && e.screen() == VehicleScreen.PANEL && e.player() == pilot));
        scene.listen(VehicleLockAttempt.class, e -> gate.ask(Step.LOCK, e, e.vehicle() == ac && e.locking()
                && !e.claimsOwnership() && e.player() == owner));
        scene.listen(VehiclePlaceAttempt.class, e -> gate.ask(Step.PLACE, e, e.player() == owner
                && e.item().getItem() == scene.item(VehicleKind.HELICOPTER, HELICOPTER)));
        scene.listen(VehicleDismantleAttempt.class, e -> gate.ask(Step.DISMANTLE, e, e.player() == owner
                && e.vehicle() == gate.placed));

        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            boolean click = tick[0] % 10 == 5;
            switch (gate.step) {
                case PILOT -> clickUntilSeated(gate, ac, pilot, 0, click);
                case PANEL -> openPanel(gate, ac, pilot);
                case PASSENGER -> clickUntilSeated(gate, ac, passenger, 1, click);
                case LOCK -> toggleLock(gate, ac, owner);
                case PLACE -> place(gate, scene, h, owner);
                case DISMANTLE -> dismantle(gate, owner, h);
                default -> {
                }
            }
        });
        h.succeedWhen(() -> {
            check(gate.problem == null, gate.problem);
            check(gate.step == Step.DONE, "still at " + gate.step);
            scene.unsubscribeAll();
            ac.discard();
            List.of(pilot, passenger, owner).forEach(Player::discard);
        });
    }

    /** Refused, the player stays on foot however often they click; allowed, they take the seat. */
    private static void clickUntilSeated(Gate gate, AircraftEntity ac, ServerPlayer player, int seat, boolean click) {
        int at = ac.seats().seatIdOf(player);
        if (gate.refusing) {
            if (at >= 0) {
                gate.problem = gate.step + ": a refused player sat down in seat " + at;
            } else if (player.distanceToSqr(ac) > 25.0) {
                gate.problem = gate.step + ": a refused player was moved to " + player.position();
            } else if (gate.asked >= 2) {
                gate.allow();
            }
        } else if (at == seat) {
            gate.next();
            return;
        } else if (at >= 0) {
            gate.problem = gate.step + ": the player sat in seat " + at + " instead of " + seat;
        }
        if (click) {
            ac.boarding().interact(player, InteractionHand.MAIN_HAND);
        }
    }

    /**
     * Only the refusal: an allowed panel would open through the loader's open-screen payload, which the mock
     * player's connection cannot take.
     */
    private static void openPanel(Gate gate, AircraftEntity ac, ServerPlayer pilot) {
        ac.boarding().openGui(pilot);
        if (pilot.containerMenu instanceof AircraftPanelMenu || gate.asked != 1) {
            gate.problem = "PANEL: a refused panel opened (" + gate.asked + " asks)";
        }
        gate.next();
    }

    private static void toggleLock(Gate gate, AircraftEntity ac, ServerPlayer owner) {
        ac.ownership().setOwner(owner);
        owner.setShiftKeyDown(true);
        ac.boarding().interact(owner, InteractionHand.MAIN_HAND);
        owner.setShiftKeyDown(false);
        if (gate.refusing) {
            if (ac.ownership().isLocked() || gate.asked != 1) {
                gate.problem = "LOCK: a refused lock changed (" + gate.asked + " asks)";
            }
            gate.allow();
        } else if (ac.ownership().isLocked()) {
            ac.ownership().setLocked(false);
            gate.next();
        } else {
            gate.problem = "LOCK: the allowed lock did not change";
        }
    }

    private static void place(Gate gate, ApiTestScene scene, GameTestHelper h, ServerPlayer owner) {
        owner.setGameMode(GameType.SURVIVAL);
        AircraftItem item = scene.item(VehicleKind.HELICOPTER, HELICOPTER);
        ItemStack stack = new ItemStack(item, 2);
        AircraftEntity placed = item.spawnAircraft(stack, h.getLevel(), owner, h.absolutePos(new BlockPos(24, 0, 24)));
        if (gate.refusing) {
            if (placed != null || stack.getCount() != 2 || gate.asked != 1) {
                gate.problem = "PLACE: a refused vehicle was placed (" + gate.asked + " asks)";
            }
            gate.allow();
        } else if (placed != null && h.getLevel().getEntity(placed.getId()) == placed && stack.getCount() == 1) {
            gate.placed = placed;
            gate.next();
        } else {
            gate.problem = "PLACE: the allowed vehicle was not placed";
        }
    }

    /** The owner breaks the vehicle they placed with a creative punch, which removes it at once. */
    private static void dismantle(Gate gate, ServerPlayer owner, GameTestHelper h) {
        AircraftEntity placed = gate.placed;
        owner.setGameMode(GameType.CREATIVE);
        placed.hurt(h.getLevel().damageSources().playerAttack(owner), 1.0F);
        if (gate.refusing) {
            if (placed.isRemoved() || gate.asked != 1) {
                gate.problem = "DISMANTLE: a refused vehicle was broken down (" + gate.asked + " asks)";
            }
            gate.allow();
        } else if (placed.isRemoved()) {
            gate.next();
        } else {
            placed.discard();
            gate.problem = "DISMANTLE: the allowed vehicle stayed";
        }
    }

    /** The listeners' answers: refusing at first in each step, allowing once the step saw a refusal work. */
    private static final class Gate {

        private Step step = Step.PILOT;
        private boolean refusing = true;
        private int asked;
        private AircraftEntity placed;
        private String problem;

        void ask(Step of, Cancellable event, boolean ours) {
            if (ours && of == this.step) {
                this.asked++;
                event.setCancelled(this.refusing);
            }
        }

        void allow() {
            this.refusing = false;
            this.asked = 0;
        }

        void next() {
            this.step = Step.values()[this.step.ordinal() + 1];
            this.refusing = true;
            this.asked = 0;
        }
    }
}
