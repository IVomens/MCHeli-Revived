package com.ivomens.mcheli.gametest.controls;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.config.HandlingSettings;
import com.ivomens.mcheli.config.JoystickAction;
import com.ivomens.mcheli.config.JoystickSettings;
import com.ivomens.mcheli.config.McheliSettings;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.vehicle.client.input.JoystickInput;
import com.ivomens.mcheli.vehicle.client.input.PilotControls;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * A joystick, scripted in place of a real one ({@link ScriptedJoystick}), drives a helicopter through
 * the pilot's controls: its axes must come out as the stick the mouse would give, its throttle and
 * buttons as the keys. Nothing here touches GLFW, so it runs on the dedicated test server.
 */
public final class JoystickControlGameTests {

    private static final String BATCH = "mcheli_controls";
    private static final float FRAME = 0.05F;
    private static final float[] MOUSE_CENTRED = {0.0F, 0.0F};

    private JoystickControlGameTests() {
    }

    public static List<TestFunction> controls() {
        return List.of(test("joystick_axes_become_the_pilots_stick", JoystickControlGameTests::axes),
                test("joystick_throttle_and_buttons_press_the_keys", JoystickControlGameTests::throttleAndButtons),
                test("without_a_joystick_the_mouse_stick_passes_untouched", JoystickControlGameTests::mouseOnly));
    }

    private static TestFunction test(String name, BiConsumer<GameTestHelper, AircraftEntity> body) {
        return new TestFunction(BATCH, name, "mcheli:empty", 40, 0, true, helper -> {
            AircraftEntity heli = spawnHelicopter(helper);
            if (heli != null) {
                body.accept(helper, heli);
                heli.discard();
                helper.succeed();
            }
        });
    }

    /** The test's settings: joystick on, linear axes without a dead zone. */
    private static JoystickSettings linear(McheliSettings base) {
        return base.with(JoystickSettings.ENABLED, true).with(JoystickSettings.DEAD_ZONE, 0.0)
                .with(JoystickSettings.EXPO, 0.0).joystick();
    }

    private static void axes(GameTestHelper helper, AircraftEntity heli) {
        // gamepad layout: left x rolls, left y pitches (forward is -1), right x yaws
        ScriptedJoystick pad = new ScriptedJoystick("Scripted Pad", true, 6, 15).axis(0, 0.5F).axis(1, -0.25F)
                .axis(2, 0.2F);
        PilotControls controls = new PilotControls(new JoystickInput(pad));
        HandlingSettings handling = McheliSettings.defaults().handling();
        controls.pollJoystick(linear(McheliSettings.defaults()));
        AttitudeInput stick = controls.stick(heli, new float[]{7.0F, 3.0F}, true, PilotControls.StickKeys.NONE, FRAME,
                handling);
        // full deflection is the mouse stick's 40 units; the joystick replaces the mouse and flies on in free look
        expect(helper, new AttitudeInput(20.0F, -10.0F, false, 8.0F), stick, "stick from the axes");

        pad.setConnected(false);
        controls.pollJoystick(linear(McheliSettings.defaults()));
        AttitudeInput unplugged = controls.stick(heli, new float[]{7.0F, 3.0F}, true, PilotControls.StickKeys.NONE,
                FRAME, handling);
        expect(helper, new AttitudeInput(7.0F, 3.0F, true, 0.0F), unplugged, "the mouse after unplugging");
    }

    private static void throttleAndButtons(GameTestHelper helper, AircraftEntity heli) {
        ScriptedJoystick pad = new ScriptedJoystick("Scripted Pad", true, 6, 15).axis(3, -1.0F).button(5, true);
        PilotControls controls = new PilotControls(new JoystickInput(pad));
        HandlingSettings handling = McheliSettings.defaults().handling();
        controls.pollJoystick(linear(McheliSettings.defaults()));
        heli.engine().setThrottle(0.0);
        controls.tick(heli, false, handling);
        helper.assertTrue(controls.throttleUp() && !controls.throttleDown(),
                "the incremental throttle pushed forward presses throttle up");
        helper.assertTrue(controls.pressed(JoystickAction.FIRE), "button 5 fires");
        helper.assertFalse(controls.pressed(JoystickAction.FLARE), "flare is on button 1");

        controls.tick(heli, true, handling);
        helper.assertFalse(controls.throttleUp(), "the pilot's own throttle key takes over");

        McheliSettings lever = McheliSettings.defaults().with(JoystickSettings.THROTTLE_MODE, "absolute");
        pad.axis(3, 1.0F);
        controls.pollJoystick(linear(lever));
        heli.engine().setThrottle(0.6);
        controls.tick(heli, false, handling);
        helper.assertTrue(controls.throttleDown(), "a lever pulled back to idle presses throttle down");
    }

    private static void mouseOnly(GameTestHelper helper, AircraftEntity heli) {
        ScriptedJoystick pad = new ScriptedJoystick("Scripted Pad", true, 6, 15).axis(0, 1.0F).button(5, true);
        PilotControls controls = new PilotControls(new JoystickInput(pad));
        controls.pollJoystick(McheliSettings.defaults().joystick());
        AttitudeInput stick = controls.stick(heli, new float[]{12.5F, -3.25F}, false, PilotControls.StickKeys.NONE,
                FRAME, McheliSettings.defaults().handling());
        expect(helper, new AttitudeInput(12.5F, -3.25F, false), stick, "the default stick");
        AttitudeInput centred = controls.stick(heli, MOUSE_CENTRED, false, PilotControls.StickKeys.NONE, FRAME,
                McheliSettings.defaults().handling());
        expect(helper, AttitudeInput.CENTRED, centred, "a centred mouse");
        helper.assertFalse(controls.pressed(JoystickAction.FIRE), "a disabled joystick presses nothing");
        controls.tick(heli, false, McheliSettings.defaults().handling());
        helper.assertFalse(controls.throttleUp() || controls.throttleDown(), "nor any throttle key");
    }

    private static void expect(GameTestHelper helper, AttitudeInput expected, AttitudeInput actual, String what) {
        helper.assertTrue(expected.equals(actual), what + ": expected " + expected + " but was " + actual);
    }

    private static AircraftEntity spawnHelicopter(GameTestHelper helper) {
        VehicleSpec info = VehicleTypes.registry(VehicleKind.HELICOPTER).get("ah-64").orElse(null);
        if (info == null || !(VehicleTypes.item(info).orElse(null) instanceof AircraftItem item)) {
            helper.fail("ah-64 is not loaded");
            return null;
        }
        Vec3 pos = helper.absoluteVec(new Vec3(8, 2, 8));
        AircraftEntity heli = item.createAircraft(helper.getLevel(), pos.x, pos.y, pos.z, new ItemStack(item));
        helper.getLevel().addFreshEntity(heli);
        return heli;
    }
}
