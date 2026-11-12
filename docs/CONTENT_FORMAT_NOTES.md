# Content format notes

Notes for pack authors who write vehicle, weapon, throwable and HUD files in YAML or in the legacy TXT
format. [ADDON_AUTHORING.md](ADDON_AUTHORING.md) covers pack layout, editor setup with the JSON Schemas,
validation and which pack wins when two define the same file.

## Messages while loading

Every content file reports each problem with the file, line and key, for example:

```
assets/mcheli/weapons/foo.yml:11 Ammo.MagSize: 0 is outside 1 to 30000, 1 is used
assets/mcheli/weapons/foo.yml:6 unknown key 'Ballistics.Acceleraton', ignored (did you mean 'Acceleration'?)
```

A value outside its range is clamped as before, and now says so. An unknown key, in YAML or TXT, is
skipped as before, and now says so too. A value of the wrong type still stops the file from loading.
List entries are counted from 1 in key paths: `Seats[2].Camera.Pos` is the camera of the second seat.

## Converting TXT weapons and throwables to YAML

The converter (`/mcheli dumpyaml`) writes every weapon and throwable setting, so a
converted file loads exactly like the original. It used to drop damage factors, dispense settings, fuel-air
explosions, beam length and radius, mark time, a `TrajectoryParticle = none` and a throwable's author.

It writes what the file says rather than what the game derives from it: a weapon without
`ExplosionBlock` gets no `DestructionPower`, which keeps following the explosion power.

Two YAML spellings were added so converted files can say everything a TXT file can:

* A colour may be a list `[a, r, g, b]` of numbers from 0 to 1 instead of hex. TXT colours (0 to 255 per
  channel) are scaled slightly differently from hex, so the converter uses the list when hex would change
  a channel; it uses hex otherwise. Muzzle flash colours accept the same list.
* `DamageFactor` values keep their fraction: `Player: 0.5` is 0.5. YAML used to drop the fraction (0.5 read
  as 0), which TXT never did.
* A vehicle that rides on several racks of the same carrier lists their numbers: `RideRack: {c5: [1, 2]}`.
  The map of one number per carrier (`c5: 2`) still reads as before; it could only name one rack, so files
  converted by McHeli CE kept the last `RideRack` line of each carrier.

## Converting TXT vehicles to YAML

The converter writes everything a TXT vehicle file says, so a converted file loads with the same values.
It used to drop extra textures, weapon bays, repelling hooks, ship rotors, wings and nozzles, plane
exhaust flames, mob drop settings, rack angles and splash particles, round positions, and flip the sign
of `OnGroundPitch`.

Converted files spell some settings the YAML way:

