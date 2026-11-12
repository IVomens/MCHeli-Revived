package com.ivomens.mcheli.combat.explosion.client;

import com.ivomens.mcheli.combat.CombatRandom;
import com.ivomens.mcheli.combat.countermeasure.FlareEntity;
import com.ivomens.mcheli.combat.explosion.McheliExplosion;
import com.ivomens.mcheli.combat.explosion.net.ExplosionEffect;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.ParticleParams;
import com.ivomens.mcheli.fx.client.ClientEffectSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

/**
 * Draws the explosions the server reports: a water burst under water, otherwise the MCHeli effect
 * (debris, smoke and burning flares from the blown blocks), vanilla particles, or the modern effect.
 */
public final class ExplosionEffects {

    private final Level world;
    private final Vec3 center;
    private final float size;
    private final Effects fx;
    private final RandomSource random;
    private final Consumer<Entity> flares;

    private ExplosionEffects(Level world, Vec3 center, float size, Effects fx, RandomSource random,
                             Consumer<Entity> flares) {
        this.world = world;
        this.center = center;
        this.size = size;
        this.fx = fx;
        this.random = random;
        this.flares = flares;
    }

    /** Draws the explosion the server reported; burning flares go to the level through {@code effectSpawner}. */
    public static void show(Level world, ExplosionEffect effect, ClientEffectSpawner effectSpawner) {
        if (!effect.inWater()) {
            new McheliExplosion(world, effect.pos(), effect.size(), effect.affectedBlocks()).pushEntities();
        }
        draw(world, effect.pos(), effect.size(), effect.inWater(), effect.affectedBlocks(), Effects.get(),
                McheliConfig.render().defaultExplosionParticle(), world.getRandom(), CombatRandom.of(world).blasts(),
                flare -> effectSpawner.addEntity(world, flare));
    }

    /**
     * The particles of the explosion at {@code at}, drawn with {@code fx}.
     *
     * @param blocks        the blocks it affected, in the order the server found them (legacy style only)
     * @param vanillaDebris whether the legacy style draws the blown blocks with vanilla particles
     * @param random        the debris' random numbers
     * @param splashRandom  the random numbers of the water thrown up under water
     * @param flares        takes the burning flares the explosion throws
     */
    public static void draw(Level world, Vec3 at, float size, boolean inWater, List<BlockPos> blocks, Effects fx,
                            boolean vanillaDebris, RandomSource random, RandomSource splashRandom,
                            Consumer<Entity> flares) {
        if (inWater) {
            splash(world, at, size, fx, splashRandom);
            return;
        }
        if (fx.explosion(at.x, at.y, at.z, size)) {
            return;
        }
        ExplosionEffects effects = new ExplosionEffects(world, at, size, fx, random, flares);
        if (vanillaDebris) {
            blocks.forEach(effects::vanillaDebris);
        } else {
            effects.mcheliDebris(blocks);
        }
    }

