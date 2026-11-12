package com.ivomens.mcheli.setup.client;

import com.ivomens.mcheli.aircraft.AircraftEntityTypes;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.combat.weapon.Cartridge;
import com.ivomens.mcheli.combat.weapon.CartridgeEntity;
import com.ivomens.mcheli.combat.weapon.SpentCases;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@link SpentCases} on a physical client: spawns a {@link CartridgeEntity} into the client level, thrown
 * sideways out of the gun with a little random scatter. A case whose model failed to load is not ejected.
 */
public final class SpentCaseEjector implements SpentCases {

    /** Random sideways velocity added on ejection. */
    private static final double EJECT_SCATTER = 0.07;

    @Override
    public void eject(Level level, Cartridge cartridge, Vec3 muzzle, Vec3 platformMotion, float yaw, float pitch) {
        if (!level.isClientSide || !cartridge.name.isEmpty() && ModelCache.find("bullets", cartridge.name) == null) {
            return;
        }
        Vec3 motion = new Vec3(platformMotion.x + (level.random.nextFloat() - 0.5) * EJECT_SCATTER, platformMotion.y,
                platformMotion.z + (level.random.nextFloat() - 0.5) * EJECT_SCATTER);
        CartridgeEntity entity = new CartridgeEntity(AircraftEntityTypes.CARTRIDGE.get(), level, cartridge, muzzle,
                motion, yaw, pitch);
        float ejectYaw = (yaw + cartridge.yaw) / 180.0F * (float) Math.PI;
        float ejectPitch = (pitch + cartridge.pitch) / 180.0F * (float) Math.PI;
        double dirX = -Mth.sin(ejectYaw) * Mth.cos(ejectPitch);
        double dirZ = Mth.cos(ejectYaw) * Mth.cos(ejectPitch);
        double dirY = -Mth.sin(ejectPitch);
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (length > 0.001) {
            entity.setDeltaMovement(entity.getDeltaMovement().add(dirX * cartridge.acceleration / length,
                    dirY * cartridge.acceleration / length, dirZ * cartridge.acceleration / length));
        }
        McheliClient.session().effectSpawner().addEntity(level, entity);
    }
}