* HUDs are set per seat: each seat gets a `Hud` (the kind's default when the TXT file names none), and a
  one-seat vehicle's second TXT HUD becomes the seat's `GunnerHud`.
* `ExclusionSeat` pairs become `ExcludeWith` on the first seat of the pair; groups of three or more seats
  are written as `ExclusiveSeats`.
* A rack lists what it carries under `Names`.

A TXT vehicle that mounts a weapon no loaded pack defines loads without that mount, as before, and now
logs `weapon 'x' is not loaded, so its AddWeapon line is left out`. A YAML vehicle that does so still
fails to load.

### Camera height

A camera position (`Camera.Pos`, a seat's `Camera.Pos` and a rack's `Camera.Pos`) is written the same in
both formats: the numbers of the TXT line (`CameraPosition`, the camera of `AddGunnerSeat` and
`AddFixRotSeat`, the loading point of `AddRack`, which TXT writes 1.5 lower) are the YAML numbers. The game
places a camera 0.7 blocks above what the file says: 0.35 for the entity offset every position gets, as
weapon mounts do, and 0.35 more that McHeli gave its cameras.

YAML cameras used to get only the first 0.35, so every YAML vehicle looked from 0.35 blocks lower than the
same vehicle in TXT. Files converted by McHeli CE's emitter write the TXT numbers, so the bundled vehicles
and community packs converted with it now look from the height their TXT files gave them, with no change
to the files. A YAML file whose author raised its cameras by 0.35 to make up for this now sits 0.35 too
high; lower its `Pos` values by 0.35. TXT files load as before, and the converter (`/mcheli dumpyaml`)
writes their camera numbers unchanged, so a converted file loads with the same cameras.

Small differences from earlier versions:

* YAML `AmmoSupplyRange` keeps its fraction, as TXT always did.
* A YAML light hatch may open up to 1800 degrees either way; YAML wing rotations are limited to 180.
* A crawler track's `Position`, `Rotation` and `PartName` are now read.
* `BoundingBoxes: []` gives the default marker size.

## HUD scripts

A HUD file is a list of commands drawn in order, every frame. Positions are offsets from the screen
centre. Every number may be a formula over the HUD variables (`altitude`, `hp_rto`, `is_uav`, ...);
formulas are kept exactly as written, and converted files keep them too.

```yaml
- IgnoreAutoScale: true                 # draw at the design size even with HUD auto scaling on
- Color: '0xFF00FF00'                   # one ARGB formula, or a list of four: [a, r, g, b]
- DrawString:
    Text: {Fmt: 'ALT %4d', Vars: [altitude]}
    Position: [-50, -40]
    Center: false
- If: hp_rto < 0.5                      # the commands in Do are drawn while the condition is not 0
  Do:
  - DrawRectangle: {Position: [-20, 20], Size: [40, 10]}
- Call: common_pilot                    # draws another HUD in place
- Exit: exit                            # stops this HUD for the frame
```

| YAML command | Keys | TXT lines |
|---|---|---|
| `Color` | a formula, or `[a, r, g, b]` | `Color = argb` or `Color = a, r, g, b` |
| `DrawString` | `Text` (`Fmt`/`Format`, `Vars`/`Variables`), `Position`/`Pos`, `Center` | `DrawString = x, y, "format", vars...`, `DrawCenteredString = ...` |
| `DrawRectangle` (or `DrawRect`) | `Position`, `Size` | `DrawRect = x, y, width, height` |
| `DrawLine` | `Position` (a list of `[x, y]`; also `StartPos`, `Start`), `Striped`, `Pattern`/`Pat`, `Factor`/`Fac` | `DrawLine = x1, y1, x2, y2, ...`, `DrawLineStipple = pattern, factor, x1, y1, ...` |
| `DrawTexture` | `Name`/`Texture`, `Position`, `Size`, `UVPos`, `UVSize`, `Rotation`/`Rot` | `DrawTexture = name, x, y, w, h, u, v, uw, vh[, rotation]` |
| `DrawRadar` | `Position`, `Size`, `Rotation`, `EntityRadar` | `DrawEntityRadar = rotation, x, y, w, h`, `DrawEnemyRadar = ...` |
| `DrawGraduation` | `Type` (`YAW`, `PITCH`, `PITCH_ROLL`, `PITCH_ROLL_ALT`), `Position`, `Rotation`, `Roll` | `DrawGraduationYaw`, `DrawGraduationPitch1` to `3` `= rotation, roll, x, y` |
| `CameraRotation` (or `CameraRot`) | `Position` | `DrawCameraRot = x, y` |
| `Call` | the HUD name | `Call = name` |
| `Exit` | any value | `Exit` |
| `If` with `Do` | a condition and a list of commands | `If = condition` ... `Endif` |

A position or size is `[x, y]` or a map (`{x: .., y: ..}`, `{width: .., height: ..}`). `If` blocks do not
nest: in TXT a second `If` before `Endif` stops the file from loading, and in YAML an `If` inside `Do` is
reported and skipped. A TXT line with the wrong number of values, or a `DrawString` text without double
quotes, is skipped as it always was. Unknown TXT HUD keys are now reported.

When a TXT HUD is converted, a four-part colour is written as the list `[a, r, g, b]` and the
`DrawString` variables keep their spelling. A TXT HUD reloaded in game is now read whole; it used to come
back empty.

## Heat seekers, radar missiles, flares and chaff

A guided missile's seeker decides what decoys it:

| Weapon setting | Seeker | Decoyed by | Locks on |
|---|---|---|---|
| `Missile.Heat.isHeatMissile: true` (the default) | infrared | flares | flares, and not a vehicle while it drops them |
| `Missile.Radar.IsRadarMissile: true` with `isHeatMissile: false` | radar | chaff | chaff clouds, and not a vehicle while it drops chaff or runs a lock-breaking jammer |
| both `true` | both | flares and chaff | both |
| both `false` | none named | flares, as McHeli let flares decoy every missile | |

A decoyed missile in flight is lost: an air-to-air missile whose target is dropping the right decoys
self-destructs, and a vehicle that drops them destroys the missiles of that kind homing on it. An ECM jammer
of type 2 (`AircraftFeatures.ECM.Type: 2`) also makes radar missiles (and anti-radiation missiles) lose their target. Only air-to-air
missiles are decoyed in flight; anti-tank and air-to-surface missiles are not.

```yaml
Missile:
  Radar:
    IsRadarMissile: true
  Heat:
    isHeatMissile: false
```

A vehicle carries flares and chaff in the same section; flares need at least one type, chaff is on or off:

```yaml
AircraftFeatures:
  Flare:
    Pos: [0.0, 0.0, 0.0]
    Types: [SIDE]
    Chaff: true            # TXT: HasChaff = true
    ChaffUseTime: 100      # ticks a burst lasts
    ChaffWaitTime: 400     # ticks before the next burst
```

The bundled radar missiles are the AIM-120, the AIM-7 (and its Sea Sparrow version), the R-77 and R-77-1,
the R-27AE, the MIM-23 HAWK, the SA-2 and the AGM-84 and Kh-35 anti-ship missiles; the other air-to-air
missiles are heat seekers. The bundled jets and military helicopters that carry chaff dispensers in reality
have chaff as well as flares. On the BVR display a radar missile marks chaff clouds as targets, since it may
lock them.

### What the crew are told

The missile warning names the seeker of every missile homing on the vehicle, by the decoys above that
fool it, so the warning and the game never disagree: **IR** for a heat seeker (flares help), **RDR** for a radar
missile (chaff helps), **IR/RDR** for one with both seekers (either helps) and **UNK** for one no decoy fools
(none of the bundled missiles). A vehicle is warned of missiles if it has an RWR or carries flares or chaff.

| Equipment | RWR scope (both HUD styles) | Missile warning on the HUD |
|---|---|---|
| `RWR.Type: DIGITAL` (the default) | each missile tagged IR / RDR under its name | named by seeker, with the countermeasure cue |
| `RWR.Type: FOUR_WAY` or `ALL_WAY_EARLY` (analog) | missiles by bearing and strength only, as McHeli's analog sets showed unknown contacts as `?` | named by seeker only if the vehicle also carries flares or chaff |
| no RWR, flares or chaff | none | named by seeker, with the countermeasure cue |

The cue (**FLARE**, **CHAFF** or **FLARE + CHAFF**) blinks while a missile is inbound that a decoy the vehicle
carries would fool: on the modern HUD under the MISSILE warning, which also counts the missiles by seeker
(`MISSILE  IR 1  RDR 1`), and under the RWR scope. An RWR sounds its lock tone higher for a heat seeker and
lower for a radar missile, and the flare dispenser's missile alert is lower for a radar missile; with both kinds
inbound the alert alternates. HUD scripts read the counts as `msl_heat`, `msl_radar` and `msl_count`
(see `Readme_HUD.txt`):

```yaml
- If: msl_radar>0
  Do:
  - Color: '0xff4fd2ff'
  - DrawString:
      Text:
        Fmt: '[ CHAFF ]'
        Vars: []
      Position: ['-60', '62']
      Center: true
```

## Unsupported settings

The settings below were read by earlier versions but nothing in the game ever used them, so they have
been removed. Files that still contain them load as before: the value is skipped. The first time a
setting turns up during a content load, the log shows one warning naming the setting and the file, for
example:

```
Setting 'Missile.Ballistic.ArcFactor' is no longer supported and is ignored (first seen in 'weapons/foo.yml')
```

Later uses of the same setting in that load are not reported again. You can delete these lines from your
files; doing so changes nothing in game.

TXT keys are case-insensitive. YAML paths are written from the top of the file, with `[]` marking a list
entry.

### Weapons

| What it was meant to do | YAML | TXT |
|---|---|---|
| Explosion type name | `Explosion.Type` | - |
| Block damage toggle | `Explosion.CanDestroyBlocks` | `DisableDestroyBlock` |
| Weapon switch cooldown | `SwitchCooldown` | `WeaponSwitchCount` |
| Crosshair type for HUDs | `CrossType` | `CrossType` |
| Explosion damage per target type | `Damage` (the whole section: `ExplosionDamageVsLiving`, `...VsPlayer`, `...VsPlane`, `...VsVehicle`, `...VsTank`, `...VsHeli`, `...VsShip`) | `ExplosionDamageVsLiving`, `ExplosionDamageVsPlayer`, `ExplosionDamageVsPlane`, `ExplosionDamageVsVehicle`, `ExplosionDamageVsTank`, `ExplosionDamageVsHeli`, `ExplosionDamageVsShip` |
| Different seeker angle and turn rate right after launch | `Missile.InitGuidance` (the whole section: `InitMaxDegreeTick`, `InitMaxDegreeOfMissile`, `InitTurningFactorTick`, `InitTurningFactor`) | `InitMaxDegreeOfMissile`, `InitTurningFactor` |
| Semi-active radar guidance | `Missile.Radar.SemiActiveRadar` | `SemiActiveRadar` |
| Data link guidance | `Missile.EnableDataLink`, `Missile.OnlyDataLink` | `EnableDataLink`, `OnlyDataLink` |
| Helmet-mounted sight | `Missile.EnableHMS` | `EnableHMS` |
| Marker rocket spawning | `Missile.MarkerRocket` (the whole section: `Count`, `Spread`, `SpawnHeight`, `Acceleration`) | `MarkerRocketSpawnNum`, `MarkerRocketSpawnDiff`, `MarkerRocketSpawnHeight`, `MarkerRocketSpawnSpeed` |
| Missile radar signature | `Missile.Signature` (the whole section: `Front`, `Side`, `Rear`, `Time`) | `RcsFactor`, `RcsTimeFactor` |
| Ballistic arc | `Missile.Ballistic.BallisticMissile`, `.ArcFactor`, `.ArcMinHeight`, `.ArcMaxHeight`, `.MinDistance` | `BallisticMissile`, `BallisticArcFactor`, `BallisticArcMinHeight`, `BallisticArcMaxHeight`, `BallisticMinDistance` |
| Sideways weave in flight | `Missile.Ballistic.LateralSine`, `.LateralAmplitude`, `.LateralWaves`, `.LateralPhaseDeg`, `.LateralStartRatio`, `.LateralEndRatio`, `.TerminalNoWeaveDist`, `.TerminalCylinderRadius` | `BallisticLateralSine`, `BallisticLateralAmplitude`, `BallisticLateralWaves`, `BallisticLateralPhaseDeg`, `BallisticLateralStartRatio`, `BallisticLateralEndRatio`, `BallisticTerminalNoWeaveDist`, `BallisticTerminalCylinderRadius` |
| Canister type | `Missile.Ballistic.CanisterType` | `CanisterType` |
| Anti-radiation missile memory and cruise | `Missile.ARM.EmitterLostGraceTick`, `.MemoryTimeTick`, `.CruiseEnable`, `.CruiseStartDistance`, `.CruiseTerminalRadius`, `.CruiseTerminalHeight` | `ArmEmitterLostGraceTick`, `ArmMemoryTimeTick`, `ArmCruiseEnable`, `ArmCruiseStartDistance`, `ArmCruiseTerminalRadius`, `ArmCruiseTerminalHeight` |
| Ahead-fuze solve interval | `Submunition.AheadSolveIntervalTick` | `AheadSolveIntervalTick` |
| Mortar radar toggle | `Camera.EnableMortarRadar` | `EnableMortarRadar` |
| CCIP texture | `Camera.CCIPTexture` | `CCIPTexture` |
| Camera following the locked target | `Camera.FollowLockEntity`, `Camera.FollowStrength` | `CameraFollowLockEntity`, `CameraFollowStrength` |
| Trajectory particle end tick | `Render.TrajectoryParticleEndTick` | `TrajectoryParticleEndTick` |
| Exhaust flare | `Render.EnableExhaustFlare` | `EnableExhaustFlare` |
| Second bullet model after a tick | `Render.BulletModelEnd` | `ModelBulletEndTick` |
| Metal hit and weapon switch sounds | `Sound.Locations.HitMetal`, `Sound.Locations.WeaponSwitch` | `HitSoundIron`, `WeaponSwitchSound` |

`Explosion.CanDestroyBlocks` has no effect; block damage comes from `Explosion.DestructionPower`
(TXT `ExplosionBlock`).

### Vehicles

These apply to helicopters, planes, ships, tanks and vehicles unless noted.

| What it was meant to do | YAML | TXT |
|---|---|---|
| Name on the advanced air-to-air radar | `NameOnAdvancedAARadar` | `NameOnAdvancedAARadar` |
| HUD type and weapon group type for HUDs | `HUDType` (or `HudType`), `WeaponGroupType` | `HudType`, `WeaponGroupType` |
| Autopilot toggle (planes and ships) | `PlaneFeatures.EnableAutoPilot`, `ShipFeatures.EnableAutoPilot` | `EnableAutoPilot` |
| TheOneProbe preview scale | `Render.OneProbeScale` | - |
| Sign markers | `Render.SignMarkers` | `AddSign` |
| Separate muzzle flash position | `Weapons[].MuzzleFlashPos` | - |
| Parachute opening altitude for dropped vehicles | `Racks[].OpenParaAlt` | 9th value of `AddRack` (ignored without a warning) |
| Repair system | `AircraftFeatures.Maintenance: true`, `AircraftFeatures.Maintenance.UseTime`, `AircraftFeatures.Maintenance.Cooldown` | `HasMaintenance`, `MaintenanceUseTime`, `MaintenanceWaitTime` |
| Damage by impact angle and ricochet | `AircraftFeatures.ImpactAngles` | `ImpactAngleCoefficient` |
| Radar detection model | `AircraftFeatures.Radar.PanelFillAlpha`, `.FollowTurretYaw`, `.DetectChanceBase`, `.GainNearFactor`, `.GainFarFactor`, `.RcsFrontFactor`, `.RcsSideFactor`, `.RcsRearFactor`, `.RcsTimeFactor`, `.ContactHoldTick`, `.ElevationReference`, `.ElevationCoverage`, `.MinScanAltitude`, `.MaxScanAltitude`, `.SearchType`, `.TrackAzimuth`, `.TrackElevation`, `.RetargetCooldownTick` | `RadarPanelFillAlpha`, `RadarFollowTurretYaw`, `RadarDetectChanceBase`, `RadarGainFactor`, `GainFactor`, `RadarRcsFactor`, `RadarRcsFrontFactor`, `RadarRcsSideFactor`, `RadarRcsRearFactor`, `RadarRcsTimeFactor`, `RadarContactHoldTick`, `RadarElevationReference`, `RadarElevationCoverage`, `RadarMinScanAltitude`, `RadarMaxScanAltitude`, `RadarSearchType`, `RadarTrackAzimuthDeg`, `RadarTrackElevationDeg`, `RadarRetargetCooldownTick` |
| Photoelectric jammer and DIRCM | `AircraftFeatures.ECM.PhotoelectricJammer`, `AircraftFeatures.ECM.DIRCM` | `HasPhotoelectricJammer`, `HasDIRCM` |
| Weapon bays and rotating parts that follow the turret | `Components.TurretWeaponBay`, `Components.TurretRotation` | `AddPartTurretWeaponBay`, `AddPartTurretRotation` |
| Part type written by older converters | `Components.*[].Type` | - |
| Old spelling of the barrel rotation flag (use `BarrelRot`) | `Components.Weapon[].RotBarrel` | - |
| Tilt factor on a helicopter rotor blade (propellers keep theirs) | `Components.HeliRotor[].Blades[].RotFactor` | - |

`AircraftFeatures.Maintenance` still accepts `EngineShutdownThreshold` (TXT `EngineShutdownThreshold`);
only the repair settings around it are gone. The other radar settings (`Type`, `MaxTargetRange`,
`ScanAzimuth`, `ScanElevation`, `ScanTick`, `EnableBVR`, `Passive`) still work.

### Throwables

| What it was meant to do | YAML | TXT |
|---|---|---|
| Particle name | `Particle` | `Particle` |
