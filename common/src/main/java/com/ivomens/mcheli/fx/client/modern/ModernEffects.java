package com.ivomens.mcheli.fx.client.modern;

import com.ivomens.mcheli.fx.ParticleParams;
import com.ivomens.mcheli.fx.client.legacy.LegacyEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The modern effects: soft sprite smoke, flashes and whole-explosion effects drawn by {@link ModernFx}.
 * Whatever they have no own version of is drawn the legacy way.
 */
public final class ModernEffects extends LegacyEffects {

    /** The block itself, then its four horizontal neighbours. */
    private static final int[][] DUST_OFFSETS = {{0, 0, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};

    @Override
    public void spawn(ParticleParams p) {
        if (!p.world().isClientSide()) {
            return;
        }
        if (p.name().equalsIgnoreCase("Splash")) {
            ModernFx.splash(p.x(), p.y(), p.z(), Math.max(0.5F, p.size() * 0.3F));
        } else {
            ModernFx.definedSmoke(p.x(), p.y(), p.z(), p.motionX(), p.motionY(), p.motionZ(), p.size(),
                    p.red(), p.green(), p.blue(), p.alpha(), p.age(), p.gravity(), p.diffusible());
        }
    }

    @Override
    protected boolean drawsNamed(String key, double x, double y, double z, double mx, double my, double mz) {
        return ModernFx.namedParticle(key, x, y, z, mx, my, mz);
    }

    @Override
    public boolean blockDust(Level level, int blockX, int blockY, int blockZ, double x, double y, double z,
                             double mx, double my, double mz, float scale) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int[] offset : DUST_OFFSETS) {
            pos.set(blockX + offset[0], blockY + offset[1], blockZ + offset[2]);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                ModernFx.blockDust(x, y, z, mx, my, mz, scale, state.getMapColor(level, pos).col);
                return true;
            }
        }
        return false;
    }

    @Override
    public void muzzleFlash(Level level, double x, double y, double z, float size, float r, float g, float b,
                            float a, int age) {
        ModernFx.muzzleFlash(x, y, z, size);
    }

    @Override
    public boolean drawsWholeExplosions() {
        return true;
    }

    @Override
    public boolean explosion(double x, double y, double z, float size) {
        ModernFx.explosion(x, y, z, size);
        return true;
    }

    @Override
    public boolean waterSplash(double x, double y, double z, float size) {
        ModernFx.splash(x, y, z, size);
        return true;
    }
}
