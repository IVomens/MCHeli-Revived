# Controls

How a pilot flies an MCHeli vehicle: the mouse, the keyboard, a joystick or gamepad, and the optional
handling aids. Everything on this page beyond the mouse and the default keys is **off by default**;
with nothing changed the vehicles handle exactly as they always did.

All options live in `config/mcheli.cfg` and in the options screen (the MCHeli options key, or *Mods →
MCHeli → Config*). The joystick and handling options are client side: they change what your client
sends, never the server's rules, so they work on any server.

## Mouse

The mouse is a virtual flight stick. Moving it deflects the stick (up to 40 "mouse units", shown on
the HUD), and the deflection sets the turn rate, so a held deflection keeps turning.

| Vehicle | Stick left/right | Stick up/down |
|---|---|---|
| Helicopter | yaw and roll together | pitch |
| Plane, VTOL | yaw, and roll at half rate (flight sim mode: roll only) | pitch |
| Tank, car | turret or steering, depending on the vehicle | gun elevation |

* **Stick mode** (`MouseControlStickModeHeli`, `MouseControlStickModePlane`): the stick stays where you
  put it. Without it a small deflection springs back to the centre.
* **Mouse flight sim mode** (`MouseControlFlightSimMode`): planes roll with the mouse and yaw with the
  side keys.
* **Invert mouse pitch** (`InvertMousePitch`): pull the mouse back to raise the nose.
* **Free look** (`KeyFreeLook`, left Ctrl): the mouse moves your head and the stick reads as centred.

## Keyboard

The movement keys are W/S (throttle, or forward/back on the ground) and A/D (helicopters roll, planes
add a roll impulse in the air and steer on the ground). They are unchanged.

### Stick keys (optional)

Six extra bindings move the stick like the mouse does, with a rate and an acceleration of their own.
They are unbound by default; bind them on the *Key Binding* page (*Stick Pitch Up* ... *Stick Yaw
Right*).

| Option | Default | Effect |
|---|---|---|
| `KeyStickPitchUp`, `KeyStickPitchDown` | 0 (unbound) | pull the nose up, push it down |
| `KeyStickRollLeft`, `KeyStickRollRight` | 0 | roll, without the yaw a helicopter's stick brings along |
| `KeyStickYawLeft`, `KeyStickYawRight` | 0 | yaw (rudder) |
| `HandlingKeyPitchRate` | 30 | pitch rate at full ramp, degrees per second |
| `HandlingKeyRollRate` | 40 | roll rate at full ramp |
| `HandlingKeyYawRate` | 20 | yaw rate at full ramp |
| `HandlingKeyRampTime` | 0.3 | seconds a key takes to reach its full rate; 0 jumps straight to it |

The vehicle's own rate limits still apply. Releasing a key (or holding both of a pair) drops its axis
back to zero at once.

## Joystick and gamepad

Any device GLFW sees works: gamepads (Xbox, PlayStation and others GLFW has a mapping for), flight
sticks, HOTAS throttles and pedals. No extra mod is needed.

### Setting one up

1. Plug the device in (before or after starting the game; it is picked up when connected).
2. Open the options, press **Joystick >>**.
3. Turn **Enabled** on. The line at the top names the device being read, or says that none is
   connected. **Device** steps between *first connected* and each connected device by name.
4. For each of Pitch, Roll, Yaw and Throttle press its **Axis** button and move the axis you want.
   Escape cancels, Delete unbinds. **Invert** flips an axis.
5. Watch the bars: the white tick is the raw position, the fill is what the vehicle will get. Set the
   **Dead Zone** until the fill stays empty with the stick at rest. The **Response** box shows the
   curve (the dead zone shaded) and where the roll axis sits on it; **Test** swaps it for a stick box
   that shows pitch and roll as the airframe gets them, with the rudder underneath.
6. Choose the throttle type: **Throttle Lever** on for a lever (its position is the throttle; forward
   is full), off for a centring stick (push forward to raise the throttle, back to lower it, at
   **Throttle Rate** per second; let go and it stays).
7. **Buttons >>** binds the vehicle actions: press an action's button, then the joystick button. The
   light next to each action shows its button live.
8. **Save & Close**.

### How the joystick flies

* A bound axis replaces the mouse on that axis, with full deflection equal to the mouse stick's full
  deflection. Pitch and roll go where the mouse's up/down and left/right go (so a helicopter's roll
  axis also yaws, as its mouse does); the yaw axis is a separate rudder.
* The mouse keeps its other jobs. While the joystick flies, free look moves only your head and the
  joystick keeps flying, so you can look around in a turn.
* The throttle axis works through the throttle keys, as you would: the client presses throttle up or
  down until the throttle matches the axis. Holding W or S takes over; after you let go an
  incremental throttle starts again from where the keys left it.
* The buttons press the same actions as their keys: firing, switching weapons, flares, gear and so
  on go through exactly the same code.
* Unplugging the device in flight centres the stick and hands control back to the mouse; plugging it
  back in resumes. Nothing crashes if a device disappears while it is read.

### Options

