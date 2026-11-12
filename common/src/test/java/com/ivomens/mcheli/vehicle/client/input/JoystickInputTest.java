package com.ivomens.mcheli.vehicle.client.input;

import com.ivomens.mcheli.config.JoystickAction;
import com.ivomens.mcheli.config.JoystickSettings;
import com.ivomens.mcheli.config.McheliSettings;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The joystick read through a scripted device: no GLFW and no real joystick involved. */
class JoystickInputTest {

    private static McheliSettings defaults;

    /** A device whose readings the test sets; it can be unplugged. */
    private static final class FakeSource implements JoystickSource {

        private final List<Device> devices = new ArrayList<>();
        private float[] axes = new float[6];
        private boolean[] buttons = new boolean[15];

        @Override
        public List<Device> devices() {
            return List.copyOf(this.devices);
        }

        @Override
        @Nullable
        public Reading read(Device device, boolean asGamepad) {
            return this.devices.contains(device) ? new Reading(this.axes.clone(), this.buttons.clone()) : null;
        }
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        defaults = McheliSettings.defaults();
    }

    private static JoystickSettings enabled(McheliSettings settings) {
        return settings.with(JoystickSettings.ENABLED, true).joystick();
    }

    @Test
    void disabledReadsNothingEvenWithADeviceConnected() {
        FakeSource source = new FakeSource();
        source.devices.add(new JoystickSource.Device(0, "Pad", "guid", true));
        source.axes[0] = 1.0F;
        JoystickInput input = new JoystickInput(source);
        input.poll(defaults.joystick());
        assertFalse(input.active());
        assertEquals(0.0F, input.roll());
        assertFalse(input.drivesRoll());
    }

    @Test
    void theBoundAxesAreShapedAndButtonsMapToActions() {
        FakeSource source = new FakeSource();
        source.devices.add(new JoystickSource.Device(0, "Pad", "guid", true));
        source.axes[0] = 1.0F;
        source.axes[1] = -0.05F;
        source.buttons[5] = true;
        JoystickInput input = new JoystickInput(source);
        input.poll(enabled(defaults.with(JoystickSettings.EXPO, 0.0)));

        assertTrue(input.active() && input.drivesRoll() && input.drivesPitch());
        assertEquals(1.0F, input.roll());
        assertEquals(0.0F, input.pitch(), "inside the default dead zone");
        assertTrue(input.pressed(JoystickAction.FIRE));
        assertFalse(input.pressed(JoystickAction.FLARE));
    }

    @Test
    void anInvertedAxisAndAnUnboundOneAreHonoured() {
        FakeSource source = new FakeSource();
        source.devices.add(new JoystickSource.Device(0, "Stick", "g", false));
        source.axes[2] = 0.55F;
        JoystickInput input = new JoystickInput(source);
        input.poll(enabled(defaults.with(JoystickSettings.YAW_INVERT, true).with(JoystickSettings.EXPO, 0.0)
                .with(JoystickSettings.PITCH_AXIS, -1)));
        assertEquals(-0.5F, input.yaw(), 1.0e-6F);
        assertFalse(input.drivesPitch());
    }

    @Test
    void theDeviceSettingPicksByNameOrGuidAndAMissingOneLeavesTheStickIdle() {
        FakeSource source = new FakeSource();
        source.devices.add(new JoystickSource.Device(0, "Xbox Controller", "0300aaa", true));
        source.devices.add(new JoystickSource.Device(1, "Thrustmaster T.16000M", "0300bbb", false));
        JoystickInput input = new JoystickInput(source);
        input.poll(enabled(defaults.with(JoystickSettings.DEVICE, "t.16000")));
        assertEquals(1, input.device().id());
        input.poll(enabled(defaults.with(JoystickSettings.DEVICE, "0300AAA")));
        assertEquals(0, input.device().id());
        input.poll(enabled(defaults.with(JoystickSettings.DEVICE, "Saitek")));
        assertFalse(input.active());
    }

    @Test
    void unpluggingMidFlightLetsGoOfEveryAxisAndPluggingBackResumes() {
        FakeSource source = new FakeSource();
        JoystickSource.Device pad = new JoystickSource.Device(0, "Pad", "guid", true);
        source.devices.add(pad);
        source.axes[0] = -1.0F;
        source.buttons[5] = true;
        JoystickInput input = new JoystickInput(source);
        JoystickSettings settings = enabled(defaults);
        input.poll(settings);
        assertEquals(-1.0F, input.roll());

        source.devices.clear();
        input.poll(settings);
        assertFalse(input.active());
        assertEquals(0.0F, input.roll());
        assertFalse(input.pressed(JoystickAction.FIRE));

        source.devices.add(pad);
        input.poll(settings);
        assertEquals(-1.0F, input.roll());
    }
}
