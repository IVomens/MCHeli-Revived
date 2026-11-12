package com.ivomens.mcheli.combat.weapon.net;

import com.ivomens.mcheli.combat.countermeasure.net.ChaffUseMessage;
import com.ivomens.mcheli.combat.countermeasure.net.IronCurtainUseMessage;
import com.ivomens.mcheli.combat.explosion.net.ExplosionDetailHandler;
import com.ivomens.mcheli.combat.explosion.net.ExplosionDetailMessage;
import com.ivomens.mcheli.combat.explosion.net.ExplosionEffectsMessage;
import com.ivomens.mcheli.networking.MessageRegistry;

/**
 * Combat messages: countermeasures, weapon effects, ammunition and weapon state, hits and locks,
 * guidance input from the client, and the radar and radar warning receiver pictures. The vehicle's
 * handlers of the serverbound ones about its weapons (ammo requests, BVR locks, airburst distance) are
 * registered with the aircraft messages.
 */
public final class CombatMessages {

    private CombatMessages() {
    }

    public static void register(MessageRegistry registry) {
        registry.clientbound(ChaffUseMessage.TYPE);
        registry.clientbound(IronCurtainUseMessage.TYPE);

        registry.clientbound(ExplosionEffectsMessage.TYPE);
        registry.serverbound(ExplosionDetailMessage.TYPE, new ExplosionDetailHandler());
        registry.clientbound(LaserBeamMessage.TYPE);
        registry.clientbound(SoundMessage.TYPE);

        registry.clientbound(AmmoCountMessage.TYPE);
        registry.clientbound(AllAmmoCountsMessage.TYPE);
        registry.clientbound(WeaponStateMessage.TYPE);
        registry.clientbound(ReloadAcceptedMessage.TYPE);

        registry.clientbound(HitMarkerMessage.TYPE);
        registry.clientbound(LockWarningMessage.TYPE);
        registry.clientbound(TvMissileMessage.TYPE);
        registry.serverbound(MissileTargetMessage.TYPE, new MissileTargetHandler());
        registry.serverbound(GpsTargetMessage.TYPE, new GpsTargetHandler());

        registry.clientbound(RwrThreatsMessage.TYPE);
        registry.clientbound(RadarContactsMessage.TYPE);
    }
}
