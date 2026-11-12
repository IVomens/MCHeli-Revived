package com.ivomens.mcheli.combat.weapon;

public interface IBulletDecay {

    float calculateDecayFactor(float distanceTraveled);

    float calculateDamage(float initialDamage, float distanceTraveled);
}
