package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.compat.hbm.NuclearTech;
import com.ivomens.mcheli.content.def.weapon.NtmPayload;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import static com.ivomens.mcheli.compat.ModCompatManager.MODID_HBM;
import static com.ivomens.mcheli.compat.ModCompatManager.isLoaded;

/** The payloads of HBM's Nuclear Tech mod a weapon definition can ask for, when that mod is installed. */
final class NtmPayloads {

    private NtmPayloads() {
    }

    static void detonate(ProjectileEntity p, WeaponSpec info, Vec3 at, float blockPower) {
        if (!isLoaded(MODID_HBM)) {
            return;
        }
        NtmPayload payload = info.definition().ntm();
        boolean effectOnly = info.effectOnly();
        Level level = p.level();
        double x = at.x;
        double y = at.y;
        double z = at.z;
        switch (info.payloadNTM()) {
            case NONE -> {
            }
            case NTM_VNT -> {
                if (payload.vnt() != null) {
                    NuclearTech.vnt(level, payload.vnt(), x, y, z, blockPower, p, effectOnly);
                }
            }
            case NTM_NT -> {
                if (payload.nt() != null && !effectOnly) {
                    NuclearTech.nt(level, payload.nt(), p, x, y, z, (int) blockPower);
                }
            }
            case NTM_MINI_NUKE -> {
                if (payload.miniNuke() != null) NuclearTech.miniNuke(level, payload.miniNuke(), x, y, z, effectOnly);
            }
            case NTM_NUKE -> NuclearTech.nukeMk5(level, (int) blockPower, x, y, z, effectOnly);
            case NTM_CHEMICAL -> NuclearTech.chlorine(level, x, y, z);
            case NTM_MIST -> {
                if (payload.mist() != null && !effectOnly) NuclearTech.mist(level, payload.mist(), x, y, z);
            }
            case NTM_FIRE -> {
                if (payload.fire() != null) NuclearTech.fire(level, payload.fire(), x, y, z, effectOnly);
            }
        }
    }
}
