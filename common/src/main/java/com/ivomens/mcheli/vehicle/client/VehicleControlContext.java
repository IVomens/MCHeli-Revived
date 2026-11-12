package com.ivomens.mcheli.vehicle.client;

import com.ivomens.mcheli.aircraft.client.WeaponWheel;
import com.ivomens.mcheli.client.camera.ViewEntity;
import com.ivomens.mcheli.vehicle.client.camera.VehicleCamera;
import com.ivomens.mcheli.vehicle.client.input.PilotControls;
import com.ivomens.mcheli.vehicle.client.input.PitchLimit;

/**
 * The per-client state every vehicle control handler shares: the camera, the view entity, the seat's
 * pitch limit, the weapon wheel and the pilot's controls besides the mouse (joystick, stick keys,
 * assists). The client session makes one and hands it to each handler.
 */
public record VehicleControlContext(VehicleCamera camera, ViewEntity view, PitchLimit pitchLimit,
                                    WeaponWheel weaponWheel, PilotControls pilot) {
}
