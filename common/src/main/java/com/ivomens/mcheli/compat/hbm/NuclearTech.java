package com.ivomens.mcheli.compat.hbm;

import com.ivomens.mcheli.content.def.weapon.NtmSections;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * The explosions of Hammer's Nuclear Tech that a weapon's {@code NTM} section can ask for. That mod has no
 * 1.21 version, so the payloads are read from content files (keeping them valid and round-trippable) but
 * these calls have no effect.
 */
public final class NuclearTech {

    private NuclearTech() {
    }

    public static void vnt(Level level, NtmSections.Vnt settings, double x, double y, double z, float size,
                           Entity exploder, boolean effectOnly) {
    }

    public static void nt(Level level, NtmSections.Nt settings, Entity exploder, double x, double y, double z,
                          int strength) {
    }

    public static void miniNuke(Level level, NtmSections.MiniNuke settings, double x, double y, double z,
                                boolean effectOnly) {
    }

    public static void nukeMk5(Level level, int radius, double x, double y, double z, boolean effectOnly) {
    }

    public static void chlorine(Level level, double x, double y, double z) {
    }

    public static void mist(Level level, NtmSections.Mist settings, double x, double y, double z) {
    }

    public static void fire(Level level, NtmSections.Fire settings, double x, double y, double z,
                            boolean effectOnly) {
    }
}