| Option | Default | Effect |
|---|---|---|
| `JoystickEnabled` | false | read a joystick at all; off, nothing is read |
| `JoystickDevice` | (empty) | empty for the first connected device, otherwise part of its name or its GUID |
| `JoystickUseGamepadMapping` | true | read known gamepads in GLFW's standard layout |
| `JoystickPitchAxis`, `JoystickRollAxis`, `JoystickYawAxis`, `JoystickThrottleAxis` | 1, 0, 2, 3 | axis numbers, -1 for none (the defaults are a gamepad's left stick, right stick x, right stick y) |
| `Joystick<Axis>Invert` | false | flip the axis |
| `JoystickThrottleMode` | incremental | `incremental` (centring stick) or `absolute` (lever) |
| `JoystickThrottleRate` | 0.5 | throttle per second at full incremental deflection |
| `JoystickDeadZone` | 0.10 | share of the stick axes' travel around the centre that reads as zero |
| `JoystickExpo` | 0.30 | 0 linear, 1 cubic: higher softens small deflections and keeps full deflection |
| `JoystickSensitivity` | 1.0 | scales the stick axes (then clamped to full) |
| `JoystickButtonFire` | 5 (RB) | fire |
| `JoystickButtonNextWeapon` | 3 (Y) | next weapon |
| `JoystickButtonWeaponMode` | 14 (d-pad left) | weapon mode |
| `JoystickButtonReload` | 11 (d-pad up) | reload |
| `JoystickButtonFlare` | 1 (B) | flare |
| `JoystickButtonChaff` | 12 (d-pad right) | chaff |
| `JoystickButtonGear` | 2 (X) | landing gear |
| `JoystickButtonCameraMode` | 13 (d-pad down) | camera mode |
| `JoystickButtonZoom` | 10 (right stick) | zoom |
| `JoystickButtonFreeLook` | 9 (left stick) | free look |
| `JoystickButtonHover` | 0 (A) | hover mode (helicopters), brake (planes) |
| `JoystickButtonExtra` | 4 (LB) | the extra key: VTOL, drop, fold blades |
| `JoystickButtonThrottleUp`, `JoystickButtonThrottleDown` | -1 | hold the throttle keys |

### Recommended settings

**Gamepad.** The defaults are made for it: left stick pitch and roll, right stick x yaw, right stick y
an incremental throttle. Raise `JoystickExpo` to 0.4–0.5 for finer small corrections and keep
`JoystickDeadZone` at 0.10–0.15 for worn sticks. For helicopters turn on the helicopter assist below:
it holds altitude while your thumbs are busy.

**HOTAS / flight stick.** Turn `JoystickUseGamepadMapping` off if your stick is reported as a gamepad,
bind the axes with the screen (stick x roll, stick y pitch, twist or pedals yaw, the lever throttle),
set **Throttle Lever** on, `JoystickDeadZone` 0.02–0.05 and `JoystickExpo` 0.1–0.3. Bind fire to the
trigger and the countermeasures, gear and camera to the base buttons or hat (hats count as buttons).
Turn the plane auto-rudder off if you fly with pedals.

## Handling aids

All in the *Handling* page of the options and the `[Handling]` section of the file. Each works on the
pilot's input (the stick and the throttle keys), never on the physics, so the server sees an
ordinary pilot.

| Option | Default | What it changes |
|---|---|---|
| `HandlingAssistedHeli` | false | **Helicopter assist.** *Auto-level:* while the stick is centred (within 2 mouse units) in the air, the stick is moved to bring pitch and roll back to level within about a second, with opposite rudder so it does not yaw. *Altitude hold:* when you let go of the throttle keys in flight, the helicopter takes the altitude it will reach a moment later and works the throttle keys to stay there; any throttle key (or the joystick throttle) takes over and the hold starts again afterwards. Off in hover mode, on the ground and in water. |
| `HandlingAutoRudderPlane` | false | **Coordinated turns.** A plane in the air gets rudder towards its bank in proportion to the sine of the bank, so it turns the way it leans. |
| `HandlingAutoRudderGain` | 0.5 | rudder at a 90 degree bank, as a share of full stick (0 to 2) |
| `HandlingStickSmoothingHeli`, `HandlingStickSmoothingPlane` | 0 | the stick the airframe gets follows the mouse with this time constant in seconds (up to 0.5), taking the edge off a jerky mouse; 0 is off |
| `HandlingStickCentringHeli`, `HandlingStickCentringPlane` | 0 | while the mouse rests, the mouse stick returns to the centre at this rate per second (up to 10); useful with stick mode; 0 is off |

### About the helicopter auto throttle

`AutoThrottleDownHeli` (on by default) eases the throttle towards one half when no throttle key is
held. That is below the hover throttle of every helicopter, so a helicopter left alone slowly sinks;
it never held altitude. The rule itself is unchanged. The altitude hold of the helicopter assist is
the opt-in way to hold altitude: it flies the throttle keys against the helicopter's own vertical law
(`HelicopterModel.throttleForClimb`).

## For developers

* The shaping math is in `:core`: `core.input` (`AxisCurve` dead zone, expo and sensitivity,
  `ThrottleAxis`, `KeyRamp`, `StickFilter`, `InputCapture`) and the assists in `core.sim.assist`
  (`AutoLevel`, `CoordinatedTurn`, `AltitudeHold`), with `ThrottleServo` and the `rudder` of
  `AttitudeInput` in `core.sim.control`. All are unit tested.
* Every source ends up in the control input the mouse uses: an `AttitudeInput` for
  `VehicleAttitude.steer` each frame, and the throttle keys of the control message each tick.
  `vehicle.client.input.PilotControls` combines them; `aircraft.assist.PilotAssist` reads the vehicle
  for the assists, and the golden harness runs it in place of the client.
* `JoystickSource` is the device seam: `GlfwJoystickSource` in the game, a scripted device in the
  unit tests, the game tests (`gametest.controls`) and the dev scene's screenshots.
