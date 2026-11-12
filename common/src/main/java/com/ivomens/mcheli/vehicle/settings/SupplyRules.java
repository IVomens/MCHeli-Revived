package com.ivomens.mcheli.vehicle.settings;

/**
 * Whether vehicles run out of things. Server side; a vehicle takes them when it is made and on its
 * first tick, so a vehicle loaded from a save follows the current settings.
 *
 * @param infiniteAmmo weapons never run dry
 * @param infiniteFuel engines never run dry
 */
public record SupplyRules(boolean infiniteAmmo, boolean infiniteFuel) {
}
