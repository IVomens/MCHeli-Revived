package com.ivomens.mcheli.combat.weapon.client;

import com.ivomens.mcheli.combat.explosion.client.ExplosionEffects;
import com.ivomens.mcheli.fx.client.ClientEffectSpawner;
import com.ivomens.mcheli.combat.explosion.net.ExplosionEffect;
import com.ivomens.mcheli.combat.explosion.net.ExplosionEffectsMessage;
import com.ivomens.mcheli.combat.weapon.net.LaserBeamMessage;
import com.ivomens.mcheli.combat.weapon.net.LockWarningMessage;
import com.ivomens.mcheli.combat.weapon.net.SoundMessage;
import com.ivomens.mcheli.networking.MessageRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Client handlers of the combat messages that concern no vehicle: explosions, laser beams, weapon sounds
 * and the lock warning. The vehicle's own (ammunition, countermeasures, hits) are the vehicle client's.
 */
public final class CombatClientMessages {

    private CombatClientMessages() {
    }

    /**
     * @param lockWarningTone the tone a lock warning sets off
     * @param effectSpawner   adds the burning flares an explosion throws to the client level
     */
    public static void register(MessageRegistry registry, LockWarningTone lockWarningTone,
                                ClientEffectSpawner effectSpawner) {
        registry.onClient(ExplosionEffectsMessage.TYPE, message -> showExplosions(message, effectSpawner));
        registry.onClient(LaserBeamMessage.TYPE, new LaserBeamHandler());
        registry.onClient(SoundMessage.TYPE, new SoundHandler());
        registry.onClient(LockWarningMessage.TYPE, message -> lockWarningTone.onLockWarning());
    }

    private static void showExplosions(ExplosionEffectsMessage message, ClientEffectSpawner effectSpawner) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (ExplosionEffect effect : message.effects()) {
            ExplosionEffects.show(level, effect, effectSpawner);
        }
    }
}