    /** Water thrown up from the water blocks inside the explosion's sphere. */
    private static void splash(Level world, Vec3 at, float size, Effects fx, RandomSource random) {
        if (size <= 0.0F) return;
        if (fx.waterSplash(at.x, at.y, at.z, size)) {
            return;
        }
        int range = (int) (size + 0.5);
        int centerX = (int) (at.x + 0.5);
        int centerY = (int) (at.y + 0.5);
        int centerZ = (int) (at.z + 0.5);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = -range; dy <= range; dy++) {
            if (centerY + dy < world.getMinBuildHeight() + 1) continue;
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    if (dx * dx + dy * dy + dz * dz >= range * range) continue;
                    pos.set(centerX + dx, centerY + dy, centerZ + dz);
                    if (world.hasChunkAt(pos) && world.getBlockState(pos).is(Blocks.WATER)) {
                        splashFrom(world, pos, dx, dz, range, fx, random);
                    }
                }
            }
        }
    }

    private static void splashFrom(Level world, BlockPos pos, int dx, int dz, int range, Effects fx,
                                   RandomSource random) {
        int count = random.nextInt(2);
        for (int i = 0; i < count; i++) {
            fx.spawn(new ParticleParams(world, "splash", pos.getX(), pos.getY(), pos.getZ(),
                    (double) dx / range * (random.nextFloat() - 0.2),
                    1.0 - Math.sqrt(dx * dx + dz * dz) / range + random.nextFloat() * 0.4 * range * 0.4,
                    (double) dz / range * (random.nextFloat() - 0.2),
                    random.nextInt(range) * 3 + range));
        }
    }

    private void mcheliDebris(List<BlockPos> blocks) {
        int flaresLeft = (int) this.size;
        int blockIndex = 0;
        for (BlockPos block : blocks) {
            blockIndex++;
            if (this.debris(blockIndex, block, flaresLeft > 0)) {
                flaresLeft--;
            }
        }
    }

    /** Debris, smoke and now and then a burning flare for one blown block; true if a flare was thrown. */
    private boolean debris(int blockIndex, BlockPos block, boolean mayThrowFlare) {
        Vec3 point = new Vec3(block.getX() + this.random.nextFloat(), block.getY() + this.random.nextFloat(),
                block.getZ() + this.random.nextFloat());
        Vec3 away = point.subtract(this.center);
        double dist = away.length();
        double speed = 0.5 / (dist / this.size + 0.1);
        speed *= this.random.nextFloat() * this.random.nextFloat() + 0.3F;
        Vec3 motion = away.scale(1.0 / dist).scale(speed * 0.5);
        Vec3 mid = point.add(this.center).scale(0.5);
        double angle = Math.PI * this.random.nextInt(360) / 180.0;
        boolean thrownFlare = this.size >= 4.0F && mayThrowFlare;
        if (thrownFlare) {
            double flareSpeed = Math.min(this.size / 12.0F, 0.6) * (0.5F + this.random.nextFloat() * 0.5F);
            this.flares.accept(new FlareEntity(this.world, mid.x, mid.y + 2.0, mid.z,
                    Math.sin(angle) * flareSpeed, (1.0 + motion.y / 5.0) * flareSpeed, Math.cos(angle) * flareSpeed,
                    2.0F, 0));
        }
        if (blockIndex % 4 == 0) {
            float dustSpeed = Math.min(this.size / 3.0F, 2.0F) * (0.5F + this.random.nextFloat() * 0.5F);
            this.fx.blockDust(this.world, (int) (mid.x + 0.5), (int) (mid.y - 0.5), (int) (mid.z + 0.5),
                    mid.x, mid.y + 1.0, mid.z, Math.sin(angle) * dustSpeed, 0.5 + motion.y / 5.0 * dustSpeed,
                    Math.cos(angle) * dustSpeed, Math.min(this.size / 2.0F, 3.0F) * (0.5F + this.random.nextFloat() * 0.5F));
        }
        int smokeEvery = (int) Math.max(this.size, 4.0F);
        if (this.size <= 1.0F || blockIndex % smokeEvery == 0) {
            this.smoke(mid, motion);
        }
        return thrownFlare;
    }

    private void smoke(Vec3 at, Vec3 motion) {
        Vec3 drift = this.random.nextBoolean() ? motion.multiply(0.1, 3.0, 0.1) : motion.multiply(3.0, 0.2, 3.0);
        ParticleParams prm = new ParticleParams(this.world, "explode", at.x, at.y, at.z, drift.x, drift.y, drift.z,
                this.size < 8.0F ? this.size * 2.0F : 16.0F);
        float grey = 0.3F + this.random.nextFloat() * 0.4F;
        prm.setRgb(grey + 0.1F, grey + 0.05F, grey);
        int age = 10 + this.random.nextInt(30);
        age = (int) (age * Math.min(this.size, 6.0F));
        prm.setAge(age * 2 / 3);
        prm.setDiffusible(true);
        this.fx.spawn(prm);
    }

    private void vanillaDebris(BlockPos block) {
        Vec3 point = new Vec3(block.getX() + this.random.nextFloat(), block.getY() + this.random.nextFloat(),
                block.getZ() + this.random.nextFloat());
        Vec3 away = point.subtract(this.center);
        double dist = away.length();
        double speed = 0.5 / (dist / this.size + 0.1);
        speed *= this.random.nextFloat() * this.random.nextFloat() + 0.3F;
        Vec3 motion = away.scale(speed / dist);
        Vec3 mid = point.add(this.center).scale(0.5);
        this.fx.vanilla("explode", mid.x, mid.y, mid.z, motion.x, motion.y, motion.z);
        this.fx.vanilla("smoke", point.x, point.y, point.z, motion.x, motion.y, motion.z);
    }
}
